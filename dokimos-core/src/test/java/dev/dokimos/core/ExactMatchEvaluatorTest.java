package dev.dokimos.core;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.evaluators.ExactMatchEvaluator;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExactMatchEvaluatorTest {

    @Test
    void shouldReturnFullScoreWhenMatch() {
        var evaluator = ExactMatchEvaluator.builder().build();

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
        var evaluator = ExactMatchEvaluator.builder().build();

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
        var evaluator = ExactMatchEvaluator.builder().threshold(0.5).build();

        var testCase =
                EvalTestCase.builder().actualOutput("A").expectedOutput("B").build();

        var result = evaluator.evaluate(testCase);

        // Score is 0.0 -> threshold is 0.5
        assertThat(result.success()).isFalse();
    }

    @Test
    void shouldBeCaseAndWhitespaceSensitiveByDefault() {
        var evaluator = ExactMatchEvaluator.builder().build();

        var caseDiff = EvalTestCase.builder()
                .actualOutput("PARIS")
                .expectedOutput("Paris")
                .build();
        var whitespaceDiff = EvalTestCase.builder()
                .actualOutput("  Paris\n")
                .expectedOutput("Paris")
                .build();

        assertThat(evaluator.evaluate(caseDiff).score()).isEqualTo(0.0);
        assertThat(evaluator.evaluate(whitespaceDiff).score()).isEqualTo(0.0);
    }

    @Test
    void shouldMatchIgnoringCaseWhenEnabled() {
        var evaluator = ExactMatchEvaluator.builder().ignoreCase(true).build();

        var testCase = EvalTestCase.builder()
                .actualOutput("THE Quick Brown fox")
                .expectedOutput("the quick brown FOX")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldStillFailOnDifferentTextWhenIgnoringCase() {
        var evaluator = ExactMatchEvaluator.builder().ignoreCase(true).build();

        var testCase = EvalTestCase.builder()
                .actualOutput("Paris")
                .expectedOutput("London")
                .build();

        assertThat(evaluator.evaluate(testCase).score()).isEqualTo(0.0);
    }

    @Test
    void shouldNotTrimWhenOnlyIgnoringCase() {
        var evaluator = ExactMatchEvaluator.builder().ignoreCase(true).build();

        var testCase = EvalTestCase.builder()
                .actualOutput(" paris ")
                .expectedOutput("Paris")
                .build();

        assertThat(evaluator.evaluate(testCase).score()).isEqualTo(0.0);
    }

    @Test
    void shouldMatchAfterTrimmingActualOutputWhenEnabled() {
        var evaluator = ExactMatchEvaluator.builder().trimWhiteSpace(true).build();

        var testCase = EvalTestCase.builder()
                .actualOutput("  Paris\n\t")
                .expectedOutput("Paris")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldTrimExpectedOutputToo() {
        var evaluator = ExactMatchEvaluator.builder().trimWhiteSpace(true).build();

        var testCase = EvalTestCase.builder()
                .actualOutput("Paris")
                .expectedOutput(" Paris \n")
                .build();

        assertThat(evaluator.evaluate(testCase).score()).isEqualTo(1.0);
    }

    @Test
    void shouldPreserveInnerWhitespaceWhenTrimming() {
        var evaluator = ExactMatchEvaluator.builder().trimWhiteSpace(true).build();

        var testCase = EvalTestCase.builder()
                .actualOutput("New  York")
                .expectedOutput("New York")
                .build();

        assertThat(evaluator.evaluate(testCase).score()).isEqualTo(0.0);
    }

    @Test
    void shouldNotIgnoreCaseWhenOnlyTrimming() {
        var evaluator = ExactMatchEvaluator.builder().trimWhiteSpace(true).build();

        var testCase = EvalTestCase.builder()
                .actualOutput(" PARIS ")
                .expectedOutput("Paris")
                .build();

        assertThat(evaluator.evaluate(testCase).score()).isEqualTo(0.0);
    }

    @Test
    void shouldCombineTrimAndIgnoreCase() {
        var evaluator = ExactMatchEvaluator.builder()
                .trimWhiteSpace(true)
                .ignoreCase(true)
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("  PARIS\n")
                .expectedOutput("paris")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.reason()).contains("exact matches");
    }

    @Test
    void shouldAllowDisablingFlagsAgain() {
        var evaluator = ExactMatchEvaluator.builder()
                .trimWhiteSpace(true)
                .ignoreCase(true)
                .trimWhiteSpace(false)
                .ignoreCase(false)
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput(" PARIS ")
                .expectedOutput("paris")
                .build();

        assertThat(evaluator.evaluate(testCase).score()).isEqualTo(0.0);
    }

    @Test
    void shouldNotThrowOnMissingExpectedOutputWhenNotRequired() {
        var evaluator = ExactMatchEvaluator.builder()
                .evaluationParams(List.of(EvalTestCaseParam.ACTUAL_OUTPUT))
                .trimWhiteSpace(true)
                .ignoreCase(true)
                .build();

        var testCase = EvalTestCase.builder().actualOutput("Paris").build();

        assertThat(evaluator.evaluate(testCase).score()).isEqualTo(0.0);
    }
}
