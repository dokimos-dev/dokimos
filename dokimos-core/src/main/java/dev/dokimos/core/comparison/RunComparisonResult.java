package dev.dokimos.core.comparison;

import java.util.List;

/**
 * The full result of comparing a baseline set of runs against a candidate set of runs.
 * <p>
 * The top-line {@code baselinePassRate} and {@code candidatePassRate} each cover that side's full
 * item set (paired plus unpaired), so they are well-defined regardless of pairing. The pass-rate
 * significance test in {@code passRateSignificance} covers only the shared (paired) items, since
 * only shared cases can be pair-tested.
 * <p>
 * The overall pass rate is a first-class regression signal. {@code passRateRegressed} is true when
 * {@code passRateDelta} is below {@code -epsilon} and {@code passRateSignificance} is significant;
 * {@code passRateImproved} is the symmetric verdict. {@link #hasRegressions()} is true when the pass
 * rate is significantly regressed or any evaluator is significantly regressed.
 * <p>
 * {@code regressedCount} and {@code improvedCount} are significance-gated counts of shared items. An
 * item counts as regressed when an evaluator that the overall comparison flagged as a significant
 * regression dropped beyond epsilon on that item, or when the overall pass rate is significantly
 * regressed and the item flipped from passing to failing; improvements are gated symmetrically (and
 * an item with any regression signal never counts as improved). This is distinct from
 * {@link #regressions()} and {@link #improvements()}, which operate at the per-evaluator level across
 * all paired items. The counts stay consistent with {@link #hasRegressions()}: when it is false there
 * is neither a significant pass-rate regression nor any overall-significant regressed evaluator, so no
 * item can be gated REGRESSED and {@code regressedCount} is 0.
 *
 * @param baselinePassRate   overall pass rate on the baseline side (mean item pass-probability over all baseline items)
 * @param candidatePassRate  overall pass rate on the candidate side (mean item pass-probability over all candidate items)
 * @param passRateDelta      candidatePassRate minus baselinePassRate
 * @param passRateSignificance significance of the overall pass-rate change, computed on shared items only
 * @param passRateRegressed  true when the overall pass rate dropped beyond epsilon and significantly
 * @param passRateImproved   true when the overall pass rate rose beyond epsilon and significantly
 * @param improvedCount      number of shared items significance-gated as IMPROVED
 * @param regressedCount     number of shared items significance-gated as REGRESSED
 * @param unchangedCount     number of paired items classified UNCHANGED
 * @param addedCount         number of items present only in the candidate
 * @param removedCount       number of items present only in the baseline
 * @param significantImprovedCount  number of overall evaluator deltas that are significant improvements
 * @param significantRegressedCount number of overall evaluator deltas that are significant regressions
 * @param evaluatorDeltas    overall per-evaluator deltas across all paired items
 * @param items              per-item comparisons (including ADDED and REMOVED)
 */
public record RunComparisonResult(
        double baselinePassRate,
        double candidatePassRate,
        double passRateDelta,
        SignificanceResult passRateSignificance,
        boolean passRateRegressed,
        boolean passRateImproved,
        int improvedCount,
        int regressedCount,
        int unchangedCount,
        int addedCount,
        int removedCount,
        int significantImprovedCount,
        int significantRegressedCount,
        List<EvaluatorDelta> evaluatorDeltas,
        List<ItemComparison> items) {

    public RunComparisonResult {
        evaluatorDeltas = evaluatorDeltas != null ? List.copyOf(evaluatorDeltas) : List.of();
        items = items != null ? List.copyOf(items) : List.of();
    }

    /**
     * Returns the overall evaluator deltas that are significant regressions.
     *
     * @return the significant regressions (possibly empty)
     */
    public List<EvaluatorDelta> regressions() {
        return evaluatorDeltas.stream()
                .filter(d -> d.status() == ComparisonStatus.REGRESSED
                        && d.significance() != null
                        && d.significance().significant())
                .toList();
    }

    /**
     * Returns the overall evaluator deltas that are significant improvements.
     *
     * @return the significant improvements (possibly empty)
     */
    public List<EvaluatorDelta> improvements() {
        return evaluatorDeltas.stream()
                .filter(d -> d.status() == ComparisonStatus.IMPROVED
                        && d.significance() != null
                        && d.significance().significant())
                .toList();
    }

    /**
     * Returns whether any significant regression was detected, considering both the overall pass rate
     * and the per-evaluator deltas.
     *
     * @return true when the pass rate is significantly regressed or at least one evaluator is
     *     significantly regressed
     */
    public boolean hasRegressions() {
        return passRateRegressed || !regressions().isEmpty();
    }
}
