package dev.dokimos.core.comparison;

/**
 * Classification of how a metric or item changed between a baseline and a candidate.
 */
public enum ComparisonStatus {
    /** The candidate is meaningfully and significantly better than the baseline. */
    IMPROVED,
    /** The candidate is meaningfully and significantly worse than the baseline. */
    REGRESSED,
    /** No meaningful or significant change. */
    UNCHANGED,
    /** The item exists only in the candidate. */
    ADDED,
    /** The item exists only in the baseline. */
    REMOVED
}
