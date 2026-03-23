package dev.dokimos.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.ChatModel;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.*;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Integration tests for agent evaluators using OpenAI tool calling.
 * Requires {@code OPENAI_API_KEY} environment variable.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class AgentEvaluatorIT {

    private static final String USER_MESSAGE = "I need to fly from New York JFK to Paris CDG on March 15, 2026, "
            + "and I need a hotel in Paris for 5 nights starting that day.";

    private static final ToolDefinition SEARCH_FLIGHTS = ToolDefinition.of(
            "search_flights",
            "Search for available flights between airports on a given date",
            Map.of(
                    "type", "object",
                    "properties",
                            Map.of(
                                    "origin",
                                    Map.of("type", "string", "description", "Origin airport IATA code"),
                                    "destination",
                                    Map.of("type", "string", "description", "Destination airport IATA code"),
                                    "date",
                                    Map.of("type", "string", "description", "Travel date in YYYY-MM-DD format"),
                                    "passengers",
                                    Map.of("type", "integer", "description", "Number of passengers")),
                    "required", List.of("origin", "destination", "date")));

    private static final ToolDefinition BOOK_HOTEL = ToolDefinition.of(
            "book_hotel",
            "Book a hotel room in a city for a specified number of nights",
            Map.of(
                    "type", "object",
                    "properties",
                            Map.of(
                                    "city",
                                    Map.of("type", "string", "description", "City name for the hotel"),
                                    "check_in",
                                    Map.of("type", "string", "description", "Check-in date in YYYY-MM-DD format"),
                                    "nights",
                                    Map.of("type", "integer", "description", "Number of nights to stay"),
                                    "guests",
                                    Map.of("type", "integer", "description", "Number of guests")),
                    "required", List.of("city", "check_in", "nights")));

    private static final List<ToolDefinition> TOOLS = List.of(SEARCH_FLIGHTS, BOOK_HOTEL);

    private static OpenAIClient client;
    private static JudgeLM judge;
    private static AgentTrace trace;

    @BeforeAll
    static void setup() {
        client = OpenAIOkHttpClient.fromEnv();
        judge = prompt -> {
            var params = ChatCompletionCreateParams.builder()
                    .addUserMessage(prompt)
                    .model(ChatModel.GPT_4O_MINI)
                    .build();
            return client.chat()
                    .completions()
                    .create(params)
                    .choices()
                    .get(0)
                    .message()
                    .content()
                    .orElse("");
        };

        trace = executeAgentLoop(USER_MESSAGE);
        assertThat(trace.toolCalls())
                .as(
                        "Model did not return any tool calls — the agent loop may need a stronger prompt or different model")
                .isNotEmpty();
    }

    @Test
    void shouldCaptureToolCalls() {
        assertThat(trace.toolCalls()).isNotEmpty();
        assertThat(trace.toolNames()).contains("search_flights", "book_hotel");
        assertThat(trace.finalResponse()).isNotBlank();

        for (ToolCall call : trace.toolCalls()) {
            assertThat(call.result()).isNotNull();
            assertThat(call.arguments()).isNotEmpty();
        }
    }

    @Test
    void shouldValidateToolCallsAgainstSchemas() {
        var result = ToolCallValidityEvaluator.builder().build().evaluate(buildTestCase());

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldDetectCorrectToolUsage() {
        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", trace.toolCalls())
                .expectedOutput(
                        "toolCalls",
                        List.of(ToolCall.of("search_flights", Map.of()), ToolCall.of("book_hotel", Map.of())))
                .build();

        var result = ToolCorrectnessEvaluator.builder().build().evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldDetectTaskCompletion() {
        var result = TaskCompletionEvaluator.builder().judge(judge).build().evaluate(buildTestCase());

        assertThat(result.score()).as("Reason: %s", result.reason()).isGreaterThanOrEqualTo(0.5);
    }

    @Test
    void shouldDetectGroundedArguments() {
        var result = ToolArgumentHallucinationEvaluator.builder()
                .judge(judge)
                .build()
                .evaluate(buildTestCase());

        assertThat(result.score())
                .as("Reason: %s, metadata: %s", result.reason(), result.metadata())
                .isGreaterThanOrEqualTo(0.5);
    }

    @Test
    void shouldGroundArgumentsFromPrecedingToolResults() {
        // Constructs a chained scenario: search_flights returns flight IDs,
        // then get_seat_availability uses a flight ID from that result.
        // Without tool result grounding, the judge would flag "AF1234" as hallucinated
        // because the user never mentioned a specific flight ID.
        var chainedTestCase = EvalTestCase.builder()
                .input("Find available seats on a morning flight from JFK to CDG on March 15, 2026")
                .actualOutput(
                        "toolCalls",
                        List.of(
                                ToolCall.builder()
                                        .name("search_flights")
                                        .arguments(Map.of(
                                                "origin", "JFK",
                                                "destination", "CDG",
                                                "date", "2026-03-15"))
                                        .result("""
                                                {"flights": [\
                                                {"id": "AF1234", "airline": "Air France", "departure": "08:30"},\
                                                {"id": "DL5678", "airline": "Delta", "departure": "10:00"}\
                                                ]}""")
                                        .build(),
                                ToolCall.builder()
                                        .name("get_seat_availability")
                                        .arguments(Map.of("flight_id", "AF1234"))
                                        .build()))
                .build();

        var result = ToolArgumentHallucinationEvaluator.builder()
                .judge(judge)
                .build()
                .evaluate(chainedTestCase);

        assertThat(result.score())
                .as("Reason: %s, metadata: %s", result.reason(), result.metadata())
                .isGreaterThanOrEqualTo(0.8);
    }

    @Test
    void shouldEvaluateToolNaming() {
        var result = ToolNameReliabilityEvaluator.builder()
                .judge(judge)
                .build()
                .evaluate(EvalTestCase.builder().metadata("tools", TOOLS).build());

        assertThat(result.score()).isGreaterThanOrEqualTo(0.6);
    }

    @Test
    void shouldEvaluateToolDescriptions() {
        var result = ToolDescriptionReliabilityEvaluator.builder()
                .judge(judge)
                .build()
                .evaluate(EvalTestCase.builder().metadata("tools", TOOLS).build());

        assertThat(result.score()).isGreaterThanOrEqualTo(0.5);
    }

    private EvalTestCase buildTestCase() {
        Map<String, Object> outputs = trace.toOutputMap();
        return EvalTestCase.builder()
                .input(USER_MESSAGE)
                .actualOutput("toolCalls", outputs.get("toolCalls"))
                .actualOutput("output", outputs.get("output"))
                .expectedOutput(
                        "toolCalls",
                        List.of(ToolCall.of("search_flights", Map.of()), ToolCall.of("book_hotel", Map.of())))
                .metadata("tools", TOOLS)
                .metadata(
                        "tasks",
                        List.of(
                                "Search for flights from JFK to CDG on 2026-03-15",
                                "Book a hotel in Paris for 5 nights starting 2026-03-15"))
                .build();
    }

    /**
     * Runs a tool-calling loop: send user message with tools, process tool calls,
     * execute them, send results back, repeat until final text response.
     */
    @SuppressWarnings("unchecked")
    private static AgentTrace executeAgentLoop(String userMessage) {
        var traceBuilder = AgentTrace.builder();

        var paramsBuilder = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4O_MINI)
                .addSystemMessage("You are a travel assistant. Use the provided tools to fulfill the user's request. "
                        + "Do not answer without calling the appropriate tools first.")
                .addUserMessage(userMessage);

        for (ToolDefinition def : TOOLS) {
            paramsBuilder.addTool(toOpenAITool(def));
        }

        for (int iteration = 0; iteration < 5; iteration++) {
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

    /** Converts a {@link ToolDefinition} to an OpenAI {@link ChatCompletionTool}. */
    private static ChatCompletionTool toOpenAITool(ToolDefinition def) {
        var paramsBuilder = FunctionParameters.builder();
        for (Map.Entry<String, Object> entry : def.inputSchema().entrySet()) {
            paramsBuilder.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
        }
        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name(def.name())
                        .description(def.description())
                        .parameters(paramsBuilder.build())
                        .build())
                .build());
    }

    /** Returns canned tool responses. In a real app, these would be actual API calls. */
    private static String executeToolFunction(String toolName) {
        return switch (toolName) {
            case "search_flights" -> """
                                                {"flights": [\
                                                {"id": "AF1234", "airline": "Air France", "departure": "08:30", "arrival": "22:15", "price": 485},\
                                                {"id": "DL5678", "airline": "Delta", "departure": "10:00", "arrival": "23:45", "price": 520}\
                                                ]}""";
            case "book_hotel" -> """
                                        {"confirmation": "HTL-98765", "hotel": "Hotel Le Marais", \
                                        "address": "12 Rue de Rivoli, Paris", "total_price": 875, "nights": 5}""";
            default -> "{\"error\": \"Unknown tool: " + toolName + "\"}";
        };
    }
}
