package dev.dokimos.core.evaluators.agents;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.evaluators.EvaluationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolArgumentHallucinationEvaluatorTest {

    @Test
    void shouldReturnFullScoreWhenAllArgsGrounded() {
        JudgeLM mockJudge = prompt -> """
                [
                    {"toolName": "search_flights", "grounded": true, "reason": "Origin and destination from user request"},
                    {"toolName": "book_hotel", "grounded": true, "reason": "City mentioned by user"}
                ]
                """;

        var evaluator =
                ToolArgumentHallucinationEvaluator.builder().judge(mockJudge).build();

        var testCase = EvalTestCase.builder()
                .input("Find flights from NYC to LAX and book a hotel in Los Angeles")
                .actualOutput(
                        "toolCalls",
                        List.of(
                                ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "LAX")),
                                ToolCall.of("book_hotel", Map.of("city", "Los Angeles"))))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldReturnPartialScoreWhenSomeArgsHallucinated() {
        JudgeLM mockJudge = prompt -> """
                [
                    {"toolName": "search_flights", "grounded": true, "reason": "User specified these"},
                    {"toolName": "book_hotel", "grounded": false, "reason": "User didn't mention Paris"}
                ]
                """;

        var evaluator =
                ToolArgumentHallucinationEvaluator.builder().judge(mockJudge).build();

        var testCase = EvalTestCase.builder()
                .input("Find flights from NYC to LAX")
                .actualOutput(
                        "toolCalls",
                        List.of(
                                ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "LAX")),
                                ToolCall.of("book_hotel", Map.of("city", "Paris"))))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.5);
    }

    @Test
    void shouldReturnFullScoreForEmptyToolCalls() {
        JudgeLM mockJudge = prompt -> "[]";

        var evaluator =
                ToolArgumentHallucinationEvaluator.builder().judge(mockJudge).build();

        var testCase = EvalTestCase.builder()
                .input("Hello")
                .actualOutput("toolCalls", List.of())
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldHandleMalformedJudgeResponse() {
        JudgeLM mockJudge = prompt -> "This is not JSON";

        var evaluator =
                ToolArgumentHallucinationEvaluator.builder().judge(mockJudge).build();

        var testCase = EvalTestCase.builder()
                .input("Find flights")
                .actualOutput("toolCalls", List.of(ToolCall.of("search", Map.of("query", "flights"))))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reason()).contains("Failed to parse");
    }

    @Test
    void shouldThrowWhenToolCallsMissing() {
        JudgeLM mockJudge = prompt -> "[]";

        var evaluator =
                ToolArgumentHallucinationEvaluator.builder().judge(mockJudge).build();

        var testCase = EvalTestCase.builder()
                .input("Hello")
                .actualOutput("output", "some response")
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("toolCalls");
    }

    @Test
    void shouldThrowWhenJudgeNotSet() {
        assertThatThrownBy(() -> ToolArgumentHallucinationEvaluator.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JudgeLM");
    }

    @Test
    void shouldHandleMarkdownWrappedResponse() {
        JudgeLM mockJudge = prompt -> """
                ```json
                [{"toolName": "search", "grounded": true, "reason": "ok"}]
                ```
                """;

        var evaluator =
                ToolArgumentHallucinationEvaluator.builder().judge(mockJudge).build();

        var testCase = EvalTestCase.builder()
                .input("Search for something")
                .actualOutput("toolCalls", List.of(ToolCall.of("search", Map.of("query", "something"))))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldHandleNestedArguments() {
        JudgeLM mockJudge = prompt -> """
                [{"toolName": "search", "grounded": true, "reason": "All args from input"}]
                """;

        var evaluator =
                ToolArgumentHallucinationEvaluator.builder().judge(mockJudge).build();

        var testCase = EvalTestCase.builder()
                .input("Search flights under $500 in economy")
                .actualOutput(
                        "toolCalls",
                        List.of(ToolCall.of(
                                "search",
                                Map.of("filter", Map.of("maxPrice", 500, "class", "economy"), "sort", "price_asc"))))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldCapScoreAtOneWhenJudgeReturnsExtraItems() {
        // Judge returns 3 verdicts for 1 tool call — all grounded
        JudgeLM mockJudge = prompt -> """
                [
                    {"toolName": "search", "grounded": true, "reason": "ok"},
                    {"toolName": "extra1", "grounded": true, "reason": "ok"},
                    {"toolName": "extra2", "grounded": true, "reason": "ok"}
                ]
                """;

        var evaluator =
                ToolArgumentHallucinationEvaluator.builder().judge(mockJudge).build();

        var testCase = EvalTestCase.builder()
                .input("Search flights")
                .actualOutput("toolCalls", List.of(ToolCall.of("search", Map.of("q", "flights"))))
                .build();

        var result = evaluator.evaluate(testCase);

        // Score should be capped at 1.0, not 3.0
        assertThat(result.score()).isLessThanOrEqualTo(1.0);
    }

    @Test
    void shouldIncludeThresholdInParseFailureResult() {
        JudgeLM mockJudge = prompt -> "not json at all";

        var evaluator = ToolArgumentHallucinationEvaluator.builder()
                .judge(mockJudge)
                .threshold(0.8)
                .build();

        var testCase = EvalTestCase.builder()
                .input("Search flights")
                .actualOutput("toolCalls", List.of(ToolCall.of("search", Map.of("q", "flights"))))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.threshold()).isEqualTo(0.8);
    }

    @Test
    void shouldAcceptToolCallsAsListOfMaps() {
        JudgeLM mockJudge = prompt -> """
                [{"toolName": "search", "grounded": true, "reason": "ok"}]
                """;

        var evaluator =
                ToolArgumentHallucinationEvaluator.builder().judge(mockJudge).build();

        // Tool calls as maps (simulating JSON deserialization)
        var toolCallMap = Map.<String, Object>of("name", "search", "arguments", Map.of("q", "flights"));

        var testCase = EvalTestCase.builder()
                .input("Search flights")
                .actualOutput("toolCalls", List.of(toolCallMap))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldRespectCustomThreshold() {
        JudgeLM mockJudge = prompt -> """
                [{"toolName": "search", "grounded": true, "reason": "ok"}]
                """;

        var evaluator = ToolArgumentHallucinationEvaluator.builder()
                .judge(mockJudge)
                .threshold(0.5)
                .build();

        var testCase = EvalTestCase.builder()
                .input("Search")
                .actualOutput("toolCalls", List.of(ToolCall.of("search", Map.of("q", "test"))))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldGroundArgumentsInPrecedingToolResults() {
        // Multi-step agent: search returns product IDs, then fetch uses one of those IDs.
        // The judge should see tool results as valid grounding context.
        JudgeLM mockJudge = prompt -> {
            assertThat(prompt).contains("Result:");
            assertThat(prompt).contains("PRD-4821");
            assertThat(prompt).contains("preceding tool calls");
            return """
                    [
                        {"toolName": "search_products", "grounded": true, "reason": "Query from user input"},
                        {"toolName": "get_product_details", "grounded": true, "reason": "Product ID from search result"}
                    ]
                    """;
        };

        var evaluator =
                ToolArgumentHallucinationEvaluator.builder().judge(mockJudge).build();

        var testCase = EvalTestCase.builder()
                .input("Find me a lightweight running shoe")
                .actualOutput(
                        "toolCalls",
                        List.of(
                                ToolCall.builder()
                                        .name("search_products")
                                        .argument("query", "lightweight running shoe")
                                        .result("[{\"id\": \"PRD-4821\", \"name\": \"UltraLight Runner\"}]")
                                        .build(),
                                ToolCall.builder()
                                        .name("get_product_details")
                                        .argument("product_id", "PRD-4821")
                                        .build()))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldOmitResultLineWhenResultIsNull() {
        // When tool calls have no results (created via ToolCall.of), the prompt
        // should not include Result: lines, preserving the original behavior.
        JudgeLM mockJudge = prompt -> {
            assertThat(prompt).doesNotContain("Result:");
            return """
                    [{"toolName": "search", "grounded": true, "reason": "ok"}]
                    """;
        };

        var evaluator =
                ToolArgumentHallucinationEvaluator.builder().judge(mockJudge).build();

        var testCase = EvalTestCase.builder()
                .input("Search flights")
                .actualOutput("toolCalls", List.of(ToolCall.of("search", Map.of("q", "flights"))))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void argumentHallucinationParsesProsePrefixedJudgeReply() {
        JudgeLM mockJudge = prompt -> "Here are the verdicts:\n"
                + "[{\"toolName\": \"search\", \"grounded\": true, \"reason\": \"From user input\"}]";

        var evaluator =
                ToolArgumentHallucinationEvaluator.builder().judge(mockJudge).build();

        var testCase = EvalTestCase.builder()
                .input("Search for shoes")
                .actualOutput("toolCalls", List.of(ToolCall.of("search", Map.of("q", "shoes"))))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }
}
