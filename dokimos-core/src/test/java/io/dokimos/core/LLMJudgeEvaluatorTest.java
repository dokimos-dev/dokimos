package io.dokimos.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class LLMJudgeEvaluatorTest {

    @Test
    void shouldParseSuccessfulResponse() {
        JudgeLM mockLLM = prompt -> """
                {"score": 0.85, "reason": "The outputs match semantically"}
                """;

        var evaluator = LLMJudgeEvaluator.builder()
                .name("correctness")
                .criteria("Check if the actual input matches the expected output")
                .evaluationParams(List.of(EvalTestCaseParam.ACTUAL_OUTPUT, EvalTestCaseParam.EXPECTED_OUTPUT))
                .threshold(0.7)
                .judge(mockLLM)
                .build();

        var testCase = EvalTestCase.builder()
                .input("question")
                .actualOutput("30 days refund")
                .expectedOutput("30 days refund policy")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.name()).isEqualTo("correctness");
        assertThat(result.score()).isEqualTo(0.85);
        assertThat(result.success()).isTrue();
        assertThat(result.reason()).isEqualTo("The outputs match semantically");
    }

    @Test
    void shouldFailWhenBelowThreshold() {
        JudgeLM mockJudge = prompt -> """
            {"score": 0.3, "reason": "Outputs are very different"}
            """;

        var evaluator = LLMJudgeEvaluator.builder()
                .name("correctness")
                .criteria("Check correctness")
                .evaluationParams(List.of(EvalTestCaseParam.ACTUAL_OUTPUT))
                .threshold(0.5)
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("q")
                .actualOutput("wrong answer")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.success()).isFalse();
        assertThat(result.score()).isEqualTo(0.3);
    }

    @Test
    void shouldBuildPromptWithSelectedParams() {
        var capturedPrompt = new String[]{null};

        JudgeLM capturingJudge = prompt -> {
            capturedPrompt[0] = prompt;
            return """
                {"score": 0.9, "reason": "ok"}
                """;
        };

        var evaluator = LLMJudgeEvaluator.builder()
                .name("test")
                .criteria("Test criteria")
                .evaluationParams(List.of(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
                .judge(capturingJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("my input")
                .actualOutput("my output")
                .expectedOutput("should not appear")
                .build();

        evaluator.evaluate(testCase);

        assertThat(capturedPrompt[0])
                .contains("my input")
                .contains("my output")
                .doesNotContain("should not appear");
    }

    @Test
    void shouldHandleMalformedLLMResponse() {
        JudgeLM brokenJudge = prompt -> "This is not really JSON at all :)";

        var evaluator = LLMJudgeEvaluator.builder()
                .name("test")
                .criteria("Test")
                .evaluationParams(List.of(EvalTestCaseParam.ACTUAL_OUTPUT))
                .judge(brokenJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("q")
                .actualOutput("a")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.success()).isFalse();
        assertThat(result.reason()).contains("Failed to parse");
    }

    @Test
    void shouldRequireJudge() {
        assertThatThrownBy(() ->
                LLMJudgeEvaluator.builder()
                        .name("test")
                        .criteria("Test")
                        .evaluationParams(List.of(EvalTestCaseParam.ACTUAL_OUTPUT))
                        .build()
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JudgeLM");
    }
}
