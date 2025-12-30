package dev.dokimos.core.evaluators;

import dev.dokimos.core.BaseEvaluator;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.MatchingStrategy;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Evaluator that measures retrieval recall.
 * <p>
 * Recall is the fraction of relevant items that were retrieved:
 * 
 * <pre>
 * recall = |relevant ∩ retrieved| / |relevant|
 * </pre>
 * <p>
 * A recall of 1.0 means all relevant items were retrieved (no false negatives).
 * A recall of 0.0 means none of the relevant items were retrieved.
 * <p>
 * This evaluator supports various RAG use cases beyond document retrieval,
 * including knowledge graph triples, API responses, and semantic matching.
 * <p>
 * Example usage:
 * 
 * <pre>{@code
 * var evaluator = RecallEvaluator.builder()
 *         .name("retrieval-recall")
 *         .retrievedKey("retrievedDocs")
 *         .expectedKey("relevantDocs")
 *         .matchingStrategy(MatchingStrategy.byEquality())
 *         .threshold(0.8)
 *         .build();
 *
 * var testCase = EvalTestCase.builder()
 *         .input("What causes diabetes?")
 *         .actualOutput("retrievedDocs", List.of("doc_1", "doc_2", "doc_3"))
 *         .expectedOutput("relevantDocs", List.of("doc_1", "doc_3", "doc_5"))
 *         .build();
 *
 * EvalResult result = evaluator.evaluate(testCase);
 * // recall = 2/3 = 0.667 (doc_1 and doc_3 were found, doc_5 was missed)
 * }</pre>
 */
public class RecallEvaluator extends BaseEvaluator {

    private final String retrievedKey;
    private final String expectedKey;
    private final MatchingStrategy matchingStrategy;

    private RecallEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        this.retrievedKey = builder.retrievedKey;
        this.expectedKey = builder.expectedKey;
        this.matchingStrategy = builder.matchingStrategy;
    }

    /**
     * Creates a new builder for constructing the recall evaluator.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        Collection<?> retrieved = extractCollection(testCase.actualOutputs(), retrievedKey, "actualOutputs");
        Collection<?> expected = extractCollection(testCase.expectedOutputs(), expectedKey, "expectedOutputs");

        if (expected.isEmpty()) {
            // No relevant items and recall is undefined, but we return 1.0
            // (all zero relevant items were "found")
            return EvalResult.builder()
                    .name(name)
                    .score(1.0)
                    .threshold(threshold)
                    .reason("No relevant items in ground truth. Recall is 1.0 by convention.")
                    .metadata(Map.of(
                            "retrieved", retrieved.size(),
                            "relevant", 0,
                            "truePositives", 0))
                    .build();
        }

        long truePositives = matchingStrategy.countMatches(retrieved, expected);
        double recall = (double) truePositives / expected.size();

        String reason = generateReason(truePositives, retrieved.size(), expected.size(), recall);

        return EvalResult.builder()
                .name(name)
                .score(recall)
                .threshold(threshold)
                .reason(reason)
                .metadata(Map.of(
                        "retrieved", retrieved.size(),
                        "relevant", expected.size(),
                        "truePositives", truePositives))
                .build();
    }

    private Collection<?> extractCollection(Map<String, Object> source, String key, String sourceName) {
        Object value = source.get(key);
        if (value == null) {
            throw new EvaluationException(
                    "Recall evaluator requires '%s' in %s".formatted(key, sourceName));
        }
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        // Single item, treat it as collection of one
        return List.of(value);
    }

    private String generateReason(long truePositives, int retrieved, int relevant, double recall) {
        if (recall == 1.0) {
            return "All %d relevant items were retrieved (perfect recall).".formatted(relevant);
        }
        if (recall == 0.0) {
            return "None of the %d relevant items were retrieved.".formatted(relevant);
        }
        long missed = relevant - truePositives;
        return "Found %d of %d relevant items (%d missed). Recall: %.1f%%"
                .formatted(truePositives, relevant, missed, recall * 100);
    }

    /**
     * Builder for constructing RecallEvaluator instances.
     */
    public static class Builder {
        private String name = "Recall";
        private String retrievedKey = "retrieved";
        private String expectedKey = "relevant";
        private double threshold = 0.5;
        private MatchingStrategy matchingStrategy = MatchingStrategy.byEquality();
        private List<EvalTestCaseParam> evaluationParams = List.of(EvalTestCaseParam.INPUT);

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
         * Sets the key for retrieved items in actualOutputs.
         * Default is "retrieved".
         *
         * @param retrievedKey the key for retrieved items
         * @return this builder
         */
        public Builder retrievedKey(String retrievedKey) {
            this.retrievedKey = retrievedKey;
            return this;
        }

        /**
         * Sets the key for expected (relevant) items in expectedOutputs.
         * Default is "relevant".
         *
         * @param expectedKey the key for expected items
         * @return this builder
         */
        public Builder expectedKey(String expectedKey) {
            this.expectedKey = expectedKey;
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
         * Sets the strategy for matching retrieved items to expected items.
         * Default is {@link MatchingStrategy#byEquality()}.
         *
         * @param matchingStrategy the matching strategy
         * @return this builder
         */
        public Builder matchingStrategy(MatchingStrategy matchingStrategy) {
            this.matchingStrategy = matchingStrategy;
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
         * Builds the RecallEvaluator.
         *
         * @return a new RecallEvaluator instance
         */
        public RecallEvaluator build() {
            return new RecallEvaluator(this);
        }
    }
}
