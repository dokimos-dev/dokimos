package dev.dokimos.core;

/**
 * A task that produces outputs together with optional {@link CallMetrics} describing the
 * underlying LLM call. The returned {@link TaskResult} carries the metrics through to the
 * resulting {@link ItemResult}.
 */
@FunctionalInterface
public interface MeasuredTask {

    /**
     * Run the task on the given {@link Example} and produce a result with optional metrics.
     *
     * @param example the example containing inputs and expected outputs
     * @return the task result produced by the task
     */
    TaskResult run(Example example);
}
