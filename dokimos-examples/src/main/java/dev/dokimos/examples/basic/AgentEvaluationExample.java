package dev.dokimos.examples.basic;

import dev.dokimos.core.*;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.*;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates how to evaluate AI agent behavior using Dokimos agent evaluators.
 * <p>
 * This example shows:
 * <ul>
 *   <li>Defining tool definitions and expected tool calls</li>
 *   <li>Building agent traces with tool calls and reasoning steps</li>
 *   <li>Using black-box evaluators (task completion)</li>
 *   <li>Using glass-box evaluators (tool validity, correctness, hallucination)</li>
 *   <li>Using tool reliability evaluators (naming, descriptions)</li>
 * </ul>
 */
public class AgentEvaluationExample {

    // Define the tools available to the agent
    private static final List<ToolDefinition> AVAILABLE_TOOLS = List.of(
            ToolDefinition.of(
                    "search_flights",
                    "Search for available flights between airports",
                    Map.of(
                            "type", "object",
                            "properties",
                                    Map.of(
                                            "origin",
                                                    Map.of("type", "string", "description", "Origin airport IATA code"),
                                            "destination",
                                                    Map.of(
                                                            "type",
                                                            "string",
                                                            "description",
                                                            "Destination airport IATA code"),
                                            "date",
                                                    Map.of(
                                                            "type",
                                                            "string",
                                                            "description",
                                                            "Travel date in YYYY-MM-DD format")),
                            "required", List.of("origin", "destination"))),
            ToolDefinition.of(
                    "book_hotel",
                    "Book a hotel room in a specific city",
                    Map.of(
                            "type", "object",
                            "properties",
                                    Map.of(
                                            "city", Map.of("type", "string", "description", "City name"),
                                            "checkIn", Map.of("type", "string", "description", "Check-in date"),
                                            "nights", Map.of("type", "integer", "description", "Number of nights")),
                            "required", List.of("city"))),
            ToolDefinition.of(
                    "get_weather",
                    "Get weather forecast for a city",
                    Map.of(
                            "type", "object",
                            "properties", Map.of("city", Map.of("type", "string", "description", "City name")),
                            "required", List.of("city"))));

    public static void main(String[] args) {
        System.out.println("=== Dokimos Agent Evaluation Example ===\n");

        // Simulate an agent trace
        AgentTrace agentTrace = AgentTrace.builder()
                .addReasoningStep("User wants to travel to Paris. I need to search for flights and book a hotel.")
                .addToolCall(ToolCall.builder()
                        .name("search_flights")
                        .argument("origin", "NYC")
                        .argument("destination", "CDG")
                        .argument("date", "2026-03-15")
                        .result("Found 5 flights from NYC to CDG on 2026-03-15")
                        .build())
                .addReasoningStep("Found flights. Now booking a hotel.")
                .addToolCall(ToolCall.builder()
                        .name("book_hotel")
                        .argument("city", "Paris")
                        .argument("checkIn", "2026-03-15")
                        .argument("nights", 3)
                        .result("Booked Hotel Le Marais for 3 nights")
                        .build())
                .finalResponse(
                        "I've found flights from NYC to Paris and booked Hotel Le Marais for 3 nights starting March 15.")
                .metadata("totalLatencyMs", 2500)
                .build();

        System.out.println("Agent trace:");
        System.out.println("  Final response: " + agentTrace.finalResponse());
        System.out.println("  Tool calls: " + agentTrace.toolNames());
        System.out.println("  Reasoning steps: " + agentTrace.reasoningSteps().size());
        System.out.println();

        // --- 1. Tool Call Validity (no LLM needed) ---
        System.out.println("--- Tool Call Validity ---");
        var validityEvaluator = ToolCallValidityEvaluator.builder().build();
        var validityTestCase = EvalTestCase.builder()
                .actualOutput("toolCalls", agentTrace.toolCalls())
                .metadata("tools", AVAILABLE_TOOLS)
                .build();
        var validityResult = validityEvaluator.evaluate(validityTestCase);
        printResult(validityResult);

        // --- 2. Tool Correctness (no LLM needed) ---
        System.out.println("--- Tool Correctness ---");
        var correctnessEvaluator = ToolCorrectnessEvaluator.builder().build();
        var correctnessTestCase = EvalTestCase.builder()
                .actualOutput("toolCalls", agentTrace.toolCalls())
                .expectedOutput(
                        "toolCalls",
                        List.of(ToolCall.of("search_flights", Map.of()), ToolCall.of("book_hotel", Map.of())))
                .build();
        var correctnessResult = correctnessEvaluator.evaluate(correctnessTestCase);
        printResult(correctnessResult);

        // --- 3. Tool Name Reliability (no LLM needed) ---
        System.out.println("--- Tool Name Reliability ---");
        var nameEvaluator = ToolNameReliabilityEvaluator.builder().build();
        var nameTestCase =
                EvalTestCase.builder().metadata("tools", AVAILABLE_TOOLS).build();
        var nameResult = nameEvaluator.evaluate(nameTestCase);
        printResult(nameResult);

        // --- 4. Tool Description Reliability (no LLM needed) ---
        System.out.println("--- Tool Description Reliability ---");
        var descEvaluator = ToolDescriptionReliabilityEvaluator.builder().build();
        var descTestCase =
                EvalTestCase.builder().metadata("tools", AVAILABLE_TOOLS).build();
        var descResult = descEvaluator.evaluate(descTestCase);
        printResult(descResult);

        System.out.println("=== Done ===");
        System.out.println("\nNote: TaskCompletionEvaluator and ToolArgumentHallucinationEvaluator");
        System.out.println("require a JudgeLM (real LLM) and are not shown in this offline example.");
        System.out.println("Set OPENAI_API_KEY and use them in integration tests.");
    }

    private static void printResult(EvalResult result) {
        System.out.printf(
                "  %s: %s (score: %.2f, threshold: %.2f)%n",
                result.name(),
                result.success() ? "PASS" : "FAIL",
                result.score(),
                result.threshold() != null ? result.threshold() : 0.0);
        System.out.println("  Reason: " + result.reason());
        System.out.println();
    }
}
