---
sidebar_position: 3
---

# Experiments

An **Experiment** is the core orchestration component in Dokimos that runs your LLM application (the **Task**) against a **Dataset**, applies **Evaluators** to assess the outputs, and aggregates the results. Experiments provide a structured, repeatable way to evaluate your LLM applications systematically.

## Why Use Experiments?

Experiments solve several key challenges in LLM application development:

- **Systematic Evaluation**: Instead of manually testing your LLM with ad-hoc prompts, experiments provide a structured approach to run your application against a comprehensive dataset and evaluate the results automatically.

- **Quantitative Metrics**: Experiments generate quantitative metrics (pass rates, average scores, success counts) that help you track improvements over time and make data-driven decisions about your LLM application.

- **Reproducibility**: By defining experiments in code with fixed datasets and evaluators, you can reproduce evaluations consistently across different runs, environments, and team members.

**Continuous Testing**: Experiments can be integrated into your CI/CD pipelines to automatically validate your LLM application's performance with every code change, preventing regressions.

- **Comprehensive Analysis**: Get detailed insights into how your application performs across different scenarios, identify edge cases, and understand which types of inputs cause failures.

## How Experiments Differ from JUnit Testing

While Dokimos integrates with JUnit 5, **Experiments** serve a different purpose:

| Aspect | JUnit Tests with `@DatasetSource` | Experiments |
|--------|-----------------------------------|-------------|
| **Purpose** | Unit/integration testing | Comprehensive evaluation & benchmarking |
| **Execution** | Individual test assertions | Batch processing with aggregation |
| **Results** | Pass/Fail per test | Aggregated metrics, pass rates, scores |
| **Use Case** | CI/CD quality gates | Performance analysis & reporting |
| **Flexibility** | Test-driven, one example at a time | Run entire datasets, analyze trends |
| **Output** | Test reports (JUnit format) | Detailed experiment results with statistics |

**The JUnit Integration** is ideal for:
- Quality gates in CI/CD pipelines
- Catching regressions on specific examples
- Failing fast when test cases don't pass

**Experiments** are ideal for:
- Analyzing performance across entire datasets
- Generating detailed reports with metrics
- Comparing different model configurations
- Understanding application behavior

Users can (and probably should) use both approaches together!

## Basic Usage

### Creating a Simple Experiment

Here's a minimal example of creating and running an experiment:

```java
import dev.dokimos.core.*;

// Define your dataset
Dataset dataset = Dataset.builder()
    .name("Simple QA Dataset")
    .addExample(Example.of("What is 2+2?", "4"))
    .addExample(Example.of("What is the capital of France?", "Paris"))
    .addExample(Example.of("What is the capital of Switzerland?", "Bern"))
    .build();

// Define your task (your LLM application)
Task task = example -> {
    String answer = generateAnswer(example.input());
    return Map.of("output", answer);
};

// Define evaluators
List<Evaluator> evaluators = List.of(
    ExactMatchEvaluator.builder()
        .name("Exact Match")
        .threshold(0.9)
        .build()
);

// Run the experiment
ExperimentResult result = Experiment.builder()
    .name("QA Evaluation")
    .dataset(dataset)
    .task(task)
    .evaluators(evaluators)
    .build()
    .run();

// Analyze results
System.out.println("Pass rate: " + String.format("%.2f%%", result.passRate() * 100));
System.out.println("Total examples: " + result.totalCount());
System.out.println("Passed: " + result.passCount());
System.out.println("Failed: " + result.failCount());
```

### Understanding the Task Interface

The `Task` is a functional interface that represents your LLM application under test:

```java
@FunctionalInterface
public interface Task {
    Map<String, Object> run(Example example);
}
```

A task receives an `Example` from the dataset and returns a map of outputs. The simplest implementation:

```java
Task task = example -> {
    String response = myLlmService.generate(example.input());
    return Map.of("output", response);
};
```

For more complex scenarios with multiple outputs:

