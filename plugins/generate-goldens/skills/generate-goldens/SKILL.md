---
name: generate-goldens
description: Generates multi-turn conversation goldens for Dokimos with GoldenGenerator and ScenarioSeed. Use this skill whenever the user wants synthetic conversation test data, a multi-turn regression suite, goldens for a chatbot or support agent, or wants to turn scenarios into a dataset their tests can replay. Also triggers when the user asks about ScenarioSeed, scripted versus persona-driven seeds, or simulated users.
---

# Generate Conversation Goldens

Generate a multi-turn conversation dataset for Dokimos. The user will describe the conversations to synthesize via `$ARGUMENTS`.

`GoldenGenerator` runs each `ScenarioSeed` through `ConversationSimulator` against the application under test and turns every resulting conversation into one dataset example. Generate a suite once, commit it, replay it in CI.

## Where things live

- **Generator**: `dokimos-core/src/main/java/dev/dokimos/core/conversation/GoldenGenerator.java`
- **Seed**: `dokimos-core/src/main/java/dev/dokimos/core/conversation/ScenarioSeed.java`
- **Personas**: `dokimos-core/src/main/java/dev/dokimos/core/conversation/UserPersonas.java`
- **Kotlin DSL**: `dokimos-kotlin/src/main/kotlin/dev/dokimos/kotlin/dsl/conversation/ConversationDsl.kt`
- **Worked example**: `dokimos-examples/src/main/java/dev/dokimos/examples/conversation/goldens/`

Read `GoldenGenerator.java` and `ScenarioSeed.java` before generating, and check `UserPersonas.java` for the available personas.

## Seed types

A seed is one conversation to synthesize. It is either scripted or persona-driven, never both and never neither.

**Scripted**: a fixed list of user turns, replayed verbatim. No judge is needed on the user side, and the run stops at the last user turn even when `maxTurns` is higher.

```java
ScenarioSeed refund = ScenarioSeed.scripted(
        "Refund for a broken product",
        List.of("My blender arrived broken and I want a refund", "The order number is #123"));
```

Setting `initialMessage` on a scripted seed replaces the first turn, so `userTurns.get(0)` is never sent. Leave it empty, as `ScenarioSeed.scripted` does, to let the script drive every turn.

**Persona-driven**: a factory that receives the generator's `JudgeLM` and returns a `SimulatedUser`, so the user side is written by a model. The factory is applied at generation time, so a method reference works without a judge in scope.

```java
ScenarioSeed escalation = ScenarioSeed.builder()
        .scenario("Angry customer escalates")
        .initialMessage("This product broke on day one!")
        .personaFactory(UserPersonas::aggressiveCustomer)
        .expectedOutcome("The agent apologizes and offers a replacement or refund")
        .maxTurns(6)
        .build();
```

Personas available as method references: `aggressiveCustomer`, `confusedUser`, `impatientUser`, `satisfiedCustomer`, `technicalExpert`, `noviceUser`, `adversarialUser`, `offTopicUser`. `UserPersonas.custom(judge, persona, guidelines)` needs a lambda.

## Generating

```java
GoldenGenerator generator = GoldenGenerator.builder()
        .application(app)          // required, a ConversationalApplication
        .judge(judgeLM)            // only needed for persona-driven seeds
        .name("support-goldens")
        .maxTurns(10)              // default limit for seeds without their own
        .seed(refund)
        .seed(escalation)
        .build();

generator.write(Path.of("src/test/resources/datasets/support-goldens.json"));
```

```kotlin
val generator = goldenGenerator {
    application = app
    judge = judgeLM
    name = "support-goldens"

    seed {
        scenario = "Refund for a broken product"
        userTurns(listOf("My blender arrived broken and I want a refund", "The order number is #123"))
        expectedOutcome = "The agent asks for the order number and then issues a refund"
    }
}
```

`toJson()`, `toJsonl()`, `write(path)` and `writeJsonl(path)` all emit the shape `Dataset` parses, so a generated file feeds `@DatasetSource` directly.

## What a golden looks like

Each seed produces one example, in seed order:

| Field | Value |
|-------|-------|
| `inputs["input"]` | the rendered transcript of the whole conversation |
| `expectedOutputs["output"]` | the application's last reply, for scripted seeds only |
| `metadata` | `scenario`, `turnCount`, `expectedOutcome` when set, the seed's own metadata, and `error` plus `errorSource` if the run failed |
| `id` | `golden-0`, `golden-1`, and so on |

A persona-driven seed gets no default answer, because grading an application against its own reply proves nothing. Set one with `expectedOutput("output", ...)` on the seed, which also overrides the scripted default.

## Steps

1. Establish what the application under test is, and whether it already implements `ConversationalApplication`. If not, wrap it: `respond(trajectory)` answers `trajectory.lastUserMessage()` given the turns so far.
2. Decide the seed mix from `$ARGUMENTS`. Prefer scripted seeds for the paths that must not regress, and persona-driven seeds for open-ended behavior. Persona seeds need a judge and cost API calls per turn.
3. Give every seed an `expectedOutcome`. It is the criterion a judge grades the replay against, and it never stops the simulation.
4. Write the generator as a small runnable class rather than wiring it into the build. Generation is stateless, so every call re-runs the seeds against the live model.
5. Write the file to `src/test/resources/datasets/`, then read it before committing. Scan for `error` and `errorSource` keys, which mark seeds whose run failed.
6. Add the replay test, then run it once to confirm it passes.

## Replaying the goldens

Never feed `inputs["input"]` back to the application as a prompt. It is the whole transcript, assistant replies included, so it hands the model the answer it is being graded on.

To grade the recording as it stands, judge the transcript against the criterion in metadata:

```java
@ParameterizedTest
@DatasetSource("classpath:datasets/support-goldens.json")
void replaysGoldens(Example example) {
    String expectedOutcome = example.metadataAs("expectedOutcome", String.class);
    assertThat(expectedOutcome).isNotNull();

    EvalTestCase testCase = EvalTestCase.builder()
        .input(example.input())
        .metadata("tasks", List.of(expectedOutcome))
        .build();

    Assertions.assertEval(testCase, TaskCompletionEvaluator.builder().judge(judgeLM).build());
}
```

To gate the application instead of the recording, replay only the recorded `USER:` turns through `ConversationSimulator` and grade the conversation that comes back. `SupportGoldenReplayTest` in `dokimos-examples` does this.

## Guidelines

- Start with 3 to 5 seeds. Each persona-driven seed costs one model call per turn on both sides.
- Keep `maxTurns` low, 2 to 6, unless the scenario genuinely needs a long conversation.
- Commit the generated file and review its diff like any other fixture. Key order is stable, so a diff means the behavior moved.
- Regenerate deliberately, after a prompt, model or policy change, not on every build.
- Scripted user turns are byte-identical run over run. Persona conversations are rewritten each time, so expect them to change even when the application did not.
