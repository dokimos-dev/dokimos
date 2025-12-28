package dev.dokimos.core;

/**
 * A handle representing an active experiment run.
 * <p>
 * This is returned by {@link Reporter#startRun} and used to correlate
 * subsequent calls to {@link Reporter#reportItem} and {@link Reporter#completeRun}.
 *
 * @param runId the unique identifier for this run
 */
public record RunHandle(String runId) {

    /**
     * Creates a new run handle.
     *
     * @param runId the unique identifier for this run
     * @throws IllegalArgumentException if runId is null or blank
     */
    public RunHandle {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be null or blank");
        }
    }
}
