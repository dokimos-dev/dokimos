package dev.dokimos.core.evaluators.agents;

import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.EvaluationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ToolCallValidityEvaluatorTest {

    private static final ToolDefinition SEARCH_TOOL = ToolDefinition.of(
            "search_flights",
            "Search for flights",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "origin", Map.of("type", "string"),
                            "destination", Map.of("type", "string"),
                            "date", Map.of("type", "string")
                    ),
                    "required", List.of("origin", "destination")
            )
    );

    private static final ToolDefinition BOOK_TOOL = ToolDefinition.of(
            "book_hotel",
            "Book a hotel",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "city", Map.of("type", "string"),
                            "nights", Map.of("type", "integer"),
                            "rating", Map.of("type", "number", "enum", List.of(3, 4, 5))
                    ),
                    "required", List.of("city"),
                    "additionalProperties", false
            )
    );

    @Test
    void shouldReturnFullScoreWhenAllCallsValid() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(
                        ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "LAX"))
                ))
                .metadata("tools", List.of(SEARCH_TOOL))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldDetectUnknownTool() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(
                        ToolCall.of("unknown_tool", Map.of("param", "value"))
                ))
                .metadata("tools", List.of(SEARCH_TOOL))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.success()).isFalse();
    }

    @Test
    void shouldDetectMissingRequiredParameter() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(
                        ToolCall.of("search_flights", Map.of("origin", "NYC"))
                ))
                .metadata("tools", List.of(SEARCH_TOOL))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void shouldDetectTypeViolation() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(
                        ToolCall.of("book_hotel", Map.of("city", "Paris", "nights", "three"))
                ))
                .metadata("tools", List.of(BOOK_TOOL))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void shouldDetectEnumViolation() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(
                        ToolCall.of("book_hotel", Map.of("city", "Paris", "rating", 2))
                ))
                .metadata("tools", List.of(BOOK_TOOL))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void shouldDetectUnexpectedParameterInStrictMode() {
        var evaluator = ToolCallValidityEvaluator.builder()
                .strictMode(true)
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(
                        ToolCall.of("search_flights", Map.of(
                                "origin", "NYC", "destination", "LAX", "extraParam", "value"))
                ))
                .metadata("tools", List.of(SEARCH_TOOL))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void shouldDetectUnexpectedParameterWithAdditionalPropertiesFalse() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(
                        ToolCall.of("book_hotel", Map.of("city", "Paris", "extraParam", "value"))
                ))
                .metadata("tools", List.of(BOOK_TOOL))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void shouldReturnFullScoreForEmptyToolCalls() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of())
                .metadata("tools", List.of(SEARCH_TOOL))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldReturnPartialScoreForMixedValidity() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(
                        ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "LAX")),
                        ToolCall.of("unknown_tool", Map.of("param", "value"))
                ))
                .metadata("tools", List.of(SEARCH_TOOL))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.5);
    }

    @Test
    void shouldThrowWhenToolCallsMissing() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("output", "some output")
                .metadata("tools", List.of(SEARCH_TOOL))
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("toolCalls");
    }

    @Test
    void shouldThrowWhenToolDefinitionsMissing() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(
                        ToolCall.of("search", Map.of())
                ))
                .metadata("other", "value")
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("tools");
    }

    @Test
    void shouldUseCustomKeys() {
        var evaluator = ToolCallValidityEvaluator.builder()
                .toolCallsKey("agentCalls")
                .toolsKey("availableTools")
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("agentCalls", List.of(
                        ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "LAX"))
                ))
                .metadata("availableTools", List.of(SEARCH_TOOL))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }
}
