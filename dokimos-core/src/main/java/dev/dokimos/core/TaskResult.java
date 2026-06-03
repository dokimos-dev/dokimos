package dev.dokimos.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The outcome of executing a {@link MeasuredTask}: the actual outputs and optional metrics
 * describing the underlying LLM call.
 *
 * @param outputs the outputs the task produced, never null (empty map if absent)
 * @param metrics optional metrics for the underlying LLM call, or null if not measured
 */
public record TaskResult(Map<String, Object> outputs, CallMetrics metrics) {
    public TaskResult {
        // Tolerate null values in the task outputs (Map.copyOf rejects them).
        outputs = outputs != null ? Collections.unmodifiableMap(new HashMap<>(outputs)) : Map.of();
    }

    /**
     * Creates a result without call metrics.
     *
     * @param outputs the outputs the task produced
     * @return a task result with null metrics
     */
    public static TaskResult of(Map<String, Object> outputs) {
        return new TaskResult(outputs, null);
    }
}
