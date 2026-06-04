---
sidebar_position: 1
---

# JUnit Integration

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

Run your LLM evaluations as JUnit tests, so a bad output fails the build the same way a broken function does.

Dokimos plugs into JUnit parameterized tests. You load a dataset, run your LLM on each example, and assert that your evaluators pass. JUnit runs the test once per example and fails fast when an output misses your threshold.

## Quick start

Three steps: add the dependency, point `@DatasetSource` at a dataset, call `Assertions.assertEval`.

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-junit</artifactId>
    <version>${dokimos.version}</version>
    <scope>test</scope>
</dependency>
```

Works with JUnit 5.x and 6.x.

Write the test:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.junit.DatasetSource;
import dev.dokimos.core.*;
import dev.dokimos.core.evaluators.*;
import org.junit.jupiter.params.ParameterizedTest;

@ParameterizedTest
@DatasetSource("classpath:datasets/support-qa.json")
void shouldAnswerSupportQuestions(Example example) {
    // Run your LLM on the example input.
    String answer = supportBot.generate(example.input());

    // Build a test case from the example plus the answer.
    EvalTestCase testCase = example.toTestCase(answer);

    // Assert the evaluator passes. The test fails if it misses its threshold.
    Evaluator correctness = LLMJudgeEvaluator.builder()
        .name("Helpfulness")
        .criteria("Is the response helpful and does it address the customer's issue?")
        .judge(judgeLM)
        .threshold(0.7)
        .build();

    Assertions.assertEval(testCase, correctness);
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.Assertions
import dev.dokimos.core.Example
import dev.dokimos.core.EvalTestCaseParam
import dev.dokimos.junit.DatasetSource
import dev.dokimos.kotlin.dsl.llmJudge
import org.junit.jupiter.params.ParameterizedTest

class SupportTests {
    private val correctness = llmJudge(judgeLM) {
        name = "Helpfulness"
        criteria = "Is the response helpful and addresses the customer's issue?"
        params(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT)
        threshold = 0.7
    }

    @ParameterizedTest
    @DatasetSource("classpath:datasets/support-qa.json")
    fun shouldAnswerSupportQuestions(example: Example) {
        val answer = supportBot.generate(example.input())
        val testCase = example.toTestCase(answer)
        Assertions.assertEval(testCase, listOf(correctness))
    }
}
```

  </TabItem>
</Tabs>

JUnit runs this test once for each example in the dataset. If any evaluator misses its threshold, the test fails.

## When to use JUnit tests

Use JUnit tests when you want fast, fail-fast checks:

- **Fast feedback during development.** A test fails the moment an output misses your criteria. You do not wait for a full evaluation run.
- **CI/CD quality gates.** Fail the build when critical test cases break, just like a regular unit test.
- **Familiar tooling.** Use the test runners, IDE integration, and reports you already have.

Reach for JUnit tests for:

- Critical examples that should never break
- Quick validation during development
- CI/CD pipelines where you want to fail fast
- Test-driven development of LLM features

Reach for experiments instead when you want:

- Analysis across large datasets
- Comparison of different models or configurations
- Detailed reports with metrics
- Exploratory evaluation of new features

