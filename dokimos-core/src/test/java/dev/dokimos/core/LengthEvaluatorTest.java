package dev.dokimos.core;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.evaluators.LengthEvaluator;
import org.junit.jupiter.api.Test;

class LengthEvaluatorTest {

    @Test
    void shouldReturnFullScoreWhenLengthIsWithinRange() {
        var evaluator = LengthEvaluator.builder()
                .minimumOutputLength(3)
                .maximumOutputLength(10)
                .build();

        var result = evaluator.evaluate(EvalTestCase.builder().actualOutput("hello").build());

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
        assertThat(result.reason()).contains("within the range");
    }

    @Test
    void shouldFailWhenLengthIsBelowMinimum() {
        var evaluator = LengthEvaluator.builder()
                .minimumOutputLength(10)
                .maximumOutputLength(20)
                .build();

        var result = evaluator.evaluate(EvalTestCase.builder().actualOutput("short").build());

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.success()).isFalse();
        assertThat(result.reason()).contains("less than the minimum");
    }

    @Test
    void shouldFailWhenLengthIsAboveMaximum() {
        var evaluator = LengthEvaluator.builder().maximumOutputLength(5).build();

        var result = evaluator.evaluate(
                EvalTestCase.builder().actualOutput("this is too long").build());

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.success()).isFalse();
        assertThat(result.reason()).contains("greater than the maximum");
    }

    @Test
    void shouldTreatBoundsAsInclusive() {
        var evaluator = LengthEvaluator.builder()
                .minimumOutputLength(5)
                .maximumOutputLength(10)
                .build();

        assertThat(evaluator
                .evaluate(EvalTestCase.builder().actualOutput("12345").build())
                .score())
                .isEqualTo(1.0);
        assertThat(evaluator
                .evaluate(EvalTestCase.builder()
                        .actualOutput("1234567890")
                        .build())
                .score())
                .isEqualTo(1.0);
        assertThat(evaluator
                .evaluate(EvalTestCase.builder().actualOutput("1234").build())
                .score())
                .isEqualTo(0.0);
        assertThat(evaluator
                .evaluate(EvalTestCase.builder()
                        .actualOutput("12345678901")
                        .build())
                .score())
                .isEqualTo(0.0);
    }

    @Test
    void shouldAcceptEmptyOutputWithDefaultMinimum() {
        var evaluator = LengthEvaluator.builder().maximumOutputLength(5).build();

        var result = evaluator.evaluate(EvalTestCase.builder().actualOutput("").build());

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldNotRequireExpectedOutput() {
        var evaluator = LengthEvaluator.builder().maximumOutputLength(100).build();

        var testCase = EvalTestCase.builder().actualOutput("no expected output here").build();

        assertThatCode(() -> evaluator.evaluate(testCase)).doesNotThrowAnyException();
    }

    @Test
    void shouldUseDefaultNameAndThreshold() {
        var evaluator = LengthEvaluator.builder().maximumOutputLength(10).build();

        assertThat(evaluator.name()).isEqualTo("Length");
        assertThat(evaluator.threshold()).isEqualTo(1.0);
    }

    @Test
    void shouldUseCustomNameAndThreshold() {
        var evaluator = LengthEvaluator.builder()
                .name("Tweet Length")
                .threshold(0.5)
                .maximumOutputLength(280)
                .build();

        var result = evaluator.evaluate(EvalTestCase.builder().actualOutput("hi").build());

        assertThat(result.name()).isEqualTo("Tweet Length");
        assertThat(result.threshold()).isEqualTo(0.5);
    }

    @Test
    void shouldThrowWhenMaximumNotSet() {
        assertThatThrownBy(() -> LengthEvaluator.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maximumOutputLength");
    }

    @Test
    void shouldThrowWhenMaximumIsLessThanOne() {
        assertThatThrownBy(
                () -> LengthEvaluator.builder().maximumOutputLength(0).build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrowWhenMinimumIsNegative() {
        assertThatThrownBy(() -> LengthEvaluator.builder()
                .minimumOutputLength(-1)
                .maximumOutputLength(10)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void shouldThrowWhenMinimumExceedsMaximum() {
        assertThatThrownBy(() -> LengthEvaluator.builder()
                .minimumOutputLength(20)
                .maximumOutputLength(10)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be greater than");
    }

    @Test
    void shouldAllowEqualMinimumAndMaximum() {
        var evaluator = LengthEvaluator.builder()
                .minimumOutputLength(3)
                .maximumOutputLength(3)
                .build();

        assertThat(evaluator
                .evaluate(EvalTestCase.builder().actualOutput("abc").build())
                .score())
                .isEqualTo(1.0);
        assertThat(evaluator
                .evaluate(EvalTestCase.builder().actualOutput("abcd").build())
                .score())
                .isEqualTo(0.0);
    }

    @Test
    void shouldCountCodePointsNotUtf16Units() {
        var evaluator = LengthEvaluator.builder().maximumOutputLength(1).build();

        var result = evaluator.evaluate(EvalTestCase.builder().actualOutput("😀").build());

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }
}
