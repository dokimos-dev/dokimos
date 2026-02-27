---
sidebar_position: 5
---

# Data Model

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

Understanding Dokimos's data model helps you work more effectively with datasets, experiments, and evaluation results. This guide covers the core classes and how they fit together.

## How the Pieces Fit Together

Here's the flow:

1. **Dataset** holds a collection of **Examples** (test cases)
2. **Experiment** runs a **Task** (your LLM) on each example
3. **Evaluators** check the outputs and produce **EvalResults**
4. Everything gets collected into an **ExperimentResult**

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

A collection of test cases you want to evaluate.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `name` | `String` | Yes | Name of the dataset |
| `description` | `String` | No | Description of the dataset |
| `examples` | `List<Example>` | Yes | Your test cases |

**Useful methods:**
- `size()` → Number of examples
- `get(int index)` → Get a specific example
- `iterator()` → Loop through examples

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

A single test case with input, expected output, and optional metadata.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `inputs` | `Map<String, Object>` | No | Input values |
| `expectedOutputs` | `Map<String, Object>` | No | What you expect as output |
| `metadata` | `Map<String, Object>` | No | Extra info (tags, categories, etc.) |

**Convenience shortcuts:**
- `input()` → Gets `inputs.get("input")`
- `expectedOutput()` → Gets `expectedOutputs.get("output")`

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

Runs your task on a dataset and evaluates the results.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `name` | `String` | No | Experiment name |
| `description` | `String` | No | What you're testing |
| `dataset` | `Dataset` | Yes | Test cases to run |
| `task` | `Task` | Yes | Your LLM or system |
| `evaluators` | `List<Evaluator>` | No | How to judge outputs |
| `metadata` | `Map<String, Object>` | No | Custom tracking info |

**What it does:**
- `run()` → Executes everything and returns ExperimentResult

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

Summary of how your experiment performed.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `name` | `String` | Yes | Experiment name |
| `description` | `String` | Yes | Experiment description |
| `metadata` | `Map<String, Object>` | No | Custom metadata |
| `itemResults` | `List<ItemResult>` | No | Results for each example |

**Key metrics:**
- `totalCount()` → Total examples evaluated
- `passCount()` → How many passed all evaluators
- `failCount()` → How many failed at least one evaluator
- `passRate()` → Percentage that passed (0.0 to 1.0)
- `averageScore(String)` → Average score for a specific evaluator

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

**What you can check:**
- `success()` → True if all evaluators passed

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

A test case ready for evaluation (combines example with actual output).

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `inputs` | `Map<String, Object>` | No | Original inputs |
| `actualOutputs` | `Map<String, Object>` | No | What the task produced |
| `expectedOutputs` | `Map<String, Object>` | No | What you expected |
| `metadata` | `Map<String, Object>` | No | Additional metadata |

**Shortcuts:**
- `input()` → Primary input
- `actualOutput()` → Primary actual output  
- `expectedOutput()` → Primary expected output

This is what gets passed to evaluators. Usually you don't create these directly; Dokimos builds them when running experiments.

**Created from:** Example + actual outputs  
**Passed to:** Evaluators

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

**Implementation examples:**

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

Interface for judging outputs.

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

**Built-in implementations:**
- `ExactMatchEvaluator` – Checks for exact match
- `RegexEvaluator` – Pattern matching
- `LLMJudgeEvaluator` – Uses another LLM to judge
- `FaithfulnessEvaluator` – Checks if answer is grounded in context
- [Agent evaluators](./agent-evaluation) – Tool call validation, task completion, argument hallucination, and tool reliability

**Custom evaluator example:**

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

Most attributes use `Map<String, Object>` for flexibility. Here are the common keys:

| Key | Used In | Description |
|-----|---------|-------------|
| `"input"` | inputs | Primary input text |
| `"output"` | outputs | Primary output text |
| `"context"` | outputs | Retrieved documents (for RAG) |
| `"query"` | inputs | Search query (for RAG) |
| `"toolCalls"` | outputs / expected | Tool calls made by an agent (for [agent evaluation](./agent-evaluation)) |
| `"tools"` | metadata | Available tool definitions (for [agent evaluation](./agent-evaluation)) |
| `"tasks"` | metadata | Task list for agent completion evaluation |

**Example with context:**

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

You can add any custom keys you need. Built-in evaluators use standard keys, but custom evaluators can access anything you put in the map.
