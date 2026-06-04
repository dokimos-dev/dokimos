---
sidebar_position: 4
---

# Evaluators

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

Evaluators check the quality of your LLM's outputs. Each one gives a score between 0.0 and 1.0, and decides whether the output passes based on a threshold you set.

You can use built-in evaluators for common checks (exact matches, regex patterns, LLM-based judging) or create custom ones for your specific needs.

## The Evaluator Interface

All evaluators implement the `Evaluator` interface:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
public interface Evaluator {
    EvalResult evaluate(EvalTestCase testCase);
    String name();
    double threshold();
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
interface Evaluator {
    fun evaluate(testCase: EvalTestCase): EvalResult
    fun name(): String
    fun threshold(): Double
}
```

  </TabItem>
</Tabs>

Evaluators extending `BaseEvaluator` also support **async evaluation**:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// Async using common fork-join pool
CompletableFuture<EvalResult> future = evaluator.evaluateAsync(testCase);

// Async with custom executor
ExecutorService executor = Executors.newFixedThreadPool(4);
CompletableFuture<EvalResult> future = evaluator.evaluateAsync(testCase, executor);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// Async using common fork-join pool
val evalResult = evaluator.evaluateAsync(testCase).await()

// Async with custom executor
val executor = Executors.newFixedThreadPool(4)
val evalResult2 = evaluator.evaluateAsync(testCase, executor).await()
```

  </TabItem>
</Tabs>

An `EvalResult` contains:
- **score**: Numeric score (0.0 to 1.0)
- **success**: Whether score meets threshold
- **reason**: Explanation of the score
- **metadata**: Additional evaluation data

## Built-in Evaluators

### ExactMatchEvaluator

Checks if the output matches the expected result exactly. Useful for deterministic outputs where there's only one correct answer.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Evaluator evaluator = ExactMatchEvaluator.builder()
    .name("Exact Match")
    .threshold(1.0)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val evaluator = exactMatch {
    name = "Exact Match"
    threshold = 1.0
}
```

  </TabItem>
</Tabs>

Returns score `1.0` if they match, `0.0` otherwise.

**When to use:** Math calculations, code generation, or any scenario where the output is a string that should be exactly as expected.

:::note
`ExactMatchEvaluator` compares the **string forms** of the outputs (`toString()`). For a structured output — a record, `Map`, or list — use [`StructuralMatchEvaluator`](#structuralmatchevaluator) instead, which compares the values structurally and ignores formatting and numeric representation (`5` vs `5.0`).
:::

### RegexEvaluator

Checks if the output matches a pattern. Useful for validating format without caring about the exact content.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val dateFormat = regex {
    name = "Date Format"
    pattern = "\\d{4}-\\d{2}-\\d{2}"  // YYYY-MM-DD
    threshold = 1.0
}

val emailFormat = regex {
    name = "Email Format"
    pattern = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    ignoreCase = true
    threshold = 1.0
}
```

  </TabItem>
</Tabs>

**When to use:** Validating dates, emails, phone numbers, IDs, URLs, or any structured format where the exact value varies but the pattern should be consistent.

### LLMJudgeEvaluator

Uses another LLM to evaluate outputs based on criteria you define in natural language. This is powerful for subjective quality checks that are hard to automate with rules.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val judge = JudgeLM { prompt -> judgeModel.generate(prompt) }

val helpfulness: Evaluator = llmJudge(judge) {
    name = "Helpfulness"
    criteria = "Is the answer helpful and complete? Does it actually solve the user's problem?"
    params(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT)
    threshold = 0.8
}
```

  </TabItem>
</Tabs>

The evaluator sends your criteria along with the test case to the judge model, which returns a score between 0 and 1. The reply is parsed leniently: a one-sentence preamble or trailing prose around the JSON is dropped, so a recoverable judgment is not lost to a formatting quirk.

A structured output (a record, `Map`, or list) is rendered to the judge as pretty-printed JSON, so you can judge a structured value directly; String and primitive output is passed through verbatim.

By default the judge scores on a 0..1 scale. To let the judge work on a different range, set `scoreRange(min, max)`. The reported score is then normalized back to 0..1, so your `threshold` always stays on the 0..1 scale:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Evaluator helpfulness = LLMJudgeEvaluator.builder()
    .name("Helpfulness")
    .criteria("Rate the answer's helpfulness.")
    .evaluationParams(List.of(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
    .scoreRange(1, 5)  // judge replies 1..5; score is normalized to 0..1
    .threshold(0.8)
    .judge(judge)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val helpfulness: Evaluator = llmJudge(judge) {
    name = "Helpfulness"
    criteria = "Rate the answer's helpfulness."
    params(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT)
    scoreRange(1.0, 5.0)  // judge replies 1..5; score is normalized to 0..1
    threshold = 0.8
}
```

