---
sidebar_position: 3
---

# Experiments

An experiment runs your LLM application (called a **Task**) against a dataset, applies evaluators to check the outputs, and gives you aggregated results. It's the main way to systematically evaluate how well your application performs.

## Why Use Experiments?

When building LLM applications, you need more than just manual testing with a few prompts. Experiments help you:

**Get quantitative metrics** - Track pass rates, average scores, and success counts over time. This makes it easier to know if a prompt change or model update actually improved things.

**Test systematically** - Run your application against a full dataset automatically, rather than manually trying different inputs.

**Compare configurations** - See how different models, prompts, or retrieval strategies perform against the same test cases.

**Catch regressions** - Integrate experiments into your CI/CD pipeline to make sure changes don't break existing functionality.

**Find patterns in failures** - When things go wrong, you can analyze which types of inputs consistently fail and why.

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

**Use JUnit tests when you want to:**
- Fail your build if critical test cases don't pass
- Catch regressions quickly during development
- Get immediate feedback on specific examples

**Use experiments when you want to:**
- Analyze performance across an entire dataset
- Generate reports with detailed metrics and trends
- Compare different model configurations or prompt versions
- Understand overall application behavior

Most projects benefit from using both approaches together.

## Basic Usage

### Creating a Simple Experiment

Here's a minimal example of creating and running an experiment:

