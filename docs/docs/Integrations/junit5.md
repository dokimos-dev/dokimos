---
sidebar_position: 1
---

# JUnit 5 Integration

Dokimos integrates seamlessly with JUnit 5's parameterized tests, enabling users to test LLM applications with dataset-driven tests that fail fast on regressions.

## Motivation

### Quality Gates in CI/CD
Run automated tests in your CI/CD pipeline and fail the build when LLM outputs don't meet quality criteria—just like traditional unit tests.

### Fast Feedback
Unlike [Experiments](../evaluation/experiments) which aggregate results for analysis, JUnit tests fail immediately on the first failing example, giving instant feedback during development.

### Familiar Testing Pattern
Leverage JUnit 5's ecosystem: test runners, IDE integration, reporting tools, and test lifecycle management—all standard Java developer tooling.

### When to Use
- **CI/CD pipelines**: Catch regressions before merging
- **Test-driven development (TDD)**: Write tests as you develop features
- **Critical examples**: Ensure important cases never break
- **Quick validation**: Fast iteration during development

### When to Use Experiments Instead
- **Performance analysis** across entire datasets
- **Benchmark comparisons** between models, prompt versions, or breaking changes 
- **Detailed reporting** with aggregated metrics
- **Exploratory evaluation** of AI/LLM behavior

