package dev.dokimos.core.evaluators.agents;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.evaluators.EvaluationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlanQualityEvaluatorTest {

    @Test
    void shouldRunRuleOnlyWithoutJudge() {
        var evaluator = PlanQualityEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .input("Book a flight to Paris")
                .actualOutput("reasoningSteps", List.of("Search flights", "Pick the cheapest", "Book it"))
                .build();

        var result = evaluator.evaluate(testCase);

        // Only non_empty_reasoning runs; it passes.
        assertThat(result.score()).isEqualTo(1.0);

        @SuppressWarnings("unchecked")
        var checks = (Map<String, Object>) result.metadata().get("checks");
        assertThat(checks.get("non_empty_reasoning")).isEqualTo(true);
        assertThat(checks.get("quality")).isEqualTo("skipped");
    }

    @Test
    void shouldScoreHighForCoherentPlanWithJudge() {
        var evaluator = PlanQualityEvaluator.builder()
                .judge(prompt -> "{\"quality\": true}")
                .build();

        var testCase = EvalTestCase.builder()
                .input("Book a flight to Paris")
                .actualOutput("reasoningSteps", List.of("Search flights", "Pick the cheapest", "Book it"))
                .build();

        var result = evaluator.evaluate(testCase);

        // non_empty_reasoning + quality both pass.
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldScoreLowForIncoherentPlanWithJudge() {
        var evaluator = PlanQualityEvaluator.builder()
                .judge(prompt -> "{\"quality\": false}")
                .build();

        var testCase = EvalTestCase.builder()
                .input("Book a flight to Paris")
                .actualOutput("reasoningSteps", List.of("Order a pizza", "Water the plants"))
                .build();

        var result = evaluator.evaluate(testCase);

        // non_empty_reasoning passes, quality fails: 1/2.
        assertThat(result.score()).isEqualTo(0.5);
    }

    @Test
    void shouldReturnPerfectScoreForEmptyPlanWithoutThrowing() {
        var evaluator = PlanQualityEvaluator.builder()
                .judge(prompt -> "{\"quality\": true}")
                .build();

        var testCase = EvalTestCase.builder()
                .input("Book a flight to Paris")
                .actualOutput("reasoningSteps", List.of())
                .build();

        var result = evaluator.evaluate(testCase);

        // Nothing to evaluate: short-circuits to 1.0. Judge is never called.
        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.reason()).isEqualTo("No reasoning steps to evaluate.");
    }

    @Test
    void shouldThrowWhenReasoningStepsKeyAbsent() {
        var evaluator = PlanQualityEvaluator.builder().build();

        var testCase = EvalTestCase.builder().input("Do something").build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("reasoningSteps");
    }

    @Test
    void shouldReturnZeroOnMalformedJudgeJson() {
        var evaluator =
                PlanQualityEvaluator.builder().judge(prompt -> "not json").build();

        var testCase = EvalTestCase.builder()
                .input("Book a flight to Paris")
                .actualOutput("reasoningSteps", List.of("Search flights", "Book it"))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reason()).contains("Failed to parse judge response:");
    }

    @Test
    void shouldHonorCustomReasoningStepsKey() {
        var evaluator = PlanQualityEvaluator.builder().reasoningStepsKey("plan").build();

        var testCase = EvalTestCase.builder()
                .input("Book a flight")
                .actualOutput("plan", List.of("Search", "Book"))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }
}
