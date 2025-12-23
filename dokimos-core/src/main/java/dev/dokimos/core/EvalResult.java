package dev.dokimos.core;

import java.util.Map;

/**
 * The result of an evaluation.
 *
 * @param name     the evaluator name
 * @param score    the numeric score
 * @param success  whether the evaluation succeeded or not
 * @param reason   explanation for the returned score
 * @param metadata additional result metadata
 */
public record EvalResult(
        String name,
        double score,
        boolean success,
        String reason,
        Map<String, Object> metadata
) {
    public EvalResult {
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Creates a result by comparing the score against a threshold.
     *
     * @param name      the evaluator name
     * @param score     the numeric score
     * @param threshold the success threshold
     * @param reason    explanation for the score
     * @return a new result
     */
    public static EvalResult of(String name, double score, double threshold, String reason) {
        boolean success = score >= threshold;
        return new EvalResult(name, score, success, reason, Map.of());
    }

    /**
     * Creates a successful result.
     *
     * @param name   the evaluator name
     * @param score  the numeric score
     * @param reason explanation for the score
     * @return a new successful result
     */
    public static EvalResult success(String name, double score, String reason) {
        return new EvalResult(name, score, true, reason, Map.of());
    }

    /**
     * Creates a failed result.
     *
     * @param name   the evaluator name
     * @param score  the numeric score
     * @param reason explanation for the failure
     * @return a new failed result
     */
    public static EvalResult failure(String name, double score, String reason) {
        return new EvalResult(name, score, false, reason, Map.of());
    }

    /**
     * Creates a new builder for constructing results.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing evaluation results.
     */
    public static class Builder {
        private String name;
        private double score;
        private Double threshold;
        private String reason;
        private Map<String, Object> metadata;

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
         * Sets the numeric score.
         *
         * @param score the score value
         * @return this builder
         */
        public Builder score(double score) {
            this.score = score;
            return this;
        }

        /**
         * Sets the success threshold.
         *
         * @param threshold the threshold value
         * @return this builder
         */
        public Builder threshold(double threshold) {
            this.threshold = threshold;
            return this;
        }

        /**
         * Sets the reason or explanation.
         *
         * @param reason the reason text
         * @return this builder
         */
        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        /**
         * Sets the metadata.
         *
         * @param metadata the metadata map
         * @return this builder
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Builds the result.
         *
         * @return a new evaluation result
         */
        public EvalResult build() {
            boolean success = threshold != null ? score >= threshold : score >= 0.5;
            return new EvalResult(name, score, success, reason, metadata);
        }
    }
}