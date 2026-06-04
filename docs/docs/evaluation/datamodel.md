---
sidebar_position: 5
---

# Data Model

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

This page shows the classes Dokimos uses to hold your test cases, run your LLM, and report scores, so you know exactly what to build and what comes back.

## How the Pieces Fit Together

The flow is short:

1. A **Dataset** holds a list of **Examples** (your test cases).
2. An **Experiment** runs a **Task** (your LLM) on each example.
3. **Evaluators** score the outputs and return **EvalResults**.
4. Everything lands in one **ExperimentResult**.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// The flow in code
var result = Experiment.builder()
    .dataset(myDataset)              // Examples to test
    .task(myTask)                    // Your LLM
    .evaluators(List.of(evaluator))  // How to judge outputs
    .run();                          // Returns ExperimentResult
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// The flow in code
val result = experiment {
    dataset(myDataset)               // Examples to test
    task(myTask)                     // Your LLM
    evaluator(evaluator)             // How to judge outputs
}.run()                              // Returns ExperimentResult
```

  </TabItem>
</Tabs>

## Core Classes

### Dataset

A list of test cases you want to evaluate.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `name` | `String` | Yes | Name of the dataset |
| `description` | `String` | No | Description of the dataset |
| `examples` | `List<Example>` | Yes | Your test cases |

Methods you will use most:

- `size()` returns the number of examples.
- `get(int index)` returns one example.
- `iterator()` lets you loop through them.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
System.out.println("Examples: " + dataset.size());
Example first = dataset.get(0);
for (Example ex : dataset) {
    System.out.println(ex.input());
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
println("Examples: ${dataset.size()}")
val first = dataset[0]
dataset.forEach { ex -> println(ex.input()) }
```

  </TabItem>
</Tabs>

Build a dataset like this:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Dataset dataset = Dataset.builder()
    .name("Support Questions")
    .examples(List.of(
        Example.of("How do I reset my password?", "Click 'Forgot Password'..."),
        Example.of("What's your refund policy?", "We offer 30-day refunds...")
    ))
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val dataset = dataset {
    name = "Support Questions"
    example {
        input = "How do I reset my password?"
        expected = "Click 'Forgot Password'..."
    }
    example {
        input = "What's your refund policy?"
        expected = "We offer 30-day refunds..."
    }
}
```

  </TabItem>
</Tabs>

**Belongs to:** Nothing (top level)  
**Contains:** Many Examples

---

### Example

One test case: input, expected output, and optional metadata.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `inputs` | `Map<String, Object>` | No | Input values |
| `expectedOutputs` | `Map<String, Object>` | No | What you expect as output |
| `metadata` | `Map<String, Object>` | No | Extra info (tags, categories, etc.) |

Two shortcuts read the primary values:

- `input()` returns `inputs.get("input")`.
- `expectedOutput()` returns `expectedOutputs.get("output")`.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Example ex = Example.of("What's 2+2?", "4");
String primaryInput = ex.input();           // "What's 2+2?"
String primaryExpected = ex.expectedOutput(); // "4"
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val ex = example {
    input = "What's 2+2?"
    expected = "4"
}
val primaryInput = ex.input()          // "What's 2+2?"
val primaryExpected = ex.expectedOutput() // "4"
```

  </TabItem>
</Tabs>

Start with the short form. Switch to the builder when you need more keys or metadata.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// Simple example (just input and output)
Example simple = Example.of(
    "What's 2+2?", 
    "4"
);

// Full example with metadata
Example detailed = Example.builder()
    .inputs(Map.of(
        "input", "What's 2+2?",
        "language", "en"
    ))
    .expectedOutputs(Map.of(
        "output", "4",
        "confidence", 1.0
    ))
    .metadata(Map.of("category", "math"))
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// Simple example (just input and output)
val simple = example {
    input = "What's 2+2?"
    expected = "4"
}

