package dev.dokimos.core.evaluators.agents;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.EvaluationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolDescriptionReliabilityEvaluatorTest {

    private static final Map<String, Object> GOOD_SCHEMA = Map.of(
            "type", "object",
            "properties",
                    Map.of(
                            "origin", Map.of("type", "string", "description", "Origin airport code"),
                            "destination", Map.of("type", "string", "description", "Destination airport code")),
            "required", List.of("origin", "destination"));

    // --- input_arguments_clarity ---

    @Test
    void shouldPassWhenAllParamsHaveDescription() {
        var evaluator = ToolDescriptionReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(ToolDefinition.of("search_flights", "Search for flights", GOOD_SCHEMA)))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldFailWhenParamMissingDescription() {
        var evaluator = ToolDescriptionReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata(
                        "tools",
                        List.of(ToolDefinition.of(
                                "search_flights",
                                "Search for flights",
                                Map.of(
                                        "type", "object",
                                        "properties",
                                                Map.of(
                                                        "origin", Map.of("type", "string") // no description
                                                        ),
                                        "required", List.of("origin")))))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isLessThan(1.0);
    }

    // --- input_arguments_types ---

    @Test
    void shouldFailWhenParamMissingType() {
        var evaluator = ToolDescriptionReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata(
                        "tools",
                        List.of(ToolDefinition.of(
                                "search_flights",
                                "Search for flights",
                                Map.of(
                                        "type", "object",
                                        "properties",
                                                Map.of(
                                                        "origin",
                                                        Map.of("description", "Origin airport code") // no type
                                                        ),
                                        "required", List.of("origin")))))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isLessThan(1.0);
    }

    // --- max_num_input_arguments ---

    @Test
    void shouldFailWhenTooManyInputArgs() {
        var evaluator =
                ToolDescriptionReliabilityEvaluator.builder().maxInputArgs(2).build();

        var testCase = EvalTestCase.builder()
                .metadata(
                        "tools",
                        List.of(ToolDefinition.of(
                                "search_flights",
                                "Search for flights",
                                Map.of(
                                        "type", "object",
                                        "properties",
                                                Map.of(
                                                        "origin", Map.of("type", "string", "description", "Origin"),
                                                        "destination",
                                                                Map.of("type", "string", "description", "Destination"),
                                                        "date", Map.of("type", "string", "description", "Date")),
                                        "required", List.of("origin", "destination", "date")))))
                .build();

        var result = evaluator.evaluate(testCase);

        // 3 params > maxInputArgs=2
        assertThat(result.score()).isLessThan(1.0);
    }

    @Test
    void shouldPassWhenInputArgsWithinDefault() {
        var evaluator = ToolDescriptionReliabilityEvaluator.builder().build();

        // 2 params, well within default of 5
        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(ToolDefinition.of("search_flights", "Search for flights", GOOD_SCHEMA)))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    // --- max_optional_input_arguments ---

    @Test
    void shouldFailWhenTooManyOptionalArgs() {
        var evaluator =
                ToolDescriptionReliabilityEvaluator.builder().maxOptionalArgs(1).build();

        var testCase = EvalTestCase.builder()
                .metadata(
                        "tools",
                        List.of(ToolDefinition.of(
                                "search_flights",
                                "Search for flights",
                                Map.of(
                                        "type", "object",
                                        "properties",
                                                Map.of(
                                                        "origin", Map.of("type", "string", "description", "Origin"),
                                                        "destination",
                                                                Map.of("type", "string", "description", "Destination"),
                                                        "date", Map.of("type", "string", "description", "Date"),
                                                        "class", Map.of("type", "string", "description", "Class")),
                                        "required", List.of("origin", "destination")))))
                .build();

        var result = evaluator.evaluate(testCase);

        // 4 params - 2 required = 2 optional > maxOptionalArgs=1
        assertThat(result.score()).isLessThan(1.0);
    }

    // --- skipped LLM checks without judge ---

    @Test
    void shouldSkipLlmChecksWithoutJudge() {
        var evaluator = ToolDescriptionReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(ToolDefinition.of("search_flights", "Search for flights", GOOD_SCHEMA)))
                .build();

        var result = evaluator.evaluate(testCase);

        // Only 4 rule checks run, all pass
        assertThat(result.score()).isEqualTo(1.0);

        @SuppressWarnings("unchecked")
        var perToolResults = (List<Map<String, Object>>) result.metadata().get("perToolResults");
        @SuppressWarnings("unchecked")
        var checks = (Map<String, Object>) perToolResults.get(0).get("checks");

        assertThat(checks.get("general_structure")).isEqualTo("skipped");
        assertThat(checks.get("has_examples")).isEqualTo("skipped");
        assertThat(checks.get("has_usage_notes")).isEqualTo("skipped");
        assertThat(checks.get("clarity")).isEqualTo("skipped");
        assertThat(checks.get("redundancy")).isEqualTo("skipped");
        assertThat(checks.get("return_statement_quality")).isEqualTo("skipped");
    }

    // --- with judge ---

    @Test
    void shouldRunAllChecksWithJudge() {
        String allPass = "{\"general_structure\": 1, \"has_examples\": 1, \"has_usage_notes\": 1, "
                + "\"intent_over_implementation\": 1, \"clarity\": 1, \"redundancy\": 1, "
                + "\"input_arguments_enum\": 1, \"input_arguments_format\": 1, \"return_statement_quality\": 1}";

        var evaluator = ToolDescriptionReliabilityEvaluator.builder()
                .judge(prompt -> allPass)
                .build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(ToolDefinition.of("search_flights", "Search for flights", GOOD_SCHEMA)))
                .build();

        var result = evaluator.evaluate(testCase);

        // All 13 checks pass
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldPenalizeFailedLlmChecks() {
        // Only general_structure passes out of 9 LLM checks
        String partialPass = "{\"general_structure\": 1, \"has_examples\": 0, \"has_usage_notes\": 0, "
                + "\"intent_over_implementation\": 0, \"clarity\": 0, \"redundancy\": 0, "
                + "\"input_arguments_enum\": 0, \"input_arguments_format\": 0, \"return_statement_quality\": 0}";

        var evaluator = ToolDescriptionReliabilityEvaluator.builder()
                .judge(prompt -> partialPass)
                .build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(ToolDefinition.of("search_flights", "Search for flights", GOOD_SCHEMA)))
                .build();

        var result = evaluator.evaluate(testCase);

        // 4 rule checks pass + 1 LLM check passes = 5 out of 13
        assertThat(result.score()).isCloseTo(5.0 / 13.0, within(0.001));
    }

    // --- multiple tools ---

    @Test
    void shouldAverageScoresAcrossTools() {
        var evaluator = ToolDescriptionReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata(
                        "tools",
                        List.of(
                                ToolDefinition.of("search_flights", "Search for flights", GOOD_SCHEMA),
                                ToolDefinition.of(
                                        "book_hotel",
                                        "Book a hotel",
                                        Map.of(
                                                "type", "object",
                                                "properties",
                                                        Map.of(
                                                                "city", Map.of("type", "string") // missing description
                                                                ),
                                                "required", List.of("city")))))
                .build();

        var result = evaluator.evaluate(testCase);

        // Tool 1: 4/4 = 1.0, Tool 2: 3/4 = 0.75 (input_arguments_clarity fails)
        double expected = (1.0 + 3.0 / 4.0) / 2.0;
        assertThat(result.score()).isCloseTo(expected, within(0.001));
    }

    // --- edge cases ---

    @Test
    void shouldReturnFullScoreForEmptyToolList() {
        var evaluator = ToolDescriptionReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder().metadata("tools", List.of()).build();

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
    void shouldPassToolWithNoParameters() {
        var evaluator = ToolDescriptionReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(ToolDefinition.of("get_time", "Get current time", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        // No params means all rule checks vacuously pass
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldRespectCustomThreshold() {
        var evaluator =
                ToolDescriptionReliabilityEvaluator.builder().threshold(0.3).build();

        var testCase = EvalTestCase.builder()
                .metadata(
                        "tools",
                        List.of(ToolDefinition.of(
                                "search_flights",
                                "Search for flights",
                                Map.of(
                                        "type", "object",
                                        "properties",
                                                Map.of(
                                                        "origin", Map.of("type", "string") // missing description
                                                        ),
                                        "required", List.of("origin")))))
                .build();

        var result = evaluator.evaluate(testCase);

        // 3/4 rule checks pass = 0.75, above 0.3 threshold
        assertThat(result.success()).isTrue();
    }

    // --- parseLlmJson ---

    @Test
    void shouldParseCleanJson() {
        var result = ToolDescriptionReliabilityEvaluator.parseLlmJson(
                "{\"general_structure\": 1, \"has_examples\": 0, \"has_usage_notes\": 1, "
                        + "\"intent_over_implementation\": 1, \"clarity\": 0, \"redundancy\": 1, "
                        + "\"input_arguments_enum\": 0, \"input_arguments_format\": 1, \"return_statement_quality\": 0}");

        assertThat(result)
                .containsEntry("general_structure", 1)
                .containsEntry("has_examples", 0)
                .containsEntry("has_usage_notes", 1)
                .containsEntry("intent_over_implementation", 1)
                .containsEntry("clarity", 0)
                .containsEntry("redundancy", 1)
                .containsEntry("input_arguments_enum", 0)
                .containsEntry("input_arguments_format", 1)
                .containsEntry("return_statement_quality", 0);
    }

    @Test
    void shouldParseJsonWithMarkdownFences() {
        var result = ToolDescriptionReliabilityEvaluator.parseLlmJson(
                "```json\n{\"general_structure\": 1, \"has_examples\": 1, \"has_usage_notes\": 1, "
                        + "\"intent_over_implementation\": 1, \"clarity\": 1, \"redundancy\": 1, "
                        + "\"input_arguments_enum\": 1, \"input_arguments_format\": 1, \"return_statement_quality\": 1}\n```");

        assertThat(result).hasSize(9);
        assertThat(result.values()).allMatch(v -> v == 1);
    }
}
