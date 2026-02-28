package dev.dokimos.examples.basic;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.ChatModel;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.*;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.*;

import java.util.List;
import java.util.Map;

/**
 * Shows how to capture tool calls from an OpenAI agent and evaluate them.
 *
 * <p>
 * Requires {@code OPENAI_API_KEY} environment variable.
 *
 * <p>
 * Flow:
 * <ol>
 * <li>Define tools (shared between OpenAI and evaluators)</li>
 * <li>Run a tool-calling loop against OpenAI</li>
 * <li>Build an {@link AgentTrace} from the captured tool calls</li>
 * <li>Evaluate the trace with all agent evaluators</li>
 * </ol>
 */
public class OpenAIAgentEvaluationExample {

        private static final List<ToolDefinition> TOOLS = List.of(
                        ToolDefinition.of("search_flights",
                                        "Search for available flights between airports on a given date",
                                        Map.of(
                                                        "type", "object",
                                                        "properties", Map.of(
                                                                        "origin",
                                                                        Map.of("type", "string", "description",
                                                                                        "Origin airport IATA code"),
                                                                        "destination",
                                                                        Map.of("type", "string", "description",
                                                                                        "Destination airport IATA code"),
                                                                        "date",
                                                                        Map.of("type", "string", "description",
                                                                                        "Travel date in YYYY-MM-DD format")),
                                                        "required", List.of("origin", "destination", "date"))),
                        ToolDefinition.of("book_hotel",
                                        "Book a hotel room in a city for a specified number of nights",
                                        Map.of(
                                                        "type", "object",
                                                        "properties", Map.of(
                                                                        "city",
                                                                        Map.of("type", "string", "description",
                                                                                        "City name"),
                                                                        "check_in",
                                                                        Map.of("type", "string", "description",
                                                                                        "Check-in date in YYYY-MM-DD format"),
                                                                        "nights",
                                                                        Map.of("type", "integer", "description",
                                                                                        "Number of nights")),
                                                        "required", List.of("city", "check_in", "nights"))));

        public static void main(String[] args) {
                OpenAIClient client = OpenAIOkHttpClient.fromEnv();

                JudgeLM judge = prompt -> {
                        var params = ChatCompletionCreateParams.builder()
                                        .addUserMessage(prompt)
                                        .model(ChatModel.GPT_5_NANO)
                                        .build();
                        return client.chat().completions().create(params)
                                        .choices().get(0).message().content().orElse("");
                };

                // 1. Run the agent loop and capture the trace
                String userMessage = "Find me a flight from JFK to CDG on 2026-03-15 and book a hotel in Paris for 3 nights.";
                System.out.println("User: " + userMessage);
                System.out.println();

                AgentTrace trace = runAgentLoop(client, userMessage);

                System.out.println("Agent response: " + trace.finalResponse());
                System.out.println("Tool calls made: " + trace.toolNames());
                System.out.println();

                // 2. Build the test case from the trace
                Map<String, Object> outputs = trace.toOutputMap();
                var testCase = EvalTestCase.builder()
                                .input(userMessage)
                                .actualOutput("toolCalls", outputs.get("toolCalls"))
                                .actualOutput("output", outputs.get("output"))
                                .expectedOutput("toolCalls", List.of(
                                                ToolCall.of("search_flights", Map.of()),
                                                ToolCall.of("book_hotel", Map.of())))
                                .metadata("tools", TOOLS)
                                .metadata("tasks", List.of("Search for flights", "Book a hotel"))
                                .build();

                // 3. Evaluate
                System.out.println("=== Evaluation Results ===");
                printResult(ToolCallValidityEvaluator.builder().build().evaluate(testCase));
                printResult(ToolCorrectnessEvaluator.builder().build().evaluate(testCase));
                printResult(TaskCompletionEvaluator.builder().judge(judge).build().evaluate(testCase));
                printResult(ToolArgumentHallucinationEvaluator.builder().judge(judge).build().evaluate(testCase));
                printResult(ToolNameReliabilityEvaluator.builder().judge(judge).build()
                                .evaluate(EvalTestCase.builder().metadata("tools", TOOLS).build()));
                printResult(ToolDescriptionReliabilityEvaluator.builder().judge(judge).build()
                                .evaluate(EvalTestCase.builder().metadata("tools", TOOLS).build()));
        }

