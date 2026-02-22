package dev.dokimos.core.evaluators.agents;

import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.EvaluationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ToolNameReliabilityEvaluatorTest {

    @Test
    void shouldReturnFullScoreForWellNamedTools() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(
                        ToolDefinition.of("search_flights", "Search for available flights", Map.of()),
                        ToolDefinition.of("book_hotel", "Book a hotel room", Map.of())
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldPenalizePoorlyNamedTools() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(
                        ToolDefinition.of("x", "A tool", Map.of()) // too short, no verb, not descriptive
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isLessThan(1.0);
    }

    @Test
    void shouldDetectGenericNames() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(
                        ToolDefinition.of("process", "Processes things", Map.of())
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        // "process" is in the generic names list, so ambiguity check should fail
        assertThat(result.score()).isLessThan(1.0);
    }

    @Test
    void shouldAcceptCamelCaseNames() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(
                        ToolDefinition.of("searchFlights", "Search for flights", Map.of())
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldAcceptSnakeCaseNames() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(
                        ToolDefinition.of("search_flights", "Search for flights", Map.of())
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldPenalizeNonVerbPrefixedNames() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(
                        ToolDefinition.of("flights_search", "Search for flights", Map.of())
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        // "flights" is not an action verb
        assertThat(result.score()).isLessThan(1.0);
    }

    @Test
    void shouldReturnFullScoreForEmptyToolList() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of())
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldThrowWhenToolsMissing() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder().build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("tools");
    }

    @Test
    void shouldUseLlmChecksWhenJudgeProvided() {
        var evaluator = ToolNameReliabilityEvaluator.builder()
                .judge(prompt -> {
                    if (prompt.contains("ambiguous")) return "no";
                    if (prompt.contains("descriptive")) return "yes";
                    return "yes";
                })
                .build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(
                        ToolDefinition.of("search_flights", "Search for flights", Map.of())
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldPenalizeTooLongNames() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        // Name over 64 characters
        String longName = "get_" + "a".repeat(65);

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(
                        ToolDefinition.of(longName, "A tool with a very long name", Map.of())
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isLessThan(1.0);
    }
}
