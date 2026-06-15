package dev.dokimos.core.conversation;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.evaluators.EvaluationException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TrajectoryEvaluatorTest {

    @Test
    void shouldEvaluateSingleCriterion() {
        JudgeLM mockJudge = prompt -> """
                {"score": 0.85, "reason": "User seemed satisfied"}
                """;

        TrajectoryEvaluator evaluator = TrajectoryEvaluator.builder()
                .name("Satisfaction")
                .threshold(0.7)
                .judge(mockJudge)
                .criterion(TrajectoryEvaluationCriteria.userSatisfaction())
                .build();

        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("I have a problem")
                .assistantMessage("Let me help you")
                .userMessage("Thanks, that worked!")
                .assistantMessage("You're welcome!")
                .build();

        EvalTestCase testCase =
                EvalTestCase.builder().actualOutput("trajectory", trajectory).build();

        EvalResult result = evaluator.evaluate(testCase);

        assertThat(result.name()).isEqualTo("Satisfaction");
        assertThat(result.score()).isEqualTo(0.85);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldAggregateMultipleCriteriaWithMean() {
        JudgeLM mockJudge = prompt -> {
            if (prompt.contains("User Satisfaction")) {
                return """
                        {"score": 0.8, "reason": "Satisfied"}
                        """;
            } else {
                return """
                        {"score": 0.6, "reason": "Partially resolved"}
                        """;
            }
        };

        TrajectoryEvaluator evaluator = TrajectoryEvaluator.builder()
                .name("Overall")
                .threshold(0.5)
                .judge(mockJudge)
                .criteria(List.of(
                        TrajectoryEvaluationCriteria.userSatisfaction(),
                        TrajectoryEvaluationCriteria.problemResolution()))
                .aggregationStrategy(AggregationStrategy.MEAN)
                .build();

        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("Help")
                .assistantMessage("Done")
                .build();

        EvalTestCase testCase =
                EvalTestCase.builder().actualOutput("trajectory", trajectory).build();

        EvalResult result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.7); // (0.8 + 0.6) / 2
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldUseWeightedMeanAggregation() {
        JudgeLM mockJudge = prompt -> {
            if (prompt.contains("Criterion A")) {
                return """
                        {"score": 1.0, "reason": "Perfect"}
                        """;
            } else {
                return """
                        {"score": 0.0, "reason": "Failed"}
                        """;
            }
        };

        // Criterion A has weight 3, Criterion B has weight 1
        EvaluationCriterion criterionA = new EvaluationCriterion("Criterion A", "Test A", 3.0);
        EvaluationCriterion criterionB = new EvaluationCriterion("Criterion B", "Test B", 1.0);

        TrajectoryEvaluator evaluator = TrajectoryEvaluator.builder()
                .name("Weighted")
                .judge(mockJudge)
                .criteria(List.of(criterionA, criterionB))
                .aggregationStrategy(AggregationStrategy.WEIGHTED_MEAN)
                .build();

        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("Test")
                .assistantMessage("Response")
                .build();

        EvalTestCase testCase =
                EvalTestCase.builder().actualOutput("trajectory", trajectory).build();

        EvalResult result = evaluator.evaluate(testCase);

        // (1.0 * 3 + 0.0 * 1) / (3 + 1) = 0.75
        assertThat(result.score()).isEqualTo(0.75);
    }

    @Test
    void shouldUseMinAggregation() {
        JudgeLM mockJudge = prompt -> {
            if (prompt.contains("High")) {
                return """
                        {"score": 0.9, "reason": "High score"}
                        """;
            } else {
                return """
                        {"score": 0.3, "reason": "Low score"}
                        """;
            }
        };

        TrajectoryEvaluator evaluator = TrajectoryEvaluator.builder()
                .name("Strict")
                .judge(mockJudge)
                .criteria(List.of(
                        EvaluationCriterion.of("High", "High scoring"), EvaluationCriterion.of("Low", "Low scoring")))
                .aggregationStrategy(AggregationStrategy.MIN)
                .build();

        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("Test")
                .assistantMessage("Response")
                .build();

        EvalTestCase testCase =
                EvalTestCase.builder().actualOutput("trajectory", trajectory).build();

        EvalResult result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.3);
    }

    @Test
    void shouldIncludePerCriterionScoresInMetadata() {
        JudgeLM mockJudge = prompt -> """
                {"score": 0.75, "reason": "Test reason"}
                """;

        TrajectoryEvaluator evaluator = TrajectoryEvaluator.builder()
                .name("Test")
                .judge(mockJudge)
                .criterion(TrajectoryEvaluationCriteria.userSatisfaction())
                .includePerCriterionScores(true)
                .build();

        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("Test")
                .assistantMessage("Response")
                .build();

        EvalTestCase testCase =
                EvalTestCase.builder().actualOutput("trajectory", trajectory).build();

        EvalResult result = evaluator.evaluate(testCase);

        assertThat(result.metadata()).containsKey("criterionScores");
        @SuppressWarnings("unchecked")
        Map<String, Object> criterionScores =
                (Map<String, Object>) result.metadata().get("criterionScores");
        assertThat(criterionScores).containsKey("User Satisfaction");
    }

    @Test
    void shouldFailWhenBelowThreshold() {
        JudgeLM mockJudge = prompt -> """
                {"score": 0.3, "reason": "Poor performance"}
                """;

        TrajectoryEvaluator evaluator = TrajectoryEvaluator.builder()
                .name("Test")
                .threshold(0.7)
                .judge(mockJudge)
                .criterion(TrajectoryEvaluationCriteria.userSatisfaction())
                .build();

        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("Test")
                .assistantMessage("Response")
                .build();

        EvalTestCase testCase =
                EvalTestCase.builder().actualOutput("trajectory", trajectory).build();

        EvalResult result = evaluator.evaluate(testCase);

        assertThat(result.success()).isFalse();
        assertThat(result.score()).isEqualTo(0.3);
    }

    @Test
    void shouldHandleEmptyTrajectory() {
        JudgeLM mockJudge = prompt -> """
                {"score": 0.5, "reason": "Test"}
                """;

        TrajectoryEvaluator evaluator = TrajectoryEvaluator.builder()
                .name("Test")
                .judge(mockJudge)
                .criterion(TrajectoryEvaluationCriteria.userSatisfaction())
                .build();

        EvalTestCase testCase = EvalTestCase.builder()
                .actualOutput("trajectory", ConversationTrajectory.empty())
                .build();

        EvalResult result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reason()).contains("empty");
    }

    @Test
    void shouldThrowWhenTrajectoryNotFound() {
        JudgeLM mockJudge = prompt -> """
                {"score": 0.5, "reason": "Test"}
                """;

        TrajectoryEvaluator evaluator = TrajectoryEvaluator.builder()
                .name("Test")
                .judge(mockJudge)
                .criterion(TrajectoryEvaluationCriteria.userSatisfaction())
                .trajectoryKey("conversation")
                .build();

        EvalTestCase testCase =
                EvalTestCase.builder().actualOutput("wrongKey", "value").build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("conversation");
    }

    @Test
    void shouldRequireJudge() {
        assertThatThrownBy(() -> TrajectoryEvaluator.builder()
                        .name("Test")
                        .criterion(TrajectoryEvaluationCriteria.userSatisfaction())
                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JudgeLM");
    }

    @Test
    void shouldRequireAtLeastOneCriterion() {
        JudgeLM mockJudge = prompt -> """
                {"score": 0.5, "reason": "Test"}
                """;

        assertThatThrownBy(() -> TrajectoryEvaluator.builder()
                        .name("Test")
                        .judge(mockJudge)
                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("criterion");
    }

    @Test
    void shouldNormalizeScoresAboveOne() {
        // Some LLMs might return scores on a 0-10 scale
        JudgeLM mockJudge = prompt -> """
                {"score": 8.5, "reason": "Good"}
                """;

        TrajectoryEvaluator evaluator = TrajectoryEvaluator.builder()
                .name("Test")
                .judge(mockJudge)
                .criterion(TrajectoryEvaluationCriteria.userSatisfaction())
                .build();

        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("Test")
                .assistantMessage("Response")
                .build();

        EvalTestCase testCase =
                EvalTestCase.builder().actualOutput("trajectory", trajectory).build();

        EvalResult result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.85); // 8.5 / 10
    }

    @Test
    void shouldHandleMalformedLLMResponse() {
        JudgeLM brokenJudge = prompt -> "This is not JSON";

        TrajectoryEvaluator evaluator = TrajectoryEvaluator.builder()
                .name("Test")
                .judge(brokenJudge)
                .criterion(TrajectoryEvaluationCriteria.userSatisfaction())
                .build();

        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("Test")
                .assistantMessage("Response")
                .build();

        EvalTestCase testCase =
                EvalTestCase.builder().actualOutput("trajectory", trajectory).build();

        EvalResult result = evaluator.evaluate(testCase);

        // Should handle gracefully with 0 score
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void shouldIncludeTurnCountInMetadata() {
        JudgeLM mockJudge = prompt -> """
                {"score": 0.8, "reason": "Good"}
                """;

        TrajectoryEvaluator evaluator = TrajectoryEvaluator.builder()
                .name("Test")
                .judge(mockJudge)
                .criterion(TrajectoryEvaluationCriteria.userSatisfaction())
                .build();

        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("U1")
                .assistantMessage("A1")
                .userMessage("U2")
                .assistantMessage("A2")
                .build();

        EvalTestCase testCase =
                EvalTestCase.builder().actualOutput("trajectory", trajectory).build();

        EvalResult result = evaluator.evaluate(testCase);

        assertThat(result.metadata()).containsEntry("turnCount", 2);
    }

    @Test
    void shouldTreatNonNumericScoreAsParseFailureNotSilentZero() {
        JudgeLM mockJudge = prompt -> """
                {"score": "high", "reason": "Great conversation"}
                """;

        TrajectoryEvaluator evaluator = TrajectoryEvaluator.builder()
                .name("Test")
                .judge(mockJudge)
                .criterion(TrajectoryEvaluationCriteria.userSatisfaction())
                .build();

        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("Test")
                .assistantMessage("Response")
                .build();

        EvalTestCase testCase =
                EvalTestCase.builder().actualOutput("trajectory", trajectory).build();

        EvalResult result = evaluator.evaluate(testCase);

        // A non-numeric score must surface as a parse failure, not a silent 0.0 judgment
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reason()).contains("Failed to parse evaluation");
    }

    @Test
    void shouldNotRenderToolCallsInPromptByDefault() {
        AtomicReference<String> capturedPrompt = new AtomicReference<>();
        JudgeLM capturingJudge = prompt -> {
            capturedPrompt.set(prompt);
            return """
                    {"score": 0.8, "reason": "Good"}
                    """;
        };

        TrajectoryEvaluator evaluator = TrajectoryEvaluator.builder()
                .name("Test")
                .judge(capturingJudge)
                .criterion(TrajectoryEvaluationCriteria.userSatisfaction())
                .build();

        evaluator.evaluate(toolBearingTrajectoryTestCase());

        // The default builder leaves tool calls off, so the judge sees no tool lines.
        assertThat(capturedPrompt.get()).doesNotContain("[tool:");
    }

    @Test
    void shouldRenderToolCallsInPromptWhenIncludeToolCallsEnabled() {
        AtomicReference<String> capturedPrompt = new AtomicReference<>();
        JudgeLM capturingJudge = prompt -> {
            capturedPrompt.set(prompt);
            return """
                    {"score": 0.8, "reason": "Good"}
                    """;
        };

        TrajectoryEvaluator evaluator = TrajectoryEvaluator.builder()
                .name("Test")
                .judge(capturingJudge)
                .criterion(TrajectoryEvaluationCriteria.userSatisfaction())
                .includeToolCalls(true)
                .build();

        evaluator.evaluate(toolBearingTrajectoryTestCase());

        // With tool calls enabled, the judge prompt carries the tool lines.
        assertThat(capturedPrompt.get()).contains("[tool: get_weather(");
    }

    private static EvalTestCase toolBearingTrajectoryTestCase() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("What's the weather in Paris?")
                .assistantMessage(
                        "Let me check that for you.", List.of(ToolCall.of("get_weather", Map.of("city", "Paris"))))
                .build();

        return EvalTestCase.builder().actualOutput("trajectory", trajectory).build();
    }
}
