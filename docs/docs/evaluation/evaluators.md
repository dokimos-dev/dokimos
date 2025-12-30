---
sidebar_position: 4
---

# Evaluators

Evaluators check the quality of your LLM's outputs. Each one gives a score between 0.0 and 1.0, and decides whether the output passes based on a threshold you set.

You can use built-in evaluators for common checks (exact matches, regex patterns, LLM-based judging) or create custom ones for your specific needs.

## The Evaluator Interface

All evaluators implement the `Evaluator` interface:

```java
public interface Evaluator {
    EvalResult evaluate(EvalTestCase testCase);
    String name();
    double threshold();
}
```

Evaluators extending `BaseEvaluator` also support **async evaluation**:

```java
// Async using common fork-join pool
CompletableFuture<EvalResult> future = evaluator.evaluateAsync(testCase);

// Async with custom executor
ExecutorService executor = Executors.newFixedThreadPool(4);
CompletableFuture<EvalResult> future = evaluator.evaluateAsync(testCase, executor);
```

An `EvalResult` contains:
- **score**: Numeric score (0.0 to 1.0)
- **success**: Whether score meets threshold
- **reason**: Explanation of the score
- **metadata**: Additional evaluation data

## Built-in Evaluators

### ExactMatchEvaluator

Checks if the output matches the expected result exactly. Useful for deterministic outputs where there's only one correct answer.

```java
Evaluator evaluator = ExactMatchEvaluator.builder()
    .name("Exact Match")
    .threshold(1.0)
    .build();
```

Returns score `1.0` if they match, `0.0` otherwise.

**When to use:** Math calculations, code generation, structured data extraction, or any scenario where the output should be exactly as expected.

### RegexEvaluator

Checks if the output matches a pattern. Useful for validating format without caring about the exact content.

```java
Evaluator dateFormat = RegexEvaluator.builder()
    .name("Date Format")
    .pattern("\\d{4}-\\d{2}-\\d{2}")  // YYYY-MM-DD
    .threshold(1.0)
    .build();

Evaluator emailFormat = RegexEvaluator.builder()
    .name("Email Format")
    .pattern("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    .ignoreCase(true)
    .threshold(1.0)
    .build();
```

**When to use:** Validating dates, emails, phone numbers, IDs, URLs, or any structured format where the exact value varies but the pattern should be consistent.

### LLMJudgeEvaluator

Uses another LLM to evaluate outputs based on criteria you define in natural language. This is powerful for subjective quality checks that are hard to automate with rules.

```java
JudgeLM judge = prompt -> judgeModel.generate(prompt);

Evaluator helpfulness = LLMJudgeEvaluator.builder()
    .name("Helpfulness")
    .criteria("Is the answer helpful and complete? Does it actually solve the user's problem?")
    .evaluationParams(List.of(
        EvalTestCaseParam.INPUT,
        EvalTestCaseParam.ACTUAL_OUTPUT
    ))
    .threshold(0.8)
    .judge(judge)
    .build();
```

The evaluator sends your criteria along with the test case to the judge model, which returns a score between 0 and 1.

**When to use:** Checking semantic correctness, helpfulness, tone, clarity, or any quality dimension that's easier to describe in words than code.

### FaithfulnessEvaluator

Checks if the output is grounded in the provided context. This is essential for RAG systems where you need to ensure the LLM isn't making things up.

```java
JudgeLM judge = prompt -> judgeModel.generate(prompt);

Evaluator faithfulness = FaithfulnessEvaluator.builder()
    .threshold(0.8)
    .judge(judge)
    .contextKey("retrievedContext")  // Where to find the context in outputs
    .includeReason(true)
    .build();
```

The evaluator:
1. Breaks down the output into individual claims
2. Checks each claim against the retrieved context
3. Calculates score = (supported claims) / (total claims)

**When to use:** Any RAG system where accuracy matters. If your LLM is answering questions based on retrieved documents, you need this to catch hallucinations.

