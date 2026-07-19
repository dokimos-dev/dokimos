package dev.dokimos.core.evaluators.agents;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.evaluators.EvaluationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlanAdherenceEvaluatorTest {

    private static final List<ToolCall> CALLS = List.of(
            ToolCall.of("search_flights", Map.of("to", "Paris")), ToolCall.of("book_flight", Map.of("id", "1")));

    @Test
    void shouldRunRuleOnlyWithoutJudge() {
        var evaluator = PlanAdherenceEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("reasoningSteps", List.of("Search flights", "Book the flight"))
                .actualOutput("toolCalls", CALLS)
                .build();

        var result = evaluator.evaluate(testCase);

        // Only plan_present runs; it passes.
        assertThat(result.score()).isEqualTo(1.0);

        @SuppressWarnings("unchecked")
        var checks = (Map<String, Object>) result.metadata().get("checks");
        assertThat(checks.get("plan_present")).isEqualTo(true);
        assertThat(checks.get("plan_followed")).isEqualTo("skipped");
    }

    @Test
    void shouldScoreHighWhenCallsFollowPlanWithJudge() {
        var evaluator = PlanAdherenceEvaluator.builder()
                .judge(prompt -> "{\"plan_followed\": true}")
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("reasoningSteps", List.of("Search flights", "Book the flight"))
                .actualOutput("toolCalls", CALLS)
                .build();

        var result = evaluator.evaluate(testCase);

        // plan_present + plan_followed both pass.
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldScoreLowWhenCallsDivergeFromPlanWithJudge() {
        var evaluator = PlanAdherenceEvaluator.builder()
                .judge(prompt -> "{\"plan_followed\": false}")
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("reasoningSteps", List.of("Search flights", "Book the flight"))
                .actualOutput("toolCalls", List.of(ToolCall.of("order_pizza", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        // plan_present passes, plan_followed fails: 1/2.
        assertThat(result.score()).isEqualTo(0.5);
    }

    @Test
    void shouldReturnPerfectScoreForNoToolCalls() {
        var evaluator = PlanAdherenceEvaluator.builder()
                .judge(prompt -> "{\"plan_followed\": true}")
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("reasoningSteps", List.of("Search flights"))
                .actualOutput("toolCalls", List.of())
                .build();

        var result = evaluator.evaluate(testCase);

        // Nothing executed: short-circuits to 1.0. Judge is never called.
        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.reason()).isEqualTo("No tool calls to evaluate.");
    }

    @Test
    void shouldFlagMissingPlanInRuleMode() {
        var evaluator = PlanAdherenceEvaluator.builder().build();

        var testCase = EvalTestCase.builder().actualOutput("toolCalls", CALLS).build();

        var result = evaluator.evaluate(testCase);

        // plan_present fails, plan_followed skipped: 0/1.
        assertThat(result.score()).isEqualTo(0.0);

        @SuppressWarnings("unchecked")
        var checks = (Map<String, Object>) result.metadata().get("checks");
        assertThat(checks.get("plan_present")).isEqualTo(false);
    }

    @Test
    void shouldThrowWhenToolCallsKeyAbsent() {
        var evaluator = PlanAdherenceEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("reasoningSteps", List.of("Search flights"))
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("toolCalls");
    }

    @Test
    void shouldReturnZeroOnMalformedJudgeJson() {
        var evaluator = PlanAdherenceEvaluator.builder().judge(prompt -> "{").build();

        var testCase = EvalTestCase.builder()
                .actualOutput("reasoningSteps", List.of("Search flights", "Book the flight"))
                .actualOutput("toolCalls", CALLS)
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reason()).contains("Failed to parse judge response:");
    }

    @Test
    void shouldHonorCustomKeys() {
        var evaluator = PlanAdherenceEvaluator.builder()
                .reasoningStepsKey("plan")
                .toolCallsKey("calls")
                .judge(prompt -> "{\"plan_followed\": true}")
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("plan", List.of("Search", "Book"))
                .actualOutput("calls", CALLS)
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }
}