See [Experiments vs JUnit Testing](../evaluation/experiments#how-experiments-differ-from-junit-testing) for a detailed comparison.

## Setup

Add the JUnit 5 integration dependency:

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-junit5</artifactId>
    <version>${dokimos.version}</version>
    <scope>test</scope>
</dependency>
```

## Basic Usage

### 1. The @DatasetSource Annotation

Use `@DatasetSource` to load [datasets](../evaluation/datasets) as parameterized test arguments:

```java
import dev.dokimos.junit5.DatasetSource;
import dev.dokimos.core.*;
import org.junit.jupiter.params.ParameterizedTest;

@ParameterizedTest
@DatasetSource("classpath:datasets/qa-dataset.json")
void testQA(Example example) {
    // Your LLM application
    String answer = myLlmService.generate(example.input());
    
    // Convert to test case
    EvalTestCase testCase = example.toTestCase(answer);
    
    // Assert evaluation passes
    Assertions.assertEval(testCase, evaluators);
}
```

**How it works:**
1. JUnit loads examples from your dataset
2. Runs the test method once for each example
3. Test fails if any evaluator doesn't pass its threshold

### 2. Loading Datasets

Load from classpath resources:

```java
@DatasetSource("classpath:datasets/qa-dataset.json")
```

Load from file system:

```java
@DatasetSource("file:src/test/resources/datasets/qa-dataset.json")
```

Or use inline JSON for quick tests:

```java
@DatasetSource(json = """
    {
      "examples": [
        {"input": "What is 2+2?", "expectedOutput": "4"},
        {"input": "What is 3*3?", "expectedOutput": "9"}
      ]
    }
    """)
```

See [Dataset Resolution](../evaluation/datasets#dataset-resolution) for all supported formats.

### 3. The assertEval Method

`Assertions.assertEval()` evaluates the test case and throws `AssertionError` if any evaluator fails:

```java
Assertions.assertEval(testCase, evaluators);
```

**On failure, you get a descriptive message:**
```
Evaluation 'Answer Correctness' failed: score=0.65 (threshold=0.80), 
reason=The answer is incomplete and missing key details.
```

## Complete Example

```java
import dev.dokimos.junit5.DatasetSource;
import dev.dokimos.core.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import java.util.List;

class CustomerSupportTest {
    
    private static List<Evaluator> evaluators;
    private static MyLLMService llmService;
    
    @BeforeAll
    static void setup() {
        llmService = new MyLLMService();
        
        evaluators = List.of(
            ExactMatchEvaluator.builder()
                .threshold(0.8)
                .build(),
            LLMJudgeEvaluator.builder()
                .name("Answer Quality")
                .criteria("Is the answer helpful and accurate?")
                .threshold(0.75)
                .judge(prompt -> llmService.evaluationModel(prompt))
                .build()
        );
    }
    
    @ParameterizedTest(name = "[{index}] {0}")
    @DatasetSource("classpath:datasets/customer-support-v2.json")
    void shouldAnswerCustomerQuestions(Example example) {
        // Generate response
        String response = llmService.generate(example.input());
        
        // Convert to test case
        EvalTestCase testCase = example.toTestCase(response);
        
        // Assert all evaluators pass
        Assertions.assertEval(testCase, evaluators);
    }
}
```

## Advanced Usage

### Testing RAG Systems

Test RAG applications by including retrieved context:

```java
@ParameterizedTest
@DatasetSource("classpath:datasets/rag-qa-v2.json")
void shouldAnswerWithContext(Example example) {
    // Retrieve context
    List<String> context = vectorStore.retrieve(example.input());
    
    // Generate answer
    String answer = ragService.generate(example.input(), context);
    
    // Create test case with context
    Map<String, Object> outputs = Map.of(
        "output", answer,
        "retrievedContext", context
    );
    EvalTestCase testCase = example.toTestCase(outputs);
    
    // Evaluate with faithfulness
    List<Evaluator> evaluators = List.of(
        FaithfulnessEvaluator.builder()
            .threshold(0.8)
            .judge(judge)
            .contextKey("retrievedContext")
            .build()
    );
    
    Assertions.assertEval(testCase, evaluators);
}
```

### Multiple Evaluators

Test different quality dimensions:

```java
@BeforeAll
static void setup() {
    JudgeLM judge = prompt -> openAI.generate(prompt);
    
    evaluators = List.of(
        // Check factual correctness
        LLMJudgeEvaluator.builder()
            .name("Correctness")
            .criteria("Is the answer factually correct?")
            .threshold(0.85)
            .judge(judge)
            .build(),
        
        // Check answer completeness
        LLMJudgeEvaluator.builder()
            .name("Completeness")
            .criteria("Does the answer fully address the question?")
            .threshold(0.75)
            .judge(judge)
            .build(),
        
        // Check format (e.g., must be a single paragraph)
        RegexEvaluator.builder()
            .name("Format Check")
            .pattern("^[^\\n]+$")  // No line breaks
            .threshold(1.0)
            .build()
    );
}
```

The test fails if **any** evaluator doesn't meet its threshold.

### Custom Test Names

Make test output more readable:

```java
@ParameterizedTest(name = "Question: {0}")
@DatasetSource("classpath:datasets/qa.json")
void testQA(Example example) {
    // Test will display as: "Question: What is the capital of France?"
}
```

### Conditional Test Execution

Skip tests based on environment:

```java
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@ParameterizedTest
@DatasetSource("classpath:datasets/qa.json")
void testWithOpenAI(Example example) {
    // Only runs if OPENAI_API_KEY is set
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

## Best Practices

### 1. Version Control Your Datasets

Keep datasets in version control alongside code:

```java
@DatasetSource("classpath:datasets/v2/customer-support.json")
```

This makes sure that tests are reproducible across executions and environments.

## Troubleshooting

### Dataset Not Found

Ensure dataset path is correct:

```java
@DatasetSource("classpath:datasets/qa-v2.json")  // ✅ In src/test/resources/datasets/
@DatasetSource("file:data/qa-v2.json")           // ✅ Relative to project root
```

See [Dataset Resolution](../evaluation/datasets#dataset-resolution) for details.

## API Reference

### @DatasetSource

```java
@DatasetSource(value = "classpath:datasets/qa.json")  // Load from URI
@DatasetSource(json = "{ ... }")                      // Inline JSON
```

### Assertions

```java
// With List<Evaluator>
Assertions.assertEval(testCase, evaluators);

// With varargs
Assertions.assertEval(testCase, evaluator1, evaluator2);
```