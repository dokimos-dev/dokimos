---
name: create-tests
description: Scaffolds eval-driven tests using dokimos-junit. Use this skill when the user wants to create evaluation tests, set up eval-driven development, write parameterized evaluation tests, use @DatasetSource, or test their LLM application with Dokimos in a JUnit test. Also use when the user mentions eval-driven testing, evaluation as unit tests, or continuous evaluation in CI.
---

# Create Eval-Driven Tests

Scaffold evaluation tests using `dokimos-junit` that run Dokimos evaluations as JUnit parameterized tests. The user will describe what they want to test via `$ARGUMENTS`.

This approach — eval-driven development — lets users treat LLM evaluations like unit tests: each dataset example becomes a test case, evaluators act as assertions, and the whole thing runs in CI with `mvn test`.

## Where things live

- **Assertions**: `dokimos-core/src/main/java/dev/dokimos/core/Assertions.java`
- **DatasetSource annotation**: `dokimos-junit/src/main/java/dev/dokimos/junit/DatasetSource.java`
- **Example class**: `dokimos-core/src/main/java/dev/dokimos/core/Example.java`
- **Real-world example**: `dokimos-examples/src/main/java/dev/dokimos/examples/junit/QuestionAnsweringTest.java`

Before writing code, read `QuestionAnsweringTest.java` to see a complete working example.

## How it works

`dokimos-junit` bridges Dokimos datasets and JUnit's `@ParameterizedTest`. Each `Example` in a dataset becomes a separate test invocation. Inside the test, you call your LLM, convert the example to an `EvalTestCase` with the actual output, and run evaluators via `Assertions.assertEval()`.

The test fails (throws `AssertionError`) if any evaluator's score falls below its threshold — just like a regular assertion.

## Core pattern

```java
import dev.dokimos.core.*;
import dev.dokimos.core.evaluators.*;
import dev.dokimos.junit.DatasetSource;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.List;

class MyEvalTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @DatasetSource("classpath:datasets/my-dataset.json")
    void shouldPassEvaluations(Example example) {
        // 1. Call your system under test
        String response = callMyLLM(example.input());

        // 2. Convert example to test case with actual output
        EvalTestCase testCase = example.toTestCase(response);

        // 3. Define evaluators
        List<Evaluator> evaluators = List.of(
                ExactMatchEvaluator.builder().build()
        );

        // 4. Assert all evaluators pass
        Assertions.assertEval(testCase, evaluators);
    }
}
```

## Dataset source options

`@DatasetSource` supports three modes:

```java
// From classpath resource (most common in tests)
@DatasetSource("classpath:datasets/qa.json")

// From file path
@DatasetSource("src/test/resources/datasets/qa.json")

// Inline JSON (useful for small, self-contained tests)
@DatasetSource(json = """
    {
      "examples": [
        {"input": "What is 2+2?", "expectedOutput": "4"},
        {"input": "Capital of France?", "expectedOutput": "Paris"}
      ]
    }
    """)

// Inline JSONL
@DatasetSource(jsonl = """
    {"input": "What is 2+2?", "expectedOutput": "4"}
    {"input": "Capital of France?", "expectedOutput": "Paris"}
    """)
```

## Converting examples to test cases

`Example` provides two `toTestCase` methods:

```java
// Simple: single output (stored as "output" key)
EvalTestCase testCase = example.toTestCase(response);

// Multi-output: for RAG or multi-field evaluations
EvalTestCase testCase = example.toTestCase(Map.of(
        "output", response,
        "retrievalContext", retrievedDocs
));
```

The multi-output form is needed when evaluators require additional data beyond the main output (e.g., `FaithfulnessEvaluator` needs a context key, `ContextualRelevanceEvaluator` needs a retrieval context key).

## Patterns by use case

### Simple Q&A evaluation

```java
@ParameterizedTest(name = "[{index}] {0}")
@DatasetSource("classpath:datasets/qa.json")
void shouldAnswerCorrectly(Example example) {
    String answer = myLLM.generate(example.input());
    EvalTestCase testCase = example.toTestCase(answer);

    Assertions.assertEval(testCase,
            ExactMatchEvaluator.builder().build());
}
```