  </TabItem>
</Tabs>

**When to use:** Checking semantic correctness, helpfulness, tone, clarity, or any quality dimension that's easier to describe in words than code.

### StructuralMatchEvaluator

Compares the actual output against the expected output as **JSON structures** rather than as opaque strings. Both operands are normalized to a JSON tree before comparison, so a record, a `Map`, or a JSON string all compare object-against-object. This makes it the right tool for structured output (extraction results, function-call arguments, typed POJOs) where reformatting, key ordering, or numeric representation should not count as a difference.

Crucially, numbers compare **by value, not representation**: `5` equals `5.0`, and `1.0` equals `1.00`, in both modes. String equality of the serialized form would flag those as mismatches; structural comparison does not.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
record Invoice(String id, double total, List<String> items) {}

Evaluator structural = StructuralMatchEvaluator.builder()
    .name("Invoice Match")
    .threshold(1.0)
    .build();  // STRICT mode, outputKey "output", partial scoring

var testCase = EvalTestCase.builder()
    .expectedOutput("output", new Invoice("INV-1", 42.0, List.of("a", "b")))
    .actualOutput("output", new Invoice("INV-1", 42.00, List.of("a", "b")))
    .build();

EvalResult result = structural.evaluate(testCase);
// result.score() == 1.0 — 42.0 and 42.00 are value-equal
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
data class Invoice(val id: String, val total: Double, val items: List<String>)

val structural: Evaluator = StructuralMatchEvaluator.builder()
    .name("Invoice Match")
    .threshold(1.0)
    .build()  // STRICT mode, outputKey "output", partial scoring

val testCase = EvalTestCase(
    expectedOutputs = mapOf("output" to Invoice("INV-1", 42.0, listOf("a", "b"))),
    actualOutputs = mapOf("output" to Invoice("INV-1", 42.00, listOf("a", "b"))),
)

val result = structural.evaluate(testCase)
// result.score() == 1.0 — 42.0 and 42.00 are value-equal
```

  </TabItem>
</Tabs>

#### Comparison modes

The comparison mode is set with `.mode(...)` using `StructuralMatchMode`:

- **`STRICT`** (the default) requires the **exact field set** and **exact array order**. An extra field in the actual output is a mismatch and penalizes the score, and a `null` value is distinct from a missing field.
- **`LENIENT`** allows **extra actual fields** (the actual object may be a superset of the expected one) and ignores array order, comparing arrays as **multisets** — `[1, 1, 2]` does not match `[1, 2]`, but order is irrelevant. A `null` value and a missing field are treated as equal.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Evaluator lenient = StructuralMatchEvaluator.builder()
    .name("Extraction Match")
    .mode(StructuralMatchMode.LENIENT)  // tolerate extra fields, ignore array order
    .threshold(0.9)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val lenient: Evaluator = StructuralMatchEvaluator.builder()
    .name("Extraction Match")
    .mode(StructuralMatchMode.LENIENT)  // tolerate extra fields, ignore array order
    .threshold(0.9)
    .build()
```

