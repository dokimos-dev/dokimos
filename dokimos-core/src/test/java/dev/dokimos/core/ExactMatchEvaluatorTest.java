package dev.dokimos.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ExactMatchEvaluatorTest {

    @Test
    void shouldReturnFullScoreWhenMatch() {
        var evaluator = ExactMatchEvaluator.builder()
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("The quick brown fox")
                .expectedOutput("The quick brown fox")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
        assertThat(result.reason()).contains("exact matches");
    }

    @Test
    void shouldReturnZeroWhenMismatch() {
        var evaluator = ExactMatchEvaluator.builder()
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("The quick brown fox")
                .expectedOutput("A fast orange cat")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.success()).isFalse();
    }

    @Test
    void shouldThrowExceptionWhenRequiredParamIsMissing() {
        var evaluator = ExactMatchEvaluator.builder()
                .evaluationParams(List.of(EvalTestCaseParam.ACTUAL_OUTPUT, EvalTestCaseParam.EXPECTED_OUTPUT))
                .build();

        // Missing expected output
        var testCase = EvalTestCase.builder()
                .actualOutput("Hello, how can I help you?")
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EXPECTED_OUTPUT");
    }

    @Test
    void shouldRespectCustomThreshold() {
        var evaluator = ExactMatchEvaluator.builder()
                .threshold(0.5)
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("A")
                .expectedOutput("B")
                .build();

        var result = evaluator.evaluate(testCase);

        // Score is 0.0 -> threshold is 0.5
        assertThat(result.success()).isFalse();
    }
}