```java
Task task = example -> {
    String response = myLlmService.generate(example.input());
    List<String> context = retrieveContext(example.input());
    double confidence = calculateConfidence(response);
    
    return Map.of(
        "output", response,
        "retrievedContext", context,
        "confidence", confidence
    );
};
```

## Running Experiments on a Dataset

### Loading Datasets from Files

Experiments work seamlessly with datasets loaded from JSON or CSV files:

```java
// Load dataset from classpath
Dataset dataset = DatasetResolverRegistry.getInstance()
    .resolve("classpath:datasets/qa-dataset.json");

// Run experiment
ExperimentResult result = Experiment.builder()
    .name("QA Evaluation")
    .dataset(dataset)
    .task(task)
    .evaluators(evaluators)
    .build()
    .run();
```

### Iterating Over Results

After running an experiment, you can iterate over individual results to analyze specific cases:

```java
ExperimentResult result = experiment.run();

// Iterate over all item results
for (ItemResult itemResult : result.itemResults()) {
    System.out.println("\nInput: " + itemResult.example().input());
    System.out.println("Expected: " + itemResult.example().expectedOutput());
    System.out.println("Actual: " + itemResult.actualOutputs().get("output"));
    System.out.println("Success: " + itemResult.success());
    
    // Check individual evaluator results
    for (EvalResult evalResult : itemResult.evalResults()) {
        System.out.println("  " + evalResult.name() + 
            ": " + (evalResult.success() ? "PASS" : "FAIL") +
            " (score: " + evalResult.score() + ")");
    }
}
```

### Filtering Failed Cases

Focus on failed cases to identify areas for improvement:

```java
ExperimentResult result = experiment.run();

List<ItemResult> failures = result.itemResults().stream()
    .filter(item -> !item.success())
    .toList();

System.out.println("Failed cases: " + failures.size());
for (ItemResult failure : failures) {
    System.out.println("Failed input: " + failure.example().input());
    System.out.println("Expected: " + failure.example().expectedOutput());
    System.out.println("Got: " + failure.actualOutputs().get("output"));
}
```

## Configuration Options

The `Experiment.Builder` provides several configuration options:

### Name and Description

```java
Experiment.builder()
    .name("Customer Support QA Evaluation")
    .description("Evaluating the assistant's ability to answer customer support questions accurately")
    .dataset(dataset)
    .task(task)
    .build();
```

### Adding Evaluators

You can add evaluators individually or as a list:

```java
// Add evaluators one by one
Experiment.builder()
    .name("QA Evaluation")
    .dataset(dataset)
    .task(task)
    .evaluator(exactMatchEvaluator)
    .evaluator(faithfulnessEvaluator)
    .evaluator(relevanceEvaluator)
    .build();

// Or add multiple evaluators at once
List<Evaluator> evaluators = List.of(
    exactMatchEvaluator,
    faithfulnessEvaluator,
    relevanceEvaluator
);

Experiment.builder()
    .name("QA Evaluation")
    .dataset(dataset)
    .task(task)
    .evaluators(evaluators)
    .build();
```

### Metadata

Attach custom metadata to track experiment parameters:

```java
Experiment.builder()
    .name("GPT-5.2 Evaluation")
    .dataset(dataset)
    .task(task)
    .evaluators(evaluators)
    .metadata("model", "gpt-5.2")
    .metadata("temperature", 0.7)
    .metadata("timestamp", Instant.now().toString())
    .metadata("version", "1.0.0")
    .build();

// Or add multiple metadata entries
Map<String, Object> metadata = Map.of(
    "model", "gpt-5.2",
    "temperature", 0.7,
    "maxTokens", 500
);

Experiment.builder()
    .name("GPT-5.2 Evaluation")
    .dataset(dataset)
    .task(task)
    .evaluators(evaluators)
    .metadata(metadata)
    .build();
```

Metadata is included in the `ExperimentResult` and can be used for tracking different experiment configurations.

## Including Evaluators

Evaluators assess the quality of your LLM's outputs. Each evaluator produces a score and determines whether the output passes based on a threshold.

### Common Evaluator Patterns