See [Experiments vs JUnit Testing](../evaluation/experiments#when-to-use-experiments-vs-junit) for the full comparison.

:::tip
Your task can return a typed record, not just a string. A JUnit test reads it back with `actualOutputAs(...)` or compares it with `StructuralMatchEvaluator`. See the [Structured & Typed Data](../evaluation/structured-typed-data.md) hub.
:::

## Load a dataset

`@DatasetSource` accepts a path or inline data. Pick the form that fits.

From the classpath (for example `src/test/resources`):

```java
@DatasetSource("classpath:datasets/support-qa.json")
@DatasetSource("classpath:datasets/support-qa.jsonl")
```

From the file system:

```java
@DatasetSource("file:testdata/support-qa.json")
@DatasetSource("file:testdata/support-qa.jsonl")
```

Inline JSON for quick tests:

```java
@DatasetSource(json = """
    {
      "examples": [
        {"input": "Reset password", "expectedOutput": "Click Forgot Password"},
        {"input": "Track order", "expectedOutput": "Check Order History"}
      ]
    }
    """)
```

Inline JSONL for quick tests:

```java
@DatasetSource(jsonl = """
    {"input": "Reset password", "expectedOutput": "Click Forgot Password"}
    {"input": "Track order", "expectedOutput": "Check Order History"}
    """)
```

## Assert with assertEval

`Assertions.assertEval()` runs your evaluators and fails the test if any miss their threshold:

```java
Assertions.assertEval(testCase, evaluators);
```

When a test fails, you get a clear message:

```
Evaluation 'Answer Quality' failed: score=0.65 (threshold=0.80)
Reason: The answer is incomplete and doesn't mention the 30-day policy.
```

## Full example

This test class sets up two evaluators once, then checks every example in the dataset.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.junit.DatasetSource;
import dev.dokimos.core.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import java.util.List;

class CustomerSupportTest {

    private static List<Evaluator> evaluators;
    private static CustomerSupportBot supportBot;

    @BeforeAll
    static void setup() {
        supportBot = new CustomerSupportBot(apiKey);
        JudgeLM judge = prompt -> judgeModel.generate(prompt);

        evaluators = List.of(
            LLMJudgeEvaluator.builder()
                .name("Answer Quality")
                .criteria("Is the answer helpful and addresses the user's question?")
                .evaluationParams(List.of(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
                .threshold(0.80)
                .judge(judge)
                .build(),
            RegexEvaluator.builder()
                .name("No Placeholders")
                .pattern(".*\\[.*\\].*")  // Catch [PLACEHOLDER] text.
                .threshold(0.0)  // Should NOT match.
                .build()
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DatasetSource("classpath:datasets/support-qa-v3.json")
    void shouldAnswerSupportQuestions(Example example) {
        String response = supportBot.generate(example.input());
        EvalTestCase testCase = example.toTestCase(response);
        Assertions.assertEval(testCase, evaluators);
    }
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.Example
import dev.dokimos.core.Evaluator
import dev.dokimos.core.JudgeLM
import dev.dokimos.core.evaluators.RegexEvaluator
import dev.dokimos.core.evaluators.LLMJudgeEvaluator
import dev.dokimos.junit.DatasetSource
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest

class CustomerSupportTest {

    companion object {
        private lateinit var evaluators: List<Evaluator>
        private lateinit var supportBot: CustomerSupportBot

        @JvmStatic
        @BeforeAll
        fun setup() {
            supportBot = CustomerSupportBot(apiKey)
            val judge = JudgeLM { prompt -> judgeModel.generate(prompt) }

            evaluators =
                evaluators {
                    llmJudge(judge) {
                        name = "Answer Quality"
                        criteria = "Is the answer helpful and addresses the user's question?"
                        threshold = 0.80
                    }
                    regex {
                        name = "No Placeholders"
                        pattern = """.*\[.*\].*"""  // Catch [PLACEHOLDER] text.
                        threshold = 0.0                // Should NOT match.
                    }
                }
        }
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DatasetSource("classpath:datasets/support-qa-v3.json")
    fun shouldAnswerSupportQuestions(example: Example) {
        val response = supportBot.generate(example.input())
        val testCase = example.toTestCase(response)
        Assertions.assertEval(testCase, evaluators)
    }
}
```

  </TabItem>
</Tabs>

## Test RAG systems

For RAG, put the retrieved context in the test case so a faithfulness check can use it. Pass a map and store the context under a key like `retrievedContext`, then point `FaithfulnessEvaluator` at that key.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
@ParameterizedTest
@DatasetSource("classpath:datasets/product-docs-qa.json")
void shouldAnswerFromDocumentation(Example example) {
    // Retrieve relevant documents.
    List<String> docs = vectorStore.search(example.input(), topK = 5);

    // Generate the answer with RAG.
    String answer = ragSystem.generate(example.input(), docs);

    // Put the answer and the context in the test case.
    EvalTestCase testCase = example.toTestCase(Map.of(
        "output", answer,
        "retrievedContext", docs
    ));

    // Check both quality and faithfulness.
    Assertions.assertEval(testCase, List.of(
        LLMJudgeEvaluator.builder()
            .name("Answer Quality")
            .criteria("Is the answer helpful?")
            .evaluationParams(List.of(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
            .threshold(0.8)
            .judge(judge)
            .build(),
        FaithfulnessEvaluator.builder()
            .threshold(0.85)
            .judge(judge)
            .contextKey("retrievedContext")
            .build()
    ));
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
@ParameterizedTest
@DatasetSource("classpath:datasets/product-docs-qa.json")
fun shouldAnswerFromDocumentation(example: Example) {
    // Retrieve relevant documents.
    val docs = vectorStore.search(example.input(), topK = 5)

    // Generate the answer with RAG.
    val answer = ragSystem.generate(example.input(), docs)

    // Put the answer and the context in the test case.
    val testCase = example.toTestCase(
        mapOf(
            "output" to answer,
            "retrievedContext" to docs
        )
    )

    // Check both quality and faithfulness.
    val answerQuality = llmJudge(judge) {
        name = "Answer Quality"
        criteria = "Is the answer helpful?"
        threshold = 0.8
    }

    val faithfulness = faithfulness(judge) {
        threshold = 0.85
        contextKey = "retrievedContext"
    }

    Assertions.assertEval(testCase, listOf(answerQuality, faithfulness))
}
```

  </TabItem>
</Tabs>

## Name your tests

Set the `name` on `@ParameterizedTest` to control how each case shows up in output:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
@ParameterizedTest(name = "{index}: {0}")
@DatasetSource("classpath:datasets/support-qa.json")
void shouldAnswerQuestions(Example example) {
    // Output: "1: How do I reset my password?"
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
@ParameterizedTest(name = "{index}: {0}")
@DatasetSource("classpath:datasets/support-qa.json")
fun shouldAnswerQuestions(example: Example) {
    // Output: "1: How do I reset my password?"
}
```

  </TabItem>
</Tabs>

## Report real outputs to a server

Declare a static `@DatasetReporter` field, and `@DatasetSource` opens a run and reports each invocation as an item result. By default that item is empty.

To carry the real outputs and eval results, add a `DatasetItemRecorder` parameter to your test method and fill it in. The extension supplies a fresh recorder per invocation, so you never reset it between examples.

```java
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.Reporter;
import dev.dokimos.junit.DatasetRunExtension.DatasetItemRecorder;
import dev.dokimos.junit.DatasetReporter;
import dev.dokimos.junit.DatasetSource;
import org.junit.jupiter.params.ParameterizedTest;

class SupportEvaluationTest {

    @DatasetReporter
    static final Reporter reporter = new DokimosServerReporter(serverConfig);

    @ParameterizedTest
    @DatasetSource("classpath:datasets/support-qa.json")
    void shouldAnswerSupportQuestions(Example example, DatasetItemRecorder recorder) {
        String answer = supportBot.generate(example.input());
        EvalTestCase testCase = example.toTestCase(answer);

        recorder.actualOutput("output", answer);
        for (Evaluator evaluator : evaluators) {
            EvalResult result = evaluator.evaluate(testCase);
            recorder.evalResult(result);
        }

        Assertions.assertEval(testCase, evaluators);
    }
}
```

The recorder methods are chainable:

- `actualOutput(String key, Object value)`
- `actualOutputs(Map<String, Object> outputs)`
- `evalResult(EvalResult result)`
- `evalResults(List<EvalResult> results)`

### Add run metadata

When a `@DatasetReporter` field is present, `@DatasetSource` forwards metadata to the reporter. Use `entries` for type-safe key-value pairs:

```java
@ParameterizedTest
@DatasetSource(
    value = "classpath:datasets/support-qa.json",
    entries = {
        @MetadataEntry(key = "model", value = "gpt-4"),
        @MetadataEntry(key = "temperature", value = "0")
    })
void shouldAnswerSupportQuestions(Example example) {
    // ...
}
```

The alternating-string form `metadata = {"model", "gpt-4", "temperature", "0"}` also works. When you set both, `entries` wins.

## Run in CI/CD

### Maven

Run the tests in your pipeline:

```bash
mvn test
```

### GitHub Actions

```yaml
name: LLM Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run LLM Tests
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
        run: mvn test

      - name: Publish Test Report
        if: always()
        uses: dorny/test-reporter@v1
        with:
          name: JUnit Tests
          path: target/surefire-reports/*.xml
          reporter: java-junit
```

### Test reports

JUnit writes standard reports that CI tools read:

```
target/surefire-reports/
  ├── TEST-CustomerSupportTest.xml
  └── CustomerSupportTest.txt
```

## Run tests in parallel

JUnit 5 and 6 run tests in parallel out of the box. Use this to speed up suites with many examples.

### Turn it on

Create `src/test/resources/junit-platform.properties`:

```properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=concurrent
junit.jupiter.execution.parallel.config.fixed.parallelism=4
```

### It works with @DatasetSource

Parameterized tests that use `@DatasetSource` get parallel execution automatically:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
@ParameterizedTest
@DatasetSource("classpath:datasets/qa-dataset.json")
void shouldAnswerCorrectly(Example example) {
    String answer = assistant.answer(example.input());
    EvalTestCase testCase = example.toTestCase(answer);
    Assertions.assertEval(testCase, evaluators);
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
@ParameterizedTest
@DatasetSource("classpath:datasets/qa-dataset.json")
fun shouldAnswerCorrectly(example: Example) {
    val answer = assistant.answer(example.input())
    val testCase = example.toTestCase(answer)
    Assertions.assertEval(testCase, evaluators)
}
```

  </TabItem>
</Tabs>

With parallelism on, JUnit runs multiple examples at the same time.

### Watch for rate limits

LLM APIs have rate limits. If you hit them:

- Lower `parallelism` in the properties file.
- Or use the programmatic `Experiment` API with explicit `.parallelism()` control.

### Keep it thread-safe

Make your task implementation and any shared state thread-safe before you run tests in parallel.

## Best practices

- **Keep datasets in version control.** Store them next to your code so tests stay reproducible.
- **Start with critical examples.** Do not test everything. Focus on the cases that must never break.
- **Use clear test names.** Make it obvious what each test checks.
- **Split CI from full evaluation.** Use a small dataset for CI (10 to 20 examples) and run full evaluations separately.
- **Test at multiple levels.** Combine unit tests (JUnit) with full evaluations (Experiments) for the best coverage.