  </TabItem>
</Tabs>

#### Scoring

By default the score is the **fraction of matching leaf paths** in `[0.0, 1.0]`, so a single wrong field on a large object is a partial miss, not a total failure. In `STRICT` the denominator is the union of expected and actual leaf paths (extra fields lower the score); in `LENIENT` the denominator is the expected leaf paths only.

Call `.binary()` for an **exact-contract gate**: the score collapses to `1.0` when the structures match completely and `0.0` when anything differs. Pair it with `threshold(1.0)` when the output contract must be satisfied exactly.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Evaluator contract = StructuralMatchEvaluator.builder()
    .name("Schema Contract")
    .binary()          // 1.0 if everything matches, 0.0 otherwise
    .threshold(1.0)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val contract: Evaluator = StructuralMatchEvaluator.builder()
    .name("Schema Contract")
    .binary()          // 1.0 if everything matches, 0.0 otherwise
    .threshold(1.0)
    .build()
```

  </TabItem>
</Tabs>

By default the evaluator reads both operands from the `"output"` key of the expected and actual output maps. Use `.outputKey(...)` to read from a different key. The expected value is required — if it is absent the evaluator throws.

:::tip
This evaluator pairs naturally with the typed output accessors on `EvalTestCase` (`actualOutputAs(...)` / `expectedOutputAs(...)`): store your structured result under a map key as a record or `Map`, compare it structurally here, and read it back as a typed object elsewhere. See the [Structured & Typed Data](./structured-typed-data.md) hub for the whole pipeline end to end.
:::

**When to use:** Structured or JSON output — extraction results, tool-call arguments, typed response objects — where you care about the data, not its textual formatting, and where numeric representation differences (`5` vs `5.0`) should never count as a regression.

### FaithfulnessEvaluator

Checks if the output is grounded in the provided context. This is essential for RAG systems where you need to ensure the LLM isn't making things up.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
JudgeLM judge = prompt -> judgeModel.generate(prompt);

Evaluator faithfulness = FaithfulnessEvaluator.builder()
    .threshold(0.8)
    .judge(judge)
    .contextKey("retrievedContext")  // Where to find the context in outputs
    .includeReason(true)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val judge = JudgeLM { prompt -> judgeModel.generate(prompt) }

val faithfulness: Evaluator = faithfulness(judge) {
    threshold = 0.8
    contextKey = "retrievedContext"  // Where to find the context in outputs
    includeReason = true
}
```

  </TabItem>
</Tabs>

The evaluator:
1. Breaks down the output into individual claims
2. Checks each claim against the retrieved context
3. Calculates score = (supported claims) / (total claims)

**When to use:** Any RAG system where accuracy matters. If your LLM is answering questions based on retrieved documents, you need this to catch hallucinations.

### HallucinationEvaluator

Detects when the output contains information not supported by the provided context. Unlike FaithfulnessEvaluator which measures how much is grounded, this evaluator specifically measures the proportion of hallucinated content.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
JudgeLM judge = prompt -> judgeModel.generate(prompt);

Evaluator hallucination = HallucinationEvaluator.builder()
    .threshold(0.3)  // Allow at most 30% hallucinated content
    .judge(judge)
    .contextKey("context")
    .includeReason(true)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val judge = JudgeLM { prompt -> judgeModel.generate(prompt) }

val hallucination: Evaluator = hallucination(judge) {
    threshold = 0.3  // Allow at most 30% hallucinated content
    contextKey = "context"
    includeReason = true
}
```

  </TabItem>
</Tabs>

The evaluator:
1. Breaks down the output into individual statements
2. Checks if each statement is supported by the context
3. Calculates score = (unsupported statements) / (total statements)

**Important:** For this evaluator, **lower scores are better** (0.0 means no hallucinations). Success is determined by `score <= threshold`.

**When to use:** When you need to specifically measure and limit hallucination rate, especially in high-stakes applications where any fabricated information is problematic.

### ContextualRelevanceEvaluator

Measures how relevant retrieved context chunks are to a user's query. This is essential for evaluating retrieval quality in RAG systems.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

EvalResult result = relevance.evaluate(testCase);
// result.score() ≈ 0.63 (average of individual scores)
// result.metadata().get("contextScores") contains per-chunk details
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val judge = JudgeLM { prompt -> judgeModel.generate(prompt) }

val relevance: Evaluator = contextualRelevance(judge) {
    threshold = 0.5
    retrievalContextKey = "retrievalContext"
    includeReason = true
    strictMode = false  // Set to true for threshold of 1.0
}
```

The evaluator:
1. Scores each context chunk independently (0.0 to 1.0) for relevance to the query
2. Calculates final score as the mean average of all chunk scores
3. Stores individual chunk scores in the result metadata for transparency

```kotlin
val testCase = EvalTestCase(
    input = "What are symptoms of dehydration?",
    actualOutputs = mapOf("retrievalContext" to listOf(
        "Dehydration symptoms include thirst and fatigue.",  // Highly relevant
        "The Pacific Ocean is the largest ocean.",           // Irrelevant
        "Severe dehydration can cause dizziness."            // Highly relevant
    )))

val result = relevance.evaluate(testCase)
// result.score() ≈ 0.63 (average of individual scores)
// result.metadata()["contextScores"] contains per-chunk details
```

