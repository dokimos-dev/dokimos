---
sidebar_position: 1
---

# JUnit Integration

Dokimos integrates with JUnit's parameterized tests so you can test LLM applications the same way you test regular code - with fast-failing tests that catch regressions.

## Why Use JUnit Integration?

**Fast feedback during development** - Tests fail immediately when an output doesn't meet your criteria. You don't have to wait for a full evaluation run to finish.

**CI/CD quality gates** - Fail your build if critical test cases don't pass, just like you would with regular unit tests.

**Familiar tooling** - Use the JUnit tools you already know: test runners, IDE integration, and reporting.

**When to use JUnit tests:**
- Testing critical examples that should never break
- Quick validation during development
- CI/CD pipelines where you want to fail fast
- Test-driven development of LLM features

**When to use experiments instead:**
- Analyzing performance across large datasets
- Comparing different models or configurations
- Generating detailed reports with metrics
- Exploratory evaluation of new features

See [Experiments vs JUnit Testing](../evaluation/experiments#how-experiments-differ-from-junit-testing) for more details.

## Setup

Add the JUnit integration dependency:

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-junit</artifactId>
    <version>${dokimos.version}</version>
    <scope>test</scope>
</dependency>
```

> **Note:** Supports JUnit 5.x and 6.x.

## Basic Usage

### Using @DatasetSource

Load datasets with the `@DatasetSource` annotation:

```java
import dev.dokimos.junit.DatasetSource;
import dev.dokimos.core.*;
import org.junit.jupiter.params.ParameterizedTest;

@ParameterizedTest
@DatasetSource("classpath:datasets/support-qa.json")
void shouldAnswerSupportQuestions(Example example) {
    // Generate answer from your LLM
    String answer = supportBot.generate(example.input());
    
    // Create test case
    EvalTestCase testCase = example.toTestCase(answer);
    
    // Assert evaluators pass (fails test if they don't)
    Assertions.assertEval(testCase, evaluators);
}
```

JUnit runs this test once for each example in the dataset. If any evaluator doesn't pass its threshold, the test fails.

### Loading Datasets

From classpath (like `src/test/resources`):
```java
@DatasetSource("classpath:datasets/support-qa.json")
```

From file system:
```java
@DatasetSource("file:testdata/support-qa.json")
```

Inline for quick tests:
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

### Using assertEval

`Assertions.assertEval()` runs your evaluators and fails the test if any don't pass:

```java
Assertions.assertEval(testCase, evaluators);
```

When a test fails, you get a clear error message:
```
Evaluation 'Answer Quality' failed: score=0.65 (threshold=0.80)
Reason: The answer is incomplete and doesn't mention the 30-day policy.
```

## Complete Example

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
                .threshold(0.80)
                .judge(judge)
                .build(),
            RegexEvaluator.builder()
                .name("No Placeholders")
                .pattern(".*\\[.*\\].*")  // Catch [PLACEHOLDER] text
                .threshold(0.0)  // Should NOT match
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

## Advanced Usage

### Testing RAG Systems

For RAG applications, include the retrieved context in your test case:

```java
@ParameterizedTest
@DatasetSource("classpath:datasets/product-docs-qa.json")
void shouldAnswerFromDocumentation(Example example) {
    // Retrieve relevant documents
    List<String> docs = vectorStore.search(example.input(), topK = 5);
    
    // Generate answer with RAG
    String answer = ragSystem.generate(example.input(), docs);
    
    // Include context in test case
    EvalTestCase testCase = example.toTestCase(Map.of(
        "output", answer,
        "retrievedContext", docs
    ));
    
    // Check both quality and faithfulness
    Assertions.assertEval(testCase, List.of(
        LLMJudgeEvaluator.builder()
            .name("Answer Quality")
            .criteria("Is the answer helpful?")
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

### Readable Test Names

Customize how tests appear in output:

```java
@ParameterizedTest(name = "{index}: {0}")
@DatasetSource("classpath:datasets/support-qa.json")
void shouldAnswerQuestions(Example example) {
    // Output: "1: How do I reset my password?"
}
```

## CI/CD Integration

### Maven

Run tests in your CI pipeline:

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

### Test Reports

JUnit generates standard test reports that integrate with CI tools:

```
target/surefire-reports/
  ├── TEST-CustomerSupportTest.xml
  └── CustomerSupportTest.txt
```

## Parallel Test Execution

JUnit 5 supports parallel test execution out of the box. This speeds up evaluation suites with many examples.

### Configuration

Create `src/test/resources/junit-platform.properties`:

```properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=concurrent
junit.jupiter.execution.parallel.config.fixed.parallelism=4
```

### With @DatasetSource

Parameterized tests using `@DatasetSource` automatically benefit from parallel execution:

```java
@ParameterizedTest
@DatasetSource("classpath:datasets/qa-dataset.json")
void shouldAnswerCorrectly(Example example) {
    String answer = assistant.answer(example.input());
    EvalTestCase testCase = example.toTestCase(answer);
    Assertions.assertEval(testCase, evaluators);
}
```

With parallelism enabled, JUnit runs multiple examples concurrently.

### Rate Limit Considerations

LLM APIs have rate limits. If you hit rate limits:

- Reduce `parallelism` in the properties file
- Or use the programmatic `Experiment` API with explicit `.parallelism()` control

### Thread Safety

Ensure your task implementation and any shared state is thread-safe when running tests in parallel.

## Best Practices

**Keep datasets in version control** - Store them alongside your code so tests are reproducible.

**Start with critical examples** - Don't try to test everything. Focus on the most important cases that should never break.

**Use clear test names** - Make it obvious what each test is checking.

**Separate CI and comprehensive testing** - Use a smaller dataset for CI (maybe 10-20 examples) and run full evaluations separately.

**Test at multiple levels** - Combine unit tests (JUnit) with comprehensive evaluations (Experiments) for best coverage.