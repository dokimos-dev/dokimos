package dev.dokimos.core.evaluators.agents;

import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.evaluators.EvaluationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ToolArgumentHallucinationEvaluatorTest {

    @Test
    void shouldReturnFullScoreWhenAllArgsGrounded() {
        JudgeLM mockJudge = prompt -> """
                [
                    {"toolName": "search_flights", "grounded": true, "reason": "Origin and destination from user request"},
                    {"toolName": "book_hotel", "grounded": true, "reason": "City mentioned by user"}
                ]
                """;

        var evaluator = ToolArgumentHallucinationEvaluator.builder()
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("Find flights from NYC to LAX and book a hotel in Los Angeles")
                .actualOutput("toolCalls", List.of(
                        ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "LAX")),
                        ToolCall.of("book_hotel", Map.of("city", "Los Angeles"))
                ))
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

        var evaluator = ToolArgumentHallucinationEvaluator.builder()
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("Find flights from NYC to LAX")
                .actualOutput("toolCalls", List.of(
                        ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "LAX")),
                        ToolCall.of("book_hotel", Map.of("city", "Paris"))
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.5);
    }

    @Test
    void shouldReturnFullScoreForEmptyToolCalls() {
        JudgeLM mockJudge = prompt -> "[]";

        var evaluator = ToolArgumentHallucinationEvaluator.builder()
                .judge(mockJudge)
                .build();

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

        var evaluator = ToolArgumentHallucinationEvaluator.builder()
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("Find flights")
                .actualOutput("toolCalls", List.of(
                        ToolCall.of("search", Map.of("query", "flights"))
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reason()).contains("Failed to parse");
    }

    @Test
    void shouldThrowWhenToolCallsMissing() {
        JudgeLM mockJudge = prompt -> "[]";

        var evaluator = ToolArgumentHallucinationEvaluator.builder()
                .judge(mockJudge)
                .build();

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

        var evaluator = ToolArgumentHallucinationEvaluator.builder()
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("Search for something")
                .actualOutput("toolCalls", List.of(
                        ToolCall.of("search", Map.of("query", "something"))
                ))
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
                .actualOutput("toolCalls", List.of(
                        ToolCall.of("search", Map.of("q", "test"))
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.success()).isTrue();
    }
}
