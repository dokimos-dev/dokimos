package dev.dokimos.core;

/**
 * Status of an experiment run.
 */
public enum RunStatus {
    /**
     * The run completed successfully.
     */
    SUCCESS,

    /**
     * The run failed due to an error.
     */
    FAILED,

    /**
     * The run was cancelled before completion.
     */
    CANCELLED
}
