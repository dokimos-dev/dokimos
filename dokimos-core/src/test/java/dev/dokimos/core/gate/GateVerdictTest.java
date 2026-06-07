package dev.dokimos.core.gate;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.dokimos.core.internal.Json;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GateVerdictTest {

    private static Map<String, Object> parse(String json) {
        return Json.read(json, new TypeReference<Map<String, Object>>() {});
    }

    private static GateVerdict failVerdict() {
        return new GateVerdict(
                "FAIL",
                false,
                "positional",
                0.9,
                0.6,
                -0.3,
                true,
                0,
                1,
                1,
                28,
                0,
                0,
                List.of(new GateVerdict.RegressedEvaluator("correctness", 0.9, 0.6, -0.3, 0.01)),
                List.of(new GateVerdict.RegressedCase(
                        null, "item-3", List.of(new GateVerdict.EvaluatorDrop("correctness", 1.0, 0.0, -1.0)))),
                false,
                true,
                List.of());
    }

    @Test
    void toJsonExposesEveryRenderConsumedFieldWithTheRightType() {
        Map<String, Object> json = parse(failVerdict().toJson());

        assertThat(json.get("status")).isInstanceOf(String.class);
        assertThat(json.get("passed")).isInstanceOf(Boolean.class);
        assertThat(json.get("pairing")).isInstanceOf(String.class);
        assertThat(json.get("significant")).isInstanceOf(Boolean.class);
        assertThat(json.get("casesTruncated")).isInstanceOf(Boolean.class);
        assertThat(json.get("candidatePassRate")).isInstanceOf(Number.class);
        assertThat(json.get("baselinePassRate")).isInstanceOf(Number.class);
        assertThat(json.get("passRateDelta")).isInstanceOf(Number.class);
        assertThat(json.get("regressedCount")).isInstanceOf(Number.class);
        assertThat(json.get("improvedCount")).isInstanceOf(Number.class);
        assertThat(json.get("addedCount")).isInstanceOf(Number.class);
        assertThat(json.get("removedCount")).isInstanceOf(Number.class);
        assertThat(json.get("regressedEvaluators")).isInstanceOf(List.class);
        assertThat(json.get("cases")).isInstanceOf(List.class);
        // Additive field the shell ignores; carries the exact pre-cap union total for toMarkdown.
        assertThat(json.get("totalRegressedCases")).isInstanceOf(Number.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void regressedCaseSerializesKeyUnderIndexNotKey() {
        Map<String, Object> json = parse(failVerdict().toJson());
        Map<String, Object> firstCase = (Map<String, Object>) ((List<?>) json.get("cases")).get(0);

        assertThat(firstCase.get("index")).isEqualTo("item-3");
        assertThat(firstCase).doesNotContainKey("key");
        assertThat(firstCase.get("evaluatorDrops")).isInstanceOf(List.class);
    }

    @Test
    void regressedEvaluatorCarriesMeansDeltaAndPValue() {
        Map<String, Object> json = parse(failVerdict().toJson());
        Map<?, ?> first = (Map<?, ?>) ((List<?>) json.get("regressedEvaluators")).get(0);

        assertThat(first.get("evaluator")).isEqualTo("correctness");
        assertThat(first.get("baselineMean")).isInstanceOf(Number.class);
        assertThat(first.get("candidateMean")).isInstanceOf(Number.class);
        assertThat(first.get("delta")).isInstanceOf(Number.class);
        assertThat(first.get("pValue")).isInstanceOf(Number.class);
    }

    @Test
    void noBaselineKeepsCandidatePassRateAsANonNullNumber() {
        Map<String, Object> json = parse(GateVerdict.noBaseline(0.0).toJson());

        assertThat(json.get("status")).isEqualTo("NO_BASELINE");
        assertThat(json.get("passed")).isEqualTo(Boolean.TRUE);
        assertThat(json).containsKey("candidatePassRate");
        assertThat(json.get("candidatePassRate")).isInstanceOf(Number.class);
        // NON_NULL omits the nullable rates rather than emitting JSON null.
        assertThat(json).doesNotContainKey("baselinePassRate");
        assertThat(json).doesNotContainKey("passRateDelta");
        // Arrays the script iterates must always be present, never null/absent.
        assertThat(json.get("regressedEvaluators")).isInstanceOf(List.class);
        assertThat(json.get("cases")).isInstanceOf(List.class);
    }

    @Test
    void comparedPassIsTrueOnAComparisonAndFalseOnNoBaseline() {
        assertThat(parse(failVerdict().toJson()).get("comparedPass")).isEqualTo(Boolean.TRUE);
        assertThat(parse(GateVerdict.noBaseline(0.0).toJson()).get("comparedPass"))
                .isEqualTo(Boolean.FALSE);
    }

    @Test
    void markdownForBroadFailWithNoCasesRendersAnAggregateLineNotAnEmptySection() {
        GateVerdict broad = new GateVerdict(
                "FAIL",
                false,
                "positional",
                0.9,
                0.6,
                -0.3,
                true,
                0,
                0,
                0,
                30,
                0,
                0,
                List.of(new GateVerdict.RegressedEvaluator("correctness", 0.9, 0.6, -0.3, 0.01)),
                List.of(),
                false,
                true,
                List.of());

        String md = broad.toMarkdown();
        assertThat(md).contains("Eval gate failed");
        assertThat(md).contains("Broad regression");
        assertThat(md).contains("correctness");
        // No empty cases section.
        assertThat(md).doesNotContain("### Regressed cases");
    }

    @Test
    void markdownForBroadPassRateFailWithNoEvaluatorsRendersThePassRateAggregateLine() {
        // A significant pass-rate drop with no per-evaluator regression and no per-item case: the
        // aggregate line must report the pass-rate drop, not an empty cases section.
        GateVerdict broad = new GateVerdict(
                "FAIL",
                false,
                "positional",
                0.9,
                0.6,
                -0.3,
                true,
                0,
                0,
                0,
                30,
                0,
                0,
                List.of(),
                List.of(),
                false,
                true,
                List.of());

        String md = broad.toMarkdown();
        assertThat(md).contains("Broad pass-rate regression");
        assertThat(md).contains("30 pts");
        assertThat(md).doesNotContain("### Regressed cases");
    }

    @Test
    void markdownForCoverageLossFailDoesNotFabricateABroadRegressionLine() {
        // A removed-evaluator FAIL has empty regressedEvaluators, empty cases, and no significant
        // pass-rate drop. The cause lives in Warnings; the cases area must stay silent rather than
        // claim a broad pass-rate regression that did not happen.
        GateVerdict coverageLoss = new GateVerdict(
                "FAIL",
                false,
                "positional",
                0.9,
                0.9,
                0.0,
                false,
                0,
                0,
                0,
                30,
                0,
                0,
                List.of(),
                List.of(),
                false,
                true,
                List.of("Evaluator 'faithfulness' is in the baseline but missing from the candidate"));

        String md = coverageLoss.toMarkdown();
        assertThat(md).contains("Eval gate failed");
        assertThat(md).doesNotContain("Broad pass-rate regression");
        assertThat(md).doesNotContain("### Regression\n");
        assertThat(md).doesNotContain("### Regressed cases");
        assertThat(md).contains("### Warnings");
        assertThat(md).contains("faithfulness");
    }

    @Test
    void markdownTruncationDenominatorUsesTheUnionTotalNotRegressedCount() {
        // A guard-2-dominant verdict: the engine's regressedCount is 0, but 60 cases were found and
        // 50 shown. The "Showing N of M" denominator must be the union total (60), never 0.
        List<GateVerdict.RegressedCase> shown = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            shown.add(new GateVerdict.RegressedCase(
                    null, "item-" + i, List.of(new GateVerdict.EvaluatorDrop("correctness", 1.0, 0.0, -1.0))));
        }
        GateVerdict v = new GateVerdict(
                "FAIL",
                false,
                "positional",
                1.0,
                0.0,
                -1.0,
                false,
                0,
                60, // displayed count (max of engine 0 and union 60)
                60, // totalRegressedCases (the honest pre-cap union total)
                0,
                0,
                0,
                List.of(),
                shown,
                true,
                true,
                List.of());

        String md = v.toMarkdown();
        assertThat(md).contains("Showing 50 of 60 regressed cases");
        assertThat(md).doesNotContain("of 0 regressed cases");
        // The metric table must not show "0" regressed cases above a populated list.
        assertThat(md).doesNotContain("| regressed cases | 0 |");
    }

    @Test
    void verdictDefensivelyCopiesItsListFields() {
        List<GateVerdict.RegressedCase> cases = new java.util.ArrayList<>();
        List<String> warnings = new java.util.ArrayList<>();
        GateVerdict v = new GateVerdict(
                "PASS",
                true,
                "positional",
                1.0,
                1.0,
                0.0,
                false,
                0,
                0,
                0,
                30,
                0,
                0,
                new java.util.ArrayList<>(),
                cases,
                false,
                true,
                warnings);
        cases.add(new GateVerdict.RegressedCase(null, "item-9", List.of()));
        warnings.add("mutated");
        assertThat(v.cases()).isEmpty();
        assertThat(v.warnings()).isEmpty();
    }

    @Test
    void markdownRendersCasesAndWarnings() {
        GateVerdict v = new GateVerdict(
                "FAIL",
                false,
                "positional",
                0.9,
                0.6,
                -0.3,
                true,
                0,
                1,
                1,
                29,
                0,
                0,
                List.of(),
                List.of(new GateVerdict.RegressedCase(
                        null, "item-3", List.of(new GateVerdict.EvaluatorDrop("correctness", 1.0, 0.0, -1.0)))),
                false,
                true,
                List.of("Evaluator 'faithfulness' is in the baseline but missing from the candidate"));

        String md = v.toMarkdown();
        assertThat(md).contains("### Regressed cases");
        assertThat(md).contains("row item-3");
        // The drop renders without trailing-zero noise (num() strips them): 1.0→0.0, not 1.000→0.000.
        assertThat(md).contains("correctness 1→0");
        assertThat(md).contains("### Warnings");
        assertThat(md).contains("faithfulness");
    }
}
