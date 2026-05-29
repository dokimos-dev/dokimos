package dev.dokimos.server.dto.v1;

import java.util.List;
import java.util.UUID;

/**
 * Verdict of a CI regression gate. {@code passed} is the single boolean CI should branch on: a build
 * fails only when {@code status == FAIL}. When no baseline can be resolved the status is
 * {@code NO_BASELINE} and {@code passed} is true (a first run cannot regress).
 *
 * <p>The shape is intentionally flat and JSON-friendly so a GitHub Action can render a PR comment
 * without walking nested objects. {@code regressedEvaluators} lists every significantly regressed
 * evaluator; {@code cases} lists up to 50 regressed items with their per-evaluator score drops. The
 * {@code cases} list is capped, so {@code regressedCount} is the authoritative total and
 * {@code casesTruncated} signals when the true count exceeds the returned list size.
 *
 * @param status               PASS, FAIL, or NO_BASELINE
 * @param passed               true when the gate allows the build to proceed
 * @param candidateRunId       the gated run
 * @param baselineRunId        the resolved baseline run, or null when NO_BASELINE
 * @param pairing              how items were paired: {@code dataset_item_id}, {@code positional}, or
 *                             {@code none} (NO_BASELINE)
 * @param baselinePassRate     baseline overall pass rate, or null when NO_BASELINE
 * @param candidatePassRate    candidate overall pass rate
 * @param passRateDelta        candidate minus baseline pass rate, or null when NO_BASELINE
 * @param significant          whether the pass-rate change is statistically significant
 * @param improvedCount        count of items significantly improved
 * @param regressedCount       count of items significantly regressed (authoritative total)
 * @param unchangedCount       count of items with no significant change
 * @param addedCount           count of items present only in the candidate
 * @param removedCount         count of items present only in the baseline
 * @param regressedEvaluators  evaluators flagged as significant regressions
 * @param cases                up to 50 regressed items, for the PR comment (capped)
 * @param casesTruncated       true when {@code regressedCount} exceeds the size of {@code cases}
 */
public record GateResult(
        String status,
        boolean passed,
        UUID candidateRunId,
        UUID baselineRunId,
        String pairing,
        Double baselinePassRate,
        double candidatePassRate,
        Double passRateDelta,
        boolean significant,
        int improvedCount,
        int regressedCount,
        int unchangedCount,
        int addedCount,
        int removedCount,
        List<RegressedEvaluator> regressedEvaluators,
        List<RegressedCase> cases,
        boolean casesTruncated) {

    public GateResult {
        regressedEvaluators = regressedEvaluators != null ? List.copyOf(regressedEvaluators) : List.of();
        cases = cases != null ? List.copyOf(cases) : List.of();
    }

    /**
     * A single evaluator's regression between baseline and candidate.
     *
     * @param evaluator     the evaluator name
     * @param baselineMean  mean score on the baseline side, or null when absent there
     * @param candidateMean mean score on the candidate side, or null when absent there
     * @param delta         candidateMean minus baselineMean
     * @param pValue        significance test p-value
     */
    public record RegressedEvaluator(
            String evaluator, Double baselineMean, Double candidateMean, double delta, double pValue) {}

    /**
     * A single regressed item, identified by its dataset item id when paired by id or by its
     * positional index otherwise.
     *
     * @param datasetItemId the dataset item id, or null for positionally paired items
     * @param index         the comparison key (dataset item id or positional key)
     * @param evaluatorDrops the per-evaluator score drops on this item
     */
    public record RegressedCase(String datasetItemId, String index, List<EvaluatorDrop> evaluatorDrops) {

        public RegressedCase {
            evaluatorDrops = evaluatorDrops != null ? List.copyOf(evaluatorDrops) : List.of();
        }
    }

    /**
     * A per-item evaluator score drop.
     *
     * @param evaluator     the evaluator name
     * @param baselineMean  baseline mean on this item, or null when absent
     * @param candidateMean candidate mean on this item, or null when absent
     * @param delta         candidateMean minus baselineMean
     */
    public record EvaluatorDrop(String evaluator, Double baselineMean, Double candidateMean, double delta) {}
}