### LLM-judged evaluation

When exact matching is too rigid, use `LLMJudgeEvaluator` for semantic evaluation. It requires `evaluationParams` (which test case fields to include in the judge prompt) and a `JudgeLM` instance.

```java
@ParameterizedTest(name = "[{index}] {0}")
@DatasetSource("classpath:datasets/qa.json")
void shouldAnswerCorrectly(Example example) {
    String answer = myLLM.generate(example.input());
    EvalTestCase testCase = example.toTestCase(answer);

    Assertions.assertEval(testCase,
            LLMJudgeEvaluator.builder()
                    .name("answer-quality")
                    .criteria("Does the actual output correctly answer the question?")
                    .evaluationParams(List.of(
                            EvalTestCaseParam.INPUT,
                            EvalTestCaseParam.EXPECTED_OUTPUT,
                            EvalTestCaseParam.ACTUAL_OUTPUT))
                    .threshold(0.7)
                    .judge(myJudge)
                    .build());
}
```

### RAG evaluation with context

For RAG pipelines, pass retrieval context alongside the output. Use a consistent context key and set it explicitly on evaluators that need it.

```java
@ParameterizedTest(name = "[{index}] {0}")
@DatasetSource("classpath:datasets/rag.json")
void shouldBeGroundedInContext(Example example) {
    String query = example.input();
    List<String> retrievedDocs = vectorStore.search(query);
    String answer = myRAG.generate(query, retrievedDocs);

    EvalTestCase testCase = example.toTestCase(Map.of(
            "output", answer,
            "context", retrievedDocs
    ));

    Assertions.assertEval(testCase, List.of(
            FaithfulnessEvaluator.builder()
                    .judge(judge)
                    .contextKey("context")
                    .threshold(0.7)
                    .build(),
            ContextualRelevanceEvaluator.builder()
                    .judge(judge)
                    .retrievalContextKey("context")
                    .threshold(0.5)
                    .build()
    ));
}
```

### Integration test with external API

Tests calling real LLM APIs should be tagged so they don't run in every `mvn test` invocation.

```java
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class MyIntegrationEvalTest {

    private static JudgeLM judge;

    @BeforeAll
    static void setup() {
        // Set up your LLM client and judge here
        judge = prompt -> callOpenAI(prompt);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DatasetSource("classpath:datasets/qa.json")
    void shouldPassEvaluations(Example example) {
        // ...
    }
}
```

Run integration tests with: `mvn verify -Dgroups=integration`

## Dependencies

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-junit</artifactId>
    <version>${dokimos.version}</version>
    <scope>test</scope>
</dependency>
```

`dokimos-junit` transitively includes `dokimos-core`, so you don't need both.

## Steps

1. Determine from `$ARGUMENTS` what the user wants to evaluate (Q&A, RAG, chat, etc.)
2. Create or identify a dataset for the test (use the `create-dataset` skill if needed)
3. Choose the right evaluators for the use case
4. Place the dataset in `src/test/resources/datasets/`
5. Create the test class with `@ParameterizedTest` and `@DatasetSource`
6. Wire `Example.toTestCase()` with the appropriate output keys
7. Use `Assertions.assertEval()` to run the evaluations
8. Tag as `@Tag("integration")` if calling external APIs

## Checklist

- [ ] Test uses `@ParameterizedTest` with `@DatasetSource`
- [ ] Dataset is in `src/test/resources/datasets/` (or inline for small tests)
- [ ] `Example.toTestCase()` is called with the right output structure
- [ ] `Assertions.assertEval()` is used (not manual assertion on scores)
- [ ] Evaluators that need context have explicit key configuration
- [ ] `LLMJudgeEvaluator` has `.evaluationParams(...)` and `.judge(...)` set
- [ ] Tests calling external APIs are tagged with `@Tag("integration")`
