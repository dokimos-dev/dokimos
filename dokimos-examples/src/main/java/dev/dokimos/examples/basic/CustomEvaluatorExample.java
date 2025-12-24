package dev.dokimos.examples.basic;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import dev.dokimos.core.*;

import java.util.List;
import java.util.Map;

/**
 * This example shows two ways to create custom evaluators:
 * <p>
 * 1. Build your own evaluator by extending BaseEvaluator
 * 2. Use {@link LLMJudgeEvaluator} with OpenAI for semantic evaluation
 * <p>
 * The first example runs locally without API keys.
 * The second example needs {@code OPENAI_API_KEY} to be set.
 * <p>
 * Run: {@code mvn exec:java -pl dokimos-examples -Dexec.mainClass="dev.dokimos.examples.basic.CustomEvaluatorExample"}
 */
public class CustomEvaluatorExample {

    public static void main(String[] args) {
        System.out.println("=== Custom Evaluator Examples ===\n");

        // Example 1: Custom Evaluator
        runCustomEvaluatorExample();

        System.out.println("\n" + "=".repeat(50) + "\n");

        // Example 2: LLM Judge Evaluator which uses OpenAI
        runLLMJudgeExample();
    }

    /**
     * Example 1: Build a custom evaluator
     * <p>
     * Here we create a {@code KeywordEvaluator} that checks if the LLM output
     * contains specific keywords. This is useful for verifying if certain
     * topics or terms are mentioned in the response.
     */
    private static void runCustomEvaluatorExample() {
        System.out.println("Example 1: Custom Keyword Evaluator");
        System.out.println("Checks if the answer contains specific keywords\n");

        // Create a new dataset programmatically
        Dataset dataset = Dataset.builder()
                .name("Product Info Dataset")
                .addExample(Example.of(
                        "What are the main features of this product?",
                        "wireless, waterproof, battery"
                ))
                .addExample(Example.of(
                        "What payment methods do you accept?",
                        "credit card, PayPal, bank transfer"
                ))
                .build();

        // Our custom evaluator
        Evaluator keywordEvaluator = new KeywordEvaluator(
                "Keyword Presence",
                0.6, // minimum 60% must be present in the LLM response
                List.of("wireless", "waterproof", "long battery life")
        );

        // Simulate some LLM responses
        Task task = example -> {
            String response = simulateProductInfoLLM(example.input());
            return Map.of("output", response);
        };

        // Run the evaluation
        ExperimentResult result = Experiment.builder()
                .name("Product Info Evaluation")
                .dataset(dataset)
                .task(task)
                .evaluator(keywordEvaluator)
                .build()
                .run();

        // Show results
        System.out.println("Results:");
        System.out.println("  Pass rate: " + String.format("%.0f%%", result.passRate() * 100));
        System.out.println("\nDetailed Results:");
        result.itemResults().forEach(item -> {
            System.out.println("  Input: " + item.example().input());
            System.out.println("  Response: " + item.actualOutputs().get("output"));
            System.out.println("  Status: " + (item.success() ? "✓ PASS" : "✗ FAIL"));
            item.evalResults().forEach(eval -> {
                System.out.println("    - " + eval.name() + ": " +
                        String.format("%.2f", eval.score()) + " " +
                        (eval.reason() != null ? "(" + eval.reason() + ")" : ""));
            });
            System.out.println();
        });
    }

    /**
     * Example 2: Use an LLM as a judge
     * <p>
     * Instead of hard-coded rules, we use OpenAI to evaluate the quality
     * of responses. This is more flexible and can understand semantic meaning.
     */
    private static void runLLMJudgeExample() {
        System.out.println("Example 2: LLM Judge Evaluator");
        System.out.println("Uses OpenAI to judge answer quality\n");

        // Check if API key is set
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Skipping this example, OPENAI_API_KEY not set");
            System.err.println("Set it with: export OPENAI_API_KEY='your-api-key'");
            return;
        }

        // Set up OpenAI client
        OpenAIClient client = OpenAIOkHttpClient.fromEnv();

        // Create a judge that calls OpenAI
        JudgeLM judge = prompt -> {
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .addUserMessage(prompt)
                    .model(ChatModel.GPT_5_NANO)
                    .build();

            return client.chat().completions().create(params)
                    .choices().get(0).message().content().orElse("");
        };

