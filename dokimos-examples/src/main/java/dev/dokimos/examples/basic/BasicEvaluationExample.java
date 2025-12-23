package dev.dokimos.examples.basic;

import dev.dokimos.core.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Basic evaluation example that demonstrates how to:
 * - Create a {@link Dataset} programmatically
 * - Define multiple evaluators that we want to run
 * - Create a simple task with an actual LLM by OpenAI
 * - Run an experiment and show the evaluation results
 * <p>
 * Run with: {@code mvn exec:java -pl dokimos-examples}
 */
public class BasicEvaluationExample {

    public static void main(String[] args) {
        System.out.println("=== Dokimos Basic Evaluation Example ===\n");

        // Create a dataset programmatically
        // Dataset examples can be added to a dataset by providing the input and the expected output.
        // Datasets can also be provided with CSV and JSON files.
        var dataset = Dataset.builder()
                .name("Simple QA Dataset")
                .description("Basic question-answering examples")
                .addExample(Example.of("What is 2+2?", "4"))
                .addExample(Example.of("What is the capital of France?", "Paris"))
                .addExample(Example.of("What is the capital of Switzerland?", "Bern"))
                .build();

        System.out.println("Dataset: " + dataset.name());
        System.out.println("Examples: " + dataset.examples().size());
        System.out.println();

        // Define evaluators
        // ...
        List<Evaluator> evaluators = List.of(
                ExactMatchEvaluator.builder()
                        .name("Exact Match")
                        .threshold(1.0)
                        .build(),
                RegexEvaluator.builder()
                        .name("Pattern Match")
                        .pattern("\\d+|[A-Z][a-z]+")
                        .build()
        );

        // Create a task - this simulates your LLM or system under test
        Task task = example -> {
            String answer = simulateLLM(Objects.requireNonNull(example.input()));
            return Map.of("output", answer);
        };

        // Run experiment
        System.out.println("Running experiment...\n");
        ExperimentResult result = Experiment.builder()
                .name("QA Evaluation")
                .dataset(dataset)
                .task(task)
                .evaluators(evaluators)
                .build()
                .run();

        // Display results
        System.out.println("=== Results ===");
        System.out.println("Experiment: " + result.name());
        System.out.println("Total examples: " + result.totalCount());
        System.out.println("Passed: " + result.passCount());
        System.out.println("Failed: " + result.failCount());
        System.out.println("Pass rate: " + String.format("%.2f%%", result.passRate() * 100));
        System.out.println("Average score (Exact Match): " + String.format("%.2f", result.averageScore("Exact Match")));
        System.out.println();

        // Show individual results
        System.out.println("=== Individual Results ===");
        result.itemResults().forEach(itemResult -> {
            System.out.println("\nInput: " + itemResult.example().input());
            System.out.println("Expected: " + itemResult.example().expectedOutput());
            System.out.println("Actual: " + itemResult.actualOutputs().get("output"));
            System.out.println("Passed: " + itemResult.success());
            itemResult.evalResults().forEach(evalResult -> System.out.println("  - " + evalResult.name() + ": " +
                    (evalResult.success() ? "PASS" : "FAIL") +
                    " (score: " + String.format("%.2f", evalResult.score()) + ")"));
        });
    }

    /**
     * Simulates an LLM call. In a real application, this would call
     * an actual LLM API (OpenAI, Anthropic, local model, etc.)
     */
    private static String simulateLLM(String input) {
        // Simple rule-based responses to demonstrate the evaluation
        return switch (input) {
            case "What is 2+2?" -> "4";
            case "What is the capital of France?" -> "Paris";
            case "What is the capital of Switzerland?" -> "Bern";
            default -> "I don't know";
        };
    }
}