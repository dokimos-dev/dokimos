package dev.dokimos.core.comparison;

import java.util.List;

/**
 * Comparison of a single paired item across baseline and candidate. Pass-probability is the
 * fraction of repetitions in which the item passed all of its evaluations. For
 * {@link ComparisonStatus#ADDED} items the baseline side is null; for
 * {@link ComparisonStatus#REMOVED} items the candidate side is null.
 *
 * @param baselinePassProbability  fraction of baseline reps that passed, or null when ADDED
 * @param candidatePassProbability fraction of candidate reps that passed, or null when REMOVED
 * @param passFlip                 true when the item flipped pass/fail (rounded probabilities)
 */
public record ItemComparison(
        String key,
        ComparisonStatus status,
        Double baselinePassProbability,
        Double candidatePassProbability,
        boolean passFlip,
        List<EvaluatorDelta> evaluatorDeltas) {

    public ItemComparison {
        evaluatorDeltas = evaluatorDeltas != null ? List.copyOf(evaluatorDeltas) : List.of();
    }
}
