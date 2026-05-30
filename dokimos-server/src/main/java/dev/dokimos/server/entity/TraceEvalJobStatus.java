package dev.dokimos.server.entity;

/** Lifecycle state of a {@link TraceEvalJob}. Stored as a string in the {@code trace_eval_jobs.status} column. */
public enum TraceEvalJobStatus {
    PENDING,
    CLAIMED,
    SUCCEEDED,
    FAILED
}
