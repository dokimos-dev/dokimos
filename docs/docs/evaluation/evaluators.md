---
sidebar_position: 4
---

# Evaluators

**Evaluators** assess the quality of your LLM's outputs by comparing actual outputs against expected results or quality criteria. Each evaluator produces by default a score (0.0 to 1.0) and determines success based on a configurable threshold.

## The Evaluator Interface

All evaluators implement the `Evaluator` interface:

```java
public interface Evaluator {
    EvalResult evaluate(EvalTestCase testCase);
    String name();
    double threshold();
}
```

An `EvalResult` contains:
- **score**: Numeric score (0.0 - 1.0)
- **success**: Whether score meets threshold
- **reason**: Explanation of the score
- **metadata**: Additional evaluation data

## Built-in Evaluators

### ExactMatchEvaluator

Checks if actual and expected outputs match exactly (case-sensitive).

```java
Evaluator evaluator = ExactMatchEvaluator.builder()
    .name("Exact Match")
    .threshold(1.0)
    .build();
```

**Returns:**
- Score `1.0` if outputs match exactly
- Score `0.0` otherwise

**Use cases:** Factual answers, calculations, deterministic outputs.

### RegexEvaluator

Checks if the actual output matches a regular expression pattern.

```java
Evaluator evaluator = RegexEvaluator.builder()
    .name("Pattern Match")
    .pattern("\\d{4}-\\d{2}-\\d{2}")  // Date format: YYYY-MM-DD
    .ignoreCase(false)
    .threshold(1.0)
    .build();
```

**Configuration:**
- `pattern`: Regular expression pattern
- `ignoreCase`: Case-insensitive matching (default: `false`)

**Use cases:** Format validation, pattern matching, structured outputs.

### LLMJudgeEvaluator

Uses an LLM to evaluate outputs based on custom criteria.

```java
JudgeLM judge = prompt -> myLlm.generate(prompt);

Evaluator evaluator = LLMJudgeEvaluator.builder()
    .name("Answer Correctness")
    .criteria("Is the answer factually correct and complete?")
    .evaluationParams(List.of(
        EvalTestCaseParam.INPUT,
        EvalTestCaseParam.EXPECTED_OUTPUT,
        EvalTestCaseParam.ACTUAL_OUTPUT
    ))
    .threshold(0.8)
    .judge(judge)
    .build();
```

**Configuration:**
- `criteria`: Evaluation criteria as natural language
- `evaluationParams`: Which test case parts to include (input, expected, actual, metadata)
- `scoreRange`: Expected score range (default: 0.0-1.0)
- `judge`: LLM function to call

**Use cases:** Semantic similarity, relevance, coherence, custom quality criteria.

### FaithfulnessEvaluator

Evaluates how well the actual output is grounded in provided context (for RAG systems).

```java
JudgeLM judge = prompt -> myLlm.generate(prompt);

Evaluator evaluator = FaithfulnessEvaluator.builder()
    .threshold(0.7)
    .judge(judge)
    .contextKey("retrievedContext")
    .includeReason(true)
    .build();
```

**How it works:**
1. Extracts claims from the actual output
2. Extracts facts from the retrieved context
3. Verifies each claim against the context using an LLM
4. Score = (supported claims) / (total claims)

**Configuration:**
- `contextKey`: Key in actualOutputs containing the context (default: `"context"`)
- `includeReason`: Include detailed claim verdicts (default: `true`)

**Use cases:** RAG systems, fact-checking, grounding verification.

## Configuration Options

All evaluators support these common configurations:

### Name

Identify the evaluator in results:

```java
.name("My Custom Evaluator")
```

### Threshold

Set the minimum score for success:

```java
.threshold(0.8)  // 80% required to pass
```

### Evaluation Parameters

Specify which test case components to include:

```java
.evaluationParams(List.of(
    EvalTestCaseParam.INPUT,
    EvalTestCaseParam.EXPECTED_OUTPUT,
    EvalTestCaseParam.ACTUAL_OUTPUT
))
```

Available parameters:
- `INPUT`: The input to the task
- `EXPECTED_OUTPUT`: Expected output
- `ACTUAL_OUTPUT`: Actual output produced
- `METADATA`: Example metadata

## Creating Custom Evaluators

Implement the `Evaluator` interface or extend `BaseEvaluator`:

