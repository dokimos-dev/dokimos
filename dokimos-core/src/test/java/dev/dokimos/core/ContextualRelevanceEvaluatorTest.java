package dev.dokimos.core;

import dev.dokimos.core.evaluators.ContextualRelevanceEvaluator;
import dev.dokimos.core.evaluators.EvaluationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class ContextualRelevanceEvaluatorTest {

    @Test
    void shouldReturnHighScoreWhenAllContextsRelevant() {
        JudgeLM mockJudge = new MockJudge()
                .withScoreResponse(0, """
                        {"score": 0.95, "reason": "Directly addresses dehydration symptoms"}
                        """)
                .withScoreResponse(1, """
                        {"score": 0.90, "reason": "Covers severe dehydration symptoms"}
                        """)
                .withSummary("Both contexts are highly relevant to the query about dehydration symptoms.");

        var evaluator = ContextualRelevanceEvaluator.builder()
                .name("contextual-relevance")
                .judge(mockJudge)
                .threshold(0.5)
                .build();

        var testCase = EvalTestCase.builder()
                .input("What are symptoms of dehydration?")
                .actualOutput("retrievalContext", List.of(
                        "Dehydration symptoms include thirst, dry mouth, and fatigue.",
                        "Severe dehydration can cause dizziness and confusion."
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.name()).isEqualTo("contextual-relevance");
        assertThat(result.score()).isCloseTo(0.925, within(0.001));
        assertThat(result.success()).isTrue();
        assertThat(result.reason()).contains("relevant");
    }

    @Test
    void shouldReturnMixedScoreWithIrrelevantContext() {
        // Example from the requirements:
        // Context about dehydration symptoms: 0.95
        // Context about Pacific Ocean: 0.05
        // Context about severe dehydration: 0.90
        // Final score: (0.95 + 0.05 + 0.90) / 3 = 0.633
        JudgeLM mockJudge = new MockJudge()
                .withScoreResponse(0, """
                        {"score": 0.95, "reason": "Directly addresses dehydration symptoms"}
                        """)
                .withScoreResponse(1, """
                        {"score": 0.05, "reason": "Pacific Ocean has no relevance to dehydration"}
                        """)
                .withScoreResponse(2, """
                        {"score": 0.90, "reason": "Covers severe dehydration symptoms"}
                        """)
                .withSummary("Two contexts are highly relevant while one is irrelevant, resulting in a moderate overall score.");

        var evaluator = ContextualRelevanceEvaluator.builder()
                .judge(mockJudge)
                .threshold(0.5)
                .build();

        var testCase = EvalTestCase.builder()
                .input("What are symptoms of dehydration?")
                .actualOutput("retrievalContext", List.of(
                        "Dehydration symptoms include thirst, dry mouth, and fatigue.",
                        "The Pacific Ocean is the largest ocean on Earth.",
                        "Severe dehydration can cause dizziness and confusion."
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isCloseTo(0.633, within(0.001));
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldFailWhenScoreBelowThreshold() {
        JudgeLM mockJudge = new MockJudge()
                .withScoreResponse(0, """
                        {"score": 0.1, "reason": "Not relevant to the query"}
                        """)
                .withScoreResponse(1, """
                        {"score": 0.2, "reason": "Marginally related"}
                        """)
                .withSummary("Retrieved contexts are mostly irrelevant to the query.");

        var evaluator = ContextualRelevanceEvaluator.builder()
                .judge(mockJudge)
                .threshold(0.5)
                .build();

        var testCase = EvalTestCase.builder()
                .input("What is machine learning?")
                .actualOutput("retrievalContext", List.of(
                        "Cooking recipes for beginners",
                        "History of ancient Rome"
                ))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isCloseTo(0.15, within(0.001));
        assertThat(result.success()).isFalse();
    }

    @Test
    void shouldStoreIndividualScoresInMetadata() {
        JudgeLM mockJudge = new MockJudge()
                .withScoreResponse(0, """
                        {"score": 0.8, "reason": "Relevant context"}
                        """)
                .withScoreResponse(1, """
                        {"score": 0.3, "reason": "Partially relevant"}
                        """)
                .withSummary("Mixed relevance.");

        var evaluator = ContextualRelevanceEvaluator.builder()
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("test query")
                .actualOutput("retrievalContext", List.of("context1", "context2"))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.metadata()).containsKey("contextScores");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contextScores =
                (List<Map<String, Object>>) result.metadata().get("contextScores");

        assertThat(contextScores).hasSize(2);
        assertThat(contextScores.get(0).get("score")).isEqualTo(0.8);
        assertThat(contextScores.get(0).get("reason")).isEqualTo("Relevant context");
        assertThat(contextScores.get(1).get("score")).isEqualTo(0.3);
    }

    @Test
    void shouldDisableReasoningWhenConfigured() {
        JudgeLM mockJudge = new MockJudge()
                .withScoreResponse(0, """
                        {"score": 0.9, "reason": "Relevant"}
                        """);

        var evaluator = ContextualRelevanceEvaluator.builder()
                .judge(mockJudge)
                .includeReason(false)
                .build();

        var testCase = EvalTestCase.builder()
                .input("test query")
                .actualOutput("retrievalContext", List.of("context"))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.reason()).isEqualTo("Reasoning was disabled");
    }

    @Test
    void shouldUseStrictModeWithThresholdOne() {
        JudgeLM mockJudge = new MockJudge()
                .withScoreResponse(0, """
                        {"score": 0.95, "reason": "Very relevant"}
                        """)
                .withSummary("High relevance.");

        var evaluator = ContextualRelevanceEvaluator.builder()
                .judge(mockJudge)
                .strictMode(true)
                .build();

        var testCase = EvalTestCase.builder()
                .input("test query")
                .actualOutput("retrievalContext", List.of("context"))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.threshold()).isEqualTo(1.0);
        assertThat(result.score()).isEqualTo(0.95);
        assertThat(result.success()).isFalse();
    }

    @Test
    void shouldPassInStrictModeWithPerfectScore() {
        JudgeLM mockJudge = new MockJudge()
                .withScoreResponse(0, """
                        {"score": 1.0, "reason": "Perfect relevance"}
                        """)
                .withSummary("Perfect relevance.");

        var evaluator = ContextualRelevanceEvaluator.builder()
                .judge(mockJudge)
                .strictMode(true)
                .build();

        var testCase = EvalTestCase.builder()
                .input("test query")
                .actualOutput("retrievalContext", List.of("context"))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.threshold()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldUseCustomRetrievalContextKey() {
        JudgeLM mockJudge = new MockJudge()
                .withScoreResponse(0, """
                        {"score": 0.85, "reason": "Relevant"}
                        """)
                .withSummary("Relevant context.");

        var evaluator = ContextualRelevanceEvaluator.builder()
                .judge(mockJudge)
                .retrievalContextKey("customContext")
                .build();

        var testCase = EvalTestCase.builder()
                .input("test query")
                .actualOutput("customContext", List.of("my custom context"))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.85);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldHandleSingleStringContext() {
        JudgeLM mockJudge = new MockJudge()
                .withScoreResponse(0, """
                        {"score": 0.75, "reason": "Mostly relevant"}
                        """)
                .withSummary("Single relevant context.");

        var evaluator = ContextualRelevanceEvaluator.builder()
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("test query")
                .actualOutput("retrievalContext", "single context as string")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.75);
    }

    @Test
    void shouldThrowExceptionWhenRetrievalContextMissing() {
        JudgeLM mockJudge = prompt -> "{}";

        var evaluator = ContextualRelevanceEvaluator.builder()
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("test query")
                .actualOutput("output")
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("retrievalContext");
    }

    @Test
    void shouldThrowExceptionWhenJudgeNotProvided() {
        assertThatThrownBy(() -> ContextualRelevanceEvaluator.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JudgeLM is required");
    }

    @Test
    void shouldHandleMalformedJsonResponse() {
        JudgeLM brokenJudge = prompt -> "This is not valid JSON";

        var evaluator = ContextualRelevanceEvaluator.builder()
                .judge(brokenJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("test query")
                .actualOutput("retrievalContext", List.of("context"))
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("Failed to parse relevance score response");
    }

    @Test
    void shouldHandleMarkdownWrappedJsonResponses() {
        JudgeLM judgeWithMarkdown = new MockJudge()
                .withScoreResponse(0, """
                        ```json
                        {"score": 0.85, "reason": "Relevant context"}
                        ```
                        """)
                .withSummary("Relevant context.");

        var evaluator = ContextualRelevanceEvaluator.builder()
                .judge(judgeWithMarkdown)
                .build();

        var testCase = EvalTestCase.builder()
                .input("test query")
                .actualOutput("retrievalContext", List.of("context"))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.85);
    }

    @Test
    void shouldClampScoresToValidRange() {
        // Test that scores outside 0-1 are clamped
        JudgeLM mockJudge = new MockJudge()
                .withScoreResponse(0, """
                        {"score": 1.5, "reason": "Over 1.0 should be clamped"}
                        """)
                .withScoreResponse(1, """
                        {"score": -0.5, "reason": "Negative should be clamped to 0"}
                        """)
                .withSummary("Scores clamped.");

        var evaluator = ContextualRelevanceEvaluator.builder()
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("test query")
                .actualOutput("retrievalContext", List.of("context1", "context2"))
                .build();

        var result = evaluator.evaluate(testCase);

        // (1.0 + 0.0) / 2 = 0.5 after clamping
        assertThat(result.score()).isCloseTo(0.5, within(0.001));
    }

    @Test
    void shouldReturnZeroScoreWithEmptyContextList() {
        JudgeLM mockJudge = prompt -> "{}";

        var evaluator = ContextualRelevanceEvaluator.builder()
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("test query")
                .actualOutput("retrievalContext", List.of())
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.success()).isFalse();
        assertThat(result.reason()).contains("No retrieval context");
    }

    @Test
    void shouldEvaluateAsynchronously() throws Exception {
        JudgeLM mockJudge = new MockJudge()
                .withScoreResponse(0, """
                        {"score": 0.9, "reason": "Relevant"}
                        """)
                .withSummary("Async test.");

        var evaluator = ContextualRelevanceEvaluator.builder()
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("test query")
                .actualOutput("retrievalContext", List.of("context"))
                .build();

        CompletableFuture<EvalResult> future = evaluator.evaluateAsync(testCase);

        EvalResult result = future.get(5, TimeUnit.SECONDS);

        assertThat(result.score()).isEqualTo(0.9);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldEvaluateAsyncWithCustomExecutor() throws Exception {
        JudgeLM mockJudge = new MockJudge()
                .withScoreResponse(0, """
                        {"score": 0.8, "reason": "Relevant"}
                        """)
                .withSummary("Custom executor test.");

        var evaluator = ContextualRelevanceEvaluator.builder()
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("test query")
                .actualOutput("retrievalContext", List.of("context"))
                .build();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<EvalResult> future = evaluator.evaluateAsync(testCase, executor);
            EvalResult result = future.get(5, TimeUnit.SECONDS);

            assertThat(result.score()).isEqualTo(0.8);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void shouldUseCustomThreshold() {
        JudgeLM mockJudge = new MockJudge()
                .withScoreResponse(0, """
                        {"score": 0.6, "reason": "Somewhat relevant"}
                        """)
                .withSummary("Custom threshold test.");

        var evaluator = ContextualRelevanceEvaluator.builder()
                .judge(mockJudge)
                .threshold(0.7)
                .build();

        var testCase = EvalTestCase.builder()
                .input("test query")
                .actualOutput("retrievalContext", List.of("context"))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.6);
        assertThat(result.threshold()).isEqualTo(0.7);
        assertThat(result.success()).isFalse();
    }

    @Test
    void shouldHandleScoreAsString() {
        JudgeLM mockJudge = new MockJudge()
                .withScoreResponse(0, """
                        {"score": "0.75", "reason": "Score as string"}
                        """)
                .withSummary("String score test.");

        var evaluator = ContextualRelevanceEvaluator.builder()
                .judge(mockJudge)
                .build();

        var testCase = EvalTestCase.builder()
                .input("test query")
                .actualOutput("retrievalContext", List.of("context"))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.75);
    }

    private static class MockJudge implements JudgeLM {
        private final Map<Integer, String> scoreResponses = new java.util.HashMap<>();
        private String summaryResponse = "Default summary.";
        private int callIndex = 0;

        public MockJudge withScoreResponse(int index, String response) {
            scoreResponses.put(index, response);
            return this;
        }

        public MockJudge withSummary(String response) {
            this.summaryResponse = response;
            return this;
        }

        @Override
        public String generate(String prompt) {
            if (prompt.contains("Evaluate how relevant")) {
                String response = scoreResponses.getOrDefault(callIndex, """
                        {"score": 0.5, "reason": "Default response"}
                        """);
                callIndex++;
                return response;
            }
            if (prompt.contains("Summarize the contextual relevance")) {
                return summaryResponse;
            }
            return "{}";
        }
    }
}
