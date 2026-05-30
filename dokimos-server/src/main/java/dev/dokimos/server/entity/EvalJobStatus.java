package dev.dokimos.server.entity;

/** Lifecycle state of an {@link EvalJob}. Stored as a string in the {@code eval_jobs.status} column. */
public enum EvalJobStatus {
    PENDING,
    CLAIMED,
    SUCCEEDED,
    FAILED
}