        /**
         * Runs a tool-calling loop: send user message with tools, process tool calls,
         * execute them, send results back, repeat until final text response.
         */
        @SuppressWarnings("unchecked")
        static AgentTrace runAgentLoop(OpenAIClient client, String userMessage) {
                var traceBuilder = AgentTrace.builder();

                var paramsBuilder = ChatCompletionCreateParams.builder()
                                .model(ChatModel.GPT_5_NANO)
                                .addUserMessage(userMessage);

                for (ToolDefinition def : TOOLS) {
                        paramsBuilder.addTool(toOpenAITool(def));
                }

                // Each iteration is one API round-trip. The model may call tools across
                // multiple turns (e.g., search first, then book based on results).
                for (int i = 0; i < 10; i++) {
                        var completion = client.chat().completions().create(paramsBuilder.build());
                        var message = completion.choices().get(0).message();
                        paramsBuilder.addMessage(message);

                        var toolCalls = message.toolCalls().orElse(List.of());
                        if (toolCalls.isEmpty()) {
                                traceBuilder.finalResponse(message.content().orElse(""));
                                break;
                        }

                        for (var toolCall : toolCalls) {
                                var funcToolCall = toolCall.asFunction();
                                var function = funcToolCall.function();
                                String name = function.name();
                                Map<String, Object> args = (Map<String, Object>) function.arguments(Map.class);
                                String result = executeToolFunction(name);

                                System.out.printf("  Tool call: %s(%s) -> %s%n", name, args, result);

                                traceBuilder.addToolCall(ToolCall.builder()
                                                .name(name)
                                                .arguments(args)
                                                .result(result)
                                                .build());

                                paramsBuilder.addMessage(ChatCompletionToolMessageParam.builder()
                                                .toolCallId(funcToolCall.id())
                                                .content(result)
                                                .build());
                        }
                }

                return traceBuilder.build();
        }

        /**
         * Converts a {@link ToolDefinition} to an OpenAI {@link ChatCompletionTool}.
         */
        static ChatCompletionTool toOpenAITool(ToolDefinition def) {
                var paramsBuilder = FunctionParameters.builder();
                for (Map.Entry<String, Object> entry : def.inputSchema().entrySet()) {
                        paramsBuilder.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
                }
                return ChatCompletionTool.ofFunction(
                                ChatCompletionFunctionTool.builder()
                                                .function(FunctionDefinition.builder()
                                                                .name(def.name())
                                                                .description(def.description())
                                                                .parameters(paramsBuilder.build())
                                                                .build())
                                                .build());
        }

        /**
         * Returns canned tool responses. In a real app, these would call your actual
         * services.
         */
        static String executeToolFunction(String toolName) {
                return switch (toolName) {
                        case "search_flights" ->
                                "{\"flights\": [{\"id\": \"AF1234\", \"airline\": \"Air France\", \"price\": 485}]}";
                        case "book_hotel" ->
                                "{\"confirmation\": \"HTL-98765\", \"hotel\": \"Hotel Le Marais\", \"total_price\": 875}";
                        default -> "{\"error\": \"Unknown tool\"}";
                };
        }

        private static void printResult(EvalResult result) {
                System.out.printf("  %s: %s (score: %.2f, threshold: %.2f) — %s%n",
                                result.name(),
                                result.success() ? "PASS" : "FAIL",
                                result.score(),
                                result.threshold() != null ? result.threshold() : 0.0,
                                result.reason());
        }
}
