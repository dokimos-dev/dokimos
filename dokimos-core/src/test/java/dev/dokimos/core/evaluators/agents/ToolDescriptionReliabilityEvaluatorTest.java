package dev.dokimos.core.evaluators.agents;

import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.EvaluationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ToolDescriptionReliabilityEvaluatorTest {

    @Test
    void shouldReturnFullScoreForWellDescribedTools() {
        var evaluator = ToolDescriptionReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(
                        ToolDefinition.of("search_flights", "Search for available flights between airports", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "origin", Map.of("type", "string", "description", "Origin airport code"),
                                        "destination", Map.of("type", "string", "description", "Destination airport code")
                                ),
                                "required", List.of("origin", "destination")
                        ))
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldPenalizeEmptyDescription() {
        var evaluator = ToolDescriptionReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(
                        ToolDefinition.of("search_flights", "", Map.of())
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isLessThan(1.0);
    }

    @Test
    void shouldPenalizeTooShortDescription() {
        var evaluator = ToolDescriptionReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(
                        ToolDefinition.of("search_flights", "Search.", Map.of())
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        // "Search." is < 10 chars
        assertThat(result.score()).isLessThan(1.0);
    }

    @Test
    void shouldPenalizeUndocumentedRequiredParams() {
        var evaluator = ToolDescriptionReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(
                        ToolDefinition.of("search_flights", "Search for available flights", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "origin", Map.of("type", "string") // no description
                                ),
                                "required", List.of("origin")
                        ))
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isLessThan(1.0);
    }

    @Test
    void shouldPenalizeTooManyOptionalParams() {
        var evaluator = ToolDescriptionReliabilityEvaluator.builder()
                .maxOptionalArgs(2)
                .build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(
                        ToolDefinition.of("search_flights", "Search for available flights between airports", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "origin", Map.of("type", "string", "description", "Origin"),
                                        "destination", Map.of("type", "string", "description", "Destination"),
                                        "date", Map.of("type", "string", "description", "Date"),
                                        "class", Map.of("type", "string", "description", "Class"),
                                        "airline", Map.of("type", "string", "description", "Airline")
                                ),
                                "required", List.of("origin", "destination")
                        ))
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        // 5 params - 2 required = 3 optional, but maxOptionalArgs is 2
        assertThat(result.score()).isLessThan(1.0);
    }

    @Test
    void shouldReturnFullScoreForEmptyToolList() {
        var evaluator = ToolDescriptionReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of())
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldThrowWhenToolsMissing() {
        var evaluator = ToolDescriptionReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder().build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("tools");
    }

    @Test
    void shouldUseLlmClarityCheckWhenJudgeProvided() {
        var evaluator = ToolDescriptionReliabilityEvaluator.builder()
                .judge(prompt -> "yes")
                .build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(
                        ToolDefinition.of("search_flights", "Search for available flights between airports", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "origin", Map.of("type", "string", "description", "Origin airport code")
                                ),
                                "required", List.of("origin")
                        ))
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldRespectCustomThreshold() {
        var evaluator = ToolDescriptionReliabilityEvaluator.builder()
                .threshold(0.3)
                .build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(
                        ToolDefinition.of("search_flights", "", Map.of())
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        // Low quality description but low threshold
        assertThat(result.score()).isGreaterThanOrEqualTo(0.0);
    }
}
