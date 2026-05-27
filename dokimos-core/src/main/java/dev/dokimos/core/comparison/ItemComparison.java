package dev.dokimos.core.comparison;

import java.util.List;

/**
 * The comparison of a single paired item across baseline and candidate sides.
 * <p>
 * Pass-probability is the fraction of repetitions in which the item passed all of its
 * evaluations. For {@link ComparisonStatus#ADDED} items the baseline side is null, and for
 * {@link ComparisonStatus#REMOVED} items the candidate side is null.
 *
 * @param key                    the item-identity key used for pairing
 * @param status                 the classification of the item's overall pass-probability change
 * @param baselinePassProbability fraction of baseline reps that passed, or null when the item is ADDED
 * @param candidatePassProbability fraction of candidate reps that passed, or null when the item is REMOVED
 * @param passFlip               true when the item flipped between passing and failing (rounded probabilities)
 * @param evaluatorDeltas        per-evaluator deltas for this item
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
