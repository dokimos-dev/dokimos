package dev.dokimos.core.comparison;

import java.util.List;

/**
 * Result of comparing a baseline set of runs against a candidate set. Side pass rates cover each
 * side's full item set; significance is computed on the paired (shared) items. Item-level counts
 * are significance-gated; {@link #hasRegressions()} is the canonical gate predicate.
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

    /** Evaluator deltas flagged as significant regressions. */
    public List<EvaluatorDelta> regressions() {
        return evaluatorDeltas.stream()
                .filter(d -> d.status() == ComparisonStatus.REGRESSED
                        && d.significance() != null
                        && d.significance().significant())
                .toList();
    }

    /** Evaluator deltas flagged as significant improvements. */
    public List<EvaluatorDelta> improvements() {
        return evaluatorDeltas.stream()
                .filter(d -> d.status() == ComparisonStatus.IMPROVED
                        && d.significance() != null
                        && d.significance().significant())
                .toList();
    }

    /** True when the overall pass rate or any evaluator is significantly regressed. */
    public boolean hasRegressions() {
        return passRateRegressed || !regressions().isEmpty();
    }
}
