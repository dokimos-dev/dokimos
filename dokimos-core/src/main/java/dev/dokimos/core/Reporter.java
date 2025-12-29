package dev.dokimos.core;

import java.util.Map;

/**
 * Interface for reporting experiment results to an external system.
 * <p>
 * Implementations can report results to a server, file, or any other destination.
 * The experiment runner calls these methods at appropriate points during execution.
 * <p>
 * Implementations should be thread-safe if used concurrently.
 */
public interface Reporter extends AutoCloseable {

    /**
     * Starts a new experiment run.
     *
     * @param experimentName the name of the experiment
     * @param metadata       configuration metadata for the run
     * @return a handle identifying this run
     */
    RunHandle startRun(String experimentName, Map<String, Object> metadata);

    /**
     * Reports a single evaluated item.
     * <p>
     * This is called after each item completes evaluation, not batched at the end.
     *
     * @param handle the run handle from {@link #startRun}
     * @param result the evaluated item result
     */
    void reportItem(RunHandle handle, ItemResult result);

    /**
     * Marks a run as complete.
     *
     * @param handle the run handle from {@link #startRun}
     * @param status the final status of the run
     */
    void completeRun(RunHandle handle, RunStatus status);

    /**
     * Blocks until all pending items are sent.
     * <p>
     * This is important for async implementations to ensure all data is transmitted
     * before the experiment returns.
     */
    void flush();

    /**
     * Flushes pending items and releases resources.
     */
    @Override
    void close();
}