  </TabItem>
</Tabs>

**When to use:** Evaluating retrieval quality in RAG pipelines. Helps identify when your retriever is returning irrelevant documents that could confuse the LLM or dilute answer quality.

### PrecisionEvaluator

Measures what fraction of retrieved items are actually relevant. Requires ground truth labels.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Evaluator precision = PrecisionEvaluator.builder()
    .name("retrieval-precision")
    .retrievedKey("retrievedDocs")   // Key in actualOutputs
    .expectedKey("relevantDocs")     // Key in expectedOutputs (ground truth)
    .matchingStrategy(MatchingStrategy.byEquality())
    .threshold(0.8)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val precision: Evaluator = precision {
    name = "retrieval-precision"
    retrievedKey = "retrievedDocs"   // Key in actualOutputs
    expectedKey = "relevantDocs"     // Key in expectedOutputs (ground truth)
    matchingStrategy = MatchingStrategy.byEquality()
    threshold = 0.8
}
```

  </TabItem>
</Tabs>

**Formula:** `precision = |relevant ∩ retrieved| / |retrieved|`

A precision of 1.0 means every retrieved item was relevant (no false positives).

**When to use:** When you need to minimize noise in retrieved results. High precision is critical when downstream processing is expensive or when irrelevant items could mislead the LLM.

### RecallEvaluator

Measures what fraction of relevant items were actually retrieved. Requires ground truth labels.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Evaluator recall = RecallEvaluator.builder()
    .name("retrieval-recall")
    .retrievedKey("retrievedDocs")
    .expectedKey("relevantDocs")
    .matchingStrategy(MatchingStrategy.byEquality())
    .threshold(0.8)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val recall: Evaluator = recall {
    name = "retrieval-recall"
    retrievedKey = "retrievedDocs"
    expectedKey = "relevantDocs"
    matchingStrategy = MatchingStrategy.byEquality()
    threshold = 0.8
}
```

  </TabItem>
</Tabs>

**Formula:** `recall = |relevant ∩ retrieved| / |relevant|`

A recall of 1.0 means all relevant items were found (no false negatives).

**When to use:** When missing relevant information is costly. High recall is critical for comprehensive answers or when the user expects complete coverage.

### Matching Strategies

Both Precision and Recall evaluators support flexible matching strategies for comparing retrieved items to ground truth:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// Simple equality (default, for string IDs)
MatchingStrategy.byEquality()

// Case-insensitive string matching
MatchingStrategy.caseInsensitive()

// Match by a specific field (for Map/JSON objects)
MatchingStrategy.byField("id")

// Match by multiple fields (e.g., knowledge graph triples)
MatchingStrategy.byFields("subject", "predicate", "object")

// Substring containment matching
MatchingStrategy.byContainment(true)  // normalized

// LLM-based semantic matching (most flexible, most expensive)
MatchingStrategy.llmBased(judge)

// Combine strategies
MatchingStrategy.anyOf(strategy1, strategy2)  // OR
MatchingStrategy.allOf(strategy1, strategy2)  // AND
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// Simple equality (default, for string IDs)
MatchingStrategy.byEquality()

// Case-insensitive string matching
MatchingStrategy.caseInsensitive()

// Match by a specific field (for Map/JSON objects)
MatchingStrategy.byField("id")

// Match by multiple fields (e.g., knowledge graph triples)
MatchingStrategy.byFields("subject", "predicate", "object")

// Substring containment matching
MatchingStrategy.byContainment(normalize = true)

// LLM-based semantic matching (most flexible, most expensive)
MatchingStrategy.llmBased(judge)

// Combine strategies
MatchingStrategy.anyOf(strategy1, strategy2)  // OR
MatchingStrategy.allOf(strategy1, strategy2)  // AND
```

  </TabItem>
</Tabs>

**Example with knowledge graph triples:**

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
var precision = PrecisionEvaluator.builder()
    .retrievedKey("triples")
    .expectedKey("relevantTriples")
    .matchingStrategy(MatchingStrategy.byFields("subject", "predicate", "object"))
    .build();

var testCase = EvalTestCase.builder()
    .input("Who founded Microsoft?")
    .actualOutput("triples", List.of(
        Map.of("subject", "Bill Gates", "predicate", "founded", "object", "Microsoft")
    ))
    .expectedOutput("relevantTriples", List.of(
        Map.of("subject", "Bill Gates", "predicate", "founded", "object", "Microsoft"),
        Map.of("subject", "Paul Allen", "predicate", "co-founded", "object", "Microsoft")
    ))
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val precision = precision {
    retrievedKey = "triples"
    expectedKey = "relevantTriples"
    matchingStrategy = MatchingStrategy.byFields("subject", "predicate", "object")
}

val testCase = EvalTestCase(
    input = "Who founded Microsoft?",
    actualOutputs = mapOf("triples" to listOf(
      mapOf("subject" to "Bill Gates", "predicate" to "founded", "object" to "Microsoft")
    )),
    expectedOutputs = mapOf("relevantTriples" to listOf(
      mapOf("subject" to "Bill Gates", "predicate" to "founded", "object" to "Microsoft"),
      mapOf("subject" to "Paul Allen", "predicate" to "co-founded", "object" to "Microsoft")
    )))
```

