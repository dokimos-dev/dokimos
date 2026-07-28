---
sidebar_position: 3
title: "Generate conversation test data: build a multi-turn LLM regression suite in Java"
description: "Generate multi-turn conversation test data with Dokimos. Describe scenarios as seeds, record real conversations against your app, commit the dataset, and replay it in JUnit and CI."
keywords:
  - generate LLM test data
  - synthetic conversation dataset
  - multi-turn LLM testing
  - conversation golden dataset
  - LLM regression test Java
  - Dokimos
---

# Generate conversation test data: build a multi-turn LLM regression suite in Java

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

This page shows you how to synthesize a set of multi-turn conversations against your own chat application, commit them as a dataset, and replay them on every pull request.

Single-prompt tests miss the failures that only show up on turn three. The agent forgets the order number it just asked for, contradicts a policy it stated two turns ago, or answers a question nobody asked. To catch those you need conversations, and typing conversations by hand is slow work that goes stale the moment your prompt changes. `GoldenGenerator` records them for you. You describe each conversation once as a seed, run it against your application, and keep the resulting transcript as a fixture.

By the end you will have:

- A support desk that answers over a real chat model and is driven turn by turn by Dokimos
- Three recorded conversations, two scripted and one written by a simulated user
- A committed dataset file and a JUnit test that replays every conversation in it
- A GitHub Actions job that fails the build when a conversation stops reaching its goal

