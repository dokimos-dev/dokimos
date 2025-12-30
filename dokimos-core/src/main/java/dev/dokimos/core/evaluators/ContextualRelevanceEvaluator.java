package dev.dokimos.core.evaluators;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.core.BaseEvaluator;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.LlmResponseUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluator that measures how relevant retrieved context chunks are to a user's input query.
 * <p>
 * This evaluator uses pointwise relevance scoring, where each context chunk is scored
 * independently between 0.0 (completely irrelevant) and 1.0 (highly relevant).
 * The final score is the mean average of all individual chunk scores.
 * <p>
 * Example usage:
 * <pre>{@code
 * var evaluator = ContextualRelevanceEvaluator.builder()
 *     .name("contextual-relevance")
 *     .judge(llmClient)
 *     .threshold(0.5)
 *     .build();
 *
 * var testCase = EvalTestCase.builder()
 *     .input("What are symptoms of dehydration?")
 *     .actualOutput("retrievalContext", List.of(
 *         "Dehydration symptoms include thirst, dry mouth, and fatigue.",
 *         "The Pacific Ocean is the largest ocean on Earth.",
 *         "Severe dehydration can cause dizziness and confusion."
 *     ))
 *     .build();
 *
 * EvalResult result = evaluator.evaluate(testCase);
 * }</pre>
 */