  </TabItem>
</Tabs>

### Agent Evaluators

Dokimos includes specialized evaluators for AI agents that use tools. These cover task completion, tool call validation, argument hallucination detection, and tool definition quality.

See the dedicated **[Agent Evaluation](./agent-evaluation)** guide for full documentation.

## Common Configuration

All evaluators have these settings:

**Name**: How the evaluator shows up in results:
<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
.name("Answer Quality")
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
name = "Answer Quality"
```

  </TabItem>
</Tabs>

**Threshold**: Minimum score needed to pass:
<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
.threshold(0.8)  // Needs 80% or higher
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
threshold = 0.8  // Needs 80% or higher
```

  </TabItem>
</Tabs>

**Evaluation Parameters**: What information to include for evaluators:
<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
.evaluationParams(List.of(
    EvalTestCaseParam.INPUT,           // The user's question
    EvalTestCaseParam.EXPECTED_OUTPUT, // What you expect
    EvalTestCaseParam.ACTUAL_OUTPUT,   // What the LLM actually said
))
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
params(
    EvalTestCaseParam.INPUT,           // The user's question
    EvalTestCaseParam.EXPECTED_OUTPUT, // What you expect
    EvalTestCaseParam.ACTUAL_OUTPUT,   // What the LLM actually said
)
```

  </TabItem>
</Tabs>

## Creating Custom Evaluators

When the built-in evaluators don't fit your needs, create your own by extending `BaseEvaluator`:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
class ResponseLengthEvaluator(
    private val minLength: Int,
    private val maxLength: Int,
    private val evaluatorName: String = "Length Check"
) : BaseEvaluator(evaluatorName, 1.0, listOf(EvalTestCaseParam.ACTUAL_OUTPUT)) {

    override fun runEvaluation(testCase: EvalTestCase): EvalResult {
        val output = testCase.actualOutput()
        val length = output.length

        val withinBounds = length in minLength..maxLength
        val score = if (withinBounds) 1.0 else 0.0
        val reason = "Output length $length (expected $minLength-$maxLength)"

        return EvalResult(
          name = name(),
          score = score,
          threshold = threshold(),
          reason = reason,
        )
    }
}

// Usage
val lengthCheck: Evaluator = ResponseLengthEvaluator(50, 200)
```