### HallucinationEvaluator

Detects when the output contains information not supported by the provided context. Unlike FaithfulnessEvaluator which measures how much is grounded, this evaluator specifically measures the proportion of hallucinated content.

```java
JudgeLM judge = prompt -> judgeModel.generate(prompt);

Evaluator hallucination = HallucinationEvaluator.builder()
    .threshold(0.3)  // Allow at most 30% hallucinated content
    .judge(judge)
    .contextKey("context")
    .includeReason(true)
    .build();
```

The evaluator:
1. Breaks down the output into individual statements
2. Checks if each statement is supported by the context
3. Calculates score = (unsupported statements) / (total statements)

**Important:** For this evaluator, **lower scores are better** (0.0 means no hallucinations). Success is determined by `score <= threshold`.

**When to use:** When you need to specifically measure and limit hallucination rate, especially in high-stakes applications where any fabricated information is problematic.

### ContextualRelevanceEvaluator

Measures how relevant retrieved context chunks are to a user's query. This is essential for evaluating retrieval quality in RAG systems.

```java
JudgeLM judge = prompt -> judgeModel.generate(prompt);

Evaluator relevance = ContextualRelevanceEvaluator.builder()
    .threshold(0.5)
    .judge(judge)
    .retrievalContextKey("retrievalContext")
    .includeReason(true)
    .strictMode(false)  // Set to true for threshold of 1.0
    .build();
```

The evaluator:
1. Scores each context chunk independently (0.0 to 1.0) for relevance to the query
2. Calculates final score as the mean average of all chunk scores
3. Stores individual chunk scores in the result metadata for transparency

```java
var testCase = EvalTestCase.builder()
    .input("What are symptoms of dehydration?")
    .actualOutput("retrievalContext", List.of(
        "Dehydration symptoms include thirst and fatigue.",  // Highly relevant
        "The Pacific Ocean is the largest ocean.",           // Irrelevant
        "Severe dehydration can cause dizziness."            // Highly relevant
    ))
    .build();

EvalResult result = evaluator.evaluate(testCase);
// result.score() ≈ 0.63 (average of individual scores)
// result.metadata().get("contextScores") contains per-chunk details
```

**When to use:** Evaluating retrieval quality in RAG pipelines. Helps identify when your retriever is returning irrelevant documents that could confuse the LLM or dilute answer quality.

## Common Configuration

All evaluators have these settings:

**Name**: How the evaluator shows up in results:
```java
.name("Answer Quality")
```

**Threshold**: Minimum score needed to pass:
```java
.threshold(0.8)  // Needs 80% or higher
```

**Evaluation Parameters**: What information to include for evaluators:
```java
.evaluationParams(List.of(
    EvalTestCaseParam.INPUT,           // The user's question
    EvalTestCaseParam.EXPECTED_OUTPUT, // What you expect
    EvalTestCaseParam.ACTUAL_OUTPUT,   // What the LLM actually said
))
```

## Creating Custom Evaluators

When the built-in evaluators don't fit your needs, create your own by extending `BaseEvaluator`:

```java
public class ResponseLengthEvaluator extends BaseEvaluator {
    
    private final int minLength;
    private final int maxLength;
    
    public ResponseLengthEvaluator(String name, int minLength, int maxLength) {
        super(name, 1.0, List.of(EvalTestCaseParam.ACTUAL_OUTPUT));
        this.minLength = minLength;
        this.maxLength = maxLength;
    }
    
    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        String output = testCase.actualOutput();
        int length = output.length();
        
        boolean withinBounds = length >= minLength && length <= maxLength;
        double score = withinBounds ? 1.0 : 0.0;
        String reason = String.format("Output length %d (expected %d-%d)",
            length, minLength, maxLength);
        
        return EvalResult.builder()
            .name(name())
            .score(score)
            .threshold(threshold())
            .reason(reason)
            .build();
    }
}

// Usage
Evaluator lengthCheck = new ResponseLengthEvaluator("Length Check", 50, 200);
```

