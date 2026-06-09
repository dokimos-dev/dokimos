package dev.dokimos.core.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.Example;
import dev.dokimos.core.ExperimentResult;
import dev.dokimos.core.ItemResult;
import dev.dokimos.core.RunResult;
import dev.dokimos.core.comparison.RunComparison;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegressionGateTest {

    private static EvalResult ev(String name, double score, double threshold) {
        return new EvalResult(name, score, threshold, score >= threshold, "", Map.of());
    }

    private static ItemResult item(int i, EvalResult... evals) {
        return new ItemResult(Example.of("q" + i, "a" + i), Map.of("output", "x"), List.of(evals));
    }

    private static ItemResult idItem(String id, EvalResult... evals) {
        Example ex =
                Example.builder().input("input", "q-" + id).datasetItemId(id).build();
        return new ItemResult(ex, Map.of("output", "x"), List.of(evals));
    }

    private static ExperimentResult experiment(List<ItemResult> items) {
        return new ExperimentResult("rag-eval", "", Map.of(), List.of(new RunResult(0, items)));
    }

    /** 30 items, single evaluator at {@code score} except {@code brokenIndex} at {@code brokenScore}. */
    private static ExperimentResult thirtyItems(int brokenIndex, double score, double brokenScore) {
        List<ItemResult> items = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            double s = i == brokenIndex ? brokenScore : score;
            items.add(item(i, ev("correctness", s, 0.7)));
        }
        return experiment(items);
    }

    @Test
    void guard2FailsOnALocalizedBreakThatGuard1IsBlindTo() {
        // Baseline: all 30 items pass (1.0). Candidate: identical except item 3 drops 1.0 -> 0.0.
        ExperimentResult baselineExp = thirtyItems(-1, 1.0, 1.0);
        ExperimentResult candidate = thirtyItems(3, 1.0, 0.0);
        BaselineFile baseline = BaselineStore.project(baselineExp, GateConfig.defaults());

        // Guard 1 is blind: a single item among 30 dropping 1.0->0.0 is not significant.
        var result = RunComparison.create().compare(BaselineStore.toRunResults(baseline), candidate.runResults());
        assertThat(result.hasRegressions())
                .as("guard 1 (broad significance) must NOT fire on one localized break in 30")
                .isFalse();

        // Guard 2 fires and injects the triggering item's case.
        GateVerdict verdict = RegressionGate.evaluate(candidate, baseline, GateConfig.defaults());
        assertThat(verdict.status()).isEqualTo("FAIL");
        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.cases()).hasSize(1);

        GateVerdict.RegressedCase only = verdict.cases().get(0);
        assertThat(only.index()).isEqualTo("item-3");
        assertThat(only.datasetItemId()).isNull();
        assertThat(only.evaluatorDrops()).hasSize(1);
        GateVerdict.EvaluatorDrop drop = only.evaluatorDrops().get(0);
        assertThat(drop.evaluator()).isEqualTo("correctness");
        assertThat(drop.baselineMean()).isEqualTo(1.0);
        assertThat(drop.candidateMean()).isEqualTo(0.0);
        assertThat(drop.delta()).isEqualTo(-1.0);

        // A guard-2-only failure: the engine's significance arm saw nothing, so its broad counts are
        // empty and the pass-rate change is not significant. The displayed regressedCount still
        // covers the injected case (so the comment never reads "0 regressed cases" over a list), and
        // totalRegressedCases is the honest union total.
        assertThat(verdict.regressedEvaluators()).isEmpty();
        assertThat(verdict.significant()).isFalse();
        assertThat(verdict.comparedPass()).isTrue();
        assertThat(verdict.regressedCount()).isEqualTo(1);
        assertThat(verdict.totalRegressedCases()).isEqualTo(1);
    }

    @Test
    void broadSubThresholdDropFiresGuard1WhileGuard2StaysSilent() {
        // Every item drops 0.10 — below severityMargin (0.15) so guard 2 cannot fire — but the
        // correlated drop across all 30 items is significant, so guard 1 must catch it alone. This is
        // the symmetric counterpart to the guard-2 test: it isolates guard 1.
        List<ItemResult> baseItems = new ArrayList<>();
        List<ItemResult> candItems = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            baseItems.add(item(i, ev("correctness", 0.90, 0.7)));
            candItems.add(item(i, ev("correctness", 0.80, 0.7)));
        }
        BaselineFile baseline = BaselineStore.project(experiment(baseItems), GateConfig.defaults());
        ExperimentResult candidate = experiment(candItems);

        // Sanity: no per-item drop exceeds severityMargin, so guard 2 contributes nothing.
        assertThat(0.80 - 0.90).isGreaterThan(-GateConfig.defaults().severityMargin());

        GateVerdict verdict = RegressionGate.evaluate(candidate, baseline, GateConfig.defaults());
        assertThat(verdict.status())
                .as("broad significant drop must fail via guard 1")
                .isEqualTo("FAIL");
        // Guard 1 fires via the per-evaluator mean regression (the evaluator is overall significant,
        // so the engine surfaces each item as a case). Every drop is -0.10, strictly inside
        // severityMargin (0.15), which proves guard 2 could not have contributed any of these cases —
        // the failure is guard 1's significance test on the evaluator mean alone.
        assertThat(verdict.regressedEvaluators()).isNotEmpty();
        assertThat(verdict.cases())
                .allSatisfy(c -> assertThat(c.evaluatorDrops()).allSatisfy(d -> assertThat(d.delta())
                        .isGreaterThan(-GateConfig.defaults().severityMargin())));
    }

    @Test
    void guard2OnDatasetIdPairingSetsDatasetItemIdOnTheCase() {
        // Drives the dataset_item_id pairing branch end-to-end: candidate is reverse-ordered, so a
        // positional pairing would mis-pair every item. Guard 2 must key by id and find only q7.
        List<ItemResult> baseItems = new ArrayList<>();
        List<ItemResult> candItems = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String id = "q" + i;
            baseItems.add(idItem(id, ev("correctness", 1.0, 0.7)));
            candItems.add(0, idItem(id, ev("correctness", id.equals("q7") ? 0.0 : 1.0, 0.7)));
        }
        GateConfig cfg =
                GateConfig.builder().pairing(GateConfig.Pairing.DATASET_ITEM_ID).build();
        BaselineFile baseline = BaselineStore.project(experiment(baseItems), cfg);
        assertThat(baseline.pairing()).isEqualTo("dataset_item_id");

        GateVerdict verdict = RegressionGate.evaluate(experiment(candItems), baseline, cfg);
        assertThat(verdict.status()).isEqualTo("FAIL");
        assertThat(verdict.cases()).hasSize(1);
        GateVerdict.RegressedCase only = verdict.cases().get(0);
        // On id-pairing the case carries the id in BOTH datasetItemId and index (the script reads index).
        assertThat(only.datasetItemId()).isEqualTo("q7");
        assertThat(only.index()).isEqualTo("q7");
    }

    @Test
    void idPairingWithACandidateItemMissingItsIdFailsHard() {
        // Baseline is id-paired and all-passing. The candidate keeps every score but one item loses its
        // id (a positional Example with no datasetItemId). That item cannot be paired by id, so a
        // regression on it would slip both guards; the gate must FAIL on the coverage loss alone.
        List<ItemResult> baseItems = new ArrayList<>();
        List<ItemResult> candItems = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String id = "q" + i;
            baseItems.add(idItem(id, ev("correctness", 1.0, 0.7)));
            // item 7 collapses 1.0 -> 0.0 AND has no id, so the regression is exactly the one that hides.
            if (i == 7) {
                candItems.add(item(i, ev("correctness", 0.0, 0.7)));
            } else {
                candItems.add(idItem(id, ev("correctness", 1.0, 0.7)));
            }
        }
        GateConfig cfg =
                GateConfig.builder().pairing(GateConfig.Pairing.DATASET_ITEM_ID).build();
        BaselineFile baseline = BaselineStore.project(experiment(baseItems), cfg);
        assertThat(baseline.pairing()).isEqualTo("dataset_item_id");

        GateVerdict verdict = RegressionGate.evaluate(experiment(candItems), baseline, cfg);
        assertThat(verdict.status()).isEqualTo("FAIL");
        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.warnings()).anyMatch(w -> w.contains("no datasetItemId") && w.contains("slip both guards"));
    }

    @Test
    void idPairingWithARenamedIdWarnsLoudlyAndFailsOnlyWhenConfigured() {
        // Baseline carries id q7; the candidate renames it to q99 (a new id) while keeping every score.
        // Under id pairing q7 pairs against nothing: a loud warning names the missing baseline id. By
        // default that is a removed-item WARN (status PASS); with failOnRemovedItems it FAILs.
        List<ItemResult> baseItems = new ArrayList<>();
        List<ItemResult> candItems = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String id = "q" + i;
            baseItems.add(idItem(id, ev("correctness", 1.0, 0.7)));
            String candId = i == 7 ? "q99" : id;
            candItems.add(idItem(candId, ev("correctness", 1.0, 0.7)));
        }
        GateConfig cfg =
                GateConfig.builder().pairing(GateConfig.Pairing.DATASET_ITEM_ID).build();
        BaselineFile baseline = BaselineStore.project(experiment(baseItems), cfg);

        GateVerdict warnVerdict = RegressionGate.evaluate(experiment(candItems), baseline, cfg);
        assertThat(warnVerdict.status()).isEqualTo("PASS");
        assertThat(warnVerdict.warnings())
                .anyMatch(w -> w.contains("Baseline item id 'q7'") && w.contains("not in the candidate"));

        GateConfig failOnRemoved = GateConfig.builder()
                .pairing(GateConfig.Pairing.DATASET_ITEM_ID)
                .failOnRemovedItems(true)
                .build();
        GateVerdict failVerdict = RegressionGate.evaluate(experiment(candItems), baseline, failOnRemoved);
        assertThat(failVerdict.status()).isEqualTo("FAIL");
        assertThat(failVerdict.warnings())
                .anyMatch(w -> w.contains("Baseline item id 'q7'") && w.contains("not in the candidate"));
    }

    @Test
    void capsCasesAtFiftyAndFlagsTruncationOffTheUnionTotal() {
        // 60 localized breaks: cases cap at 50, casesTruncated true, and the honest union total is 60
        // even though the engine's significance-gated regressedCount may differ.
        List<ItemResult> baseItems = new ArrayList<>();
        List<ItemResult> candItems = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            baseItems.add(item(i, ev("correctness", 1.0, 0.7)));
            candItems.add(item(i, ev("correctness", 0.0, 0.7)));
        }
        BaselineFile baseline = BaselineStore.project(experiment(baseItems), GateConfig.defaults());
        GateVerdict verdict = RegressionGate.evaluate(experiment(candItems), baseline, GateConfig.defaults());

        assertThat(verdict.status()).isEqualTo("FAIL");
        assertThat(verdict.cases()).hasSize(50);
        assertThat(verdict.casesTruncated()).isTrue();
        assertThat(verdict.totalRegressedCases()).isEqualTo(60);
        // The displayed count never undercounts the cases that were actually found.
        assertThat(verdict.regressedCount())
                .isGreaterThanOrEqualTo(verdict.cases().size());
    }

    @Test
    void guard2OnlyOverflowKeepsTheDisplayedCountConsistentWithTheCases() {
        // Balanced input: half the items drop -0.20 (guard 2 breaches) and half rise +0.20, so the
        // mean is flat and guard 1 stays quiet (regressedCount 0 from the engine). The displayed
        // count and union total must still reflect the breaching items, never 0.
        List<ItemResult> baseItems = new ArrayList<>();
        List<ItemResult> candItems = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            baseItems.add(item(i, ev("correctness", 0.70, 0.5)));
            double cand = i < 30 ? 0.50 : 0.90; // -0.20 (breach) or +0.20
            candItems.add(item(i, ev("correctness", cand, 0.5)));
        }
        BaselineFile baseline = BaselineStore.project(experiment(baseItems), GateConfig.defaults());
        GateVerdict verdict = RegressionGate.evaluate(experiment(candItems), baseline, GateConfig.defaults());

        assertThat(verdict.status()).isEqualTo("FAIL");
        assertThat(verdict.cases()).isNotEmpty();
        // The rendered count must match the listed cases, not the engine's significance-gated 0.
        assertThat(verdict.regressedCount()).isEqualTo(verdict.totalRegressedCases());
        assertThat(verdict.totalRegressedCases()).isEqualTo(30);
        // The Markdown header and footer agree with the case list (no "Showing N of 0").
        assertThat(verdict.toMarkdown()).doesNotContain("of 0 regressed cases");
        assertThat(verdict.toMarkdown()).doesNotContain("| regressed cases | 0 |");
    }

    @Test
    void broadImprovementPassesAndCountsImprovedItems() {
        List<ItemResult> baseItems = new ArrayList<>();
        List<ItemResult> candItems = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            baseItems.add(item(i, ev("correctness", 0.60, 0.7)));
            candItems.add(item(i, ev("correctness", 0.90, 0.7)));
        }
        BaselineFile baseline = BaselineStore.project(experiment(baseItems), GateConfig.defaults());
        GateVerdict verdict = RegressionGate.evaluate(experiment(candItems), baseline, GateConfig.defaults());

        assertThat(verdict.status()).isEqualTo("PASS");
        assertThat(verdict.passed()).isTrue();
        assertThat(verdict.improvedCount()).isGreaterThan(0);
        assertThat(verdict.cases()).isEmpty();
        assertThat(verdict.passRateDelta()).isGreaterThan(0.0);
    }

    @Test
    void casesAreDedupedByKeyAcrossBothGuards() {
        // Item 0 breaks on BOTH evaluators; the key must appear once with both drops present.
        List<ItemResult> baseItems = new ArrayList<>();
        List<ItemResult> candItems = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            baseItems.add(item(i, ev("a", 1.0, 0.7), ev("b", 1.0, 0.7)));
            candItems.add(item(i, ev("a", i == 0 ? 0.0 : 1.0, 0.7), ev("b", i == 0 ? 0.0 : 1.0, 0.7)));
        }
        BaselineFile baseline = BaselineStore.project(experiment(baseItems), GateConfig.defaults());
        GateVerdict verdict = RegressionGate.evaluate(experiment(candItems), baseline, GateConfig.defaults());

        assertThat(verdict.status()).isEqualTo("FAIL");
        assertThat(verdict.cases().stream()
                        .map(GateVerdict.RegressedCase::index)
                        .distinct()
                        .count())
                .isEqualTo(verdict.cases().size());
        assertThat(verdict.cases()).filteredOn(c -> "item-0".equals(c.index())).hasSize(1);
    }

    @Test
    void collidingGuardsMergeDropsSoTheSevereLocalizedBreakIsNotHidden() {
        // correctness drops broadly to 0.60 (guard 1 significant across all items) while faithfulness
        // crashes 0.95 -> 0.0 only on item 3 (guard 2; faithfulness is not overall significant).
        // The merged item-3 case must list BOTH evaluators, not just guard 1's correctness drop.
        List<ItemResult> baseItems = new ArrayList<>();
        List<ItemResult> candItems = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            baseItems.add(item(i, ev("correctness", 0.95, 0.7), ev("faithfulness", 0.95, 0.7)));
            double faith = i == 3 ? 0.0 : 0.95;
            candItems.add(item(i, ev("correctness", 0.60, 0.7), ev("faithfulness", faith, 0.7)));
        }
        BaselineFile baseline = BaselineStore.project(experiment(baseItems), GateConfig.defaults());
        GateVerdict verdict = RegressionGate.evaluate(experiment(candItems), baseline, GateConfig.defaults());

        assertThat(verdict.status()).isEqualTo("FAIL");
        GateVerdict.RegressedCase itemThree = verdict.cases().stream()
                .filter(c -> "item-3".equals(c.index()))
                .findFirst()
                .orElseThrow();
        assertThat(itemThree.evaluatorDrops())
                .extracting(GateVerdict.EvaluatorDrop::evaluator)
                .contains("correctness", "faithfulness");
    }

    @Test
    void guard2DoesNotFireAtExactlyTheSeverityMargin() {
        // A drop of exactly severityMargin (0.15) must not fire (strict < comparison).
        ExperimentResult baselineExp = thirtyItems(-1, 1.0, 1.0);
        ExperimentResult candidate = thirtyItems(3, 1.0, 0.85); // delta = -0.15 exactly
        BaselineFile baseline = BaselineStore.project(baselineExp, GateConfig.defaults());

        GateVerdict verdict = RegressionGate.evaluate(candidate, baseline, GateConfig.defaults());
        assertThat(verdict.status()).isEqualTo("PASS");
        assertThat(verdict.cases()).isEmpty();
    }

    @Test
    void identicalCandidatePassesWithNoCases() {
        ExperimentResult exp = thirtyItems(-1, 1.0, 1.0);
        BaselineFile baseline = BaselineStore.project(exp, GateConfig.defaults());

        GateVerdict verdict = RegressionGate.evaluate(exp, baseline, GateConfig.defaults());
        assertThat(verdict.status()).isEqualTo("PASS");
        assertThat(verdict.passed()).isTrue();
        assertThat(verdict.comparedPass()).isTrue();
        assertThat(verdict.cases()).isEmpty();
        assertThat(verdict.regressedEvaluators()).isEmpty();
        assertThat(verdict.warnings()).isEmpty();
    }

    @Test
    void broadCorrelatedDropFailsWithRegressedEvaluators() {
        // A drop across nearly every item is significant -> guard 1 fires.
        List<ItemResult> baseItems = new ArrayList<>();
        List<ItemResult> candItems = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            baseItems.add(item(i, ev("correctness", 0.90, 0.7)));
            candItems.add(item(i, ev("correctness", 0.60, 0.7)));
        }
        ExperimentResult baselineExp = experiment(baseItems);
        ExperimentResult candidate = experiment(candItems);
        BaselineFile baseline = BaselineStore.project(baselineExp, GateConfig.defaults());

        GateVerdict verdict = RegressionGate.evaluate(candidate, baseline, GateConfig.defaults());
        assertThat(verdict.status()).isEqualTo("FAIL");
        assertThat(verdict.regressedEvaluators()).isNotEmpty();
        assertThat(verdict.regressedEvaluators().get(0).evaluator()).isEqualTo("correctness");
    }

    @Test
    void removedEvaluatorFailsByDefaultAndWarnsUnderWarnPolicy() {
        // Baseline has two evaluators; candidate drops faithfulness on every item.
        List<ItemResult> baseItems = new ArrayList<>();
        List<ItemResult> candItems = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            baseItems.add(item(i, ev("correctness", 0.95, 0.7), ev("faithfulness", 0.90, 0.7)));
            candItems.add(item(i, ev("correctness", 0.95, 0.7)));
        }
        BaselineFile baseline = BaselineStore.project(experiment(baseItems), GateConfig.defaults());
        ExperimentResult candidate = experiment(candItems);

        GateVerdict failVerdict = RegressionGate.evaluate(candidate, baseline, GateConfig.defaults());
        assertThat(failVerdict.status()).isEqualTo("FAIL");
        assertThat(failVerdict.warnings()).anyMatch(w -> w.contains("faithfulness") && w.contains("missing"));

        GateConfig warn = GateConfig.builder()
                .onRemovedEvaluator(GateConfig.RemovedEvaluatorPolicy.WARN)
                .build();
        GateVerdict warnVerdict = RegressionGate.evaluate(candidate, baseline, warn);
        assertThat(warnVerdict.status()).isEqualTo("PASS");
        assertThat(warnVerdict.warnings()).anyMatch(w -> w.contains("faithfulness"));
    }

    @Test
    void thresholdDriftWarnsWithoutFailing() {
        // Same scores and pass verdicts on both sides, but the candidate threshold moved 0.7 -> 0.8.
        List<ItemResult> baseItems = new ArrayList<>();
        List<ItemResult> candItems = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            baseItems.add(item(i, ev("correctness", 0.95, 0.7)));
            candItems.add(item(i, ev("correctness", 0.95, 0.8)));
        }
        BaselineFile baseline = BaselineStore.project(experiment(baseItems), GateConfig.defaults());
        ExperimentResult candidate = experiment(candItems);

        GateVerdict verdict = RegressionGate.evaluate(candidate, baseline, GateConfig.defaults());
        assertThat(verdict.status()).isEqualTo("PASS");
        assertThat(verdict.warnings()).anyMatch(w -> w.contains("threshold changed"));
    }

    @Test
    void removedItemWarnsByDefaultAndFailsWhenConfigured() {
        // Baseline has 30 items; candidate drops the last positional item.
        ExperimentResult baselineExp = thirtyItems(-1, 1.0, 1.0);
        BaselineFile baseline = BaselineStore.project(baselineExp, GateConfig.defaults());

        List<ItemResult> candItems = new ArrayList<>();
        for (int i = 0; i < 29; i++) {
            candItems.add(item(i, ev("correctness", 1.0, 0.7)));
        }
        ExperimentResult candidate = experiment(candItems);

        GateVerdict warnVerdict = RegressionGate.evaluate(candidate, baseline, GateConfig.defaults());
        assertThat(warnVerdict.status()).isEqualTo("PASS");
        assertThat(warnVerdict.removedCount()).isEqualTo(1);
        assertThat(warnVerdict.warnings()).anyMatch(w -> w.contains("removed"));

        GateConfig failOnRemoved = GateConfig.builder().failOnRemovedItems(true).build();
        GateVerdict failVerdict = RegressionGate.evaluate(candidate, baseline, failOnRemoved);
        assertThat(failVerdict.status()).isEqualTo("FAIL");
    }
}
