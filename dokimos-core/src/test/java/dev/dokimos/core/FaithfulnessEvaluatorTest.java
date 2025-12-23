package dev.dokimos.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class FaithfulnessEvaluatorTest {

    @Test
    void shouldReturnFullScoreWhenAllClaimsSupported() {
        JudgeLM mockJudge = new MockJudge()
                .withTruths("""
                        ["Paris is the capital of France", "The Eiffel Tower is in Paris"]
                        """)
                .withClaims("""
                        ["Paris is the capital of France", "The Eiffel Tower is located in Paris"]
                        """)
                .withVerdicts("""
                        [
                            {"verdict": "Yes", "reasoning": "Directly stated in truths"},
                            {"verdict": "Yes", "reasoning": "Matches the second truth"}
                        ]
                        """)
                .withReason("All claims are fully supported by the provided context.");

        var evaluator = FaithfulnessEvaluator.builder()
                .name("faithfulness")
                .contextKey("context")
                .threshold(0.8)
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("What is the capital of France?")
                .actualOutput("context", "Paris is the capital of France. The Eiffel Tower is in Paris.")
                .actualOutput("Paris is the capital of France. The Eiffel Tower is located in Paris.")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.name()).isEqualTo("faithfulness");
        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
        assertThat(result.reason()).isEqualTo("All claims are fully supported by the provided context.");
    }

    @Test
    void shouldReturnPartialScoreWhenSomeClaimsNotSupported() {
        JudgeLM mockJudge = new MockJudge()
                .withTruths("""
                        ["Paris is the capital of France"]
                        """)
                .withClaims("""
                        ["Paris is the capital of France", "Paris has 10 million inhabitants"]
                        """)
                .withVerdicts("""
                        [
                            {"verdict": "Yes", "reasoning": "Directly stated"},
                            {"verdict": "No", "reasoning": "Population not mentioned in context"}
                        ]
                        """)
                .withReason("One claim about population is not supported by the context.");

        var evaluator = FaithfulnessEvaluator.builder()
                .judge(mockJudge)
                .threshold(0.8)
                .build();

        var testCase = EvalTestCase.builder()
                .input("Tell me about Paris")
                .actualOutput("context", "Paris is the capital of France.")
                .actualOutput("Paris is the capital of France. Paris has 10 million inhabitants.")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.success()).isFalse();
        assertThat(result.reason()).contains("population");
    }

    @Test
    void shouldReturnFullScoreWhenNoClaimsFound() {
        JudgeLM mockJudge = new MockJudge()
                .withTruths("""
                        ["Some truth"]
                        """)
                .withClaims("""
                        []
                        """)
                .withVerdicts("""
                        []
                        """)
                .withReason("No claims were found to evaluate.");

        var evaluator = FaithfulnessEvaluator.builder()
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("some input")
                .actualOutput("context", "Some context")
                .actualOutput("")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.reason()).contains("No claims");
    }

    @Test
    void shouldHandleIdkVerdictsAsNotSupported() {
        JudgeLM mockJudge = new MockJudge()
                .withTruths("""
                        ["The sky is blue"]
                        """)
                .withClaims("""
                        ["The sky is blue", "The grass is green"]
                        """)
                .withVerdicts("""
                        [
                            {"verdict": "Yes", "reasoning": "Stated in truths"},
                            {"verdict": "IDK", "reasoning": "Grass color not mentioned"}
                        ]
                        """)
                .withReason("One claim could not be verified from the context.");

        var evaluator = FaithfulnessEvaluator.builder()
                .judge(mockJudge)
                .threshold(0.8)
                .build();

        var testCase = EvalTestCase.builder()
                .input("some input")
                .actualOutput("context", "The sky is blue.")
                .actualOutput("The sky is blue. The grass is green.")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.success()).isFalse();
    }

    @Test
    void shouldDisableReasoningWhenConfigured() {
        JudgeLM mockJudge = new MockJudge()
                .withTruths("""
                        ["Truth"]
                        """)
                .withClaims("""
                        ["Claim"]
                        """)
                .withVerdicts("""
                        [{"verdict": "Yes", "reasoning": "ok"}]
                        """);

        var evaluator = FaithfulnessEvaluator.builder()
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
                .withTruths("""
                        ["Custom context truth"]
                        """)
                .withClaims("""
                        ["Custom context truth"]
                        """)
                .withVerdicts("""
                        [{"verdict": "Yes", "reasoning": "ok"}]
                        """)
                .withReason("ok");

        var evaluator = FaithfulnessEvaluator.builder()
                .contextKey("retrieved_docs")
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("some input")
                .actualOutput("retrieved_docs", "Custom context truth")
                .actualOutput("Custom context truth")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldRespectCustomThreshold() {
        JudgeLM mockJudge = new MockJudge()
                .withTruths("""
                        ["Truth"]
                        """)
                .withClaims("""
                        ["Claim 1", "Claim 2"]
                        """)
                .withVerdicts("""
                        [
                            {"verdict": "Yes", "reasoning": "ok"},
                            {"verdict": "No", "reasoning": "not supported"}
                        ]
                        """)
                .withReason("ok");

        var evaluator = FaithfulnessEvaluator.builder()
                .judge(mockJudge)
                .threshold(0.4)
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
    void shouldHandleMalformedTruthsJson() {
        JudgeLM brokenJudge = prompt -> {
            if (prompt.contains("Extract the factual truths")) {
                return "This is not valid JSON";
            }
            return """
                    ["claim"]
                    """;
        };

        var evaluator = FaithfulnessEvaluator.builder()
                .judge(brokenJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("some input")
                .actualOutput("context", "Some context")
                .actualOutput("Some output")
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("Could not parse JSON response to extract truths");
    }

    @Test
    void shouldHandleMalformedClaimsJson() {
        JudgeLM brokenJudge = prompt -> {
            if (prompt.contains("Extract the factual truths")) {
                return """
                        ["truth"]
                        """;
            }
            if (prompt.contains("break it down into individual claims")) {
                return "Not valid JSON at all";
            }
            return "{}";
        };

        var evaluator = FaithfulnessEvaluator.builder()
                .judge(brokenJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("some input")
                .actualOutput("context", "Some context")
                .actualOutput("Some output")
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("Could not parse JSON response to extract statements");
    }

    @Test
    void shouldThrowExceptionWhenContextKeyMissing() {
        JudgeLM mockJudge = prompt -> "[]";

        var evaluator = FaithfulnessEvaluator.builder()
                .contextKey("context")
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("some input")
                .actualOutput("Some output")
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class);
    }

    private static class MockJudge implements JudgeLM {
        private String truthsResponse;
        private String claimsResponse;
        private String verdictsResponse;
        private String reasonResponse;
        private boolean malformedVerdicts = false;

        public MockJudge withTruths(String response) {
            this.truthsResponse = response;
            return this;
        }

        public MockJudge withClaims(String response) {
            this.claimsResponse = response;
            return this;
        }

        public MockJudge withVerdicts(String response) {
            this.verdictsResponse = response;
            return this;
        }

        public MockJudge withReason(String response) {
            this.reasonResponse = response;
            return this;
        }

        public MockJudge withMalformedVerdicts() {
            this.malformedVerdicts = true;
            return this;
        }

        @Override
        public String generate(String prompt) {
            if (prompt.contains("Extract the factual truths")) {
                return truthsResponse;
            }
            if (prompt.contains("break it down into individual claims")) {
                return claimsResponse;
            }
            if (prompt.contains("Compare each CLAIM")) {
                return malformedVerdicts ? "Not valid JSON" : verdictsResponse;
            }
            if (prompt.contains("Summarize the faithfulness")) {
                return reasonResponse;
            }
            return "{}";
        }
    }
}
