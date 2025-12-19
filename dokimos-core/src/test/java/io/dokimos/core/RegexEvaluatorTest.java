package io.dokimos.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RegexEvaluatorTest {

    @Test
    void shouldReturnFullScoreWhenPatternMatches() {
        var evaluator = RegexEvaluator.builder()
                .pattern("quick.*fox")
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("The quick brown fox jumps")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
        assertThat(result.reason()).contains("matches the pattern");
    }

    @Test
    void shouldReturnZeroWhenPatternDoesNotMatch() {
        var evaluator = RegexEvaluator.builder()
                .pattern("\\d{3}-\\d{4}")
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("Call me at 555-HELP")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.success()).isFalse();
        assertThat(result.reason()).contains("does not match");
    }

    @Test
    void shouldRespectCaseSensitiveMatchingByDefault() {
        var evaluator = RegexEvaluator.builder()
                .pattern("HELLO")
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("hello world")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.success()).isFalse();
    }

    @Test
    void shouldMatchCaseInsensitivelyWhenConfigured() {
        var evaluator = RegexEvaluator.builder()
                .pattern("HELLO")
                .ignoreCase(true)
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("hello world")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldHandleComplexRegexPatterns() {
        var evaluator = RegexEvaluator.builder()
                .pattern("\\b[A-Z][a-z]+\\s[A-Z][a-z]+\\b")
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("Contact John Doe for details")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldSupportEmailPatterns() {
        var evaluator = RegexEvaluator.builder()
                .pattern("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("Contact us at support@dokimos.com")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldThrowExceptionWhenActualOutputIsNull() {
        var evaluator = RegexEvaluator.builder()
                .pattern("test")
                .build();

        var testCase = EvalTestCase.builder()
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACTUAL_OUTPUT");
    }

    @Test
    void shouldThrowExceptionWhenRequiredParamIsMissing() {
        var evaluator = RegexEvaluator.builder()
                .pattern("test")
                .evaluationParams(List.of(EvalTestCaseParam.ACTUAL_OUTPUT, EvalTestCaseParam.EXPECTED_OUTPUT))
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("test output")
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EXPECTED_OUTPUT");
    }

    @Test
    void shouldSupportCustomName() {
        var evaluator = RegexEvaluator.builder()
                .name("Email Validator")
                .pattern("@")
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("test@dokimos.com")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.name()).isEqualTo("Email Validator");
    }

    @Test
    void shouldUseDefaultName() {
        var evaluator = RegexEvaluator.builder()
                .pattern("test")
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("test")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.name()).isEqualTo("Regex Match");
    }

    @Test
    void shouldMatchPartialString() {
        var evaluator = RegexEvaluator.builder()
                .pattern("fox")
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("The quick brown fox jumps over the lazy dog")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldHandleEmptyString() {
        var evaluator = RegexEvaluator.builder()
                .pattern(".*")
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }
    
}
