package dev.dokimos.examples.conversation;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.conversation.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This example demonstrates how to test a chatbot against multiple user personas.
 * <p>
 * Testing with different personas helps ensure your chatbot handles various
 * user types appropriately:
 * <ul>
 *   <li>Aggressive customers who are frustrated</li>
 *   <li>Confused users who need extra help</li>
 *   <li>Technical experts who ask detailed questions</li>
 *   <li>Satisfied customers who are cooperative</li>
 * </ul>
 * <p>
 * Run with: {@code mvn exec:java -pl dokimos-examples -Dexec.mainClass="dev.dokimos.examples.conversation.PersonaTestingExample"}
 */
public class PersonaTestingExample {

    public static void main(String[] args) {
        System.out.println("=== Dokimos Persona Testing Example ===\n");

        // Create a mock JudgeLM for demonstration
        JudgeLM judgeLM = createMockJudgeLM();

        // Create a mock chatbot to test
        ConversationalApplication chatbot = createMockChatbot();

        // Define personas to test against
        Map<String, SimulatedUser> personas = new LinkedHashMap<>();
        personas.put("Aggressive Customer", UserPersonas.aggressiveCustomer(judgeLM));
        personas.put("Confused User", UserPersonas.confusedUser(judgeLM));
        personas.put("Technical Expert", UserPersonas.technicalExpert(judgeLM));
        personas.put("Satisfied Customer", UserPersonas.satisfiedCustomer(judgeLM));

        // Create evaluator
        TrajectoryEvaluator evaluator = TrajectoryEvaluator.builder()
                .name("Chatbot Quality")
                .threshold(0.6)
                .judge(judgeLM)
                .criteria(List.of(
                        TrajectoryEvaluationCriteria.userSatisfaction(),
                        TrajectoryEvaluationCriteria.helpfulness(),
                        TrajectoryEvaluationCriteria.professionalTone()))
                .aggregationStrategy(AggregationStrategy.MEAN)
                .build();

        // Test against each persona
        System.out.println("Testing chatbot against different user personas...\n");
        System.out.println("=".repeat(60));

        Map<String, EvalResult> results = new LinkedHashMap<>();

        for (Map.Entry<String, SimulatedUser> entry : personas.entrySet()) {
            String personaName = entry.getKey();
            SimulatedUser user = entry.getValue();

            System.out.println("\nTesting with: " + personaName);
            System.out.println("-".repeat(40));

            // Run simulation
            ConversationTrajectory trajectory = ConversationSimulator.builder()
                    .simulatedUser(user)
                    .application(chatbot)
                    .maxTurns(3)
                    .scenario("User asking for help with a product issue")
                    .initialMessage("Hi, I need help with something.")
                    .build()
                    .simulate();

            // Print brief conversation
            System.out.println("Conversation (" + trajectory.turnCount() + " turns):");
            trajectory
                    .messages()
                    .forEach(msg -> System.out.println("  " + msg.role() + ": " + truncate(msg.content(), 60)));

            // Evaluate
            EvalTestCase testCase = EvalTestCase.builder()
                    .actualOutput("trajectory", trajectory)
                    .build();

            EvalResult result = evaluator.evaluate(testCase);
            results.put(personaName, result);

            System.out.println(
                    "Score: " + String.format("%.2f", result.score()) + (result.success() ? " (PASS)" : " (FAIL)"));
        }

        // Print summary
        System.out.println("\n" + "=".repeat(60));
        System.out.println("SUMMARY");
        System.out.println("=".repeat(60));
        System.out.printf("%-25s %10s %10s%n", "Persona", "Score", "Status");
        System.out.println("-".repeat(45));

        int passed = 0;
        double totalScore = 0;

        for (Map.Entry<String, EvalResult> entry : results.entrySet()) {
            EvalResult result = entry.getValue();
            System.out.printf(
                    "%-25s %10.2f %10s%n", entry.getKey(), result.score(), result.success() ? "PASS" : "FAIL");
            totalScore += result.score();
            if (result.success()) passed++;
        }

        System.out.println("-".repeat(45));
        System.out.printf("%-25s %10.2f%n", "Average Score:", totalScore / results.size());
        System.out.printf("%-25s %10s%n", "Pass Rate:", passed + "/" + results.size());

        // Recommendations
        System.out.println("\n" + "=".repeat(60));
        System.out.println("RECOMMENDATIONS");
        System.out.println("=".repeat(60));

        for (Map.Entry<String, EvalResult> entry : results.entrySet()) {
            if (!entry.getValue().success()) {
                System.out.println("- Improve handling of: " + entry.getKey());
            }
        }

        if (passed == results.size()) {
            System.out.println("All persona tests passed!");
        }
    }

    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Creates a mock JudgeLM for demonstration purposes.
     */
    private static JudgeLM createMockJudgeLM() {
        return prompt -> {
            // Mock simulated user responses based on persona
            if (prompt.contains("simulating a user") || prompt.contains("Generate your next message")) {
                if (prompt.contains("aggressive") || prompt.contains("frustrated")) {
                    return "This is taking too long! I want this resolved NOW!";
                } else if (prompt.contains("confused") || prompt.contains("difficulty understanding")) {
                    return "I don't understand. Can you explain that more simply?";
                } else if (prompt.contains("technical expert")) {
                    return "What's the specific error code and which API version are you using?";
                } else if (prompt.contains("satisfied") || prompt.contains("cooperative")) {
                    return "Thank you for your help! That makes sense.";
                }
                return "I need more information about this.";
            }

            // Mock evaluation responses with varied scores by persona
            if (prompt.contains("aggressive") || prompt.contains("frustrated")) {
                return """
                        {"score": 0.65, "reason": "Handled frustration reasonably but could improve"}
                        """;
            } else if (prompt.contains("confused")) {
                return """
                        {"score": 0.75, "reason": "Provided clear explanations"}
                        """;
            } else if (prompt.contains("technical")) {
                return """
                        {"score": 0.70, "reason": "Technical questions answered adequately"}
                        """;
            } else if (prompt.contains("satisfied") || prompt.contains("cooperative")) {
                return """
                        {"score": 0.90, "reason": "Excellent interaction with cooperative user"}
                        """;
            }

            return """
                    {"score": 0.75, "reason": "Adequate performance"}
                    """;
        };
    }

    /**
     * Creates a mock chatbot for demonstration purposes.
     */
    private static ConversationalApplication createMockChatbot() {
        return trajectory -> {
            Message lastUser = trajectory.lastUserMessage();
            String userMsg = lastUser != null ? lastUser.content().toLowerCase() : "";

            String response;
            if (userMsg.contains("don't understand") || userMsg.contains("explain")) {
                response = "Of course! Let me break this down step by step. "
                        + "First, you'll want to check your order status in your account. "
                        + "Would you like me to walk you through that?";
            } else if (userMsg.contains("now") || userMsg.contains("too long")) {
                response = "I completely understand your urgency and I'm working as fast as I can. "
                        + "I've escalated this to our priority queue. "
                        + "Is there anything specific I can address immediately?";
            } else if (userMsg.contains("error code") || userMsg.contains("api")) {
                response = "Good technical question! The error code indicates a timeout. "
                        + "We're using API v2.3 which has improved retry logic. "
                        + "Have you checked the connection pool settings?";
            } else if (userMsg.contains("thank")) {
                response = "You're very welcome! I'm glad I could help. "
                        + "Is there anything else you'd like assistance with today?";
            } else {
                response = "I'd be happy to help you with that. "
                        + "Could you provide me with a few more details about your issue?";
            }

            return Message.assistant(response);
        };
    }
}
