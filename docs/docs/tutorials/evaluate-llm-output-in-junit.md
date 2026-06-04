---
sidebar_position: 2
title: "Test your LLM in JUnit: evaluate and gate model output in Java"
description: "Evaluate LLM output in JUnit with Dokimos. Add one dependency, assert model responses in Java or Kotlin, add an LLM judge, and gate quality in CI."
keywords:
  - evaluate LLM output
  - LLM testing Java
  - JUnit LLM evaluation
  - test LLM in CI
  - Dokimos
---

# Test your LLM in JUnit: evaluate and gate model output in Java

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

This page shows you how to check whether your model's output is good from inside a plain JUnit test, so a quality drop turns your build red.

Most LLM evaluation tooling is Python-first. If you ship on the JVM, that means a second language, a second toolchain, and a separate pipeline just to grade model output. Dokimos runs where your code already runs. You write the test in Java or Kotlin, run the same `mvn test` your team already runs, and let the CI that already gates your merges gate model quality too. No new service. No Python.

By the end you will have:

- A JUnit test that calls a model, asserts its output, and fails the build when quality drops
- A deterministic check (exact match), a semantic check (an LLM judge), and a structured-output check
- A dataset-driven test that runs many cases from one method

## Prerequisites

- Java 17 or later
- Maven or Gradle
- An OpenAI API key exported as `OPENAI_API_KEY`