        // Test cases
        Dataset dataset = Dataset.builder()
                .name("Customer Support QA")
                .addExample(Example.of(
                        "Can I return my order after 60 days?",
                        "30-day return policy"
                ))
                .addExample(Example.of(
                        "What's the shipping time?",
                        "5-7 business days"
                ))
                .build();

        // Create the LLM judge evaluator
        Evaluator llmJudge = LLMJudgeEvaluator.builder()
                .name("Answer Correctness")
                .judge(judge)
                .criteria("Does the answer correctly address the question? " +
                        "Score 1.0 if accurate and helpful, 0.0 if wrong.")
                .evaluationParams(List.of(
                        EvalTestCaseParam.INPUT,
                        EvalTestCaseParam.ACTUAL_OUTPUT,
                        EvalTestCaseParam.EXPECTED_OUTPUT
                ))
                .threshold(0.7)
                .scoreRange(0.0, 1.0)
                .build();

        // Simulate responses
        Task task = example -> {
            String response = simulateCustomerSupportLLM(example.input());
            return Map.of("output", response);
        };

        // Run evaluation
        System.out.println("Calling OpenAI to judge responses...");
        ExperimentResult result = Experiment.builder()
                .name("Customer Support Evaluation")
                .dataset(dataset)
                .task(task)
                .evaluator(llmJudge)
                .build()
                .run();

        // Show results
        System.out.println("\nResults:");
        System.out.println("  Pass rate: " + String.format("%.0f%%", result.passRate() * 100));
        System.out.println("\nDetailed Results:");
        result.itemResults().forEach(item -> {
            System.out.println("  Question: " + item.example().input());
            System.out.println("  Response: " + item.actualOutputs().get("output"));
            System.out.println("  Status: " + (item.success() ? "✓ PASS" : "✗ FAIL"));
            item.evalResults().forEach(eval -> {
                System.out.println("    - Score: " + String.format("%.2f", eval.score()));
                System.out.println("    - Reasoning: " + eval.reason());
            });
            System.out.println();
        });
    }

    // Helper methods to simulate LLM responses

    private static String simulateProductInfoLLM(String input) {
        if (input.contains("features")) {
            return "This product is wireless and waterproof with a long battery life of up to 48 hours.";
        } else if (input.contains("payment")) {
            return "We accept all major credit cards and PayPal for your convenience.";
        }
        return "Product information available on request.";
    }

    private static String simulateCustomerSupportLLM(String input) {
        if (input.contains("return") && input.contains("60 days")) {
            return "Our return policy allows returns within 30 days of purchase.";
        } else if (input.contains("shipping")) {
            return "Standard shipping typically takes 5-7 business days.";
        }
        return "Please contact our support team for more information.";
    }

    /**
     * KeywordEvaluator, which checks if an output contains any required keywords
     * <p>
     * This is a simple example showing how to extend {@link BaseEvaluator}.
     * You can use this pattern for any rule-based evaluation logic.
     */
    static class KeywordEvaluator extends BaseEvaluator {
        private final List<String> requiredKeywords;

        public KeywordEvaluator(String name, double threshold, List<String> requiredKeywords) {
            super(name, threshold, List.of(EvalTestCaseParam.ACTUAL_OUTPUT));
            this.requiredKeywords = requiredKeywords;
        }

        @Override
        protected EvalResult runEvaluation(EvalTestCase testCase) {
            String actualOutput = testCase.actualOutput().toLowerCase();

            // Count matching keywords
            long matchedKeywords = requiredKeywords.stream()
                    .filter(keyword -> actualOutput.contains(keyword.toLowerCase()))
                    .count();

            // Calculate the score
            double score = requiredKeywords.isEmpty() ? 1.0 :
                    (double) matchedKeywords / requiredKeywords.size();

            String reason = String.format("Found %d/%d required keywords",
                    matchedKeywords, requiredKeywords.size());

            return EvalResult.builder()
                    .name(name)
                    .score(score)
                    .threshold(threshold)
                    .reason(reason)
                    .build();
        }
    }
}