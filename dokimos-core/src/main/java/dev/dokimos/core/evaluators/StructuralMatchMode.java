package dev.dokimos.core.evaluators;

/**
 * Controls how {@link StructuralMatchEvaluator} compares an expected JSON structure against an actual
 * one.
 *
 * <p>Both modes are numeric-value-aware: a number compares by value, not by representation, so {@code
 * 5} and {@code 5.0} (and {@code 1.0} versus {@code 1.00}) always match in either mode. The modes
 * differ only on how they treat object field sets and array ordering.
 */
public enum StructuralMatchMode {

    /**
     * Strict comparison (the eval-safe default).
     *
     * <ul>
     *   <li>Objects must have the exact same set of fields — an extra field in the actual output is a
     *       mismatch and penalizes the score.
     *   <li>Arrays must match element-for-element in the same order.
     *   <li>A {@code null} value and a missing field are distinct.
     * </ul>
     */
    STRICT,

    /**
     * Lenient comparison.
     *
     * <ul>
     *   <li>Extra fields in the actual output are ignored (the actual may be a superset of the
     *       expected object).
     *   <li>Array order is ignored, but multiplicity is not — arrays are compared as multisets, so
     *       {@code [1, 1, 2]} does not match {@code [1, 2]}.
     *   <li>A {@code null} value and a missing field are treated as equal.
     * </ul>
     */
    LENIENT
}
