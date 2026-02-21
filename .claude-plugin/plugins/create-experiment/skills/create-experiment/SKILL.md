---
name: create-experiment
description: Scaffolds a Dokimos Experiment that wires together a dataset, task, evaluators, and optional reporter. Use this skill when the user wants to create an evaluation experiment, set up an eval pipeline, run evaluations against a dataset, or build an end-to-end evaluation workflow. Also use when the user says "evaluate my LLM" or "test my model".
---

# Create Experiment

Scaffold a Dokimos Experiment. The user will describe the evaluation goal via `$ARGUMENTS`.

## Where things live

- **Experiment class**: `dokimos-core/src/main/java/dev/dokimos/core/Experiment.java`
- **Task interface**: `dokimos-core/src/main/java/dev/dokimos/core/Task.java`
- **Example experiments**: `dokimos-examples/src/main/java/dev/dokimos/examples/`

Before writing code, read these files to understand the API:
- `Experiment.java` — the orchestrator
- `dokimos-examples/src/main/java/dev/dokimos/examples/basic/BasicEvaluationExample.java` — simplest example

## Experiment anatomy

An experiment consists of four parts:

1. **Dataset** — the test cases (see `create-dataset` skill)
2. **Task** — a function that takes an `Example` and returns `Map<String, Object>` of actual outputs
3. **Evaluators** — one or more `Evaluator` implementations that score the outputs
4. **Reporter** (optional) — sends results to the Dokimos server or elsewhere

### Basic template

```java
Dataset dataset = Dataset.fromJson(Path.of("src/test/resources/datasets/my-dataset.json"));

Task task = example -> {
    String input = example.input();
    String output = callYourLLM(input);
    return Map.of("output", output);
};

List<Evaluator> evaluators = List.of(
        ExactMatchEvaluator.builder()
                .name("Exact Match")
                .threshold(1.0)
                .build()
);

ExperimentResult result = Experiment.builder()
        .name("My Evaluation")
        .description("Evaluating my LLM on QA tasks")
        .dataset(dataset)
        .task(task)
        .evaluators(evaluators)
        .build()
        .run();

System.out.println("Pass rate: " + result.passRate());
```

### With JUnit integration

```java
public class MyEvaluationTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @DatasetSource("classpath:datasets/my-dataset.json")
    void testMyLLM(Example example) {
        String actualOutput = callYourLLM(example.input());
        EvalTestCase testCase = example.toTestCase(actualOutput);

        List<Evaluator> evaluators = List.of(
                ExactMatchEvaluator.builder().build()
        );

        assertEval(testCase, evaluators);
    }
}
```

### With server reporting

```java
Reporter reporter = DokimosServerReporter.builder()
        .baseUrl("http://localhost:8080")
        .build();

ExperimentResult result = Experiment.builder()
        .name("My Evaluation")
        .dataset(dataset)
        .task(task)
        .evaluators(evaluators)
        .reporter(reporter)
        .build()
        .run();
```

## Builder options

| Method | Description | Default |
|--------|-------------|---------|
| `.name(String)` | Experiment name | `"unnamed"` |
| `.description(String)` | Description | `""` |
| `.dataset(Dataset)` | Test dataset | **required** |
| `.task(Task)` | System under test | **required** |
| `.evaluator(Evaluator)` | Add a single evaluator | — |
| `.evaluators(List)` | Add multiple evaluators | — |
| `.reporter(Reporter)` | Result reporter | `NoOpReporter` |
| `.parallelism(int)` | Concurrent examples | `1` |
| `.runs(int)` | Repeat count for variance reduction | `1` |
| `.metadata(String, Object)` | Add experiment metadata | — |

## Steps

1. Determine from `$ARGUMENTS` what the user wants to evaluate
2. Choose the right integration (plain Java, LangChain4j, Spring AI, Koog)
3. Create or reference a dataset
4. Define the task function wrapping the user's system
5. Select appropriate evaluators for the use case
6. Wire everything together with `Experiment.builder()`
7. Add a JUnit test if the experiment should run in CI