// Full example with metadata
val detailed = example {
    input("input", "What's 2+2?")
    input("language", "en")
    expected("output", "4")
    expected("confidence", 1.0)
    metadata("category", "math")
}
```

  </TabItem>
</Tabs>

**Belongs to:** Dataset  
**Becomes:** EvalTestCase (after task runs)

---

### Experiment

Runs your task on a dataset and scores the results.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `name` | `String` | No | Experiment name |
| `description` | `String` | No | What you're testing |
| `dataset` | `Dataset` | Yes | Test cases to run |
| `task` | `Task` | Yes | Your LLM or system |
| `evaluators` | `List<Evaluator>` | No | How to judge outputs |
| `metadata` | `Map<String, Object>` | No | Custom tracking info |

Call `run()` to execute everything. It returns an `ExperimentResult`.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
ExperimentResult result = experiment.run();
System.out.println("Pass rate: " + result.passRate());
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val result = experiment.run()
println("Pass rate: ${result.passRate()}")
```

  </TabItem>
</Tabs>

A full experiment with two evaluators:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
ExperimentResult result = Experiment.builder()
    .name("Test GPT-5.2 on support questions")
    .dataset(supportDataset)
    .task(chatbotTask)
    .evaluators(List.of(
        new ExactMatchEvaluator(),
        new FaithfulnessEvaluator(judgeModel)
    ))
    .run();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val result = experiment {
    name = "Test GPT-5.2 on support questions"
    dataset(supportDataset)
    task(chatbotTask)
    evaluators {
        exactMatch{ }
        faithfulness(judge) {
            contextKey = "ctx"
            threshold = 0.4
        }
    }
}.run()
```

  </TabItem>
</Tabs>

**Uses:** Dataset, Task, Evaluators  
**Produces:** ExperimentResult

---

### ExperimentResult

The summary of how your experiment did.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `name` | `String` | Yes | Experiment name |
| `description` | `String` | Yes | Experiment description |
| `metadata` | `Map<String, Object>` | No | Custom metadata |
| `itemResults` | `List<ItemResult>` | No | Results for each example |

The metrics you will read:

- `totalCount()` returns the number of examples evaluated.
- `passCount()` returns how many passed every evaluator.
- `failCount()` returns how many failed at least one evaluator.
- `passRate()` returns the fraction that passed (0.0 to 1.0).
- `averageScore(String)` returns the average score for one named evaluator.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
System.out.println("Pass rate: " + result.passRate());
System.out.println("Average faithfulness: " + result.averageScore("Faithfulness"));

// Check individual results
for (ItemResult item : result.itemResults()) {
    if (!item.success()) {
        System.out.println("Failed: " + item.example().input());
    }
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
println("Pass rate: ${result.passRate()}")
println("Average faithfulness: ${result.averageScore("Faithfulness")}")

// Check individual results
result.itemResults().filterNot { it.success() }.forEach { item ->
    println("Failed: ${item.example().input()}")
}
```

  </TabItem>
</Tabs>

**Contains:** Many ItemResults

---

### ItemResult

The result of evaluating one example.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `example` | `Example` | Yes | The original test case |
| `actualOutputs` | `Map<String, Object>` | No | What your task produced |
| `evalResults` | `List<EvalResult>` | No | Results from each evaluator |