This tutorial calls OpenAI directly through the [OpenAI Java SDK](https://github.com/openai/openai-java), so there is no framework prerequisite. If you already use Spring AI or LangChain4j, see the [Spring AI agent evaluation tutorial](./spring-ai-agent-evaluation) instead.

## Step 1: Add the dependency

Add the Dokimos JUnit integration and core library in test scope.

#### Maven

```xml
<dependencies>
    <!-- Dokimos core: evaluators and test cases -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-core</artifactId>
        <version>${dokimos.version}</version>
        <scope>test</scope>
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
        <groupId>com.openai</groupId>
        <artifactId>openai-java</artifactId>
        <version>4.11.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

#### Gradle

```groovy
dependencies {
    testImplementation 'dev.dokimos:dokimos-core:${dokimosVersion}'
    testImplementation 'dev.dokimos:dokimos-junit:${dokimosVersion}'
    testImplementation 'com.openai:openai-java:4.11.0'
}
```

See [Installation](../getting-started/installation) for the current version and other build setups.

## Step 2: Call the model and get text out

Dokimos does not call the model for you. You bring your own call and hand the result to an evaluator. Here is a small helper that calls a `gpt-5.x` model through the OpenAI Responses API and returns the output text.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;

static final OpenAIClient CLIENT = OpenAIOkHttpClient.fromEnv(); // reads OPENAI_API_KEY

static String ask(String prompt) {
    Response response = CLIENT
            .responses()
            .create(ResponseCreateParams.builder()
                    .model(ChatModel.GPT_5_2)
                    .input(prompt)
                    .build());
    return response.output().stream()
            .filter(item -> item.isMessage())
            .flatMap(item -> item.asMessage().content().stream())
            .filter(content -> content.isOutputText())
            .map(content -> content.asOutputText().text())
            .reduce("", String::concat)
            .trim();
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatModel
import com.openai.models.responses.ResponseCreateParams

val CLIENT = OpenAIOkHttpClient.fromEnv() // reads OPENAI_API_KEY

fun ask(prompt: String): String {
    val response = CLIENT.responses().create(
        ResponseCreateParams.builder()
            .model(ChatModel.GPT_5_2)
            .input(prompt)
            .build()
    )
    return response.output()
        .filter { it.isMessage }
        .flatMap { it.asMessage().content() }
        .filter { it.isOutputText }
        .joinToString("") { it.asOutputText().text() }
        .trim()
}
```

  </TabItem>
</Tabs>

`OpenAIOkHttpClient.fromEnv()` reads `OPENAI_API_KEY` from the environment, so you keep no secrets in your code.

## Step 3: Write a deterministic eval

Some questions have one correct answer: math, extraction, a known fact. For these, use `ExactMatchEvaluator`. It compares the actual output to the expected output, and the test fails when they differ.

Drive the cases from a dataset file so adding a case is a one-line edit. Create `src/test/resources/datasets/junit-tutorial-qa.json`:

```json
{
  "name": "JUnit Tutorial QA",
  "examples": [
    {
      "input": "What is the capital of France? Reply with only the city name.",
      "expectedOutput": "Paris",
      "metadata": { "category": "geography" }
    },
    {
      "input": "What is the capital of Japan? Reply with only the city name.",
      "expectedOutput": "Tokyo",
      "metadata": { "category": "geography" }
    },
    {
      "input": "What is the capital of Italy? Reply with only the city name.",
      "expectedOutput": "Rome",
      "metadata": { "category": "geography" }
    }
  ]
}
```

`@DatasetSource` turns each example into one run of a parameterized test. `example.toTestCase(answer)` builds the `EvalTestCase`. `Assertions.assertEval(...)` fails the test if any evaluator does not pass.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.Assertions;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.Evaluator;
import dev.dokimos.core.Example;
import dev.dokimos.core.evaluators.ExactMatchEvaluator;
import dev.dokimos.junit.DatasetSource;
import org.junit.jupiter.params.ParameterizedTest;

@ParameterizedTest(name = "[{index}] {0}")
@DatasetSource("classpath:datasets/junit-tutorial-qa.json")
void factualAnswerMatchesExactly(Example example) {
    String answer = ask(example.input());

    EvalTestCase testCase = example.toTestCase(answer);
    Evaluator exactMatch = ExactMatchEvaluator.builder()
            .name("Exact Match")
            .threshold(1.0)
            .build();

    Assertions.assertEval(testCase, exactMatch);
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.Assertions
import dev.dokimos.core.Example
import dev.dokimos.core.evaluators.ExactMatchEvaluator
import dev.dokimos.junit.DatasetSource
import org.junit.jupiter.params.ParameterizedTest

@ParameterizedTest(name = "[{index}] {0}")
@DatasetSource("classpath:datasets/junit-tutorial-qa.json")
fun factualAnswerMatchesExactly(example: Example) {
    val answer = ask(example.input())

    val testCase = example.toTestCase(answer)
    val exactMatch = ExactMatchEvaluator.builder()
        .name("Exact Match")
        .threshold(1.0)
        .build()

    Assertions.assertEval(testCase, exactMatch)
}
```

  </TabItem>
</Tabs>

Run it with `mvn test`. Each dataset row shows up as a separate test case in your IDE and your CI report.

## Step 4: Add an LLM judge for open-ended answers

Exact match breaks the moment an answer has more than one correct phrasing. For open-ended output, use `LLMJudgeEvaluator`. It scores the answer against criteria you write in plain English, using an LLM as the grader. Pick a cheaper model for the judge.

The judge is a [`JudgeLM`](../evaluation/evaluators#llmjudgeevaluator), a one-method functional interface that takes a prompt and returns text. So you wrap the same OpenAI client.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.Assertions;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.Evaluator;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.evaluators.LLMJudgeEvaluator;
import com.openai.models.ChatModel;
import com.openai.models.responses.ResponseCreateParams;
import java.util.List;
import org.junit.jupiter.api.Test;

JudgeLM judge() {
    return prompt -> CLIENT
            .responses()
            .create(ResponseCreateParams.builder()
                    .model(ChatModel.GPT_5_MINI)
                    .input(prompt)
                    .build())
            .output()
            .stream()
            .filter(item -> item.isMessage())
            .flatMap(item -> item.asMessage().content().stream())
            .filter(content -> content.isOutputText())
            .map(content -> content.asOutputText().text())
            .reduce("", String::concat);
}

@Test
void openEndedAnswerIsHelpful() {
    String answer = ask("In one sentence, what does an LLM evaluation framework do?");

    EvalTestCase testCase = EvalTestCase.builder()
            .input("What does an LLM evaluation framework do?")
            .actualOutput(answer)
            .build();
    Evaluator helpfulness = LLMJudgeEvaluator.builder()
            .name("Helpfulness")
            .criteria("Is the answer accurate, clear, and genuinely helpful?")
            .evaluationParams(List.of(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
            .threshold(0.7)
            .judge(judge())
            .build();

    Assertions.assertEval(testCase, helpfulness);
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.Assertions
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.EvalTestCaseParam
import dev.dokimos.core.JudgeLM
import dev.dokimos.core.evaluators.LLMJudgeEvaluator
import com.openai.models.ChatModel
import com.openai.models.responses.ResponseCreateParams
import org.junit.jupiter.api.Test

fun judge(): JudgeLM = JudgeLM { prompt ->
    CLIENT.responses().create(
        ResponseCreateParams.builder()
            .model(ChatModel.GPT_5_MINI)
            .input(prompt)
            .build()
    )
        .output()
        .filter { it.isMessage }
        .flatMap { it.asMessage().content() }
        .filter { it.isOutputText }
        .joinToString("") { it.asOutputText().text() }
}

@Test
fun openEndedAnswerIsHelpful() {
    val answer = ask("In one sentence, what does an LLM evaluation framework do?")

    val testCase = EvalTestCase.builder()
        .input("What does an LLM evaluation framework do?")
        .actualOutput(answer)
        .build()
    val helpfulness = LLMJudgeEvaluator.builder()
        .name("Helpfulness")
        .criteria("Is the answer accurate, clear, and genuinely helpful?")
        .evaluationParams(listOf(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
        .threshold(0.7)
        .judge(judge())
        .build()

    Assertions.assertEval(testCase, helpfulness)
}
```

  </TabItem>
</Tabs>

The judge returns a score in `[0, 1]`. The test passes when the score meets the `threshold`. See [LLMJudgeEvaluator](../evaluation/evaluators#llmjudgeevaluator) for scoring details.

## Step 5 (bonus): Assert on structured output

Models increasingly return JSON. Comparing JSON as a string is fragile. `21` versus `21.0`, reordered keys, and extra whitespace all break `equals`. `StructuralMatchEvaluator` compares the two payloads as JSON structures, so numbers match by value and you choose how strict to be about field sets and array order.

Ask the model for JSON, parse it into a `Map`, store it under the `output` key, and compare it against the expected contract. Then read the same output back through the typed accessor `actualOutputAs(...)`, with no manual map juggling.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.core.Assertions;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.Evaluator;
import dev.dokimos.core.evaluators.StructuralMatchEvaluator;
import dev.dokimos.core.evaluators.StructuralMatchMode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

static final ObjectMapper JSON = new ObjectMapper();

record WeatherReport(String city, int temperatureCelsius, String condition) {}

@Test
void structuredOutputMatchesContract() throws Exception {
    String raw = ask("Return ONLY compact JSON with keys city (string), temperatureCelsius "
            + "(integer), and condition (string) for this report: it is 21 degrees Celsius and "
            + "sunny in Paris. Do not wrap it in markdown.");

    Map<String, Object> actual = JSON.readValue(raw, new TypeReference<>() {});

    EvalTestCase testCase = EvalTestCase.builder()
            .input("weather report for Paris")
            .actualOutput("output", actual)
            .expectedOutput("output", Map.of(
                    "city", "Paris", "temperatureCelsius", 21, "condition", "sunny"))
            .build();

    Evaluator structuralMatch = StructuralMatchEvaluator.builder()
            .name("Structural Match")
            .mode(StructuralMatchMode.LENIENT)  // tolerate extra fields, ignore array order
            .threshold(1.0)
            .build();

    Assertions.assertEval(testCase, structuralMatch);

    // Typed accessor: read the same output back as a record.
    WeatherReport report = testCase.actualOutputAs(WeatherReport.class);
    assertEquals("Paris", report.city());
    assertEquals(21, report.temperatureCelsius());
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import dev.dokimos.core.Assertions
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.evaluators.StructuralMatchEvaluator
import dev.dokimos.core.evaluators.StructuralMatchMode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

val JSON = ObjectMapper()

data class WeatherReport(val city: String, val temperatureCelsius: Int, val condition: String)

@Test
fun structuredOutputMatchesContract() {
    val raw = ask(
        "Return ONLY compact JSON with keys city (string), temperatureCelsius " +
            "(integer), and condition (string) for this report: it is 21 degrees Celsius and " +
            "sunny in Paris. Do not wrap it in markdown."
    )

    val actual: Map<String, Any> = JSON.readValue(raw, object : TypeReference<Map<String, Any>>() {})

    val testCase = EvalTestCase.builder()
        .input("weather report for Paris")
        .actualOutput("output", actual)
        .expectedOutput("output", mapOf(
            "city" to "Paris", "temperatureCelsius" to 21, "condition" to "sunny"))
        .build()

    val structuralMatch = StructuralMatchEvaluator.builder()
        .name("Structural Match")
        .mode(StructuralMatchMode.LENIENT) // tolerate extra fields, ignore array order
        .threshold(1.0)
        .build()

    Assertions.assertEval(testCase, structuralMatch)

    // Typed accessor: read the same output back as a typed object.
    val report = testCase.actualOutputAs(WeatherReport::class.java)
    assertEquals("Paris", report.city)
    assertEquals(21, report.temperatureCelsius)
}
```

  </TabItem>
</Tabs>

`LENIENT` mode lets the model add fields you do not care about, and it ignores array order. Switch to `StructuralMatchMode.STRICT` when the contract must be exact. See [StructuralMatchEvaluator](../evaluation/evaluators#structuralmatchevaluator) for the full scoring and mode rules.

## Step 6: Gate your build in CI

Here is the payoff. These are ordinary JUnit tests, so any CI that runs your tests already gates on them. When the model regresses below your thresholds, the build goes red.

The only setup is making the API key available. In GitHub Actions:

```yaml
name: LLM Evaluation

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  evaluate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Run LLM evaluations
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
        run: mvn test
```

:::tip Keep model calls off the critical path
Tests that hit a live model cost money and add latency. A common pattern is to tag them and run the full set on a schedule or on demand, while keeping every commit fast. Annotate model-calling tests with JUnit's `@Tag("integration")` and gate them on the key with `@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")`, then run them with `mvn verify -Dgroups=integration`.
:::

## Next steps

- Browse every built-in evaluator in the [Evaluators reference](../evaluation/evaluators)
- Read the [JUnit integration guide](../integrations/junit) for more `@DatasetSource` options
- Evaluating tool-using agents? See [Agent evaluation](../evaluation/agent-evaluation)
- Track scores over time and compare runs with the [Dokimos Server](../server/overview)

## Resources

- [Tutorial example code](https://github.com/dokimos-dev/dokimos/tree/master/dokimos-examples/src/test/java/dev/dokimos/examples/junit5): the complete, compiling test from this tutorial
- [OpenAI Java SDK](https://github.com/openai/openai-java)
- [Dokimos GitHub repository](https://github.com/dokimos-dev/dokimos)

---

If this saved you from standing up a Python pipeline just to test your model, consider giving the repository a star on GitHub ⭐.
