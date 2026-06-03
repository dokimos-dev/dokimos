package dev.dokimos.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The outcome of executing a single example: the example itself, the actual outputs the task
 * produced, the evaluator results, and optional metrics describing the underlying LLM call.
 *
 * @param example the example that was executed
 * @param actualOutputs the outputs the task produced, never null (empty map if absent)
 * @param evalResults the evaluator results, never null (empty list if absent)
 * @param metrics optional metrics for the underlying LLM call, or null if not measured
 */
public record ItemResult(
        Example example, Map<String, Object> actualOutputs, List<EvalResult> evalResults, CallMetrics metrics) {
    public ItemResult {
        // Tolerate null values in the task outputs (Map.copyOf rejects them).
        actualOutputs = actualOutputs != null ? Collections.unmodifiableMap(new HashMap<>(actualOutputs)) : Map.of();
        evalResults = evalResults != null ? List.copyOf(evalResults) : List.of();
    }

    /**
     * Creates a result without call metrics.
     *
     * @param example the example that was executed
     * @param actualOutputs the outputs the task produced
     * @param evalResults the evaluator results
     */
    public ItemResult(Example example, Map<String, Object> actualOutputs, List<EvalResult> evalResults) {
        this(example, actualOutputs, evalResults, null);
    }

    public boolean success() {
        return !evalResults.isEmpty() && evalResults.stream().allMatch(EvalResult::success);
    }

    public EvalTestCase toTestCase() {
        return example.toTestCase(actualOutputs);
    }
}
