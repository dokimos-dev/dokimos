package dev.dokimos.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class HallucinationEvaluatorTest {

    @Test
    void shouldReturnZeroScoreWhenAllStatementsFactual() {
        JudgeLM mockJudge = new MockJudge()
                .withVerdicts("""
                        [
                            {"verdict": "yes", "reason": "Paris is the capital is stated in context"},
                            {"verdict": "yes", "reason": "Eiffel Tower location matches context"}
                        ]
                        """)
                .withReason("All statements are factually aligned with the provided context.");

        var evaluator = HallucinationEvaluator.builder()
                .name("hallucination")
                .contextKey("context")
                .threshold(0.5)
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("What is the capital of France?")
                .actualOutput("context", "Paris is the capital of France. The Eiffel Tower is in Paris.")
                .actualOutput("Paris is the capital of France. The Eiffel Tower is in Paris.")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.name()).isEqualTo("hallucination");
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.success()).isTrue();
        assertThat(result.reason()).isEqualTo("All statements are factually aligned with the provided context.");
    }

    @Test
    void shouldReturnPartialScoreWhenSomeStatementsHallucinated() {
        JudgeLM mockJudge = new MockJudge()
                .withVerdicts("""
                        [
                            {"verdict": "yes", "reason": "Capital is correct"},
                            {"verdict": "no", "reason": "Population of 10 million is not supported by context"}
                        ]
                        """)
                .withReason("One statement about population is not supported by the context.");

        var evaluator = HallucinationEvaluator.builder()
                .judge(mockJudge)
                .threshold(0.5)
                .build();

        var testCase = EvalTestCase.builder()
                .input("Tell me about Paris")
                .actualOutput("context", "Paris is the capital of France.")
                .actualOutput("Paris is the capital of France. Paris has 10 million inhabitants.")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.success()).isTrue();
        assertThat(result.reason()).contains("population");
    }

    @Test
    void shouldReturnFullScoreWhenAllStatementsHallucinated() {
        JudgeLM mockJudge = new MockJudge()
                .withVerdicts("""
                        [
                            {"verdict": "no", "reason": "Berlin is not the capital of France"},
                            {"verdict": "no", "reason": "Population is not mentioned in context"}
                        ]
                        """)
                .withReason("All statements contradict or are not supported by the context.");

        var evaluator = HallucinationEvaluator.builder()
                .judge(mockJudge)
                .threshold(0.5)
                .build();

        var testCase = EvalTestCase.builder()
                .input("Tell me about France")
                .actualOutput("context", "Paris is the capital of France.")
                .actualOutput("Berlin is the capital of France with 5 million people.")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isFalse();
        assertThat(result.reason()).contains("contradict");
    }

    @Test
    void shouldReturnZeroScoreWhenNoStatementsFound() {
        JudgeLM mockJudge = new MockJudge()
                .withVerdicts("""
                        []
                        """)
                .withReason("No statements were found to evaluate.");

        var evaluator = HallucinationEvaluator.builder()
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("some input")
                .actualOutput("context", "Some context")
                .actualOutput("")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reason()).contains("No statements");
    }

    @Test
    void shouldDisableReasoningWhenConfigured() {
        JudgeLM mockJudge = new MockJudge()
                .withVerdicts("""
                        [{"verdict": "yes", "reason": "ok"}]
                        """);

        var evaluator = HallucinationEvaluator.builder()
                .judge(mockJudge)
                .includeReason(false)
                .build();

        var testCase = EvalTestCase.builder()
                .input("some input")
                .actualOutput("context", "Some context")
                .actualOutput("Some output")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.reason()).isEqualTo("Reasoning was disabled");
    }

    @Test
    void shouldUseCustomContextKey() {
        JudgeLM mockJudge = new MockJudge()
                .withVerdicts("""
                        [{"verdict": "yes", "reason": "Supported by retrieved docs"}]
                        """)
                .withReason("All statements are factual.");

        var evaluator = HallucinationEvaluator.builder()
                .contextKey("retrieved_docs")
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("some input")
                .actualOutput("retrieved_docs", "Custom context truth")
                .actualOutput("Custom context truth")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldRespectCustomThreshold() {
        JudgeLM mockJudge = new MockJudge()
                .withVerdicts("""
                        [
                            {"verdict": "yes", "reason": "ok"},
                            {"verdict": "yes", "reason": "ok"},
                            {"verdict": "no", "reason": "not supported"}
                        ]
                        """)
                .withReason("One statement not supported");

        var evaluator = HallucinationEvaluator.builder()
                .judge(mockJudge)
                .threshold(0.3)
                .build();

        var testCase = EvalTestCase.builder()
                .input("some input")
                .actualOutput("context", "context")
                .actualOutput("output")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isCloseTo(0.333, within(0.01));
        assertThat(result.success()).isFalse();
    }

    @Test
    void shouldPassWhenScoreEqualsThreshold() {
        JudgeLM mockJudge = new MockJudge()
                .withVerdicts("""
                        [
                            {"verdict": "yes", "reason": "ok"},
                            {"verdict": "no", "reason": "hallucinated"}
                        ]
                        """)
                .withReason("ok");

        var evaluator = HallucinationEvaluator.builder()
                .judge(mockJudge)
                .threshold(0.5)
                .build();

        var testCase = EvalTestCase.builder()
                .input("some input")
                .actualOutput("context", "context")
                .actualOutput("output")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldHandleCaseInsensitiveVerdicts() {
        JudgeLM mockJudge = new MockJudge()
                .withVerdicts("""
                        [
                            {"verdict": "YES", "reason": "ok"},
                            {"verdict": "No", "reason": "not ok"},
                            {"verdict": "  yes  ", "reason": "ok with spaces"}
                        ]
                        """)
                .withReason("Mixed case verdicts");

        var evaluator = HallucinationEvaluator.builder()
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("some input")
                .actualOutput("context", "context")
                .actualOutput("output")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isCloseTo(0.333, within(0.01));
    }

    @Test
    void shouldThrowExceptionWhenContextKeyMissing() {
        JudgeLM mockJudge = prompt -> "[]";

        var evaluator = HallucinationEvaluator.builder()
                .contextKey("context")
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("some input")
                .actualOutput("Some output")
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("Hallucination evaluator requires 'context'");
    }

    @Test
    void shouldHandleMalformedVerdictsJson() {
        JudgeLM brokenJudge = prompt -> "This is not valid JSON";

        var evaluator = HallucinationEvaluator.builder()
                .judge(brokenJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("some input")
                .actualOutput("context", "Some context")
                .actualOutput("Some output")
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("Failed to parse verdict response");
    }

    @Test
    void shouldHandleMarkdownWrappedJsonResponses() {
        JudgeLM judgeWithMarkdown = new MockJudge()
                .withVerdicts("""
                        ```json
                        [
                            {"verdict": "yes", "reason": "Matches context"},
                            {"verdict": "no", "reason": "Not in context"}
                        ]
                        ```
                        """)
                .withReason("Some hallucination detected");

        var evaluator = HallucinationEvaluator.builder()
                .judge(judgeWithMarkdown)
                .build();

        var testCase = EvalTestCase.builder()
                .input("Tell me about Paris")
                .actualOutput("context", "Paris is the capital")
                .actualOutput("Paris is the capital. Paris has a zoo.")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldHandleMarkdownWithoutJsonLabel() {
        JudgeLM judgeWithMarkdown = new MockJudge()
                .withVerdicts("""
                        ```
                        [{"verdict": "yes", "reason": "ok"}]
                        ```
                        """)
                .withReason("ok");

        var evaluator = HallucinationEvaluator.builder()
                .judge(judgeWithMarkdown)
                .build();

        var testCase = EvalTestCase.builder()
                .input("input")
                .actualOutput("context", "context")
                .actualOutput("output")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void shouldRequireJudgeLM() {
        assertThatThrownBy(() -> HallucinationEvaluator.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JudgeLM is required");
    }

    @Test
    void shouldUseCustomName() {
        JudgeLM mockJudge = new MockJudge()
                .withVerdicts("""
                        [{"verdict": "yes", "reason": "ok"}]
                        """)
                .withReason("ok");

        var evaluator = HallucinationEvaluator.builder()
                .name("custom-hallucination")
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("input")
                .actualOutput("context", "context")
                .actualOutput("output")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.name()).isEqualTo("custom-hallucination");
    }

    private static class MockJudge implements JudgeLM {
        private String verdictsResponse;
        private String reasonResponse;

        public MockJudge withVerdicts(String response) {
            this.verdictsResponse = response;
            return this;
        }

        public MockJudge withReason(String response) {
            this.reasonResponse = response;
            return this;
        }

        @Override
        public String generate(String prompt) {
            if (prompt.contains("determine whether each statement")) { // verdicts
                return verdictsResponse;
            }
            if (prompt.contains("Summarize the hallucination evaluation")) { // reason
                return reasonResponse;
            }
            return "{}";
        }
    }
}