Want to run the finished code first? The [complete example](https://github.com/dokimos-dev/dokimos/tree/master/dokimos-examples/src/main/java/dev/dokimos/examples/conversation/goldens) is in the repository, with the [replay test](https://github.com/dokimos-dev/dokimos/blob/master/dokimos-examples/src/test/java/dev/dokimos/examples/conversation/goldens/SupportGoldenReplayTest.java) alongside it under `src/test/java`. Everything below builds it step by step.

This page is the walkthrough. For the field-by-field reference on seeds and generated goldens, see [Generating Conversation Goldens](../evaluation/multi-turn-conversations#generating-conversation-goldens).

## Prerequisites

- Java 17 or later
- Maven or Gradle
- An OpenAI API key exported as `OPENAI_API_KEY`

This tutorial calls the model through [LangChain4j](https://github.com/langchain4j/langchain4j), but any client works. Dokimos never calls the model itself, it calls your code.

If you are running the example straight from a clone of the repository, install the modules once first. `exec:java` resolves Dokimos from your local Maven repository, not from the reactor, so compiling alone is not enough:

```bash
mvn clean install -DskipTests
```

## Step 1: Add the dependencies

#### Maven

```xml
<dependencies>
    <!-- Dokimos core: GoldenGenerator, ScenarioSeed, evaluators -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-core</artifactId>
        <version>${dokimos.version}</version>
    </dependency>

    <!-- Dokimos LangChain4j integration: LangChain4jSupport.asJudge -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-langchain4j</artifactId>
        <version>${dokimos.version}</version>
    </dependency>

    <!-- Dokimos JUnit integration: @DatasetSource -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-junit</artifactId>
        <version>${dokimos.version}</version>
        <scope>test</scope>
    </dependency>

    <!-- The model client used in this tutorial -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai</artifactId>
        <version>1.11.0</version>
    </dependency>
</dependencies>
```

#### Gradle

```groovy
dependencies {
    implementation 'dev.dokimos:dokimos-core:${dokimosVersion}'
    implementation 'dev.dokimos:dokimos-langchain4j:${dokimosVersion}'
    implementation 'dev.langchain4j:langchain4j-open-ai:1.11.0'
    testImplementation 'dev.dokimos:dokimos-junit:${dokimosVersion}'
}
```

See [Installation](../getting-started/installation) for the current version and other build setups.

## Step 2: Make your application conversational

Dokimos drives your application through `ConversationalApplication`. It is a one-method interface: you get the conversation so far, you return the next assistant message.

Here is the application this tutorial evaluates. It is a support desk for a fictional kitchen appliance store, and its policies are what the suite will hold it to.

```java
import dev.dokimos.core.conversation.ConversationTrajectory;
import dev.dokimos.core.conversation.ConversationalApplication;
import dev.dokimos.core.conversation.Message;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.ArrayList;
import java.util.List;

public class SupportDesk implements ConversationalApplication {

    public static final String MODEL_ID = "gpt-4o-mini";

    private static final String SYSTEM_PROMPT = """
            You are the support agent for Kettleworks, an online kitchen appliance store.

            Policies:
            - Returns are accepted within 30 days of delivery.
            - A refund can only be issued once you have the order number.
            - Delivery takes 5 to 7 business days.
            - Shipping is free on orders above 50 USD.
            - An order that has already shipped cannot be changed, but it can be returned.

            Answer in at most three sentences. Ask for the order number when you need it, and
            name the relevant policy when it applies.
            """;

    private final ChatModel model;

    public SupportDesk(ChatModel model) {
        this.model = model;
    }

    public static SupportDesk withOpenAi(String apiKey) {
        return new SupportDesk(
                OpenAiChatModel.builder().apiKey(apiKey).modelName(MODEL_ID).build());
    }

    @Override
    public Message respond(ConversationTrajectory trajectory) {
        List<ChatMessage> history = new ArrayList<>();
        history.add(SystemMessage.from(SYSTEM_PROMPT));
        for (Message message : trajectory.messages()) {
            history.add(toChatMessage(message));
        }
        return Message.assistant(model.chat(history).aiMessage().text());
    }

    private static ChatMessage toChatMessage(Message message) {
        return switch (message.role()) {
            case USER -> UserMessage.from(message.content());
            case ASSISTANT -> AiMessage.from(message.content());
            case SYSTEM -> SystemMessage.from(message.content());
        };
    }
}
```

The desk keeps no per-conversation state. Every call replays the whole trajectory to the model, so one instance can answer several conversations, which is exactly what the generator asks it to do: it holds a single application and hands it every seed. If your application keeps per-conversation state of its own, key that state off the trajectory, or run one generator per seed and concatenate the datasets.

## Step 3: Describe the conversations as seeds

A seed is one conversation you want recorded. There are two kinds.

**Scripted seeds** carry a fixed list of user turns. The generator replays them verbatim, so no LLM writes the user side and the customer half of the transcript is byte-stable across runs. Use these for the paths you care about most: the refund flow, the pre-purchase question, the policy the agent keeps getting wrong.

**Persona-driven seeds** carry an opening message and a persona factory. The persona reads what your application just said and writes the next user turn against it. Use these to find the turns you would never have thought to script.

Both kinds carry an `expectedOutcome`, a plain-English description of where the conversation is supposed to end up. It does not steer or stop the simulation. It rides along in metadata so that Step 7 can grade against it.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.conversation.ScenarioSeed;
import dev.dokimos.core.conversation.UserPersonas;
import java.util.List;

// Scripted: fixed user turns, no LLM on the user side.
ScenarioSeed refund = ScenarioSeed.builder()
        .scenario("Refund for a blender that arrived cracked")
        .userTurns(List.of(
                "My blender arrived with a cracked jug and I want my money back",
                "The order number is KW-4471, it was delivered last Tuesday"))
        .expectedOutcome("The agent asks for the order number and then confirms the refund is being processed")
        .metadata("suite", "support")
        .build();

ScenarioSeed shipping = ScenarioSeed.builder()
        .scenario("Delivery and shipping cost before ordering")
        .userTurns(List.of(
                "How long does delivery take if I order a kettle today?",
                "Do I pay for shipping on a 60 dollar order?"))
        .expectedOutcome("The agent states the 5 to 7 business day delivery window and says shipping is free"
                + " on orders above 50 USD")
        .metadata("suite", "support")
        .build();

// Persona-driven: the judge writes each user turn against the desk's answers.
ScenarioSeed confusedReturn = ScenarioSeed.builder()
        .scenario("Confused customer wants to send an item back")
        .initialMessage("I think I have to send something back but I have no idea where to start")
        .personaFactory(UserPersonas::confusedUser)
        .expectedOutcome("The agent explains the 30 day return window and tells the customer what to do next")
        .metadata("suite", "support")
        .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.conversation.UserPersonas
import dev.dokimos.kotlin.dsl.conversation.scenarioSeed

// Scripted: fixed user turns, no LLM on the user side.
val refund = scenarioSeed {
    scenario = "Refund for a blender that arrived cracked"
    userTurns(
        listOf(
            "My blender arrived with a cracked jug and I want my money back",
            "The order number is KW-4471, it was delivered last Tuesday",
        ),
    )
    expectedOutcome = "The agent asks for the order number and then confirms the refund is being processed"
    metadata("suite", "support")
}

val shipping = scenarioSeed {
    scenario = "Delivery and shipping cost before ordering"
    userTurns(
        listOf(
            "How long does delivery take if I order a kettle today?",
            "Do I pay for shipping on a 60 dollar order?",
        ),
    )
    expectedOutcome = "The agent states the 5 to 7 business day delivery window and says shipping is free" +
        " on orders above 50 USD"
    metadata("suite", "support")
}

// Persona-driven: the judge writes each user turn against the desk's answers.
val confusedReturn = scenarioSeed {
    scenario = "Confused customer wants to send an item back"
    initialMessage = "I think I have to send something back but I have no idea where to start"
    personaFactory = UserPersonas::confusedUser
    expectedOutcome = "The agent explains the 30 day return window and tells the customer what to do next"
    metadata("suite", "support")
}
```

  </TabItem>
</Tabs>

`UserPersonas` ships several personas. `confusedUser` misunderstands instructions and needs them again, `aggressiveCustomer` demands immediate resolution, `noviceUser` asks the fundamental questions, `adversarialUser` tries to talk your application out of its rules. Pick the one that matches the failure you are hunting.

Anything you put in `metadata` is copied onto the generated example, so `suite` here is a filter you can use later when the file holds more than one product area.

## Step 4: Generate the suite

The generator takes your application, the seeds, and a judge. The judge is only used for persona-driven seeds: it is the model that plays the customer. A scripted suite needs no judge at all.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.conversation.GoldenGenerator;
import dev.dokimos.langchain4j.LangChain4jSupport;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.nio.file.Path;

String apiKey = System.getenv("OPENAI_API_KEY");

SupportDesk supportDesk = SupportDesk.withOpenAi(apiKey);
JudgeLM judge = LangChain4jSupport.asJudge(OpenAiChatModel.builder()
        .apiKey(apiKey)
        .modelName(SupportDesk.MODEL_ID)
        .build());

GoldenGenerator generator = GoldenGenerator.builder()
        .application(supportDesk)
        .judge(judge)
        .name("support-goldens")
        .description("Synthetic multi-turn support conversations recorded against SupportDesk")
        .maxTurns(3)
        .seed(refund)
        .seed(shipping)
        .seed(confusedReturn)
        .build();

generator.write(Path.of("src/test/resources/datasets/support-goldens.json"));
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.kotlin.dsl.conversation.goldenGenerator
import dev.dokimos.langchain4j.LangChain4jSupport
import dev.langchain4j.model.openai.OpenAiChatModel
import java.nio.file.Path

val apiKey = System.getenv("OPENAI_API_KEY")

val supportDesk = SupportDesk.withOpenAi(apiKey)
val judgeLM = LangChain4jSupport.asJudge(
    OpenAiChatModel.builder()
        .apiKey(apiKey)
        .modelName(SupportDesk.MODEL_ID)
        .build(),
)

val generator = goldenGenerator {
    application = supportDesk
    judge = judgeLM
    name = "support-goldens"
    description = "Synthetic multi-turn support conversations recorded against SupportDesk"
    maxTurns = 3
    seed(refund)
    seed(shipping)
    seed(confusedReturn)
}

generator.write(Path.of("src/test/resources/datasets/support-goldens.json"))
```

  </TabItem>
</Tabs>

`maxTurns(3)` caps the persona-driven conversation. It does not pad the scripted ones: a script stops at its last user turn, so the two scripted seeds run two turns each, because the cap is higher than either script. A cap lower than the script truncates it.

Run it. From a clone of the repository:

```bash
export OPENAI_API_KEY='your-api-key'
mvn exec:java -pl dokimos-examples \
  -Dexec.mainClass="dev.dokimos.examples.conversation.goldens.GenerateSupportGoldens"
```

The example prints one block per conversation:

```text
Generating goldens against gpt-4o-mini...

golden-0: Refund for a blender that arrived cracked
  turns:   2
  outcome: The agent asks for the order number and then confirms the refund is being processed
  opening: My blender arrived with a cracked jug and I want my money back

golden-1: Delivery and shipping cost before ordering
  turns:   2
  outcome: The agent states the 5 to 7 business day delivery window and says shipping is free on orders above 50 USD
  opening: How long does delivery take if I order a kettle today?

golden-2: Confused customer wants to send an item back
  turns:   3
  outcome: The agent explains the 30 day return window and tells the customer what to do next
  opening: I think I have to send something back but I have no idea where to start

Wrote 3 goldens to /path/to/dokimos/dokimos-examples/src/test/resources/datasets/support-goldens.json
Replay them with: RUN_EVAL_TESTS=true mvn test -pl dokimos-examples -Dtest=SupportGoldenReplayTest
```

Note the path. Run from the root of the repository, the example writes into the `dokimos-examples` module. In your own single-module project the file lands at the path in the snippet above, `src/test/resources/datasets/support-goldens.json`.

:::tip Use a judge you trust
This example uses one model for both the desk and the judge, which keeps the tutorial short. When the grade matters, judge with a different or stronger model than the one under test.
:::

## Step 5: Look at what came out

The generator wrote a dataset file. Open it before you trust it. Here is `golden-0` from the run that produced the committed file. The desk answers over a live model, so your own run words things differently:

```json
{
  "inputs" : {
    "input" : "Scenario: Refund for a blender that arrived cracked\n\nUSER: My blender arrived with a cracked jug and I want my money back\n\nASSISTANT: I'm sorry to hear that your blender arrived damaged. Please provide your order number so I can initiate the refund process for your return, as we accept returns within 30 days of delivery.\n\nUSER: The order number is KW-4471, it was delivered last Tuesday\n\nASSISTANT: Thank you for providing your order number, KW-4471. Since it was delivered last Tuesday, you are within our 30-day return policy, and I will process your refund for the returned blender. Please make sure to initiate the return as we cannot change an order that has already shipped."
  },
  "expectedOutputs" : {
    "output" : "Thank you for providing your order number, KW-4471. Since it was delivered last Tuesday, you are within our 30-day return policy, and I will process your refund for the returned blender. Please make sure to initiate the return as we cannot change an order that has already shipped."
  },
  "metadata" : {
    "expectedOutcome" : "The agent asks for the order number and then confirms the refund is being processed",
    "scenario" : "Refund for a blender that arrived cracked",
    "suite" : "support",
    "turnCount" : 2
  },
  "id" : "golden-0"
}
```

Read the transcript first. The desk asked for the order number before promising anything and named the 30 day window without being asked, which is the behavior the seed was written to pin down. `expectedOutputs["output"]` holds the desk's last reply, a baseline of what it says today.

The persona-driven conversation ends differently. Here is the tail of `golden-2`:

```json
"expectedOutputs" : { },
"metadata" : {
  "expectedOutcome" : "The agent explains the 30 day return window and tells the customer what to do next",
  "scenario" : "Confused customer wants to send an item back",
  "suite" : "support",
  "turnCount" : 3
}
```

No default golden answer. Grading an application against its own reply proves nothing, so a persona-driven seed gets none. The `expectedOutcome` is what you grade it on instead. Set `expectedOutput("output", ...)` on the seed if you have a reference answer of your own.

For the full list of fields the generator writes, and the rules that decide which ones appear, see [What a Golden Looks Like](../evaluation/multi-turn-conversations#what-a-golden-looks-like).

One more thing to know before you commit: a seed whose run fails still produces an example, carrying `error` and `errorSource` in metadata. A rate limit on the third conversation does not cost you the first two, but it does leave a truncated transcript in the file, so scan for those keys before committing.

## Step 6: Commit the suite

The generated file is a fixture, so treat it like one. Commit it to `src/test/resources/datasets/` and let code review see it.

```bash
git add dokimos-examples/src/test/resources/datasets/support-goldens.json
```

That is the path from the root of this repository. In your own project it is `src/test/resources/datasets/support-goldens.json`.

Generation is stateless. Every call re-runs the seeds against the live model, so regenerating is a deliberate act, not something to wire into a build. Rerun it when you change the system prompt, swap the model, or change a policy, then read the diff. That diff is the record of how your application's behavior moved.

Two properties make the diff readable. Key order in the written file is stable, so the diff only ever covers text that really changed. Scripted user turns are byte-identical run over run, so the desk's replies are the only part of a scripted golden that moves. Persona-driven conversations are written fresh each time, user side included, so expect them to change even when nothing about your application did.

## Step 7: Replay the suite in JUnit

`@DatasetSource` reads the generated file directly and turns each conversation into one test case.

Replay the user side, not the whole transcript. `inputs["input"]` holds the entire conversation, the desk's own replies included, so sending it back as a prompt would hand the model the answer it is being graded on. Pull the recorded `USER:` turns out of the transcript instead, feed them to `ConversationSimulator` as a script, and let the desk answer them again. What the judge grades is the conversation the desk produces today, which is what makes this a regression test rather than a re-reading of the fixture.

`ConversationTrajectory.toTestCase(tools, tasks)` builds the test case `TaskCompletionEvaluator` reads: the transcript as the input, the tasks in metadata. The desk calls no tools, so that list is empty, and the single task is the `expectedOutcome` the golden carries.

```java
import static org.assertj.core.api.Assertions.assertThat;

import dev.dokimos.core.Assertions;
import dev.dokimos.core.Example;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.conversation.ConversationSimulator;
import dev.dokimos.core.conversation.ConversationTrajectory;
import dev.dokimos.core.conversation.Message;
import dev.dokimos.core.conversation.SimulatedUser;
import dev.dokimos.core.evaluators.agents.TaskCompletionEvaluator;
import dev.dokimos.junit.DatasetSource;
import dev.dokimos.langchain4j.LangChain4jSupport;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;

@EnabledIfEnvironmentVariable(named = "RUN_EVAL_TESTS", matches = "true")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class SupportGoldenReplayTest {

    private static final List<String> ROLE_PREFIXES = List.of("USER: ", "ASSISTANT: ", "SYSTEM: ");

    private SupportDesk supportDesk;
    private TaskCompletionEvaluator outcomeReached;

    @BeforeEach
    void setup() {
        String apiKey = System.getenv("OPENAI_API_KEY");

        supportDesk = SupportDesk.withOpenAi(apiKey);

        JudgeLM judge = LangChain4jSupport.asJudge(OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(SupportDesk.MODEL_ID)
                .build());

        outcomeReached = TaskCompletionEvaluator.builder()
                .name("Expected Outcome")
                .judge(judge)
                .threshold(1.0)
                .build();
    }

    @ParameterizedTest
    @DatasetSource("classpath:datasets/support-goldens.json")
    void shouldReachTheExpectedOutcome(Example example) {
        String expectedOutcome = example.metadataAs("expectedOutcome", String.class);
        assertThat(expectedOutcome)
                .as("golden %s carries no expectedOutcome to grade against", example.datasetItemId())
                .isNotNull();

        List<String> userTurns = recordedUserTurns(example.input());
        assertThat(userTurns)
                .as("golden %s records no user turn to replay", example.datasetItemId())
                .isNotEmpty();

        ConversationTrajectory replay = ConversationSimulator.builder()
                .application(supportDesk)
                .simulatedUser(scriptOf(userTurns))
                .scenario(example.metadataAs("scenario", String.class))
                .maxTurns(userTurns.size())
                .build()
                .simulate();

        Assertions.assertEval(replay.toTestCase(List.of(), List.of(expectedOutcome)), outcomeReached);
    }

    private static SimulatedUser scriptOf(List<String> userTurns) {
        return trajectory -> {
            int index = trajectory.userMessages().size();
            return Message.user(index < userTurns.size() ? userTurns.get(index) : "");
        };
    }

    private static List<String> recordedUserTurns(String transcript) {
        List<String> turns = new ArrayList<>();
        StringBuilder current = null;
        for (String line : transcript.split("\n")) {
            if (startsATurn(line)) {
                if (current != null) {
                    turns.add(current.toString().strip());
                }
                current = line.startsWith("USER: ") ? new StringBuilder(line.substring("USER: ".length())) : null;
            } else if (current != null) {
                current.append("\n").append(line);
            }
        }
        if (current != null) {
            turns.add(current.toString().strip());
        }
        return turns;
    }

    private static boolean startsATurn(String line) {
        return ROLE_PREFIXES.stream().anyMatch(line::startsWith);
    }
}
```

`scriptOf` is the same trick the generator uses for a scripted seed: hand back the next recorded turn and ignore what the application just said. So a persona-driven golden replays as deterministically on the user side as a scripted one, once it is recorded.

The two `@EnabledIfEnvironmentVariable` gates keep the model out of your normal build and skip cleanly when there is no key to call it with. Without `RUN_EVAL_TESTS`, the test is skipped and `mvn test` costs nothing. With it:

```bash
RUN_EVAL_TESTS=true OPENAI_API_KEY='your-api-key' mvn test -pl dokimos-examples -Dtest=SupportGoldenReplayTest
```

```text
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 11.87 s -- in dev.dokimos.examples.conversation.goldens.SupportGoldenReplayTest
```

Each conversation is its own test case, so a regression fails on the case it belongs to, with the judge's verdict attached:

```text
java.lang.AssertionError: Evaluation 'Expected Outcome' failed: score=0.00 (threshold=1.00), reason=0/1 tasks completed.
```

`TaskCompletionEvaluator` scores the fraction of tasks completed. With one outcome per conversation the score is 1.0 or 0.0, so the threshold only decides how the failure reads. Give a conversation several tasks when you want partial credit.

## Step 8: Gate it in CI

Keep generation out of CI. It costs money on every run, and a persona-driven seed writes a different conversation each time, so a build that generates its own fixtures fails in ways you cannot reproduce. CI replays the committed file against your application and nothing else.

```yaml
name: Conversation Goldens

on:
  push:
    branches: [master]
  pull_request:
    branches: [master]

jobs:
  replay:
    name: Replay conversation goldens
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'maven'

      - name: Replay the committed suite
        env:
          RUN_EVAL_TESTS: 'true'
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
        run: mvn -B test
```

`RUN_EVAL_TESTS` is the whole switch. Your existing build job runs without it and stays fast, and this job turns the replay on. If the suite grows past what you want to pay for per pull request, split it: a nightly schedule for the whole file, pull requests for the conversations that matter most.

:::warning Secrets and fork pull requests
GitHub does not pass repository secrets to pull requests from forks, so `OPENAI_API_KEY` arrives empty, the `@EnabledIfEnvironmentVariable` gate skips every case, and the job goes green having graded nothing. Guard the step with `if: github.event_name == 'push' || github.event.pull_request.head.repo.full_name == github.repository` so it is visibly not run, which keeps the `push` trigger covering merged work.
:::

## Next steps

- Read the reference on seeds and generated fields in [Generating Conversation Goldens](../evaluation/multi-turn-conversations#generating-conversation-goldens)
- Score the conversation itself, turn by turn, with the [multi-turn evaluators](../evaluation/multi-turn-conversations)
- Grading a tool-using agent instead? See [Agent evaluation](../evaluation/agent-evaluation)
- Load, filter, and split generated files with the [Datasets guide](../evaluation/datasets)
- Track scores run over run and compare two suites with the [Dokimos Server](../server/overview)

## Resources

- [Tutorial example code](https://github.com/dokimos-dev/dokimos/tree/master/dokimos-examples/src/main/java/dev/dokimos/examples/conversation/goldens): the runnable package from this tutorial
- [GenerateSupportGoldens.java](https://github.com/dokimos-dev/dokimos/blob/master/dokimos-examples/src/main/java/dev/dokimos/examples/conversation/goldens/GenerateSupportGoldens.java): the seeds and the generator
- [SupportDesk.java](https://github.com/dokimos-dev/dokimos/blob/master/dokimos-examples/src/main/java/dev/dokimos/examples/conversation/goldens/SupportDesk.java): the application under test
- [SupportGoldenReplayTest.java](https://github.com/dokimos-dev/dokimos/blob/master/dokimos-examples/src/test/java/dev/dokimos/examples/conversation/goldens/SupportGoldenReplayTest.java): the replay test
- [LangChain4j](https://github.com/langchain4j/langchain4j)
- [Dokimos GitHub repository](https://github.com/dokimos-dev/dokimos)

---

If this saved you an afternoon of typing conversation fixtures by hand, consider giving the repository a star on GitHub ⭐.
