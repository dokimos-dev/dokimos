package dev.dokimos.server.dto.v1;

import java.util.List;

/**
 * Per-run, per-evaluator agreement between automated evaluator verdicts and human annotations. For
 * each evaluator the agreement rate is the fraction of comparable items where the evaluator's
 * pass/fail matched the human verdict (CORRECT treated as pass, INCORRECT as fail). Items with an
 * UNSURE verdict or no annotation are excluded from the rate and reported separately.
 *
 * @param annotatedItems the number of run items carrying any human verdict (including UNSURE)
 * @param evaluators     per-evaluator agreement breakdown, one entry per distinct evaluator name
 */
public record AlignmentView(int annotatedItems, List<EvaluatorAlignment> evaluators) {

    public AlignmentView {
        evaluators = List.copyOf(evaluators);
    }

    /**
     * Agreement breakdown for a single evaluator across a run's annotated items.
     *
     * @param evaluatorName  the evaluator the breakdown is for
     * @param comparableCount items where the evaluator ran and the human verdict was CORRECT or
     *     INCORRECT; the denominator of {@code alignmentRate}
     * @param agreedCount    comparable items where the evaluator's pass/fail matched the human verdict
     * @param excludedUnsure items the evaluator ran on whose human verdict was UNSURE
     * @param alignmentRate  {@code agreedCount / comparableCount}, or null when {@code comparableCount}
     *     is zero
     */
    public record EvaluatorAlignment(
            String evaluatorName, int comparableCount, int agreedCount, int excludedUnsure, Double alignmentRate) {}
}
