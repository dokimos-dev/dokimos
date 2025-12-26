---
sidebar_position: 5
---

# Data Model

This page describes the core data models, including classes, their attributes, types, and relationships.

## Core Classes

### Dataset

A collection of examples for evaluation.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `name` | `String` | Yes | Name of the dataset |
| `description` | `String` | No | Description of the dataset (default: `""`) |
| `examples` | `List<Example>` | Yes | Collection of examples (immutable) |

**Methods:**
- `size()` → `int`: Number of examples in the dataset
- `get(int index)` → `Example`: Get example by index
- `iterator()` → `Iterator<Example>`: Iterate over examples

**Relationships:**
- **1:n** with `Example` (contains many examples)

---

### Example

A single test case with inputs, expected outputs, and metadata.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `inputs` | `Map<String, Object>` | No | Input values |
| `expectedOutputs` | `Map<String, Object>` | No | Expected output values |
| `metadata` | `Map<String, Object>` | No | Additional metadata |

**Convenience Methods:**
- `input()` → `String`: Primary input value (from `inputs.get("input")`)
- `expectedOutput()` → `String`: Primary expected output (from `expectedOutputs.get("output")`)
- `toTestCase(Map<String, Object>)` → `EvalTestCase`: Convert to test case with actual outputs
- `toTestCase(String)` → `EvalTestCase`: Convert to test case with single actual output

**Relationships:**
- **n:1** with `Dataset` (belongs to one dataset)
- **1:1** with `EvalTestCase` (converted to test case)
- **1:1** with `ItemResult` (included in item result)

---

### Experiment

Orchestrates evaluation by running a task on a dataset with evaluators.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `name` | `String` | No | Experiment name (default: `"unnamed"`) |
| `description` | `String` | No | Experiment description (default: `""`) |
| `dataset` | `Dataset` | Yes | Dataset to evaluate |
| `task` | `Task` | Yes | Task that generates outputs |
| `evaluators` | `List<Evaluator>` | No | Evaluators to apply (default: empty list) |
| `metadata` | `Map<String, Object>` | No | Custom metadata (default: empty map) |

**Methods:**
- `run()` → `ExperimentResult`: Execute the experiment and return results

**Relationships:**
- **1:1** with `Dataset` (uses one dataset)
- **1:1** with `Task` (executes one task)
- **1:n** with `Evaluator` (applies multiple evaluators)
- **1:1** with `ExperimentResult` (produces one result)

---

### ExperimentResult

Aggregated results from an experiment run.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `name` | `String` | Yes | Experiment name |
| `description` | `String` | Yes | Experiment description |
| `metadata` | `Map<String, Object>` | No | Experiment metadata (default: empty map) |
| `itemResults` | `List<ItemResult>` | No | Results for each example (default: empty list) |

**Computed Methods:**
- `totalCount()` → `int`: Total number of evaluated items
- `passCount()` → `long`: Number of items that passed all evaluations
- `failCount()` → `long`: Number of items that failed at least one evaluation
- `passRate()` → `double`: Proportion of items that passed (0.0 - 1.0)
- `averageScore(String)` → `double`: Average score for a specific evaluator

**Relationships:**
- **1:n** with `ItemResult` (contains results for each example)

---

### ItemResult

Result for a single example evaluation.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `example` | `Example` | Yes | The original example |
| `actualOutputs` | `Map<String, Object>` | No | Outputs produced by the task (default: empty map) |
| `evalResults` | `List<EvalResult>` | No | Evaluation results from each evaluator (default: empty list) |

**Methods:**
- `success()` → `boolean`: True if all evaluators passed
- `toTestCase()` → `EvalTestCase`: Convert to test case

**Relationships:**
- **1:1** with `Example` (evaluates one example)
- **1:n** with `EvalResult` (contains results from multiple evaluators)

---

### EvalTestCase

A test case for evaluation containing inputs, outputs, and metadata.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `inputs` | `Map<String, Object>` | No | Inputs provided to the system (default: empty map) |
| `actualOutputs` | `Map<String, Object>` | No | Outputs produced by the system (default: empty map) |
| `expectedOutputs` | `Map<String, Object>` | No | Expected outputs for comparison (default: empty map) |
| `metadata` | `Map<String, Object>` | No | Additional metadata (default: empty map) |

**Convenience Methods:**
- `input()` → `String`: Primary input (from `inputs.get("input")`)
- `actualOutput()` → `String`: Primary actual output (from `actualOutputs.get("output")`)
- `expectedOutput()` → `String`: Primary expected output (from `expectedOutputs.get("output")`)

**Relationships:**
- Created from `Example` with actual outputs
- **1:1** with `Evaluator` (passed to evaluator for assessment)

---

### EvalResult

The result of an evaluation.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `name` | `String` | Yes | Name of the evaluator |
| `score` | `double` | Yes | Numeric score (0.0 - 1.0) |
| `success` | `boolean` | Yes | Whether evaluation passed (score ≥ threshold) |
| `reason` | `String` | Yes | Explanation for the score |
| `metadata` | `Map<String, Object>` | No | Additional result metadata (default: empty map) |

**Relationships:**
- **n:1** with `ItemResult` (multiple eval results per item)
- Produced by `Evaluator`

---

## Interfaces

### Task

Functional interface that runs your LLM or system under test.

**Method:**
- `run(Example)` → `Map<String, Object>`: Execute task on an example and return outputs

**Usage:**
```java
Task task = example -> {
    String response = llm.generate(example.input());
    return Map.of("output", response);
};
```

---

### Evaluator

Interface for evaluating test cases.

**Methods:**
- `evaluate(EvalTestCase)` → `EvalResult`: Evaluate a test case
- `name()` → `String`: Name of the evaluator
- `threshold()` → `double`: Minimum score threshold for success

**Implementations:**
- `ExactMatchEvaluator`
- `RegexEvaluator`
- `LLMJudgeEvaluator`
- `FaithfulnessEvaluator`
- Custom evaluators

---

## Type Conventions

`Map<String, Object>`

Used throughout for key-value stores:

**Common keys:**
- `"input"`: Primary input value
- `"output"`: Primary output value
- `"context"`: Retrieved context (for RAG)

**Example:**
```java
Map<String, Object> outputs = Map.of(
    "output", "Paris",
    "confidence", 0.95,
    "context", List.of("Paris is the capital of France")
);
```