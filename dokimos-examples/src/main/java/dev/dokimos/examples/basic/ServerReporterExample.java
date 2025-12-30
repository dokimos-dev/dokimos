package dev.dokimos.examples.basic;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import dev.dokimos.core.*;
import dev.dokimos.core.evaluators.ExactMatchEvaluator;
import dev.dokimos.core.evaluators.RegexEvaluator;
import dev.dokimos.server.client.DokimosServerReporter;

import java.util.List;
import java.util.Map;

/**
 * Example demonstrating end-to-end evaluation with OpenAI and server reporting.
 * <p>
 * This example:
 * - Uses the OpenAI API to generate responses
 * - Evaluates with two different evaluators (exact match and semantic
 * similarity)
 * - Reports results to a Dokimos server
 * <p>
 * Prerequisites:
 * - Set OPENAI_API_KEY environment variable
 * - Start the Dokimos server (mvn spring-boot:run -pl dokimos-server)
 * - Server should be running at http://localhost:8080
 * <p>
 * Run with:
 * {@code mvn exec:java -pl dokimos-examples -Dexec.mainClass="dev.dokimos.examples.basic.ServerReporterExample"}
 */
public class ServerReporterExample {

        private static final String SERVER_URL = "http://localhost:8080";
        private static final String PROJECT_NAME = "openai-qa-demo";

        public static void main(String[] args) {
                System.out.println("=== Dokimos Server Reporter Example ===\n");

                String apiKey = System.getenv("OPENAI_API_KEY");
                if (apiKey == null || apiKey.isBlank()) {
                        System.err.println("ERROR: OPENAI_API_KEY environment variable not set");
                        System.err.println("Please set it with: export OPENAI_API_KEY='your-api-key'");
                        System.exit(1);
                }

                OpenAIClient openai = OpenAIOkHttpClient.builder()
                                .apiKey(apiKey)
                                .build();

                // Create a small dataset with 2 examples
                Dataset dataset = Dataset.builder()
                                .name("Capital Cities QA")
                                .description("Simple questions about world capitals")
                                .addExample(Example.of("What is the capital of France?", "Paris"))
                                .addExample(Example.of("What is the capital of Japan?", "Tokyo"))
                                .build();

                System.out.println("Dataset: " + dataset.name());
                System.out.println("Examples: " + dataset.examples().size());
                System.out.println();

                // Define two evaluators
                List<Evaluator> evaluators = List.of(
                                ExactMatchEvaluator.builder()
                                                .name("exact-match")
                                                .threshold(1.0)
                                                .build(),

                                RegexEvaluator.builder()
                                                .name("capital-pattern")
                                                .pattern("[A-Z][a-z]+")
                                                .threshold(1.0)
                                                .build());

                // Create a task that calls OpenAI
                Task task = example -> {
                        System.out.println("Processing: " + example.input());

                        var response = openai.chat().completions().create(
                                        ChatCompletionCreateParams.builder()
                                                        .model(ChatModel.GPT_5_NANO)
                                                        .addSystemMessage(
                                                                        "You are a helpful assistant that answers questions concisely with just the answer, no explanations")
                                                        .addUserMessage(example.input())
                                                        .build());

                        String answer = response.choices().get(0).message().content().orElse("");
                        System.out.println("Response: " + answer);

                        return Map.of("output", answer);
                };

                DokimosServerReporter reporter = DokimosServerReporter.builder()
                                .serverUrl(SERVER_URL)
                                .projectName(PROJECT_NAME)
                                .build();

                try {
                        System.out.println("Connecting to Dokimos server at " + SERVER_URL);
                        System.out.println("Project: " + PROJECT_NAME);
                        System.out.println();

                        System.out.println("Running experiment...\n");
                        ExperimentResult result = Experiment.builder()
                                        .name("openai-capitals-eval")
                                        .dataset(dataset)
                                        .task(task)
                                        .evaluators(evaluators)
                                        .reporter(reporter)
                                        .metadata(Map.of(
                                                        "model", "gpt-5-nano",
                                                        "temperature", 0.0,
                                                        "version", "1.0.0"))
                                        .build()
                                        .run();

                        // Display local results
                        System.out.println("\n=== Results ===");
                        System.out.println("Experiment: " + result.name());
                        System.out.println("Total examples: " + result.totalCount());
                        System.out.println("Passed: " + result.passCount());
                        System.out.println("Failed: " + result.failCount());
                        System.out.println("Pass rate: " + String.format("%.2f%%", result.passRate()
                                        * 100));
                        System.out.println();

                        // Show individual results
                        System.out.println("=== Individual Results ===");
                        result.itemResults().forEach(itemResult -> {
                                System.out.println("\nInput: " + itemResult.example().input());
                                System.out.println("Expected: " + itemResult.example().expectedOutput());
                                System.out.println("Actual: " + itemResult.actualOutputs().get("output"));
                                System.out.println("Success: " + itemResult.success());
                                itemResult.evalResults().forEach(evalResult -> System.out.println(" - "
                                                + evalResult.name() + ": " +
                                                (evalResult.success() ? "PASS" : "FAIL") +
                                                " (score: " + String.format("%.2f", evalResult.score()) + ")"));
                        });

                        System.out.println("\n=== Server ===");
                        System.out.println("Results have been sent to the Dokimos server!");
                        System.out.println("View them at: " + SERVER_URL);

                } catch (Exception e) {
                        System.err.println("Error running experiment: " + e.getMessage());
                        e.printStackTrace();
                } finally {
                        reporter.close();
                }
        }
}