  </TabItem>
</Tabs>

For very simple checks, you can also implement the `Evaluator` interface directly.

## Combining Multiple Evaluators

Most applications need to pass multiple quality checks. You can use several evaluators together:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val evaluators: List<Evaluator> = evaluators {
    // Check if the answer is correct
    llmJudge(judge) {
        name = "Correctness"
        criteria = "Is the answer factually correct?"
        threshold = 0.85
    }

    // Check if it's grounded in retrieved docs (RAG)
    faithfulness(judge) {
        threshold = 0.80
        contextKey = "retrievedContext"
    }

    // Check if it follows the required format
    regex {
        name = "Format Check"
        pattern = "^[A-Z].*\\.$"  // Must start with capital and end with period
        threshold = 1.0
    }
}
```

  </TabItem>
</Tabs>

An output only passes if it meets **all** the thresholds. This lets you enforce multiple quality dimensions at once.

## Best Practices

### Pick the right evaluator for the job

- Use **ExactMatch** when there's only one correct answer (like math or data extraction)
- Use **Regex** for format validation (dates, emails, IDs)
- Use **StructuralMatch** for structured/JSON output where formatting and numeric representation shouldn't count as differences (see the [Structured & Typed Data](./structured-typed-data.md) hub)
- Use **LLMJudge** for semantic quality (helpfulness, clarity, tone)
- Use **Faithfulness** for RAG systems to measure how grounded the output is
- Use **Hallucination** to specifically measure and limit fabricated content
- Use **ContextualRelevance** to evaluate retrieval quality without ground truth
- Use **Precision/Recall** when you have ground truth labels for relevant items
- Use **[Agent evaluators](./agent-evaluation)** to evaluate AI agents that use tools (task completion, tool validity, argument hallucination, tool reliability)
- Build **custom evaluators** for domain-specific requirements

### Start with looser thresholds

Don't aim for perfection right away. Start with thresholds around 0.7-0.8 and tighten them as your system improves. A threshold of 1.0 means any imperfection fails.

### Write specific criteria for LLM judges

Be clear about what you're evaluating:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// Good (specific and measurable)
.criteria("Does the answer correctly explain the refund process and mention the 30-day policy?")

// Bad (too vague)
.criteria("Is this good?")
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// Good (specific and measurable)
criteria = "Does the answer correctly explain the refund process and mention the 30-day policy?"

// Bad (too vague)
criteria = "Is this good?"
```

  </TabItem>
</Tabs>

### Use multiple evaluators for important outputs

Check different aspects independently: correctness, format, grounding, tone, etc. This gives you more insight into where things go wrong.

### Test your evaluators

Make sure your evaluators work as expected on known examples before relying on them:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
@Test
fun faithfulnessEvaluatorShouldCatchHallucination() {
    val testCase = EvalTestCase(
        actualOutputs = "The product costs $500",  // Made up
        metadata = mapOf("context" to listOf("The product costs $100"))
    )

    val result = faithfulnessEvaluator.evaluate(testCase)

    // Should fail because claim isn't in context
    assertFalse(result.success())
}
```

  </TabItem>
</Tabs>

## Using Evaluator Results

Evaluators return `EvalResult` objects with score, success status, and explanation:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
EvalResult result = evaluator.evaluate(testCase);

System.out.println("Score: " + result.score());
System.out.println("Passed: " + result.success());
System.out.println("Reason: " + result.reason());
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val result = evaluator.evaluate(testCase)

println("Score: ${result.score()}")
println("Passed: ${result.success()}")
println("Reason: ${result.reason()}")
```

  </TabItem>
</Tabs>

In experiments, you can analyze results across all examples:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val experimentResult = experiment.run()

// Average scores per evaluator
val avgCorrectness = experimentResult.averageScore("Correctness")
val avgFaithfulness = experimentResult.averageScore("Faithfulness")

// Dig into individual results
experimentResult.itemResults().forEach { item ->
    item.evalResults()
        .filterNot { eval -> eval.success() }
        .forEach { eval ->
            println("Failed: ${eval.name()} (${eval.reason()})")
        }
}
```

  </TabItem>
</Tabs>

In JUnit tests, evaluators fail the test if they don't pass:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
@ParameterizedTest
@DatasetSource("classpath:datasets/qa.json")
fun shouldProduceQualityAnswers(example: Example) {
    val answer = aiService.generate(example.input())
    val testCase = example.toTestCase(answer)

    // Fails test if evaluators don't pass
    Assertions.assertEval(testCase, evaluators)
}
```

  </TabItem>
</Tabs>
