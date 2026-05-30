package dev.dokimos.core;

import java.util.List;
import java.util.Map;

/**
 * The outcome of executing a single example: the example itself, the actual outputs the task
 * produced, the evaluator results, and optional metrics describing the underlying LLM call.
 *
 * @param example the example that was executed
 * @param actualOutputs the outputs the task produced, never null (empty map if absent)
 * @param evalResults the evaluator results, never null (empty list if absent)
 * @param tokensIn prompt tokens consumed by the call, or null if not measured
 * @param tokensOut completion tokens produced by the call, or null if not measured
 * @param costUsd cost of the call in US dollars, or null if not measured
 * @param latencyMs wall-clock latency of the call in milliseconds, or null if not measured
 */
public record ItemResult(
        Example example,
        Map<String, Object> actualOutputs,
        List<EvalResult> evalResults,
        Integer tokensIn,
        Integer tokensOut,
        Double costUsd,
        Long latencyMs) {
    public ItemResult {
        actualOutputs = actualOutputs != null ? Map.copyOf(actualOutputs) : Map.of();
        evalResults = evalResults != null ? List.copyOf(evalResults) : List.of();
    }

    /**
     * Creates a result without call metrics. The metric fields default to null.
     *
     * @param example the example that was executed
     * @param actualOutputs the outputs the task produced
     * @param evalResults the evaluator results
     */
    public ItemResult(Example example, Map<String, Object> actualOutputs, List<EvalResult> evalResults) {
        this(example, actualOutputs, evalResults, null, null, null, null);
    }

    public boolean success() {
        return evalResults.stream().allMatch(EvalResult::success);
    }

    public EvalTestCase toTestCase() {
        return example.toTestCase(actualOutputs);
    }

    /**
     * Returns a builder for assembling an {@link ItemResult} with optional call metrics, so callers
     * can set only the fields they have without a long positional argument list.
     *
     * @param example the example that was executed
     * @param actualOutputs the outputs the task produced
     * @param evalResults the evaluator results
     * @return a new builder seeded with the required fields
     */
    public static Builder builder(Example example, Map<String, Object> actualOutputs, List<EvalResult> evalResults) {
        return new Builder(example, actualOutputs, evalResults);
    }

    /** Builder for {@link ItemResult} that allows the call metrics to be set selectively. */
    public static final class Builder {
        private final Example example;
        private final Map<String, Object> actualOutputs;
        private final List<EvalResult> evalResults;
        private Integer tokensIn;
        private Integer tokensOut;
        private Double costUsd;
        private Long latencyMs;

        private Builder(Example example, Map<String, Object> actualOutputs, List<EvalResult> evalResults) {
            this.example = example;
            this.actualOutputs = actualOutputs;
            this.evalResults = evalResults;
        }

        /**
         * Sets the prompt tokens consumed by the call.
         *
         * @param tokensIn prompt token count, or null if unknown
         * @return this builder
         */
        public Builder tokensIn(Integer tokensIn) {
            this.tokensIn = tokensIn;
            return this;
        }

        /**
         * Sets the completion tokens produced by the call.
         *
         * @param tokensOut completion token count, or null if unknown
         * @return this builder
         */
        public Builder tokensOut(Integer tokensOut) {
            this.tokensOut = tokensOut;
            return this;
        }

        /**
         * Sets the cost of the call in US dollars.
         *
         * @param costUsd cost in USD, or null if unknown
         * @return this builder
         */
        public Builder costUsd(Double costUsd) {
            this.costUsd = costUsd;
            return this;
        }

        /**
         * Sets the wall-clock latency of the call in milliseconds.
         *
         * @param latencyMs latency in milliseconds, or null if unknown
         * @return this builder
         */
        public Builder latencyMs(Long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        /**
         * Builds the immutable {@link ItemResult}.
         *
         * @return the assembled result
         */
        public ItemResult build() {
            return new ItemResult(example, actualOutputs, evalResults, tokensIn, tokensOut, costUsd, latencyMs);
        }
    }
}