For very simple checks, you can also implement the `Evaluator` interface directly.
```

## Combining Multiple Evaluators

Most applications need to pass multiple quality checks. You can use several evaluators together:

```java
List<Evaluator> evaluators = List.of(
    // Check if the answer is correct
    LLMJudgeEvaluator.builder()
        .name("Correctness")
        .criteria("Is the answer factually correct?")
        .threshold(0.85)
        .judge(judge)
        .build(),
    
    // Check if it's grounded in retrieved docs (RAG)
    FaithfulnessEvaluator.builder()
        .threshold(0.80)
        .judge(judge)
        .contextKey("retrievedContext")
        .build(),
    
    // Check if it follows the required format
    RegexEvaluator.builder()
        .name("Format Check")
        .pattern("^[A-Z].*\\.$")  // Must start with capital and end with period
        .threshold(1.0)
        .build()
);
```

An output only passes if it meets **all** the thresholds. This lets you enforce multiple quality dimensions at once.

## Best Practices

### Pick the right evaluator for the job

- Use **ExactMatch** when there's only one correct answer (like math or data extraction)
- Use **Regex** for format validation (dates, emails, IDs)
- Use **LLMJudge** for semantic quality (helpfulness, clarity, tone)
- Use **Faithfulness** for RAG systems to measure how grounded the output is
- Use **Hallucination** to specifically measure and limit fabricated content
- Use **ContextualRelevance** to evaluate retrieval quality in RAG pipelines
- Build **custom evaluators** for domain-specific requirements

### Start with looser thresholds

Don't aim for perfection right away. Start with thresholds around 0.7-0.8 and tighten them as your system improves. A threshold of 1.0 means any imperfection fails.

### Write specific criteria for LLM judges

Be clear about what you're evaluating:

```java
// Good (specific and measurable)
.criteria("Does the answer correctly explain the refund process and mention the 30-day policy?")

// Bad (too vague)
.criteria("Is this good?")
```

### Use multiple evaluators for important outputs

Check different aspects independently: correctness, format, grounding, tone, etc. This gives you more insight into where things go wrong.

### Test your evaluators

Make sure your evaluators work as expected on known examples before relying on them:

```java
@Test
void faithfulnessEvaluatorShouldCatchHallucination() {
    var testCase = EvalTestCase.builder()
        .actualOutput("The product costs $500")  // Made up
        .metadata(Map.of("context", List.of("The product costs $100")))
        .build();
    
    var result = faithfulnessEvaluator.evaluate(testCase);
    
    // Should fail because claim isn't in context
    assertFalse(result.success());
}
```

## Using Evaluator Results

Evaluators return `EvalResult` objects with score, success status, and explanation:

```java
EvalResult result = evaluator.evaluate(testCase);

System.out.println("Score: " + result.score());
System.out.println("Passed: " + result.success());
System.out.println("Reason: " + result.reason());
```

In experiments, you can analyze results across all examples:

```java
ExperimentResult experimentResult = experiment.run();

// Average scores per evaluator
double avgCorrectness = experimentResult.averageScore("Correctness");
double avgFaithfulness = experimentResult.averageScore("Faithfulness");

// Dig into individual results
for (ItemResult item : experimentResult.itemResults()) {
    for (EvalResult eval : item.evalResults()) {
        if (!eval.success()) {
            System.out.println("Failed: " + eval.name() + " (" + eval.reason() + ")");
        }
    }
}
```

In JUnit tests, evaluators fail the test if they don't pass:

```java
@ParameterizedTest
@DatasetSource("classpath:datasets/qa.json")
void shouldProduceQualityAnswers(Example example) {
    String answer = aiService.generate(example.input());
    var testCase = example.toTestCase(answer);
    
    // Fails test if evaluators don't pass
    Assertions.assertEval(testCase, evaluators);
}
```
