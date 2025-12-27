---
sidebar_position: 5
---

# Data Model

Understanding Dokimos's data model helps you work more effectively with datasets, experiments, and evaluation results. This guide covers the core classes and how they fit together.

## How the Pieces Fit Together

Here's the flow:

1. **Dataset** holds a collection of **Examples** (test cases)
2. **Experiment** runs a **Task** (your LLM) on each example
3. **Evaluators** check the outputs and produce **EvalResults**
4. Everything gets collected into an **ExperimentResult**

```java
// The flow in code
var result = Experiment.builder()
    .dataset(myDataset)              // Examples to test
    .task(myTask)                     // Your LLM
    .evaluators(List.of(evaluator))   // How to judge outputs
    .run();                           // Returns ExperimentResult
```

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

```java
Dataset dataset = Dataset.builder()
    .name("Support Questions")
    .examples(List.of(
        Example.of("How do I reset my password?", "Click 'Forgot Password'..."),
        Example.of("What's your refund policy?", "We offer 30-day refunds...")
    ))
    .build();
```

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

```java
for (EvalResult eval : itemResult.evalResults()) {
    System.out.println(eval.name() + ": " + eval.score());
    if (!eval.success()) {
        System.out.println("  Failed because: " + eval.reason());
    }
}
```

**Produced by:** Evaluator  
**Part of:** ItemResult

---

## Interfaces

### Task

The function that runs your LLM or system.

```java
@FunctionalInterface
public interface Task {
    Map<String, Object> run(Example example);
}
```

**Implementation examples:**

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

---

### Evaluator

Interface for judging outputs.

```java
public interface Evaluator {
    EvalResult evaluate(EvalTestCase testCase);
    String name();
    double threshold();
}
```

**Built-in implementations:**
- `ExactMatchEvaluator` – Checks for exact match
- `RegexEvaluator` – Pattern matching
- `LLMJudgeEvaluator` – Uses another LLM to judge
- `FaithfulnessEvaluator` – Checks if answer is grounded in context

**Custom evaluator example:**

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

---

## Working with Maps

Most attributes use `Map<String, Object>` for flexibility. Here are the common keys:

| Key | Used In | Description |
|-----|---------|-------------|
| `"input"` | inputs | Primary input text |
| `"output"` | outputs | Primary output text |
| `"context"` | outputs | Retrieved documents (for RAG) |
| `"query"` | inputs | Search query (for RAG) |

**Example with context:**

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

You can add any custom keys you need. Built-in evaluators use standard keys, but custom evaluators can access anything you put in the map.