```java
// Exact match evaluator
Evaluator exactMatch = ExactMatchEvaluator.builder()
    .name("Exact Match")
    .threshold(1.0)  // Must match exactly
    .build();

// Regex pattern evaluator
Evaluator regexMatch = RegexEvaluator.builder()
    .name("Pattern Match")
    .pattern("\\d{4}")  // Must contain a 4-digit number
    .threshold(1.0)
    .build();

// LLM-based judge
Evaluator llmJudge = LLMJudgeEvaluator.builder()
    .name("Answer Correctness")
    .criteria("Is the answer factually correct and complete?")
    .evaluationParams(List.of(
        EvalTestCaseParam.INPUT,
        EvalTestCaseParam.EXPECTED_OUTPUT,
        EvalTestCaseParam.ACTUAL_OUTPUT
    ))
    .threshold(0.8)
    .judge(prompt -> myLlm.generate(prompt))
    .build();

// Faithfulness evaluator (for RAG systems)
Evaluator faithfulness = FaithfulnessEvaluator.builder()
    .name("Faithfulness")
    .threshold(0.7)
    .judge(prompt -> myLlm.generate(prompt))
    .contextKey("retrievedContext")
    .build();
```

### Multiple Evaluators

Use multiple evaluators to assess different quality dimensions:

```java
List<Evaluator> evaluators = List.of(
    // Check factual correctness
    LLMJudgeEvaluator.builder()
        .name("Correctness")
        .criteria("Is the answer factually correct?")
        .threshold(0.8)
        .judge(judge)
        .build(),
    
    // Check relevance
    LLMJudgeEvaluator.builder()
        .name("Relevance")
        .criteria("Is the answer relevant to the question?")
        .threshold(0.7)
        .judge(judge)
        .build(),
    
    // Check faithfulness to source
    FaithfulnessEvaluator.builder()
        .threshold(0.8)
        .judge(judge)
        .contextKey("retrievedContext")
        .build()
);

ExperimentResult result = Experiment.builder()
    .name("Multi-dimensional Evaluation")
    .dataset(dataset)
    .task(task)
    .evaluators(evaluators)
    .build()
    .run();

// Get average score per evaluator
System.out.println("Correctness: " + result.averageScore("Correctness"));
System.out.println("Relevance: " + result.averageScore("Relevance"));
System.out.println("Faithfulness: " + result.averageScore("Faithfulness"));
```

## Analyzing Experiment Results

The `ExperimentResult` provides comprehensive metrics and detailed results:

### Aggregate Metrics

```java
ExperimentResult result = experiment.run();

// Overall metrics
System.out.println("Experiment: " + result.name());
System.out.println("Description: " + result.description());
System.out.println("Total examples: " + result.totalCount());
System.out.println("Passed: " + result.passCount());
System.out.println("Failed: " + result.failCount());
System.out.println("Pass rate: " + String.format("%.2f%%", result.passRate() * 100));

// Per-evaluator metrics
System.out.println("\nAverage scores:");
System.out.println("Exact Match: " + result.averageScore("Exact Match"));
System.out.println("Relevance: " + result.averageScore("Relevance"));
```

### Item-Level Results

```java
// Access individual results
List<ItemResult> itemResults = result.itemResults();

for (ItemResult item : itemResults) {
    Example example = item.example();
    Map<String, Object> actualOutputs = item.actualOutputs();
    List<EvalResult> evalResults = item.evalResults();
    boolean success = item.success();
    
    // Your analysis logic here
}
```

### Metadata Access

```java
// Access experiment metadata
Map<String, Object> metadata = result.metadata();
System.out.println("Model: " + metadata.get("model"));
System.out.println("Temperature: " + metadata.get("temperature"));
```

## Testing in CI/CD Pipelines

Experiments can be integrated into CI/CD pipelines to automatically validate your LLM application:

### Approach 1: Programmatic Validation

Create a main class that runs experiments and exits with appropriate status codes:

```java
public class EvaluationPipeline {
    public static void main(String[] args) {
        Dataset dataset = DatasetResolverRegistry.getInstance()
            .resolve("classpath:datasets/qa-dataset.json");
        
        ExperimentResult result = Experiment.builder()
            .name("CI Validation")
            .dataset(dataset)
            .task(task)
            .evaluators(evaluators)
            .build()
            .run();
        
        System.out.println("Pass rate: " + result.passRate() * 100 + "%");
        
        // Fail the build if pass rate is below threshold
        if (result.passRate() < 0.95) {
            System.err.println("❌ Evaluation failed: pass rate below 95%");
            System.exit(1);
        }
        
        System.out.println("✅ Evaluation passed!");
        System.exit(0);
    }
}
```

### Approach 2: JUnit Integration

Use JUnit for CI/CD integration with better test reporting:

```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LLMEvaluationTest {
    
    @Test
    void experimentShouldPassQualityThreshold() {
        Dataset dataset = DatasetResolverRegistry.getInstance()
            .resolve("classpath:datasets/qa-dataset.json");
        
        ExperimentResult result = Experiment.builder()
            .name("QA Evaluation")
            .dataset(dataset)
            .task(task)
            .evaluators(evaluators)
            .build()
            .run();
        
        // Assert pass rate threshold
        assertTrue(result.passRate() >= 0.95,
            "Pass rate " + result.passRate() + " is below threshold 0.95");
        
        // Assert individual evaluator performance
        assertTrue(result.averageScore("Correctness") >= 0.8,
            "Correctness score too low");
    }
}
```

### GitHub Actions Example

```yaml
name: LLM Evaluation

on: [push, pull_request]

jobs:
  evaluate:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Run LLM Evaluations
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
        run: mvn test -Dtest=LLMEvaluationTest
      
      - name: Upload Evaluation Report
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: evaluation-results
          path: target/evaluation-results/
```

### Best Practices for CI/CD

1. **Use separate datasets for CI vs. full evaluation**: Use a smaller, curated dataset for CI to keep builds fast
2. **Set appropriate thresholds**: Don't require 100% pass rate initially; set realistic thresholds
3. **Cache model responses**: Consider caching LLM responses to reduce API costs in CI
4. **Fail fast**: Run critical evaluations first to catch regressions early
5. **Generate reports**: Save detailed experiment results as artifacts for later analysis

## Concurrent Execution

:::info Coming Soon
Concurrent execution support is planned for a future release. This will enable:
- Parallel processing of dataset examples
- Faster experiment execution for large datasets
- Configurable concurrency levels
- Thread-safe evaluator execution

Stay tuned for updates!
:::

## Usage with LangChain4j

The `dokimos-langchain4j` module provides seamless integration with LangChain4j applications:

```java
import dev.dokimos.langchain4j.LangChain4jSupport;

// Create your LangChain4j AI Service
interface Assistant {
    Result<String> chat(String userMessage);
}

Assistant assistant = AiServices.builder(Assistant.class)
    .chatLanguageModel(chatModel)
    .retrievalAugmentor(retrievalAugmentor)
    .build();

// Wrap it as a Task
Task task = LangChain4jSupport.ragTask(assistant::chat);

// Run experiment
ExperimentResult result = Experiment.builder()
    .name("RAG Evaluation")
    .dataset(dataset)
    .task(task)
    .evaluators(evaluators)
    .build()
    .run();
```

The `ragTask` method automatically extracts the retrieved context and includes it in the outputs for faithfulness evaluation.

## Complete Example

Here's a complete example putting it all together:

```java
import dev.dokimos.core.*;
import java.util.List;
import java.util.Map;

public class ComprehensiveEvaluation {
    
    public static void main(String[] args) {
        // 1. Load dataset
        Dataset dataset = Dataset.builder()
            .name("Customer Support QA")
            .addExample(Example.of(
                "What is the refund policy?",
                "30-day money-back guarantee"
            ))
            .addExample(Example.of(
                "How long does shipping take?",
                "5-7 business days"
            ))
            .build();
        
        // 2. Define task
        Task task = example -> {
            String response = generateResponse(example.input());
            List<String> context = retrieveContext(example.input());
            
            return Map.of(
                "output", response,
                "retrievedContext", context
            );
        };
        
        // 3. Define evaluators
        JudgeLM judge = prompt -> llmJudge.generate(prompt);
        
        List<Evaluator> evaluators = List.of(
            LLMJudgeEvaluator.builder()
                .name("Answer Correctness")
                .criteria("Is the answer correct and complete?")
                .threshold(0.8)
                .judge(judge)
                .build(),
            FaithfulnessEvaluator.builder()
                .threshold(0.7)
                .judge(judge)
                .contextKey("retrievedContext")
                .build()
        );
        
        // 4. Run experiment
        ExperimentResult result = Experiment.builder()
            .name("Customer Support Evaluation")
            .description("Evaluating RAG-based customer support assistant")
            .dataset(dataset)
            .task(task)
            .evaluators(evaluators)
            .metadata("model", "gpt-5.2")
            .metadata("timestamp", System.currentTimeMillis())
            .build()
            .run();
        
        // 5. Analyze results
        System.out.println("=== Experiment Results ===");
        System.out.println("Pass rate: " + 
            String.format("%.2f%%", result.passRate() * 100));
        System.out.println("Passed: " + result.passCount());
        System.out.println("Failed: " + result.failCount());
        
        System.out.println("\n=== Evaluator Scores ===");
        System.out.println("Answer Correctness: " + 
            String.format("%.2f", result.averageScore("Answer Correctness")));
        System.out.println("Faithfulness: " + 
            String.format("%.2f", result.averageScore("Faithfulness")));
        
        // 6. Analyze failures
        List<ItemResult> failures = result.itemResults().stream()
            .filter(item -> !item.success())
            .toList();
        
        if (!failures.isEmpty()) {
            System.out.println("\n=== Failed Cases ===");
            failures.forEach(failure -> {
                System.out.println("\nInput: " + failure.example().input());
                System.out.println("Expected: " + failure.example().expectedOutput());
                System.out.println("Actual: " + failure.actualOutputs().get("output"));
                failure.evalResults().forEach(eval -> {
                    if (!eval.success()) {
                        System.out.println("  Failed: " + eval.name() + 
                            " (score: " + eval.score() + ")");
                    }
                });
            });
        }
    }
    
    private static String generateResponse(String input) {
        // Your LLM call here
        return "...";
    }
    
    private static List<String> retrieveContext(String input) {
        // Your RAG retrieval here
        return List.of();
    }
}
```

## Best Practices

### 1. Start Small, Scale Up

Begin with a small, high-quality dataset (10-20 examples) to validate your experiment setup, then scale up to larger datasets.

### 2. Use Descriptive Names

Give experiments clear, descriptive names that indicate what's being tested:

```java
.name("gpt-5.2 Customer Support QA - Temperature 0.7")
```

### 3. Track Configuration with Metadata

Always include relevant configuration in metadata for reproducibility:

```java
.metadata("model", "gpt-5.2")
.metadata("temperature", 0.7)
.metadata("maxTokens", 500)
.metadata("timestamp", Instant.now())
```

### 4. Choose Appropriate Evaluators

Select evaluators that match your quality requirements:
- **Exact match**: For deterministic outputs (calculations, code)
- **LLM judges**: For creative or open-ended responses
- **Faithfulness**: For RAG systems to ensure grounding
- **Custom evaluators**: For domain-specific requirements

### 5. Set Realistic Thresholds

Don't expect 100% pass rates initially. Start with achievable thresholds and improve over time:

```java
.threshold(0.7)  // 70% is often a good starting point
```

### 6. Version Your Datasets

Keep datasets in version control and update them as you discover new edge cases:

```
src/main/resources/datasets/
  ├── customer-support-v1.json
  ├── customer-support-v2.json
  └── customer-support-latest.json
```

### 7. Automate Regular Evaluations

Run experiments regularly (nightly builds, weekly reports) to track performance over time and catch regressions early.
