package dev.dokimos.core.integration;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for agent evaluators using real LLM calls.
 * Requires OPENAI_API_KEY environment variable.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class AgentEvaluatorIT {

    private static JudgeLM judge;

    // Realistic tool definitions for a travel agent
    private static final ToolDefinition SEARCH_FLIGHTS = ToolDefinition.of(
            "search_flights",
            "Search for available flights between airports on a given date",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "origin", Map.of("type", "string", "description", "Origin airport IATA code"),
                            "destination", Map.of("type", "string", "description", "Destination airport IATA code"),
                            "date", Map.of("type", "string", "description", "Travel date in YYYY-MM-DD format"),
                            "passengers", Map.of("type", "integer", "description", "Number of passengers")
                    ),
                    "required", List.of("origin", "destination", "date")
            )
    );

    private static final ToolDefinition BOOK_HOTEL = ToolDefinition.of(
            "book_hotel",
            "Book a hotel room in a city for a specified number of nights",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "city", Map.of("type", "string", "description", "City name for the hotel"),
                            "check_in", Map.of("type", "string", "description", "Check-in date in YYYY-MM-DD format"),
                            "nights", Map.of("type", "integer", "description", "Number of nights to stay"),
                            "guests", Map.of("type", "integer", "description", "Number of guests")
                    ),
                    "required", List.of("city", "check_in", "nights")
            )
    );

    private static final List<ToolDefinition> TOOLS = List.of(SEARCH_FLIGHTS, BOOK_HOTEL);

    // Simulated agent trace: user asks to plan a trip, agent searches flights and books hotel
    private static final AgentTrace GOOD_TRACE = AgentTrace.builder()
            .addReasoningStep("User wants to travel from NYC to Paris on March 15 for 5 nights. I'll search flights first.")
            .addToolCall(ToolCall.builder()
                    .name("search_flights")
                    .argument("origin", "JFK")
                    .argument("destination", "CDG")
                    .argument("date", "2026-03-15")
                    .argument("passengers", 1)
                    .result("{\"flights\": [{\"id\": \"FL123\", \"price\": 450}]}")
                    .build())
            .addReasoningStep("Found flights. Now booking hotel in Paris for 5 nights starting March 15.")
            .addToolCall(ToolCall.builder()
                    .name("book_hotel")
                    .argument("city", "Paris")
                    .argument("check_in", "2026-03-15")
                    .argument("nights", 5)
                    .argument("guests", 1)
                    .result("{\"confirmation\": \"HTL456\", \"hotel\": \"Hotel Lumiere\"}")
                    .build())
            .finalResponse("I've found a flight from JFK to CDG on March 15 for $450 and booked Hotel Lumiere in Paris for 5 nights. Your confirmation number is HTL456.")
            .build();

    // Agent trace with hallucinated arguments
    private static final AgentTrace HALLUCINATED_TRACE = AgentTrace.builder()
            .addToolCall(ToolCall.builder()
                    .name("search_flights")
                    .argument("origin", "JFK")
                    .argument("destination", "CDG")
                    .argument("date", "2026-03-15")
                    .argument("passengers", 3) // User said nothing about 3 passengers
                    .build())
            .addToolCall(ToolCall.builder()
                    .name("book_hotel")
                    .argument("city", "London") // User asked for Paris, not London
                    .argument("check_in", "2026-03-15")
                    .argument("nights", 5)
                    .build())
            .finalResponse("Booked everything.")
            .build();

    @BeforeAll
    static void setup() {
        OpenAIClient client = OpenAIOkHttpClient.fromEnv();
        judge = prompt -> {
            var params = ChatCompletionCreateParams.builder()
                    .addUserMessage(prompt)
                    .model(ChatModel.GPT_5_MINI)
                    .build();
            return client.chat().completions().create(params)
                    .choices().get(0).message().content().orElse("");
        };
    }

    @Test
    void shouldEvaluateCompleteAgentTraceWithAllRuleBasedEvaluators() {
        Map<String, Object> outputs = GOOD_TRACE.toOutputMap();

        // 1. Tool call validity
        var validityResult = ToolCallValidityEvaluator.builder().build()
                .evaluate(EvalTestCase.builder()
                        .actualOutput("toolCalls", outputs.get("toolCalls"))
                        .metadata("tools", TOOLS)
                        .build());
        assertThat(validityResult.score()).isEqualTo(1.0);
        assertThat(validityResult.success()).isTrue();

        // 2. Tool correctness
        var correctnessResult = ToolCorrectnessEvaluator.builder().build()
                .evaluate(EvalTestCase.builder()
                        .actualOutput("toolCalls", outputs.get("toolCalls"))
                        .expectedOutput("toolCalls", List.of(
                                ToolCall.of("search_flights", Map.of()),
                                ToolCall.of("book_hotel", Map.of())
                        ))
                        .build());
        assertThat(correctnessResult.score()).isEqualTo(1.0);

        // 3. Tool name reliability
        var nameResult = ToolNameReliabilityEvaluator.builder().build()
                .evaluate(EvalTestCase.builder()
                        .metadata("tools", TOOLS)
                        .build());
        assertThat(nameResult.score()).isGreaterThanOrEqualTo(0.8);

        // 4. Tool description reliability
        var descResult = ToolDescriptionReliabilityEvaluator.builder().build()
                .evaluate(EvalTestCase.builder()
                        .metadata("tools", TOOLS)
                        .build());
        assertThat(descResult.score()).isGreaterThanOrEqualTo(0.8);
    }

    @Test
    void shouldDetectTaskCompletionWithRealLLM() {
        var evaluator = TaskCompletionEvaluator.builder()
                .judge(judge)
                .threshold(0.5)
                .build();

        var testCase = EvalTestCase.builder()
                .input("Book me a flight from JFK to CDG on 2026-03-15 for 1 passenger, and a hotel in Paris for 5 nights starting 2026-03-15 for 1 guest.")
                .actualOutput("output", GOOD_TRACE.finalResponse())
                .metadata("tasks", List.of(
                        "Search for flights from New York to Paris",
                        "Book a hotel in Paris for 5 nights"
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score())
                .as("Agent completed both tasks, score should be high")
                .isGreaterThanOrEqualTo(0.5);
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    void shouldDetectGroundedArgumentsWithRealLLM() {
        var evaluator = ToolArgumentHallucinationEvaluator.builder()
                .judge(judge)
                .threshold(0.8)
                .build();

        var testCase = EvalTestCase.builder()
                .input("Book me a flight from JFK to CDG on 2026-03-15 for 1 passenger, and a hotel in Paris for 5 nights starting 2026-03-15 for 1 guest.")
                .actualOutput("toolCalls", GOOD_TRACE.toolCalls())
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score())
                .as("All arguments are grounded in user input, score should be high. Reason: %s, metadata: %s",
                        result.reason(), result.metadata())
                .isGreaterThanOrEqualTo(0.5);
    }

    @Test
    void shouldDetectHallucinatedArgumentsWithRealLLM() {
        var evaluator = ToolArgumentHallucinationEvaluator.builder()
                .judge(judge)
                .threshold(0.8)
                .build();

        var testCase = EvalTestCase.builder()
                .input("Book me a flight from JFK to CDG on 2026-03-15 for 1 passenger, and a hotel in Paris for 5 nights starting 2026-03-15.")
                .actualOutput("toolCalls", HALLUCINATED_TRACE.toolCalls())
                .build();

        var result = evaluator.evaluate(testCase);

        // Should detect that passengers=3 (user said 1) and city=London (user said Paris) are hallucinated
        assertThat(result.score())
                .as("Some arguments are hallucinated, score should be lower")
                .isLessThan(1.0);
    }

    @Test
    void shouldEvaluateToolNamingWithRealLLM() {
        var evaluator = ToolNameReliabilityEvaluator.builder()
                .judge(judge)
                .threshold(0.6)
                .build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", TOOLS)
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score())
                .as("Well-named tools should score high with LLM checks")
                .isGreaterThanOrEqualTo(0.6);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldRunFullExperimentWithAgentTrace() {
        // Tests AgentTrace.toOutputMap() integration with multiple evaluators
        Map<String, Object> outputs = GOOD_TRACE.toOutputMap();

        var testCase = EvalTestCase.builder()
                .input("Book me a flight from JFK to CDG on 2026-03-15 for 1 passenger, and a hotel in Paris for 5 nights starting 2026-03-15 for 1 guest.")
                .actualOutput("toolCalls", outputs.get("toolCalls"))
                .actualOutput("output", outputs.get("output"))
                .expectedOutput("toolCalls", List.of(
                        ToolCall.of("search_flights", Map.of()),
                        ToolCall.of("book_hotel", Map.of())
                ))
                .metadata("tools", TOOLS)
                .metadata("tasks", List.of("Search flights", "Book hotel"))
                .build();

        // Run all evaluators against the same test case
        var validityResult = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
        var correctnessResult = ToolCorrectnessEvaluator.builder().build().evaluate(testCase);
        var completionResult = TaskCompletionEvaluator.builder().judge(judge).build().evaluate(testCase);
        var hallucinationResult = ToolArgumentHallucinationEvaluator.builder().judge(judge).build().evaluate(testCase);

        assertThat(validityResult.score()).isEqualTo(1.0);
        assertThat(correctnessResult.score()).isEqualTo(1.0);
        assertThat(completionResult.score())
                .as("Completion reason: %s, metadata: %s", completionResult.reason(), completionResult.metadata())
                .isGreaterThanOrEqualTo(0.5);
        assertThat(hallucinationResult.score())
                .as("Hallucination reason: %s, metadata: %s", hallucinationResult.reason(), hallucinationResult.metadata())
                .isGreaterThanOrEqualTo(0.5);
    }
}
