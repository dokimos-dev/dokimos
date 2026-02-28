package dev.dokimos.core.evaluators.agents;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.EvaluationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolNameReliabilityEvaluatorTest {

    // --- snakecase_format ---

    @Test
    void shouldPassSnakeCaseFormat() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata(
                        "tools", List.of(ToolDefinition.of("search_flights", "Search for available flights", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldFailCamelCaseFormat() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(ToolDefinition.of("searchFlights", "Search for flights", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        // camelCase fails snakecase_format check
        assertThat(result.score()).isLessThan(1.0);
    }

    @Test
    void shouldFailUpperCaseStart() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(ToolDefinition.of("Search_flights", "Search for flights", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isLessThan(1.0);
    }

    // --- conciseness ---

    @Test
    void shouldPassConciseName() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        // 3 segments: well within limit
        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(ToolDefinition.of("get_user_profile", "Get user profile", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldFailOverlyLongName() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        // 8 segments: exceeds the 7-segment limit
        var testCase = EvalTestCase.builder()
                .metadata(
                        "tools",
                        List.of(ToolDefinition.of(
                                "get_user_profile_data_from_main_database_cache", "Get user profile data", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isLessThan(1.0);
    }

    @Test
    void shouldPassSevenSegmentName() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        // Exactly 7 segments: should pass
        var testCase = EvalTestCase.builder()
                .metadata(
                        "tools",
                        List.of(ToolDefinition.of(
                                "get_booking_property_name_by_id_v2", "Get booking property name", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    // --- intent_over_implementation (blocklist, no judge) ---

    @Test
    void shouldFailBlocklistedImplementationSuffix() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(ToolDefinition.of("get_data_via_api", "Get data", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isLessThan(1.0);
    }

    @Test
    void shouldFailBlocklistedLlmSuffix() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(ToolDefinition.of("summarize_with_llm", "Summarize text", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isLessThan(1.0);
    }

    @Test
    void shouldPassCleanIntentName() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(ToolDefinition.of("summarize_text", "Summarize text", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    // --- skipped LLM checks without judge ---

    @Test
    void shouldSkipLlmChecksWithoutJudge() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(ToolDefinition.of("search_flights", "Search for flights", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        // Without judge: 3 rule checks (snakecase_format, conciseness, intent_over_implementation)
        // All pass, score = 1.0. clarity and name_order are skipped.
        assertThat(result.score()).isEqualTo(1.0);

        @SuppressWarnings("unchecked")
        var perToolResults = (List<Map<String, Object>>) result.metadata().get("perToolResults");
        @SuppressWarnings("unchecked")
        var checks = (Map<String, Object>) perToolResults.get(0).get("checks");

        assertThat(checks.get("clarity")).isEqualTo("skipped");
        assertThat(checks.get("name_order")).isEqualTo("skipped");
    }

    // --- with judge ---

    @Test
    void shouldRunAllChecksWithJudge() {
        var evaluator = ToolNameReliabilityEvaluator.builder()
                .judge(prompt -> "{\"clarity\": 1, \"name_order\": 1, \"intent_over_implementation\": 1}")
                .build();

        var testCase = EvalTestCase.builder()
                .metadata(
                        "tools", List.of(ToolDefinition.of("search_flights", "Search for available flights", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        // All 5 checks pass
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldPenalizeFailedLlmChecks() {
        var evaluator = ToolNameReliabilityEvaluator.builder()
                .judge(prompt -> "{\"clarity\": 0, \"name_order\": 0, \"intent_over_implementation\": 1}")
                .build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(ToolDefinition.of("search_flights", "Search for flights", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        // 5 checks total, 3 pass (snakecase_format, conciseness, intent_over_implementation), 2 fail
        assertThat(result.score()).isEqualTo(3.0 / 5.0);
    }

    @Test
    void shouldCombineBlocklistAndLlmForIntentCheck() {
        // Blocklist fails but LLM says pass — blocklist takes precedence (AND logic)
        var evaluator = ToolNameReliabilityEvaluator.builder()
                .judge(prompt -> "{\"clarity\": 1, \"name_order\": 1, \"intent_over_implementation\": 1}")
                .build();

        var testCase = EvalTestCase.builder()
                .metadata("tools", List.of(ToolDefinition.of("get_data_via_api", "Get data from the API", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        // snakecase_format: pass, conciseness: pass, intent_over_implementation: fail (blocklist),
        // clarity: pass, name_order: pass → 4/5
        assertThat(result.score()).isEqualTo(4.0 / 5.0);
    }

    // --- multiple tools ---

    @Test
    void shouldAverageScoresAcrossTools() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .metadata(
                        "tools",
                        List.of(
                                ToolDefinition.of("search_flights", "Search for flights", Map.of()), // all pass
                                ToolDefinition.of("searchFlights", "Search for flights", Map.of()) // snakecase fails
                                ))
                .build();

        var result = evaluator.evaluate(testCase);

        // Tool 1: 3/3, Tool 2: 2/3 → average = (1.0 + 2.0/3.0) / 2
        double expected = (1.0 + 2.0 / 3.0) / 2.0;
        assertThat(result.score()).isCloseTo(expected, within(0.001));
    }

    // --- edge cases ---

    @Test
    void shouldReturnFullScoreForEmptyToolList() {
        var evaluator = ToolNameReliabilityEvaluator.builder().build();

        var testCase = EvalTestCase.builder().metadata("tools", List.of()).build();

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

    // --- parseLlmJson ---

    @Test
    void shouldParseCleanJson() {
        var result = ToolNameReliabilityEvaluator.parseLlmJson(
                "{\"clarity\": 1, \"name_order\": 0, \"intent_over_implementation\": 1}");

        assertThat(result)
                .containsEntry("clarity", 1)
                .containsEntry("name_order", 0)
                .containsEntry("intent_over_implementation", 1);
    }

    @Test
    void shouldParseJsonWithMarkdownFences() {
        var result = ToolNameReliabilityEvaluator.parseLlmJson(
                "```json\n{\"clarity\": 1, \"name_order\": 1, \"intent_over_implementation\": 0}\n```");

        assertThat(result)
                .containsEntry("clarity", 1)
                .containsEntry("name_order", 1)
                .containsEntry("intent_over_implementation", 0);
    }
}
