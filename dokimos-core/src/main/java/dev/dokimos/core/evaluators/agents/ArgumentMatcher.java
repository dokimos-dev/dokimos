package dev.dokimos.core.evaluators.agents;

import java.util.Map;

/**
 * Strategy for comparing a tool call's actual arguments against the expected arguments.
 * <p>
 * Tool-call evaluators ({@link ToolTrajectoryEvaluator}, {@link ToolCorrectnessEvaluator},
 * {@link ToolEfficiencyEvaluator}) delegate argument comparison to an
 * {@code ArgumentMatcher} so the matching policy is decoupled from the evaluation
 * logic. The default implementation, {@link TolerantArgumentMatcher}, treats
 * numerically equal values such as {@code 1}, {@code 1.0} and {@code 1L} as equal,
 * which avoids spurious mismatches from JSON number widening.
 *
 * @see TolerantArgumentMatcher
 * @see ArgMatchMode
 */
@FunctionalInterface
public interface ArgumentMatcher {

    /**
     * Determines whether the actual arguments match the expected arguments.
     *
     * @param expected the expected arguments
     * @param actual   the actual arguments produced by the agent
     * @return {@code true} if the arguments match under this matcher's policy
     */
    boolean matches(Map<String, Object> expected, Map<String, Object> actual);

    /**
     * Returns the default tolerant matcher using {@link ArgMatchMode#EXACT}.
     * <p>
     * Numerically equal values are treated as equal; string comparison is exact.
     *
     * @return a tolerant exact-match argument matcher
     */
    static ArgumentMatcher tolerant() {
        return TolerantArgumentMatcher.builder().build();
    }

    /**
     * Returns a tolerant matcher using the given key-set mode.
     *
     * @param mode the key-set comparison mode
     * @return a tolerant argument matcher for the given mode
     */
    static ArgumentMatcher of(ArgMatchMode mode) {
        return TolerantArgumentMatcher.builder().mode(mode).build();
    }
}