public class ContextualRelevanceEvaluator extends BaseEvaluator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_RETRIEVAL_CONTEXT_KEY = "retrievalContext";

    private final String retrievalContextKey;
    private final JudgeLM judge;
    private final boolean includeReason;

    private ContextualRelevanceEvaluator(Builder builder) {
        super(builder.name, builder.effectiveThreshold(), builder.evaluationParams);
        this.retrievalContextKey = builder.retrievalContextKey;
        this.judge = builder.judge;
        this.includeReason = builder.includeReason;
    }

    /**
     * Creates a new builder for constructing the contextual relevance evaluator.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        String input = testCase.input();
        List<String> retrievalContext = extractRetrievalContext(testCase);

        if (retrievalContext.isEmpty()) {
            return EvalResult.builder()
                    .name(name)
                    .score(0.0)
                    .threshold(threshold)
                    .reason("No retrieval context provided to evaluate.")
                    .metadata(Map.of("contextScores", List.of()))
                    .build();
        }

        List<ContextScore> contextScores = scoreContextChunks(input, retrievalContext);
        double finalScore = calculateMeanScore(contextScores);
        String reason = includeReason
                ? generateSummaryReason(input, contextScores, finalScore)
                : "Reasoning was disabled";

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("contextScores", contextScores.stream()
                .map(cs -> Map.of(
                        "context", cs.context(),
                        "score", cs.score(),
                        "reason", cs.reason()
                ))
                .toList());

        return EvalResult.builder()
                .name(name)
                .score(finalScore)
                .threshold(threshold)
                .reason(reason)
                .metadata(metadata)
                .build();
    }

    private List<String> extractRetrievalContext(EvalTestCase testCase) {
        Object context = testCase.actualOutputs().get(retrievalContextKey);
        if (context == null) {
            throw new EvaluationException(
                    "ContextualRelevance requires '%s' in actualOutputs".formatted(retrievalContextKey)
            );
        }

        if (context instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .toList();
        } else if (context instanceof String str) {
            return List.of(str);
        } else {
            throw new EvaluationException(
                    "Expected '%s' to be a List<String> or String, but got: %s"
                            .formatted(retrievalContextKey, context.getClass().getSimpleName())
            );
        }
    }

    private List<ContextScore> scoreContextChunks(String input, List<String> contexts) {
        List<ContextScore> scores = new ArrayList<>();

        for (int i = 0; i < contexts.size(); i++) {
            String context = contexts.get(i);
            ContextScore score = scoreContext(input, context, i);
            scores.add(score);
        }

        return scores;
    }

    private ContextScore scoreContext(String input, String context, int index) {
        String prompt = """
                Evaluate how relevant the following CONTEXT is to answering the USER QUERY.

                USER QUERY: %s

                CONTEXT: %s

                Score the relevance from 0.0 to 1.0 where:
                - 1.0 = Highly relevant, directly addresses the query
                - 0.7-0.9 = Mostly relevant, contains useful information for the query
                - 0.4-0.6 = Partially relevant, some connection to the query
                - 0.1-0.3 = Minimally relevant, weak connection to the query
                - 0.0 = Completely irrelevant, no connection to the query

                Respond ONLY as a JSON object in the following format:
                Do not include any markdown formatting or extra text.

                {"score": <number between 0.0 and 1.0>, "reason": "<brief explanation>"}
                """.formatted(input, context);

        String response = LlmResponseUtils.stripMarkdown(judge.generate(prompt));

        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(
                    response, new TypeReference<Map<String, Object>>() {}
            );

            double score = parseScore(parsed.get("score"));
            String reason = parsed.getOrDefault("reason", "No reason provided").toString();

            return new ContextScore(context, score, reason);
        } catch (Exception e) {
            throw new EvaluationException(
                    "Failed to parse relevance score response for context chunk %d".formatted(index), e
            );
        }
    }

    private double parseScore(Object scoreObj) {
        if (scoreObj instanceof Number num) {
            double score = num.doubleValue();
            return Math.max(0.0, Math.min(1.0, score));
        }
        if (scoreObj instanceof String str) {
            try {
                double score = Double.parseDouble(str);
                return Math.max(0.0, Math.min(1.0, score));
            } catch (NumberFormatException e) {
                throw new EvaluationException("Invalid score format: " + str);
            }
        }
        throw new EvaluationException("Score must be a number, got: " + scoreObj);
    }

    private double calculateMeanScore(List<ContextScore> scores) {
        if (scores.isEmpty()) {
            return 0.0;
        }
        double sum = scores.stream()
                .mapToDouble(ContextScore::score)
                .sum();
        return sum / scores.size();
    }

    private String generateSummaryReason(String input, List<ContextScore> contextScores, double finalScore) {
        if (contextScores.isEmpty()) {
            return "No context chunks to evaluate.";
        }

        List<String> highlyRelevant = new ArrayList<>();
        List<String> partiallyRelevant = new ArrayList<>();
        List<String> irrelevant = new ArrayList<>();

        for (int i = 0; i < contextScores.size(); i++) {
            ContextScore cs = contextScores.get(i);
            String contextRef = "Context %d (score: %.2f)".formatted(i + 1, cs.score());

            if (cs.score() >= 0.7) {
                highlyRelevant.add(contextRef);
            } else if (cs.score() >= 0.3) {
                partiallyRelevant.add(contextRef);
            } else {
                irrelevant.add(contextRef);
            }
        }

        String prompt = """
                Summarize the contextual relevance evaluation into exactly one brief sentence.

                USER QUERY: %s
                FINAL SCORE: %.3f
                TOTAL CONTEXTS: %d

                HIGHLY RELEVANT: %s
                PARTIALLY RELEVANT: %s
                IRRELEVANT: %s

                INDIVIDUAL SCORES:
                %s

                One-sentence summary explaining the overall relevance of the retrieved contexts:
                """.formatted(
                input,
                finalScore,
                contextScores.size(),
                highlyRelevant.isEmpty() ? "None" : String.join(", ", highlyRelevant),
                partiallyRelevant.isEmpty() ? "None" : String.join(", ", partiallyRelevant),
                irrelevant.isEmpty() ? "None" : String.join(", ", irrelevant),
                formatContextScores(contextScores)
        );

        return judge.generate(prompt).trim();
    }

    private String formatContextScores(List<ContextScore> scores) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < scores.size(); i++) {
            ContextScore cs = scores.get(i);
            sb.append("Context %d: score=%.2f, reason=%s%n".formatted(i + 1, cs.score(), cs.reason()));
        }
        return sb.toString();
    }

    /**
     * Represents the relevance score for a single context chunk.
     *
     * @param context the context text
     * @param score   the relevance score (0.0 to 1.0)
     * @param reason  the explanation for the score
     */
    public record ContextScore(String context, double score, String reason) {}

    /**
     * Builder for constructing ContextualRelevanceEvaluator instances.
     */
    public static class Builder {
        private String name = "ContextualRelevance";
        private String retrievalContextKey = DEFAULT_RETRIEVAL_CONTEXT_KEY;
        private double threshold = 0.5;
        private boolean strictMode = false;
        private List<EvalTestCaseParam> evaluationParams = List.of(EvalTestCaseParam.INPUT);
        private JudgeLM judge;
        private boolean includeReason = true;

        /**
         * Sets the evaluator name.
         *
         * @param name the evaluator name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the key used to retrieve context chunks from actualOutputs.
         * Defaults to "retrievalContext".
         *
         * @param retrievalContextKey the key for retrieval context
         * @return this builder
         */
        public Builder retrievalContextKey(String retrievalContextKey) {
            this.retrievalContextKey = retrievalContextKey;
            return this;
        }

        /**
         * Sets the minimum score threshold for success.
         * Default is 0.5.
         *
         * @param threshold the threshold value (0.0 to 1.0)
         * @return this builder
         */
        public Builder threshold(double threshold) {
            this.threshold = threshold;
            return this;
        }

        /**
         * Enables or disables strict mode.
         * When enabled, the threshold is automatically set to 1.0.
         * Default is false.
         *
         * @param strictMode true to enable strict mode
         * @return this builder
         */
        public Builder strictMode(boolean strictMode) {
            this.strictMode = strictMode;
            return this;
        }

        /**
         * Sets which test case parameters to validate before evaluation.
         *
         * @param params the parameters to validate
         * @return this builder
         */
        public Builder evaluationParams(List<EvalTestCaseParam> params) {
            this.evaluationParams = List.copyOf(params);
            return this;
        }

        /**
         * Sets the LLM judge to use for scoring context relevance.
         *
         * @param judge the LLM judge implementation
         * @return this builder
         */
        public Builder judge(JudgeLM judge) {
            this.judge = judge;
            return this;
        }

        /**
         * Sets whether to include a detailed reason in the result.
         * Default is true.
         *
         * @param includeReason true to include reasoning
         * @return this builder
         */
        public Builder includeReason(boolean includeReason) {
            this.includeReason = includeReason;
            return this;
        }

        /**
         * Calculates the effective threshold based on strictMode.
         */
        private double effectiveThreshold() {
            return strictMode ? 1.0 : threshold;
        }

        /**
         * Builds the ContextualRelevanceEvaluator.
         *
         * @return a new ContextualRelevanceEvaluator instance
         * @throws IllegalStateException if judge is not set
         */
        public ContextualRelevanceEvaluator build() {
            if (judge == null) {
                throw new IllegalStateException("JudgeLM is required for ContextualRelevanceEvaluator");
            }
            return new ContextualRelevanceEvaluator(this);
        }
    }
}