Call `success()` to check if every evaluator passed.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
for (ItemResult item : experimentResult.itemResults()) {
    System.out.println("Input: " + item.example().input());
    System.out.println("Expected: " + item.example().expectedOutput());
    System.out.println("Actual: " + item.actualOutputs().get("output"));
    System.out.println("Passed: " + item.success());
    
    // See why it failed
    for (EvalResult eval : item.evalResults()) {
        if (!eval.success()) {
            System.out.println(eval.name() + ": " + eval.reason());
        }
    }
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
experimentResult.itemResults().forEach { item ->
    println("Input: ${item.example().input()}")
    println("Expected: ${item.example().expectedOutput()}")
    println("Actual: ${item.actualOutputs()["output"]}")
    println("Passed: ${item.success()}")

    // See why it failed
    item.evalResults().filterNot { it.success() }.forEach { eval ->
        println("${eval.name()}: ${eval.reason()}")
    }
}
```

  </TabItem>
</Tabs>

**Contains:** Example, EvalResults  
**Part of:** ExperimentResult

---

### EvalTestCase

A test case ready for evaluation. It combines an example with the actual output.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `inputs` | `Map<String, Object>` | No | Original inputs |
| `actualOutputs` | `Map<String, Object>` | No | What the task produced |
| `expectedOutputs` | `Map<String, Object>` | No | What you expected |
| `metadata` | `Map<String, Object>` | No | Additional metadata |

Three shortcuts read the primary values:

- `input()` returns the primary input.
- `actualOutput()` returns the primary actual output.
- `expectedOutput()` returns the primary expected output.

This is the object Dokimos passes to each evaluator. You rarely build one yourself. Dokimos builds it when an experiment runs.

**Created from:** Example + actual outputs  
**Passed to:** Evaluators

---

## Typed outputs

The output and expected-output maps hold `Object` values, so the usual habit is to stringify everything. A task can instead return a structured object (a record, a list, a POJO) and read it back type-safely later. This keeps your task body honest (you return the thing you built, not a hand-assembled map) and lets custom evaluators work with real domain objects instead of parsing strings.

:::tip
For the whole typed pipeline in one place (authoring a typed output, comparing it, reading it back, judging it as JSON, and typing tool-call results), see the [Structured & Typed Data](./structured-typed-data.md) hub. The sections below are the per-method reference it links into.
:::

### Returning a typed value from a task

`Task.typed(fn)` wraps a function that returns a single value and stores it under the conventional `"output"` key. In Kotlin, the reified `typedTask<T> { ... }` DSL does the same thing.

:::note
`Task.typed` rejects a `null` return with `NullPointerException`, because the output map cannot hold a null value. If you genuinely need an absent output, use a raw `Task`. As a convenience guard, if your function already returns a `Map`, that map is used directly as the output map rather than being nested under `"output"`, so a multi-key task can adopt `typed` without double-nesting.
:::

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
record Movie(String title, String director, int year) {}

Task task = Task.typed(example -> {
    String json = llm.chat(example.input());
    return Json.parseMovie(json); // returns a Movie record
});
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
data class Movie(val title: String, val director: String, val year: Int)

val task = typedTask<Movie> { example ->
    val json = llm.chat(example.input())
    parseMovie(json) // returns a Movie
}
```

Inside `experiment { ... }` you can also set it directly with the `typedTask` builder method:

```kotlin
val experiment = experiment {
    name = "Movie extraction"
    dataset(movieDataset)
    typedTask<Movie> { example -> parseMovie(llm.chat(example.input())) }
    evaluator(StructuralMatchEvaluator())
}
```

  </TabItem>
</Tabs>

### Reading typed values back

Both `EvalTestCase` and `Example` expose typed accessors. For a non-generic target, pass a `Class<T>`. The accessors default to the `"output"` key, and keyed overloads read any other key.

| Method | Reads | Returns |
|--------|-------|---------|
| `actualOutputAs(Class<T>)` | actual `"output"` | converted value or `null` |
| `actualOutputAs(OutputType<T>)` | actual `"output"` | converted value or `null` |
| `actualOutputAs(String, Class<T>)` | actual under `key` | converted value or `null` |
| `actualOutputAs(String, OutputType<T>)` | actual under `key` | converted value or `null` |
| `expectedOutputAs(Class<T>)` | expected `"output"` | converted value or `null` |
| `expectedOutputAs(OutputType<T>)` | expected `"output"` | converted value or `null` |
| `expectedOutputAs(String, Class<T>)` | expected under `key` | converted value or `null` |
| `expectedOutputAs(String, OutputType<T>)` | expected under `key` | converted value or `null` |

`Example` carries the `expectedOutputAs(...)` twins only (it has no actual output yet). `EvalTestCase` carries both the actual and expected variants.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
public class MovieEvaluator implements Evaluator {
    @Override
    public EvalResult evaluate(EvalTestCase testCase) {
        Movie actual = testCase.actualOutputAs(Movie.class);
        Movie expected = testCase.expectedOutputAs(Movie.class);

        boolean match = actual != null
            && actual.director().equals(expected.director());

        return EvalResult.builder()
            .name("Movie Director")
            .score(match ? 1.0 : 0.0)
            .success(match)
            .reason(match ? "Director matches" : "Wrong director")
            .build();
    }

    @Override
    public String name() { return "Movie Director"; }

    @Override
    public double threshold() { return 1.0; }
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
class MovieEvaluator : Evaluator {
    override fun evaluate(testCase: EvalTestCase): EvalResult {
        val actual = testCase.actualOutputAs(Movie::class.java)
        val expected = testCase.expectedOutputAs(Movie::class.java)

        val match = actual != null && actual.director == expected?.director

        return EvalResult(
            name = "Movie Director",
            score = if (match) 1.0 else 0.0,
            success = match,
            reason = if (match) "Director matches" else "Wrong director",
        )
    }

    override fun name(): String = "Movie Director"

    override fun threshold(): Double = 1.0
}
```

  </TabItem>
</Tabs>

### Generic types with `OutputType<T>`

A plain `Class<T>` cannot express a generic target like `List<Movie>`, because type arguments are erased at runtime. `OutputType<T>` is a super-type token (the "Gafter gadget", like Jackson's `TypeReference` or Spring's `ParameterizedTypeReference`) that captures the full generic type. Always instantiate it as an **anonymous subclass** so the type argument is recorded:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// Task produces a List<Movie>
Task task = Task.typed(example -> parseMovies(llm.chat(example.input())));

// Read it back, preserving the element type
List<Movie> movies =
    testCase.actualOutputAs(new OutputType<List<Movie>>() {});

// A keyed, non-"output" variant works the same way
List<Movie> shortlist =
    testCase.actualOutputAs("shortlist", new OutputType<List<Movie>>() {});
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// Task produces a List<Movie>
val task = typedTask<List<Movie>> { example -> parseMovies(llm.chat(example.input())) }

// Read it back, preserving the element type
val movies: List<Movie> =
    testCase.actualOutputAs(object : OutputType<List<Movie>>() {})

// A keyed, non-"output" variant works the same way
val shortlist: List<Movie> =
    testCase.actualOutputAs("shortlist", object : OutputType<List<Movie>>() {})
```

  </TabItem>
</Tabs>

:::tip
Constructing an `OutputType` raw (`new OutputType() {}`) throws `IllegalArgumentException`, because there is no type argument to capture. Use the `Class<T>` accessors for non-generic targets, and reach for `OutputType<T>` only when the target is generic.
:::

### Conversion contract

The typed accessors share one conversion contract across `EvalTestCase` and `Example`:

- **Absent key returns `null`.** If the requested key is missing from the map, the accessor returns `null` instead of throwing.
- **Already the right type is returned as-is.** For the `Class<T>` accessors, a stored value that is already an instance of the target type is cast directly without going through serialization.
- **Otherwise it is converted, or it throws.** Any other value is converted (via Jackson under the hood). If the value cannot be converted to the requested type, the accessor throws `DokimosTypeConversionException` (in `dev.dokimos.core.exceptions`).

This is why a typed task pairs naturally with structural matching: `StructuralMatchEvaluator` compares the stored structured value against the expected structure, and your custom evaluators can read the same value back as a real object.

---

### EvalResult

The score and feedback from one evaluator.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `name` | `String` | Yes | Evaluator name |
| `score` | `double` | Yes | Score (0.0 to 1.0) |
| `success` | `boolean` | Yes | Whether it passed the threshold |
| `reason` | `String` | Yes | Why this score was given |
| `metadata` | `Map<String, Object>` | No | Extra info from evaluator |

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
for (EvalResult eval : itemResult.evalResults()) {
    System.out.println(eval.name() + ": " + eval.score());
    if (!eval.success()) {
        System.out.println("  Failed because: " + eval.reason());
    }
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
itemResult.evalResults().onEach { eval ->
    println("${eval.name()}: ${eval.score()}")
}.filterNot { it.success() }.forEach { eval ->
    println("  Failed because: ${eval.reason()}")
}
```

  </TabItem>
</Tabs>

**Produced by:** Evaluator  
**Part of:** ItemResult

---

## Interfaces

### Task

The function that runs your LLM or system.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
@FunctionalInterface
public interface Task {
    Map<String, Object> run(Example example);
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
fun interface Task {
    fun run(example: Example): Map<String, Any>
}
```

  </TabItem>
</Tabs>

Return a single output, or return several keys at once:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// Simple task
Task simple = example -> {
    String response = llm.chat(example.input());
    return Map.of("output", response);
};

// Task with multiple outputs
Task detailed = example -> {
    String response = llm.chat(example.input());
    return Map.of(
        "output", response,
        "tokens", 150,
        "latency_ms", 320
    );
};
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// Simple task
val simple: Task = Task { example ->
    val response = llm.chat(example.input())
    mapOf("output" to response)
}

// Task with multiple outputs
val detailed: Task = Task { example ->
    val response = llm.chat(example.input())
    mapOf(
        "output" to response,
        "tokens" to 150,
        "latency_ms" to 320
    )
}
```

  </TabItem>
</Tabs>

---

### Evaluator

The interface for judging outputs.

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

Dokimos ships these built-in implementations:

- `ExactMatchEvaluator` checks for an exact match.
- `RegexEvaluator` matches a pattern.
- `LLMJudgeEvaluator` uses another LLM to judge.
- `FaithfulnessEvaluator` checks that the answer is grounded in the context.
- [Agent evaluators](./agent-evaluation) cover tool call validation, task completion, argument hallucination, and tool reliability.

Write your own by implementing the three methods:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
public class LengthEvaluator implements Evaluator {
    @Override
    public EvalResult evaluate(EvalTestCase testCase) {
        String output = testCase.actualOutput();
        boolean inRange = output.length() >= 50 && output.length() <= 500;
        
        return EvalResult.builder()
            .name("Length Check")
            .score(inRange ? 1.0 : 0.0)
            .success(inRange)
            .reason(inRange ? "Good length" : "Too short or too long")
            .build();
    }
    
    @Override
    public String name() { return "Length Check"; }
    
    @Override
    public double threshold() { return 1.0; }
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
class LengthEvaluator : Evaluator {
    override fun evaluate(testCase: EvalTestCase): EvalResult {
        val output = testCase.actualOutput()
        val inRange = output.length in 50..500

        return EvalResult(
            name = "Length Check",
            score = if (inRange) 1.0 else 0.0,
            success = inRange,
            reason = if (inRange) "Good length" else "Too short or too long"
        )
    }

    override fun name(): String = "Length Check"

    override fun threshold(): Double = 1.0
}
```

  </TabItem>
</Tabs>

---

## Working with Maps

Most attributes use `Map<String, Object>` so you can store anything. These are the keys Dokimos recognizes:

| Key | Used In | Description |
|-----|---------|-------------|
| `"input"` | inputs | Primary input text |
| `"output"` | outputs | Primary output text |
| `"context"` | outputs | Retrieved documents (for RAG) |
| `"query"` | inputs | Search query (for RAG) |
| `"toolCalls"` | outputs / expected | Tool calls made by an agent (for [agent evaluation](./agent-evaluation)) |
| `"tools"` | metadata | Available tool definitions (for [agent evaluation](./agent-evaluation)) |
| `"tasks"` | metadata | Task list for agent completion evaluation |

For a RAG task, put the retrieved docs under `"context"` so evaluators can read them:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Task ragTask = example -> {
    List<String> docs = retriever.search(example.input());
    String answer = llm.generate(example.input(), docs);
    
    return Map.of(
        "output", answer,
        "context", docs,  // Evaluators can check this
        "num_docs", docs.size()
    );
};
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val ragTask: Task = Task { example ->
    val docs = retriever.search(example.input())
    val answer = llm.generate(example.input(), docs)

    mapOf(
        "output" to answer,
        "context" to docs,  // Evaluators can check this
        "num_docs" to docs.size
    )
}
```

  </TabItem>
</Tabs>

Add any custom keys you need. Built-in evaluators read the standard keys, and custom evaluators can read anything you put in the map.
