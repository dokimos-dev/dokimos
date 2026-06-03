package dev.dokimos.core;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.evaluators.LLMJudgeEvaluator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

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

        var testCase =
                EvalTestCase.builder().input("q").actualOutput("wrong answer").build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.success()).isFalse();
        assertThat(result.score()).isEqualTo(0.3);
    }

    @Test
    void shouldBuildPromptWithSelectedParams() {
        var capturedPrompt = new String[] {null};

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

        assertThat(capturedPrompt[0]).contains("my input").contains("my output").doesNotContain("should not appear");
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

        var testCase = EvalTestCase.builder().input("q").actualOutput("a").build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.success()).isFalse();
        assertThat(result.reason()).contains("Failed to parse");
    }

    @Test
    void shouldRequireJudge() {
        assertThatThrownBy(() -> LLMJudgeEvaluator.builder()
                        .name("test")
                        .criteria("Test")
                        .evaluationParams(List.of(EvalTestCaseParam.ACTUAL_OUTPUT))
                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JudgeLM");
    }

    @Test
    void shouldCaptureCustomScoreRange() {
        var capturedPrompt = new String[] {null};

        JudgeLM mockJudge = prompt -> {
            capturedPrompt[0] = prompt;
            return """
                    {"score": 4.2, "reason": "Good response"}
                    """;
        };

        var evaluator = LLMJudgeEvaluator.builder()
                .name("answer-quality")
                .criteria("Rate the quality")
                .evaluationParams(List.of(EvalTestCaseParam.ACTUAL_OUTPUT))
                .scoreRange(1, 5)
                .threshold(3.5)
                .judge(mockJudge)
                .build();

        var testCase =
                EvalTestCase.builder().input("question").actualOutput("answer").build();

        var result = evaluator.evaluate(testCase);

        assertThat(capturedPrompt[0]).contains("between 1.0 and 5.0");
        assertThat(result.score()).isCloseTo(0.8, within(1e-9));
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldFailWhenBelowCustomThreshold() {
        JudgeLM mockJudge = prompt -> """
                {"score": 1.5, "reason": "The response was incorrect"}
                """;

        var evaluator = LLMJudgeEvaluator.builder()
                .name("quality")
                .criteria("Rate the quality")
                .evaluationParams(List.of(EvalTestCaseParam.ACTUAL_OUTPUT))
                .scoreRange(1, 5)
                .threshold(3.5)
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("question")
                .actualOutput("I don't know")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isCloseTo(0.125, within(1e-9));
        assertThat(result.success()).isFalse();
    }

    @Test
    void shouldThrowExceptionWhenRequiredParamMissing() {
        JudgeLM mockJudge = prompt -> "{\"score\": 1.0, \"reason\": \"ok\"}";

        var evaluator = LLMJudgeEvaluator.builder()
                .name("input-check")
                .criteria("Check if input is relevant")
                .evaluationParams(List.of(EvalTestCaseParam.INPUT))
                .judge(mockJudge)
                .build();

        // Test case has actual/expected, but input is missing and required here
        var testCase = EvalTestCase.builder()
                .actualOutput("output")
                .expectedOutput("output")
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INPUT");
    }

    @Test
    void shouldRequireAtLeastOneEvalParamOnBuild() {
        JudgeLM mockJudge = prompt -> "";

        assertThatThrownBy(() -> LLMJudgeEvaluator.builder()
                        .name("test")
                        .criteria("Test")
                        .judge(mockJudge)
                        .evaluationParams(List.of())
                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one evaluation param");
    }

    @Test
    void shouldRecoverScoreFromProsePreamble() {
        JudgeLM judge = prompt -> "Here is my assessment:\n{\"score\": 0.9, \"reason\": \"Matches well\"}\nThanks!";

        var evaluator = LLMJudgeEvaluator.builder()
                .name("correctness")
                .criteria("Check correctness")
                .evaluationParams(List.of(EvalTestCaseParam.ACTUAL_OUTPUT))
                .threshold(0.7)
                .judge(judge)
                .build();

        var testCase = EvalTestCase.builder().input("q").actualOutput("a").build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.9);
        assertThat(result.success()).isTrue();
        assertThat(result.reason()).isEqualTo("Matches well");
    }

    @Test
    void shouldRejectInvertedOrEmptyScoreRange() {
        assertThatThrownBy(() -> LLMJudgeEvaluator.builder().scoreRange(5, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LLMJudgeEvaluator.builder().scoreRange(5, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * CRITICAL REGRESSION GUARD: for String outputs the produced prompt must be byte-identical to
     * the pre-change behavior (raw String rendered verbatim). The expected prompt is constructed
     * explicitly here so any drift in formatting fails loudly.
     */
    @Test
    void shouldProduceByteIdenticalPromptForStringOutput() {
        var capturedPrompt = new String[] {null};
        JudgeLM capturingJudge = prompt -> {
            capturedPrompt[0] = prompt;
            return "{\"score\": 0.9, \"reason\": \"ok\"}";
        };

        var evaluator = LLMJudgeEvaluator.builder()
                .name("correctness")
                .criteria("Check correctness")
                .evaluationParams(List.of(EvalTestCaseParam.ACTUAL_OUTPUT, EvalTestCaseParam.EXPECTED_OUTPUT))
                .judge(capturingJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("q")
                .actualOutput("30 days refund")
                .expectedOutput("30 days refund policy")
                .build();

        evaluator.evaluate(testCase);

        String expected = "Evaluate the following based on this criteria: Check correctness\n\n"
                + "Actual Output: 30 days refund\n"
                + "Expected Output: 30 days refund policy\n"
                + "Provide a score between 0.0 and 1.0, and a brief reasoning."
                + "Respond in JSON format: {\"score\": <number>, \"reason\": \"<explanation>\"}";

        assertThat(capturedPrompt[0]).isEqualTo(expected);
    }

    @Test
    void shouldRenderPojoOutputAsPrettyJson() {
        var capturedPrompt = new String[] {null};
        JudgeLM capturingJudge = prompt -> {
            capturedPrompt[0] = prompt;
            return "{\"score\": 0.9, \"reason\": \"ok\"}";
        };

        var evaluator = LLMJudgeEvaluator.builder()
                .name("test")
                .criteria("Test")
                .evaluationParams(List.of(EvalTestCaseParam.ACTUAL_OUTPUT))
                .judge(capturingJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("q")
                .actualOutput("output", new Whisky("Lagavulin", 16))
                .build();

        evaluator.evaluate(testCase);

        // Pretty JSON spans multiple lines and uses JSON syntax, not Java toString().
        assertThat(capturedPrompt[0])
                .contains("Actual Output: {")
                .contains("\"name\" : \"Lagavulin\"")
                .contains("\"age\" : 16")
                .doesNotContain("Whisky[");
    }

    @Test
    void shouldRenderListOutputAsJsonArray() {
        var capturedPrompt = new String[] {null};
        JudgeLM capturingJudge = prompt -> {
            capturedPrompt[0] = prompt;
            return "{\"score\": 0.9, \"reason\": \"ok\"}";
        };

        var evaluator = LLMJudgeEvaluator.builder()
                .name("test")
                .criteria("Test")
                .evaluationParams(List.of(EvalTestCaseParam.ACTUAL_OUTPUT))
                .judge(capturingJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("q")
                .actualOutput("output", List.of("a", "b", "c"))
                .build();

        evaluator.evaluate(testCase);

        assertThat(capturedPrompt[0])
                .contains("Actual Output: [")
                .contains("\"a\"")
                .contains("\"b\"")
                .contains("\"c\"")
                .contains("]");
    }

    /**
     * Documented INTENTIONAL behavior change: a Map output previously rendered via Java's {@code
     * toString()} (e.g. {@code {region=Islay}}) now renders as JSON ({@code {"region":"Islay"}}).
     */
    @Test
    void shouldRenderMapOutputAsJsonNotJavaToString() {
        var capturedPrompt = new String[] {null};
        JudgeLM capturingJudge = prompt -> {
            capturedPrompt[0] = prompt;
            return "{\"score\": 0.9, \"reason\": \"ok\"}";
        };

        var evaluator = LLMJudgeEvaluator.builder()
                .name("test")
                .criteria("Test")
                .evaluationParams(List.of(EvalTestCaseParam.ACTUAL_OUTPUT))
                .judge(capturingJudge)
                .build();

        Map<String, Object> mapOutput = new LinkedHashMap<>();
        mapOutput.put("region", "Islay");

        var testCase = EvalTestCase.builder()
                .input("q")
                .actualOutput("output", mapOutput)
                .build();

        evaluator.evaluate(testCase);

        assertThat(capturedPrompt[0]).contains("\"region\" : \"Islay\"").doesNotContain("region=Islay");
    }

    /**
     * A null output must never surface as an NPE from the rendering path. When the param is required
     * the documented validation error ({@link IllegalArgumentException}) fires first; either way no
     * {@link NullPointerException} escapes.
     */
    @Test
    void shouldNotNpeWhenRequiredOutputIsNull() {
        JudgeLM mockJudge = prompt -> "{\"score\": 0.9, \"reason\": \"ok\"}";

        var evaluator = LLMJudgeEvaluator.builder()
                .name("test")
                .criteria("Test")
                .evaluationParams(List.of(EvalTestCaseParam.ACTUAL_OUTPUT))
                .judge(mockJudge)
                .build();

        // No actual output set -> raw value is null.
        var testCase = EvalTestCase.builder().input("q").build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(NullPointerException.class);
    }

    /**
     * Directly exercises the defensive rendering branch: a null raw value must render as the literal
     * {@code "null"} (matching the historical {@code append((String) null)} behavior) rather than
     * throwing a {@link NullPointerException}.
     */
    @Test
    void shouldRenderNullOutputAsLiteralWithoutNpe() throws Exception {
        var method = LLMJudgeEvaluator.class.getDeclaredMethod("renderOutput", Object.class);
        method.setAccessible(true);

        Object rendered = method.invoke(null, new Object[] {null});

        assertThat(rendered).isEqualTo("null");
    }

    private record Whisky(String name, int age) {}
}
