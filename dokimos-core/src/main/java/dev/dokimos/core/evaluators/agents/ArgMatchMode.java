package dev.dokimos.core.evaluators.agents;

/**
 * Controls how a tool call's arguments are compared against the expected arguments.
 * <p>
 * Key-set semantics are defined relative to the expected arguments:
 * <ul>
 *   <li>{@link #EXACT} — the actual arguments must have the same keys as expected,
 *       and every value must match.</li>
 *   <li>{@link #SUBSET} — every expected entry must be present and matching in the
 *       actual arguments; the actual arguments may contain additional keys. Use this
 *       when you only care that specific arguments are correct.</li>
 *   <li>{@link #SUPERSET} — every actual entry must be present and matching in the
 *       expected arguments; the actual arguments may omit some expected keys.</li>
 *   <li>{@link #IGNORE} — arguments are not compared at all (name/order only).</li>
 * </ul>
 */
public enum ArgMatchMode {
    /** Same key set as expected, all values match. */
    EXACT,
    /** Expected entries are a subset of the actual arguments (extras allowed). */
    SUBSET,
    /** Actual entries are a subset of the expected arguments (omissions allowed). */
    SUPERSET,
    /** Do not compare arguments. */
    IGNORE
}
