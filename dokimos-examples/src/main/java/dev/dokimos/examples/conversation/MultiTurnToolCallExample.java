package dev.dokimos.examples.conversation;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.conversation.ConversationTrajectory;
import dev.dokimos.core.evaluators.agents.ArgMatchMode;
import dev.dokimos.core.evaluators.agents.ArgumentMatcher;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import dev.dokimos.core.evaluators.agents.ToolCorrectnessEvaluator;
import dev.dokimos.core.evaluators.agents.ToolEfficiencyEvaluator;
import dev.dokimos.core.evaluators.agents.ToolTrajectoryEvaluator;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates the primary path for evaluating tool use across a multi-turn conversation: an
 * assistant turn carries the {@link ToolCall}s it made, and each turn is scored on its own with the
 * deterministic agent tool evaluators (no LLM, no API key).
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Building a {@link ConversationTrajectory} whose assistant turns carry a typed
 *       {@code List<ToolCall>} via {@code assistantMessage(content, toolCalls)}.
 *   <li>Reading those calls back per turn with {@link ConversationTrajectory#toolCallsByTurn()} and
 *       scoring each turn against its own expected calls (validity, correctness, trajectory order,
 *       efficiency).
 *   <li>The whole-conversation shortcut {@link ConversationTrajectory#toTestCase(List)} for the
 *       deterministic evaluators, which flattens every turn's calls and uses the last user message
 *       as the input.
 * </ul>
 *
 * <p>The {@code toTestCase(tools, tasks)} judge path (for {@code TaskCompletionEvaluator} and
 * {@code ToolArgumentHallucinationEvaluator}) needs a real {@code JudgeLM}, so it is described in the
 * docs rather than run here. In a real application, the assistant turns come from your agent: convert
 * its per-turn tool calls into {@link ToolCall}s and attach them with
 * {@code assistantMessage(content, toolCalls)}.
 *
 * <p>Run with: {@code mvn exec:java -pl dokimos-examples -Dexec.mainClass="dev.dokimos.examples.conversation.MultiTurnToolCallExample"}
 */
public class MultiTurnToolCallExample {

    // The tools the assistant could call across the conversation.
    private static final List<ToolDefinition> TOOLS = List.of(
            ToolDefinition.of(
                    "get_weather",
                    "Get the current weather for a city",
                    Map.of(
                            "type", "object",
                            "properties", Map.of("city", Map.of("type", "string", "description", "City name")),
                            "required", List.of("city"))),
            ToolDefinition.of(
                    "search_flights",
                    "Search for available flights between two cities",
                    Map.of(
                            "type", "object",
                            "properties",
                                    Map.of(
                                            "origin", Map.of("type", "string", "description", "Origin city"),
                                            "destination", Map.of("type", "string", "description", "Destination city")),
                            "required", List.of("origin", "destination"))),
            ToolDefinition.of(
                    "book_hotel",
                    "Book a hotel room in a city",
                    Map.of(
                            "type", "object",
                            "properties", Map.of("city", Map.of("type", "string", "description", "City name")),
                            "required", List.of("city"))));

    public static void main(String[] args) {
        System.out.println("=== Dokimos Multi-Turn Tool-Call Example ===\n");

        // 1. Build a conversation whose assistant turns carry the tool calls they made.
        //    The third turn answers from context, so it carries no tool calls.
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .scenario("Plan a trip to Paris")
                .userMessage("What's the weather in Paris?")
                .assistantMessage(
                        "It's 18C and sunny in Paris.",
                        List.of(ToolCall.builder()
                                .name("get_weather")
                                .argument("city", "Paris")
                                .result("18C, sunny")
                                .build()))
                .userMessage("Great. Find me a flight there from New York and book a hotel.")
                .assistantMessage(
                        "I found a flight and booked the Hotel Le Marais.",
                        List.of(
                                ToolCall.builder()
                                        .name("search_flights")
                                        .argument("origin", "New York")
                                        .argument("destination", "Paris")
                                        .result("3 flights found")
                                        .build(),
                                ToolCall.builder()
                                        .name("book_hotel")
                                        .argument("city", "Paris")
                                        .result("Booked Hotel Le Marais")
                                        .build()))
                .userMessage("Thanks!")
                .assistantMessage("You're all set. Enjoy your trip!")
                .build();

        System.out.println("=== Conversation Transcript ===");
        System.out.println(trajectory.toText());
        System.out.println();

        // 2. Score each assistant turn on its own. toolCallsByTurn() returns one list per assistant
        //    message (in order), which we pair with the calls we expected that turn to make.
        List<List<ToolCall>> actualByTurn = trajectory.toolCallsByTurn();
        List<List<ToolCall>> expectedByTurn = List.of(
                List.of(ToolCall.of("get_weather", Map.of())),
                List.of(ToolCall.of("search_flights", Map.of()), ToolCall.of("book_hotel", Map.of())),
                List.of()); // final turn calls no tools

        var validity = ToolCallValidityEvaluator.builder().build();
        var correctness = ToolCorrectnessEvaluator.builder().build();
        // The expected lists name the tools per turn but not their arguments, so compare on names and
        // order only. Drop the IGNORE matcher to also assert the arguments of each call.
        var trajectoryOrder = ToolTrajectoryEvaluator.builder()
                .matchMode(ToolTrajectoryEvaluator.MatchMode.IN_ORDER)
                .argumentMatcher(ArgumentMatcher.of(ArgMatchMode.IGNORE))
                .build();
        var efficiency = ToolEfficiencyEvaluator.builder().build();

        System.out.println("=== Per-Turn Tool Evaluation ===");
        for (int turn = 0; turn < actualByTurn.size(); turn++) {
            List<ToolCall> calls = actualByTurn.get(turn);
            System.out.printf("Turn %d (%d tool call%s):%n", turn + 1, calls.size(), calls.size() == 1 ? "" : "s");

            // The deterministic evaluators read "toolCalls" from actual/expected outputs, the tools
            // from metadata, and never call an LLM.
            EvalTestCase turnCase = EvalTestCase.builder()
                    .actualOutput("toolCalls", calls)
                    .expectedOutput("toolCalls", expectedByTurn.get(turn))
                    .metadata("tools", TOOLS)
                    .build();

            printResult(validity.evaluate(turnCase));
            printResult(correctness.evaluate(turnCase));
            printResult(trajectoryOrder.evaluate(turnCase));
            printResult(efficiency.evaluate(turnCase));
            System.out.println();
        }

        // 3. Whole-conversation shortcut: toTestCase(tools) flattens every turn's calls into one
        //    "toolCalls" list and uses the last user message as the input. Use it to assert the full
        //    set of tools the assistant reached for across the conversation.
        System.out.println("=== Whole-Conversation Tool Validity ===");
        EvalTestCase wholeCase = trajectory.toTestCase(TOOLS);
        printResult(validity.evaluate(wholeCase));
        System.out.printf(
                "Flattened tool calls across all turns: %s%n",
                trajectory.toolCalls().stream().map(ToolCall::name).toList());
        System.out.println();

        System.out.println("=== Done ===");
        System.out.println("TaskCompletionEvaluator and ToolArgumentHallucinationEvaluator need a JudgeLM (real LLM).");
        System.out.println("Feed them trajectory.toTestCase(tools, tasks), whose input is the full transcript. See");
        System.out.println("the Agent Evaluation and Multi-Turn Conversations docs.");
    }

    private static void printResult(EvalResult result) {
        System.out.printf(
                "  %-28s %s (score: %.2f)%n", result.name(), result.success() ? "PASS" : "FAIL", result.score());
    }
}