```java
public class CustomEvaluator extends BaseEvaluator {
    
    public CustomEvaluator(String name, double threshold) {
        super(name, threshold, List.of(
            EvalTestCaseParam.ACTUAL_OUTPUT,
            EvalTestCaseParam.EXPECTED_OUTPUT
        ));
    }
    
    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        String actual = testCase.actualOutput();
        String expected = testCase.expectedOutput();
        
        // Your evaluation logic
        double score = calculateScore(actual, expected);
        String reason = "Custom evaluation reason";
        
        return EvalResult.builder()
            .name(name())
            .score(score)
            .threshold(threshold())
            .reason(reason)
            .build();
    }
    
    private double calculateScore(String actual, String expected) {
        // Your scoring logic
        return 0.0;
    }
}
```

### Using Functional Interface

For simple evaluators, use the functional interface directly:

```java
Evaluator lengthEvaluator = new Evaluator() {
    @Override
    public EvalResult evaluate(EvalTestCase testCase) {
        String output = testCase.actualOutput();
        double score = output.length() >= 100 ? 1.0 : 0.0;
        return EvalResult.of("Length Check", score, 1.0, 
            "Output length: " + output.length());
    }
    
    @Override
    public String name() {
        return "Length Check";
    }
    
    @Override
    public double threshold() {
        return 1.0;
    }
};
```

## Using Multiple Evaluators

Combine evaluators to assess different quality dimensions:

```java
List<Evaluator> evaluators = List.of(
    // Factual correctness
    LLMJudgeEvaluator.builder()
        .name("Correctness")
        .criteria("Is the answer factually correct?")
        .threshold(0.8)
        .judge(judge)
        .build(),
    
    // Grounding in context
    FaithfulnessEvaluator.builder()
        .threshold(0.7)
        .judge(judge)
        .build(),
    
    // Format validation
    RegexEvaluator.builder()
        .name("Format Check")
        .pattern("^[A-Z].*\\.$")  // Starts with capital, ends with period
        .threshold(1.0)
        .build()
);
```

An example passes only if **all** evaluators pass their thresholds.

## Best Practices

### 1. Choose Appropriate Evaluators

Match evaluators to your requirements:
- **Deterministic outputs** → ExactMatch or Regex
- **Creative/semantic content** → LLMJudge
- **RAG systems** → Faithfulness
- **Domain-specific** → Custom evaluators

### 2. Set Realistic Thresholds

Start with achievable thresholds and tighten over time:

```java
.threshold(0.7)  // 70% is often a good starting point for LLM judges
```

### 3. Use Descriptive Names

Make results easy to understand:

```java
.name("Answer Correctness - LLM as a Judge")
```

### 4. Combine Evaluators

Assess multiple quality aspects:

```java
List<Evaluator> evaluators = List.of(
    correctnessEvaluator,
    relevanceEvaluator,
    faithfulnessEvaluator
);
```

### 5. Optimize LLM Judge Criteria

Write clear, specific criteria:

```java
// Good
.criteria("Is the answer factually correct based on the provided context?")

// Too vague
.criteria("Is this good?")
```

### 6. Test Your Evaluators

Validate evaluators with known examples:

```java
@Test
void evaluatorShouldPassCorrectAnswer() {
    var testCase = EvalTestCase.builder()
        .actualOutput("Paris")
        .expectedOutput("Paris")
        .build();
    
    var result = evaluator.evaluate(testCase);
    
    assertTrue(result.success());
    assertEquals(1.0, result.score());
}
```

## Evaluator Results

### Accessing Results

```java
EvalResult result = evaluator.evaluate(testCase);

System.out.println("Score: " + result.score());
System.out.println("Success: " + result.success());
System.out.println("Reason: " + result.reason());
```

### In Experiments

```java
ExperimentResult result = experiment.run();

// Per-evaluator average scores
result.averageScore("Correctness");
result.averageScore("Faithfulness");

// Individual item results
for (ItemResult item : result.itemResults()) {
    for (EvalResult eval : item.evalResults()) {
        System.out.println(eval.name() + ": " + eval.score());
    }
}
```

### In JUnit Tests

```java
@ParameterizedTest
@DatasetSource("classpath:datasets/qa.json")
void testEvaluation(Example example) {
    String answer = aiService.generate(example.input());
    var testCase = example.toTestCase(answer);
    
    Assertions.assertEval(testCase, evaluators);
}
```
