package dev.dokimos.server.dto.v1;

import java.util.List;

/**
 * One row of the per-case run-diff table: a single item compared across baseline and candidate,
 * with its per-evaluator old-to-new deltas. Identified by its dataset item id when paired by id, or
 * by its positional index otherwise (exactly one of {@code datasetItemId} / {@code index} carries
 * the meaningful identity, but both are populated from the comparison key for convenience).
 *
 * @param datasetItemId the dataset item id, or null for positionally paired items
 * @param index         the comparison key (dataset item id or positional key such as {@code item-3})
 * @param status        REGRESSED, IMPROVED, UNCHANGED, ADDED, or REMOVED
 * @param passFlip      true when the item flipped pass/fail between the two runs
 * @param input         the item's input text, taken from the candidate run (or baseline for REMOVED)
 * @param evaluators    per-evaluator deltas for this item
 */
public record DiffCase(
        String datasetItemId,
        String index,
        String status,
        boolean passFlip,
        String input,
        List<EvaluatorDiff> evaluators) {

    public DiffCase {
        evaluators = evaluators != null ? List.copyOf(evaluators) : List.of();
    }

    /**
     * A single evaluator's change on one item between baseline and candidate.
     *
     * @param name          the evaluator name
     * @param baselineMean  baseline mean score on this item, or null when absent there
     * @param candidateMean candidate mean score on this item, or null when absent there
     * @param delta         candidateMean minus baselineMean, or 0.0 when either side is missing
     * @param status        IMPROVED, REGRESSED, or UNCHANGED for this evaluator on this item
     * @param significant   whether this evaluator's change is statistically significant
     */
    public record EvaluatorDiff(
            String name, Double baselineMean, Double candidateMean, double delta, String status, boolean significant) {}
}
