package dev.dokimos.examples.conversation;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.conversation.*;

import java.util.List;

/**
 * This example demonstrates how to use Dokimos to simulate and evaluate
 * multi-turn conversations.
 * <p>
 * The example shows:
 * <ul>
 *   <li>Creating a simulated user with a specific persona</li>
 *   <li>Wrapping an application for conversation testing</li>
 *   <li>Running a conversation simulation</li>
 *   <li>Evaluating the conversation trajectory</li>
 * </ul>
 * <p>
 * In a real application, you would replace the mock LLM implementations
 * with actual LLM API calls (OpenAI, Anthropic, etc.).
 * <p>
 * Run with: {@code mvn exec:java -pl dokimos-examples -Dexec.mainClass="dev.dokimos.examples.conversation.MultiTurnConversationExample"}
 */
public class MultiTurnConversationExample {

    public static void main(String[] args) {
        System.out.println("=== Dokimos Multi-Turn Conversation Example ===\n");

        // Create a mock JudgeLM for demonstration
        // In production, this would call an actual LLM API
        JudgeLM judgeLM = createMockJudgeLM();

        // Create a simulated user with the "aggressive customer" persona
        System.out.println("1. Creating simulated user (aggressive customer)...\n");
        SimulatedUser user = LLMSimulatedUser.builder()
                .judge(judgeLM)
                .persona("frustrated customer who received a defective product")
                .behaviorGuidelines("""
                        - Express strong dissatisfaction with the product
                        - Demand a full refund or replacement
                        - Be firm but not abusive
                        - Mention that you've been a loyal customer
                        """)
                .fixedResponses(List.of(
                        "I just received my order and it's completely broken! This is unacceptable!",
                        "I've been a customer for 5 years and this is how you treat me?"
                ))
                .build();

        // Create a mock application (chatbot) to test
        System.out.println("2. Creating mock customer service chatbot...\n");
        ConversationalApplication chatbot = createMockChatbot();

        // Run the conversation simulation
        System.out.println("3. Running conversation simulation...\n");
        ConversationSimulator simulator = ConversationSimulator.builder()
                .simulatedUser(user)
                .application(chatbot)
                .maxTurns(4)
                .scenario("Customer received defective product and wants resolution")
                .build();

        ConversationTrajectory trajectory = simulator.simulate();

        // Print the conversation
        System.out.println("=== Conversation Transcript ===");
        System.out.println(trajectory.toText());
        System.out.println();

        // Create trajectory evaluator
        System.out.println("4. Evaluating conversation...\n");
        TrajectoryEvaluator evaluator = TrajectoryEvaluator.builder()
                .name("Customer Service Quality")
                .threshold(0.7)
                .judge(judgeLM)
                .criteria(List.of(
                        TrajectoryEvaluationCriteria.userSatisfaction(),
                        TrajectoryEvaluationCriteria.problemResolution(),
                        TrajectoryEvaluationCriteria.professionalTone()
                ))
                .aggregationStrategy(AggregationStrategy.MEAN)
                .includePerCriterionScores(true)
                .build();

        // Evaluate the trajectory
        EvalTestCase testCase = EvalTestCase.builder()
                .actualOutput("trajectory", trajectory)
                .build();

        EvalResult result = evaluator.evaluate(testCase);

        // Print evaluation results
        System.out.println("=== Evaluation Results ===");
        System.out.println("Overall Score: " + String.format("%.2f", result.score()));
        System.out.println("Passed: " + result.success());
        System.out.println("Threshold: " + result.threshold());
        System.out.println("Reason: " + result.reason());
        System.out.println();

        // Print per-criterion scores
        System.out.println("=== Per-Criterion Scores ===");
        @SuppressWarnings("unchecked")
        var criterionScores = (java.util.Map<String, Object>) result.metadata().get("criterionScores");
        if (criterionScores != null) {
            criterionScores.forEach((name, details) -> {
                @SuppressWarnings("unchecked")
                var d = (java.util.Map<String, Object>) details;
                System.out.println(name + ":");
                System.out.println("  Score: " + String.format("%.2f", (Double) d.get("score")));
                System.out.println("  Reason: " + d.get("reason"));
            });
        }

        System.out.println("\n=== JSON Trajectory (for debugging) ===");
        System.out.println(trajectory.toJson());
    }

    /**
     * Creates a mock JudgeLM for demonstration purposes.
     * In production, this would call an actual LLM API.
     */
    private static JudgeLM createMockJudgeLM() {
        return prompt -> {
            // Mock simulated user responses
            if (prompt.contains("simulating a user")) {
                if (prompt.contains("I apologize")) {
                    return "Fine, I'll accept the replacement. But this better not happen again.";
                } else if (prompt.contains("shipping a replacement")) {
                    return "Okay, when will it arrive?";
                }
                return "I'm still not satisfied with this response!";
            }

            // Mock evaluation responses
            if (prompt.contains("User Satisfaction")) {
                return """
                        {"score": 0.7, "reason": "User was initially frustrated but accepted the resolution"}
                        """;
            } else if (prompt.contains("Problem Resolution")) {
                return """
                        {"score": 0.85, "reason": "A replacement was offered and accepted"}
                        """;
            } else if (prompt.contains("Professional Tone")) {
                return """
                        {"score": 0.9, "reason": "Agent maintained professional demeanor throughout"}
                        """;
            }

            return """
                    {"score": 0.75, "reason": "Generally acceptable performance"}
                    """;
        };
    }

    /**
     * Creates a mock chatbot for demonstration purposes.
     * In production, this would be your actual chatbot implementation.
     */
    private static ConversationalApplication createMockChatbot() {
        return trajectory -> {
            int turnNumber = trajectory.assistantMessages().size();

            String response = switch (turnNumber) {
                case 0 -> "I'm so sorry to hear about your experience. I completely understand your frustration. " +
                        "Let me look into this right away. Can you please provide your order number?";
                case 1 -> "Thank you for your patience and loyalty over the years. I can see your order history " +
                        "and I sincerely apologize for this issue. I'm immediately shipping a replacement " +
                        "at no additional cost, and I'm also adding a 20% discount to your next order.";
                case 2 -> "The replacement will be shipped today via express delivery and should arrive " +
                        "within 2-3 business days. You'll receive a tracking number within the hour. " +
                        "Is there anything else I can help you with?";
                default -> "Thank you for your patience. Please don't hesitate to reach out if you " +
                        "need any further assistance. Have a great day!";
            };

            return Message.assistant(response);
        };
    }
}
