package dev.dokimos.core.evaluators.agents;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.EvaluationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolCallValidityEvaluatorTest {

    private static final ToolDefinition SEARCH_TOOL = ToolDefinition.of(
            "search_flights",
            "Search for flights",
            Map.of(
                    "type", "object",
                    "properties",
                            Map.of(
                                    "origin", Map.of("type", "string"),
                                    "destination", Map.of("type", "string"),
                                    "date", Map.of("type", "string")),
                    "required", List.of("origin", "destination")));

    private static final ToolDefinition BOOK_TOOL = ToolDefinition.of(
            "book_hotel",
            "Book a hotel",
            Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of(
                            "city", Map.of("type", "string"),
                            "nights", Map.of("type", "integer"),
                            "rating", Map.of("type", "number", "enum", List.of(3, 4, 5))),
                    "required",
                    List.of("city"),
                    "additionalProperties",
                    false));

    private static final ToolDefinition INTEGER_TOOL = ToolDefinition.of(
            "set_count",
            "Set a count",
            Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of("count", Map.of("type", "integer")),
                    "required",
                    List.of("count")));

    private static final ToolDefinition NUMERIC_ENUM_TOOL = ToolDefinition.of(
            "set_rating",
            "Set a rating",
            Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of("rating", Map.of("type", "integer", "enum", List.of(1, 2, 3))),
                    "required",
                    List.of("rating")));

    @Test
    void shouldReturnFullScoreWhenAllCallsValid() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput(
                        "toolCalls",
                        List.of(ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "LAX"))))
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
                .actualOutput("toolCalls", List.of(ToolCall.of("unknown_tool", Map.of("param", "value"))))
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
                .actualOutput("toolCalls", List.of(ToolCall.of("search_flights", Map.of("origin", "NYC"))))
                .metadata("tools", List.of(SEARCH_TOOL))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void shouldDetectTypeViolation() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput(
                        "toolCalls", List.of(ToolCall.of("book_hotel", Map.of("city", "Paris", "nights", "three"))))
                .metadata("tools", List.of(BOOK_TOOL))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void shouldDetectEnumViolation() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(ToolCall.of("book_hotel", Map.of("city", "Paris", "rating", 2))))
                .metadata("tools", List.of(BOOK_TOOL))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void shouldDetectUnexpectedParameterInStrictMode() {
        var evaluator = ToolCallValidityEvaluator.builder().strictMode(true).build();

        var testCase = EvalTestCase.builder()
                .actualOutput(
                        "toolCalls",
                        List.of(ToolCall.of(
                                "search_flights",
                                Map.of("origin", "NYC", "destination", "LAX", "extraParam", "value"))))
                .metadata("tools", List.of(SEARCH_TOOL))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void shouldDetectUnexpectedParameterWithAdditionalPropertiesFalse() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput(
                        "toolCalls", List.of(ToolCall.of("book_hotel", Map.of("city", "Paris", "extraParam", "value"))))
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
                .actualOutput(
                        "toolCalls",
                        List.of(
                                ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "LAX")),
                                ToolCall.of("unknown_tool", Map.of("param", "value"))))
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
                .actualOutput("toolCalls", List.of(ToolCall.of("search", Map.of())))
                .metadata("other", "value")
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("tools");
    }

    @Test
    void shouldDetectNullArgumentValueViaMapDeserialization() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        // Simulates how tool calls arrive from JSON deserialization with null values
        var args = new HashMap<String, Object>();
        args.put("origin", null);
        args.put("destination", "LAX");
        var callMap = new HashMap<String, Object>();
        callMap.put("name", "search_flights");
        callMap.put("arguments", args);

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(callMap))
                .metadata("tools", List.of(SEARCH_TOOL))
                .build();

        var result = evaluator.evaluate(testCase);

        // origin is null but typed as "string" — should be flagged
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void shouldValidateNestedObjectArguments() {
        var toolWithObject = ToolDefinition.of(
                "search_flights",
                "Search flights with filters",
                Map.of(
                        "type", "object",
                        "properties",
                                Map.of(
                                        "origin", Map.of("type", "string"),
                                        "filter", Map.of("type", "object")),
                        "required", List.of("origin")));

        var evaluator = ToolCallValidityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput(
                        "toolCalls",
                        List.of(ToolCall.of(
                                "search_flights",
                                Map.of("origin", "NYC", "filter", Map.of("maxPrice", 500, "class", "economy")))))
                .metadata("tools", List.of(toolWithObject))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldValidateArrayArguments() {
        var toolWithArray = ToolDefinition.of(
                "book_flights",
                "Book multiple flights",
                Map.of(
                        "type", "object",
                        "properties", Map.of("flight_ids", Map.of("type", "array")),
                        "required", List.of("flight_ids")));

        var evaluator = ToolCallValidityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput(
                        "toolCalls",
                        List.of(ToolCall.of("book_flights", Map.of("flight_ids", List.of("FL001", "FL002")))))
                .metadata("tools", List.of(toolWithArray))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldAcceptToolCallsAsListOfMaps() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        // Simulates how tool calls might arrive from JSON deserialization
        var toolCallMap = Map.<String, Object>of(
                "name", "search_flights", "arguments", Map.of("origin", "NYC", "destination", "LAX"));

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(toolCallMap))
                .metadata("tools", List.of(SEARCH_TOOL))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void integerParamAcceptsWholeNumberDouble() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(ToolCall.of("set_count", Map.of("count", 42.0))))
                .metadata("tools", List.of(INTEGER_TOOL))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void integerParamRejectsFractionalNumber() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(ToolCall.of("set_count", Map.of("count", 1.5))))
                .metadata("tools", List.of(INTEGER_TOOL))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void numericEnumAcceptsEquivalentDouble() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(ToolCall.of("set_rating", Map.of("rating", 1.0))))
                .metadata("tools", List.of(NUMERIC_ENUM_TOOL))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void numericEnumRejectsOutOfSetWholeValue() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(ToolCall.of("set_rating", Map.of("rating", 5.0))))
                .metadata("tools", List.of(NUMERIC_ENUM_TOOL))
                .build();

        assertThat(evaluator.evaluate(testCase).score()).isEqualTo(0.0);
    }

    @Test
    void nonFiniteNumericArgScoresInvalidWithoutThrowing() {
        var evaluator = ToolCallValidityEvaluator.builder().build();

        for (Object value : List.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            var testCase = EvalTestCase.builder()
                    .actualOutput("toolCalls", List.of(ToolCall.of("set_rating", Map.of("rating", value))))
                    .metadata("tools", List.of(NUMERIC_ENUM_TOOL))
                    .build();

            // Must not throw (a non-finite value previously made the enum check throw NumberFormatException).
            assertThat(evaluator.evaluate(testCase).score()).isEqualTo(0.0);
        }
    }

    @Test
    void shouldUseCustomKeys() {
        var evaluator = ToolCallValidityEvaluator.builder()
                .toolCallsKey("agentCalls")
                .toolsKey("availableTools")
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput(
                        "agentCalls",
                        List.of(ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "LAX"))))
                .metadata("availableTools", List.of(SEARCH_TOOL))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }
}