```java
import dev.dokimos.core.*;

// Define your dataset
Dataset dataset = Dataset.builder()
    .name("Product Support Questions")
    .addExample(Example.of(
        "How do I reset my password?",
        "Click 'Forgot Password' on the login page and follow the email instructions"
    ))
    .addExample(Example.of(
        "Where can I track my order?",
        "Go to your account dashboard and click on 'Order History'"
    ))
    .addExample(Example.of(
        "What payment methods do you accept?",
        "We accept credit cards, PayPal, and bank transfers"
    ))
    .build();

// Define your task (your LLM application)
Task task = example -> {
    String answer = customerSupportBot.generateAnswer(example.input());
    return Map.of("output", answer);
};

// Define evaluators
List<Evaluator> evaluators = List.of(
    LLMJudgeEvaluator.builder()
        .name("Answer Quality")
        .criteria("Is the answer helpful and accurate?")
        .judge(judge)
        .threshold(0.8)
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

A Task is what runs your LLM application for each example in the dataset. It's a simple functional interface:

```java
@FunctionalInterface
public interface Task {
    Map<String, Object> run(Example example);
}
```

Your task takes an example, runs your application, and returns the outputs. Here's the simplest version:

```java
Task task = example -> {
    String response = myLlmService.generate(example.input());
    return Map.of("output", response);
};
```

For RAG systems or other complex scenarios, you can return multiple values:

```java
Task ragTask = example -> {
    // Retrieve relevant documents
    List<String> retrievedDocs = vectorStore.search(example.input(), topK = 3);
    
    // Generate response using retrieved context
    String response = ragSystem.generate(example.input(), retrievedDocs);
    
    // Calculate confidence score
    double confidence = ragSystem.getConfidenceScore();
    
    return Map.of(
        "output", response,
        "retrievedContext", retrievedDocs,
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

### Analyzing Individual Results

After running an experiment, you can dig into specific cases to understand what went wrong:

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

### Finding Failures

When things don't work as expected, filter for failed cases:

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

## Parallelism and Multiple Runs

Dokimos supports running experiments with parallelism and multiple runs for statistical confidence.

### Parallelism

Set `.parallelism(n)` to process n examples concurrently within each run:

```java
ExperimentResult result = Experiment.builder()
    .name("Knowledge Assistant Evaluation")
    .dataset(dataset)
    .task(task)
    .evaluators(evaluators)
    .parallelism(4)  // Run 4 examples concurrently
    .build()
    .run();
```

Default is 1 for sequential execution. Increase for faster execution, but be mindful of API rate limits.

When using parallelism, ensure your task implementation is thread-safe.

### Multiple Runs

Set `.runs(n)` to repeat the experiment n times:

```java
ExperimentResult result = Experiment.builder()
    .name("Knowledge Assistant Evaluation")
    .dataset(dataset)
    .task(task)
    .evaluators(evaluators)
    .runs(3)         // Run experiment 3 times
    .parallelism(4)  // Parallelism within each run
    .build()
    .run();
```

Runs execute sequentially while parallelism applies within each run. This helps reduce variance from LLM non-determinism and provides statistical confidence in results.

Access run statistics:

```java
result.averageScore("Faithfulness")     // Mean across all runs
result.scoreStdDev("Faithfulness")      // Standard deviation across runs
result.runCount()                       // Number of runs performed
result.runs()                           // Individual run results
```

High standard deviation suggests instability in your task or evaluator outputs.

## Configuring Experiments

You can customize experiments with names, descriptions, evaluators, and metadata.

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

### Tracking Experiment Configuration

Use metadata to record what settings you used for each experiment run. This is helpful when comparing results across different model versions or configurations:

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

## Working with Evaluators

Evaluators check the quality of your outputs. Each evaluator gives a score (0.0 to 1.0) and decides if the output passes based on a threshold you set.

Here are some common patterns:

```java
// For deterministic outputs like calculations
Evaluator exactMatch = ExactMatchEvaluator.builder()
    .name("Exact Match")
    .threshold(1.0)
    .build();

// For checking output format (e.g., dates, phone numbers)
Evaluator formatCheck = RegexEvaluator.builder()
    .name("Date Format")
    .pattern("\\d{4}-\\d{2}-\\d{2}")  // YYYY-MM-DD
    .threshold(1.0)
    .build();

// For semantic correctness using an LLM as judge
Evaluator semanticCorrectness = LLMJudgeEvaluator.builder()
    .name("Answer Correctness")
    .criteria("Is the answer factually correct and complete?")
    .evaluationParams(List.of(
        EvalTestCaseParam.INPUT,
        EvalTestCaseParam.EXPECTED_OUTPUT,
        EvalTestCaseParam.ACTUAL_OUTPUT
    ))
    .threshold(0.8)
    .judge(prompt -> judgeModel.generate(prompt))
    .build();

// For checking if RAG outputs are grounded in retrieved docs
Evaluator faithfulness = FaithfulnessEvaluator.builder()
    .name("Faithfulness")
    .threshold(0.7)
    .judge(prompt -> judgeModel.generate(prompt))
    .contextKey("retrievedContext")
    .build();
```

### Evaluating Multiple Dimensions

Most real applications need to be evaluated on several criteria at once:

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

The `ExperimentResult` provides comprehensive metrics and detailed results. When running multiple runs, all metrics are automatically averaged across runs.

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

// For multi-run experiments, check stability
if (result.runCount() > 1) {
    System.out.println("\nScore stability (standard deviation):");
    System.out.println("Exact Match: " + result.scoreStdDev("Exact Match"));
    System.out.println("Relevance: " + result.scoreStdDev("Relevance"));
}
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

## Running Experiments in CI/CD

You can run experiments automatically in your CI/CD pipeline to catch regressions before they reach production.

### Simple Approach: Exit Code

Create a main class that fails the build if results don't meet your threshold:

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

### JUnit Integration

For better test reporting and IDE integration, wrap experiments in JUnit tests:

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

### Tips for CI/CD

**Keep CI datasets small** - Use a subset of your full dataset for CI (maybe 20-50 examples) to keep builds fast. Run comprehensive evaluations nightly or weekly.

**Set realistic thresholds** - Don't expect 100% pass rates right away. Start with something achievable (like 80%) and gradually increase it.

**Cache responses when possible** - If you're testing the same examples repeatedly, consider caching LLM responses to save on API costs.

**Fail early** - Put your most critical evaluators first so you catch obvious problems quickly.

**Save detailed results** - Upload experiment results as build artifacts so you can review failures later.

## LangChain4j Integration

If you're using LangChain4j, the `dokimos-langchain4j` module makes it easy to evaluate AI Services:

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

The `ragTask()` method automatically extracts retrieved context from `Result.sources()` and includes it in the outputs for faithfulness evaluation.

## Best Practices

### Start small, then grow

Don't try to build a huge dataset upfront. Start with 10-20 high-quality examples that cover your main use cases. Run experiments frequently and add more examples as you discover edge cases.

### Name experiments clearly

When you're comparing results later, you'll want to know exactly what each experiment tested:

```java
.name("gpt-5-nano-customer-support-temp0.7-2025-12-27")
```

### Track everything with metadata

Record model settings, versions, and timestamps so you can reproduce results:

```java
.metadata("model", "gpt-5-nano")
.metadata("temperature", 0.7)
.metadata("prompt_version", "v3")
.metadata("timestamp", Instant.now().toString())
```

### Match evaluators to your needs

- Use **exact match** for factual answers that should be deterministic (like calculations)
- Use **LLM judges** when you need semantic understanding (like checking if an explanation makes sense)
- Use **faithfulness** for RAG systems to ensure answers are grounded in your documents
- Build **custom evaluators** for domain-specific requirements

### Set achievable thresholds

Don't expect perfection right away. Start with realistic thresholds (maybe 70-80%) and increase them as you improve your application.

### Version your datasets

As you add test cases, keep old versions around so you can track how your application improves over time:

```
src/test/resources/datasets/
  ├── support-v1-initial.json
  ├── support-v2-edge-cases.json
  └── support-v3-current.json
```

### Run experiments regularly

Set up nightly builds or weekly evaluations to catch performance regressions early. You can also run quick experiments during development with a smaller dataset.
