package dev.dokimos.core.gate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.dokimos.core.internal.Json;
import java.util.List;
import java.util.Locale;

/**
 * The server-free regression-gate verdict: the server {@code GateResult} minus the two run UUIDs
 * (the PR-comment renderer never reads them), plus two additive fields the renderer ignores —
 * {@code warnings} (coverage-loss and threshold-drift notices) and {@code comparedPass} (true when a
 * real comparison ran, distinguishing it from a no-op {@link #noBaseline(double)}).
 *
 * <p>{@link #toJson()} is field-name compatible with the server result on the subset {@code
 * render-comment.sh} consumes, so the unmodified shell renders this verdict on GitHub. The per-case
 * key serializes under the property name {@code index} (the script reads {@code .index}), not {@code
 * key}. {@code candidatePassRate} is a primitive double so it is always present as a number — the
 * script multiplies it by 100 without a null guard.
 *
 * <p>Thresholds are advisory: pass/fail is the stored verdict, never recomputed here. A threshold
 * change between baseline and candidate produces a warning, never a gate failure.
 *
 * @param status one of {@code PASS}, {@code FAIL}, {@code NO_BASELINE}
 * @param passed true when the gate allows the build to proceed ({@code status != FAIL})
 * @param pairing {@code positional}, {@code dataset_item_id}, or {@code none} (NO_BASELINE)
 * @param baselinePassRate baseline overall pass rate, or null when NO_BASELINE
 * @param candidatePassRate candidate overall pass rate (always a number)
 * @param passRateDelta candidate minus baseline pass rate, or null when NO_BASELINE
 * @param significant whether the pass-rate change is statistically significant
 * @param improvedCount items significantly improved
 * @param regressedCount the displayed regressed-case count: the larger of the engine's broad-guard
 *     count and the deduped union of guard-1 and guard-2 cases, so the shell renderer (which reuses
 *     this field as both the metric value and the "Showing N of M" denominator) never reports 0
 *     above a populated case list
 * @param totalRegressedCases the deduped union total of regressed cases before the display cap;
 *     additive (the shell renderer ignores it) and used by {@link #toMarkdown()} as the truncation
 *     denominator so it is exact even for a guard-2-only break
 * @param unchangedCount items with no significant change
 * @param addedCount items present only in the candidate
 * @param removedCount items present only in the baseline
 * @param regressedEvaluators evaluators flagged as significant regressions
 * @param cases regressed items shown in the comment (capped at 50)
 * @param casesTruncated true when the deduped case total exceeded the cap
 * @param comparedPass true when a real comparison ran (false only for NO_BASELINE)
 * @param warnings additive coverage-loss / drift notices; the shell renderer ignores them
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GateVerdict(
        String status,
        boolean passed,
        String pairing,
        Double baselinePassRate,
        double candidatePassRate,
        Double passRateDelta,
        boolean significant,
        int improvedCount,
        int regressedCount,
        int totalRegressedCases,
        int unchangedCount,
        int addedCount,
        int removedCount,
        List<RegressedEvaluator> regressedEvaluators,
        List<RegressedCase> cases,
        boolean casesTruncated,
        boolean comparedPass,
        List<String> warnings) {

    public GateVerdict {
        regressedEvaluators = regressedEvaluators != null ? List.copyOf(regressedEvaluators) : List.of();
        cases = cases != null ? List.copyOf(cases) : List.of();
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }

    /**
     * A single evaluator's significant regression between baseline and candidate.
     *
     * @param evaluator the evaluator name
     * @param baselineMean mean score on the baseline side, or null when absent there
     * @param candidateMean mean score on the candidate side, or null when absent there
     * @param delta candidateMean minus baselineMean
     * @param pValue the significance-test p-value
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RegressedEvaluator(
            String evaluator, Double baselineMean, Double candidateMean, double delta, double pValue) {}

    /**
     * A single regressed item, identified by its dataset item id when paired by id or by its
     * positional index otherwise. The comparison key serializes under {@code index} (a hard contract
     * with the PR-comment renderer); {@code datasetItemId} is set only when the key is a dataset id.
     *
     * @param datasetItemId the dataset item id, or null for positionally paired items
     * @param index the comparison key (dataset item id or {@code "item-<index>"})
     * @param evaluatorDrops the per-evaluator score drops on this item
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RegressedCase(
            String datasetItemId, @JsonProperty("index") String index, List<EvaluatorDrop> evaluatorDrops) {

        public RegressedCase {
            evaluatorDrops = evaluatorDrops != null ? List.copyOf(evaluatorDrops) : List.of();
        }
    }

    /**
     * A per-item evaluator score drop.
     *
     * @param evaluator the evaluator name
     * @param baselineMean baseline mean on this item, or null when absent
     * @param candidateMean candidate mean on this item, or null when absent
     * @param delta candidateMean minus baselineMean
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EvaluatorDrop(String evaluator, Double baselineMean, Double candidateMean, double delta) {}

    /**
     * The verdict when no baseline exists yet: nothing to regress against, so the gate passes. The
     * candidate pass rate is still reported (the renderer always renders it).
     *
     * @param candidatePassRate the candidate overall pass rate (pass {@code ExperimentResult.passRate()})
     * @return a NO_BASELINE verdict
     */
    public static GateVerdict noBaseline(double candidatePassRate) {
        return new GateVerdict(
                "NO_BASELINE",
                true,
                "none",
                null,
                candidatePassRate,
                null,
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                List.of(),
                List.of(),
                false,
                false,
                List.of());
    }

    /**
     * Serializes to compact JSON for {@code render-comment.sh} (read via jq from stdin).
     *
     * @return the verdict as a single-line JSON document
     */
    public String toJson() {
        return Json.writeCompact(this);
    }

    /**
     * Renders the verdict as CI-agnostic Markdown, mirroring {@code render-comment.sh} so GitLab,
     * Jenkins, and any non-GitHub runner can post the same comment without the shell. Unlike the
     * shell, a FAIL with no per-item case (a broad significance failure) renders an explicit
     * aggregate-regression line rather than an empty section, and any {@code warnings} are appended.
     *
     * @return the Markdown summary
     */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!-- dokimos-eval-gate -->\n");
        sb.append("## ").append(header()).append("\n\n");
        if ("NO_BASELINE".equals(status)) {
            sb.append("No comparable baseline was found, so there is nothing to regress against.\n\n");
        }

        sb.append("| metric | value |\n");
        sb.append("| --- | --- |\n");
        sb.append("| pass rate | ")
                .append(pctOrNa(baselinePassRate))
                .append(" → ")
                .append(pct(candidatePassRate))
                .append(" (")
                .append(deltaText())
                .append(") |\n");
        sb.append("| significant | ").append(significant).append(" |\n");
        sb.append("| regressed cases | ").append(regressedCount).append(" |\n");
        sb.append("| improved cases | ").append(improvedCount).append(" |\n");
        sb.append("| added / removed | ")
                .append(addedCount)
                .append(" / ")
                .append(removedCount)
                .append(" |\n");
        sb.append("| pairing | ").append(pairing).append(" |\n\n");

        if (!regressedEvaluators.isEmpty()) {
            sb.append("### Regressed evaluators\n\n");
            sb.append("| evaluator | baseline | candidate | delta | p |\n");
            sb.append("| --- | --- | --- | --- | --- |\n");
            for (RegressedEvaluator e : regressedEvaluators) {
                sb.append("| ")
                        .append(e.evaluator())
                        .append(" | ")
                        .append(meanOrNa(e.baselineMean()))
                        .append(" | ")
                        .append(meanOrNa(e.candidateMean()))
                        .append(" | ")
                        .append(num(e.delta()))
                        .append(" | ")
                        .append(num(e.pValue()))
                        .append(" |\n");
            }
            sb.append('\n');
        }

        if (!cases.isEmpty()) {
            sb.append("### Regressed cases\n\n");
            for (RegressedCase c : cases) {
                sb.append("- ")
                        .append(caseLabel(c))
                        .append(": ")
                        .append(dropList(c))
                        .append('\n');
            }
            if (casesTruncated) {
                // The denominator is the deduped union total, not regressedCount — guard 2 contributes
                // cases the engine's significance-gated count never sees.
                sb.append("\n_Showing ")
                        .append(cases.size())
                        .append(" of ")
                        .append(totalRegressedCases)
                        .append(" regressed cases._\n");
            }
            sb.append('\n');
        } else if ("FAIL".equals(status) && (!regressedEvaluators.isEmpty() || significant)) {
            // A red gate with no per-item case but a broad-significance trigger: explain the aggregate
            // regression rather than render an empty cases section that reads as a renderer bug. A
            // FAIL with neither a regressed evaluator nor a significant pass-rate drop is a
            // coverage-loss fail (removed evaluator/item); its reason is in the Warnings section, so
            // do not fabricate a pass-rate line that did not happen.
            sb.append("### Regression\n\n").append(aggregateLine()).append("\n\n");
        }

        if (!warnings.isEmpty()) {
            sb.append("### Warnings\n\n");
            for (String w : warnings) {
                sb.append("- ").append(w).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private String header() {
        return switch (status) {
            case "PASS" -> "Eval gate passed";
            case "FAIL" -> "Eval gate failed";
            case "NO_BASELINE" -> "Eval gate: no baseline";
            default -> "Eval gate: " + status;
        };
    }

    private String aggregateLine() {
        if (!regressedEvaluators.isEmpty()) {
            StringBuilder sb = new StringBuilder("Broad regression: ");
            for (int i = 0; i < regressedEvaluators.size(); i++) {
                RegressedEvaluator e = regressedEvaluators.get(i);
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append("mean ")
                        .append(e.evaluator())
                        .append(" dropped ")
                        .append(pct(-e.delta()))
                        .append(" pts (p=")
                        .append(num(e.pValue()))
                        .append(')');
            }
            return sb.toString();
        }
        long drop = passRateDelta != null ? pctRound(-passRateDelta) : 0L;
        String p = significant ? "<alpha" : "n/a";
        return "Broad pass-rate regression: pass rate dropped " + drop + " pts across the shared items (p=" + p + ").";
    }

    private static String caseLabel(RegressedCase c) {
        return c.datasetItemId() != null ? "item " + c.datasetItemId() : "row " + c.index();
    }

    private static String dropList(RegressedCase c) {
        StringBuilder sb = new StringBuilder();
        List<EvaluatorDrop> drops = c.evaluatorDrops();
        for (int i = 0; i < drops.size(); i++) {
            EvaluatorDrop d = drops.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(d.evaluator())
                    .append(' ')
                    .append(meanOrNa(d.baselineMean()))
                    .append("→")
                    .append(meanOrNa(d.candidateMean()));
        }
        return sb.toString();
    }

    private String deltaText() {
        return passRateDelta != null ? pctRound(passRateDelta) + " pts" : "n/a";
    }

    private static String pctOrNa(Double rate) {
        return rate != null ? pct(rate) : "n/a";
    }

    private static String pct(double rate) {
        return pctRound(rate) + "%";
    }

    private static long pctRound(double rate) {
        return Math.round(rate * 100.0);
    }

    private static String meanOrNa(Double mean) {
        return mean != null ? num(mean) : "n/a";
    }

    /**
     * Formats a score, delta, or p-value for the comment: fixed decimals (no scientific notation),
     * trailing zeros stripped so a clean {@code 1.0→0.0} drop reads the same as the shell's raw jq
     * output rather than {@code 1.000→0.000}.
     */
    private static String num(double value) {
        if (value == 0.0) {
            return "0";
        }
        String s = String.format(Locale.ROOT, "%.6f", value);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }
}
