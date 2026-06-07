package dev.dokimos.core.gate;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.ExperimentResult;
import dev.dokimos.core.ItemResult;
import dev.dokimos.core.RunResult;
import dev.dokimos.core.comparison.ComparisonStatus;
import dev.dokimos.core.comparison.ItemComparison;
import dev.dokimos.core.comparison.RunComparison;
import dev.dokimos.core.comparison.RunComparisonResult;
import dev.dokimos.core.gate.BaselineFile.BaselineItem;
import dev.dokimos.core.gate.BaselineFile.EvaluatorEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The server-free regression gate: compares a candidate experiment result against a committed
 * baseline and produces a {@link GateVerdict}. This is the verdict layer only — no file I/O, no CI
 * detection.
 *
 * <p>The gate fails if either of two independent guards fires:
 *
 * <ul>
 *   <li><b>Guard 1 (broad)</b> — the core {@link RunComparison} engine reports a significant
 *       aggregate pass-rate drop or any significantly regressed evaluator ({@link
 *       RunComparisonResult#hasRegressions()}). Quiet on judge noise; insensitive to a localized
 *       break on a small dataset.
 *   <li><b>Guard 2 (localized-severe)</b> — any shared item's worst raw per-evaluator score drop
 *       falls below {@code -severityMargin}. Guard 2 computes its deltas directly from the baseline
 *       entries and the candidate means; it must <em>not</em> read {@link
 *       ItemComparison#evaluatorDeltas()}, which the engine gates to UNCHANGED whenever the
 *       evaluator's overall verdict is not significant — exactly the blindness guard 2 exists to fix.
 * </ul>
 *
 * <p>Two coverage-loss conditions fail the gate independently of {@code failOnRegression}, because
 * guard 1 structurally cannot see them: a removed evaluator (per {@code onRemovedEvaluator}) and a
 * removed item (only when {@code failOnRemovedItems}). A threshold change between sides is advisory
 * and only warns.
 */
public final class RegressionGate {

    /** Cap on regressed cases shown inline, to keep the PR comment bounded. Matches the server. */
    private static final int MAX_CASES = 50;

    private static final double PRECISION_SCALE = 1_000_000.0; // 6 decimals, matching the baseline writer

    private RegressionGate() {}

    /**
     * Compares a candidate against a baseline and returns the gate verdict.
     *
     * @param candidate the candidate experiment result
     * @param baseline the committed baseline to compare against
     * @param config the gate configuration
     * @return the verdict (status {@code PASS} or {@code FAIL}; never {@code NO_BASELINE}, which the
     *     assertion layer produces when no baseline file exists)
     */
    public static GateVerdict evaluate(ExperimentResult candidate, BaselineFile baseline, GateConfig config) {
        boolean byId = "dataset_item_id".equals(baseline.pairing());

        // Round candidate scores to 6 decimals up front: the baseline writer rounds, so the live
        // candidate is the only unrounded input. Skipping this reintroduces phantom deltas and can
        // flip the engine's binary/continuous path (it inspects the raw double).
        List<RunResult> candidateRuns = roundedCandidateRuns(candidate);
        List<RunResult> baselineRuns = BaselineStore.toRunResults(baseline);

        RunComparison.Builder engine = RunComparison.builder()
                .alpha(config.alpha())
                .seed(config.seed())
                .permutationIterations(config.permutationIterations())
                .bootstrapIterations(config.bootstrapIterations());
        if (byId) {
            engine.itemKey(ir -> ir.example().datasetItemId());
        }
        RunComparisonResult result = engine.build().compare(baselineRuns, candidateRuns);

        boolean guard1 = result.hasRegressions();

        Map<String, Map<String, Double>> candidateMeans = candidateMeans(candidateRuns, byId);
        List<GateVerdict.RegressedCase> guard2Cases = new ArrayList<>();
        boolean guard2 = collectGuard2(baseline, candidateMeans, config.severityMargin(), guard2Cases);

        List<String> warnings = new ArrayList<>();
        boolean removedEvaluatorFail = checkRemovedEvaluators(baseline, candidateRuns, config, warnings);
        checkThresholdDrift(baseline, candidateRuns, warnings);
        if (result.removedCount() > 0) {
            warnings.add(result.removedCount() + " baseline item(s) removed; excluded from the significance test.");
        }
        boolean removedItemFail = config.failOnRemovedItems() && result.removedCount() > 0;

        boolean regressionFail = config.failOnRegression() && (guard1 || guard2);
        boolean fail = regressionFail || removedEvaluatorFail || removedItemFail;
        String status = fail ? "FAIL" : "PASS";

        List<GateVerdict.RegressedEvaluator> regressedEvaluators = result.regressions().stream()
                .map(d -> new GateVerdict.RegressedEvaluator(
                        d.evaluatorName(),
                        d.baselineMean(),
                        d.candidateMean(),
                        // result.regressions() pre-filters to significant deltas, so significance() is non-null.
                        d.delta(),
                        d.significance().pValue()))
                .toList();

        // Union guard-1 cases (significance-gated REGRESSED items) with the guard-2 cases it cannot
        // see, keyed by the comparison key. On a key collision the two guards may have caught
        // DIFFERENT evaluators, so merge their drops rather than dropping one guard's evidence —
        // otherwise guard 2's severe localized break is hidden behind guard 1's milder one.
        LinkedHashMap<String, GateVerdict.RegressedCase> byKey = new LinkedHashMap<>();
        for (GateVerdict.RegressedCase c : guard1Cases(result)) {
            byKey.put(c.index(), c);
        }
        for (GateVerdict.RegressedCase c : guard2Cases) {
            byKey.merge(c.index(), c, RegressionGate::mergeCases);
        }
        int totalCaseCount = byKey.size();
        List<GateVerdict.RegressedCase> shown =
                byKey.values().stream().limit(MAX_CASES).toList();
        // casesTruncated is the deduped union total over the cap, NOT the server's
        // regressedCount>size formula, which is wrong once guard 2 contributes cases.
        boolean casesTruncated = totalCaseCount > shown.size();

        boolean significant = result.passRateSignificance().significant();

        // regressedCount is the displayed regressed-case count: render-comment.sh reuses it as the
        // metrics-table value AND the "Showing N of M" denominator, so it must cover the union of
        // both guards' cases. The engine's significance-gated count is 0 for a guard-2-only break,
        // which would otherwise print "0 regressed cases" above a populated list. totalRegressedCases
        // carries the exact pre-cap union total for the canonical Markdown renderer.
        int displayedRegressedCount = Math.max(result.regressedCount(), totalCaseCount);

        return new GateVerdict(
                status,
                !fail,
                baseline.pairing(),
                result.baselinePassRate(),
                result.candidatePassRate(),
                result.passRateDelta(),
                significant,
                result.improvedCount(),
                displayedRegressedCount,
                totalCaseCount,
                result.unchangedCount(),
                result.addedCount(),
                result.removedCount(),
                regressedEvaluators,
                shown,
                casesTruncated,
                true,
                List.copyOf(warnings));
    }

    /**
     * Merges a guard-2 case into a colliding guard-1 case: keeps every drop from the first, then
     * appends any evaluator the second caught that the first did not. Identity (datasetItemId/index)
     * is the same on both sides because they share a key, so the first wins.
     */
    private static GateVerdict.RegressedCase mergeCases(
            GateVerdict.RegressedCase first, GateVerdict.RegressedCase second) {
        List<GateVerdict.EvaluatorDrop> merged = new ArrayList<>(first.evaluatorDrops());
        Set<String> seen = new HashSet<>();
        for (GateVerdict.EvaluatorDrop d : merged) {
            seen.add(d.evaluator());
        }
        for (GateVerdict.EvaluatorDrop d : second.evaluatorDrops()) {
            if (seen.add(d.evaluator())) {
                merged.add(d);
            }
        }
        return new GateVerdict.RegressedCase(first.datasetItemId(), first.index(), merged);
    }

    // --- internals ---

    private static List<RunResult> roundedCandidateRuns(ExperimentResult candidate) {
        List<RunResult> runs = new ArrayList<>(candidate.runResults().size());
        for (RunResult run : candidate.runResults()) {
            List<ItemResult> items = new ArrayList<>(run.itemResults().size());
            for (ItemResult item : run.itemResults()) {
                List<EvalResult> evals = new ArrayList<>(item.evalResults().size());
                for (EvalResult er : item.evalResults()) {
                    evals.add(new EvalResult(
                            er.name(), round6(er.score()), er.threshold(), er.success(), er.reason(), er.metadata()));
                }
                items.add(new ItemResult(item.example(), item.actualOutputs(), evals));
            }
            runs.add(new RunResult(run.runIndex(), items));
        }
        return runs;
    }

    /**
     * Candidate per-item per-evaluator mean across runs, rounded to 6 decimals (matching the
     * baseline writer's projection so an unchanged item yields delta exactly 0.0). Items are keyed
     * the same way the engine pairs them: by {@code datasetItemId} when {@code byId}, else by the
     * run-local positional index ({@code "item-<i>"}, reset per run).
     */
    private static Map<String, Map<String, Double>> candidateMeans(List<RunResult> runs, boolean byId) {
        Map<String, Map<String, double[]>> sums = new LinkedHashMap<>(); // key -> eval -> {sum, count}
        for (RunResult run : runs) {
            List<ItemResult> items = run.itemResults();
            for (int i = 0; i < items.size(); i++) {
                ItemResult item = items.get(i);
                String key = byId ? item.example().datasetItemId() : "item-" + i;
                Map<String, double[]> evals = sums.computeIfAbsent(key, k -> new LinkedHashMap<>());
                for (EvalResult er : item.evalResults()) {
                    double[] acc = evals.computeIfAbsent(er.name(), n -> new double[2]);
                    acc[0] += er.score();
                    acc[1] += 1;
                }
            }
        }
        Map<String, Map<String, Double>> means = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, double[]>> e : sums.entrySet()) {
            Map<String, Double> evals = new LinkedHashMap<>();
            for (Map.Entry<String, double[]> ev : e.getValue().entrySet()) {
                double[] acc = ev.getValue();
                evals.put(ev.getKey(), round6(acc[0] / acc[1]));
            }
            means.put(e.getKey(), evals);
        }
        return means;
    }

    /**
     * Fires guard 2 and appends a case for each item whose worst raw per-evaluator drop is strictly
     * below {@code -severityMargin}. Only the breaching evaluators become drops.
     *
     * @return true if any shared item breached the margin
     */
    private static boolean collectGuard2(
            BaselineFile baseline,
            Map<String, Map<String, Double>> candidateMeans,
            double severityMargin,
            List<GateVerdict.RegressedCase> out) {
        boolean fired = false;
        for (BaselineItem item : baseline.items()) {
            Map<String, Double> candEvals = candidateMeans.get(item.key());
            if (candEvals == null) {
                continue; // removed item — not a guard-2 concern
            }
            List<GateVerdict.EvaluatorDrop> drops = new ArrayList<>();
            for (EvaluatorEntry e : item.evaluators()) {
                Double cm = candEvals.get(e.name());
                if (cm == null) {
                    continue; // missing evaluator — handled by the removed-evaluator check
                }
                double delta = round6(cm - e.score());
                if (delta < -severityMargin) {
                    drops.add(new GateVerdict.EvaluatorDrop(e.name(), e.score(), cm, delta));
                }
            }
            if (!drops.isEmpty()) {
                fired = true;
                drops.sort((a, b) -> a.evaluator().compareTo(b.evaluator()));
                out.add(new GateVerdict.RegressedCase(
                        isDatasetItemKey(item.key()) ? item.key() : null, item.key(), drops));
            }
        }
        return fired;
    }

    private static List<GateVerdict.RegressedCase> guard1Cases(RunComparisonResult result) {
        List<GateVerdict.RegressedCase> cases = new ArrayList<>();
        for (ItemComparison item : result.items()) {
            if (item.status() != ComparisonStatus.REGRESSED) {
                continue;
            }
            List<GateVerdict.EvaluatorDrop> drops = item.evaluatorDeltas().stream()
                    .filter(d -> d.status() == ComparisonStatus.REGRESSED)
                    .map(d -> new GateVerdict.EvaluatorDrop(
                            d.evaluatorName(), d.baselineMean(), d.candidateMean(), d.delta()))
                    .toList();
            String key = item.key();
            cases.add(new GateVerdict.RegressedCase(isDatasetItemKey(key) ? key : null, key, drops));
        }
        return cases;
    }

    /**
     * Fails (or warns) when an evaluator present in the baseline is absent from the candidate. Guard
     * 1 cannot see this: a one-sided evaluator co-occurs on no shared item and is reported UNCHANGED.
     *
     * @return true if the gate must fail because of a removed evaluator
     */
    private static boolean checkRemovedEvaluators(
            BaselineFile baseline, List<RunResult> candidateRuns, GateConfig config, List<String> warnings) {
        TreeSet<String> removed = new TreeSet<>(evaluatorNames(baseline));
        removed.removeAll(candidateEvaluatorNames(candidateRuns));
        for (String name : removed) {
            warnings.add("Evaluator '" + name
                    + "' is in the baseline but missing from the candidate; a dropped evaluator hides any"
                    + " regression it would have caught. Re-baseline to accept, or restore the evaluator.");
        }
        return !removed.isEmpty() && config.onRemovedEvaluator() == GateConfig.RemovedEvaluatorPolicy.FAIL;
    }

    /** Warns once per evaluator whose stored baseline threshold differs from the candidate's. */
    private static void checkThresholdDrift(
            BaselineFile baseline, List<RunResult> candidateRuns, List<String> warnings) {
        Map<String, Double> baselineThresholds = new LinkedHashMap<>();
        for (BaselineItem item : baseline.items()) {
            for (EvaluatorEntry e : item.evaluators()) {
                baselineThresholds.putIfAbsent(e.name(), e.threshold());
            }
        }
        Map<String, Double> candidateThresholds = candidateThresholds(candidateRuns);
        TreeSet<String> shared = new TreeSet<>(baselineThresholds.keySet());
        shared.retainAll(candidateThresholds.keySet());
        for (String name : shared) {
            Double bt = baselineThresholds.get(name);
            Double ct = candidateThresholds.get(name);
            if (!Objects.equals(bt, ct)) {
                warnings.add("Evaluator '" + name + "' threshold changed (baseline " + bt + " vs candidate "
                        + ct + "); pass/fail is compared under each side's own threshold, so the pass-rate"
                        + " comparison may be apples-to-oranges. Re-baseline to realign.");
            }
        }
    }

    private static TreeSet<String> evaluatorNames(BaselineFile baseline) {
        TreeSet<String> names = new TreeSet<>();
        for (BaselineItem item : baseline.items()) {
            for (EvaluatorEntry e : item.evaluators()) {
                names.add(e.name());
            }
        }
        return names;
    }

    private static TreeSet<String> candidateEvaluatorNames(List<RunResult> runs) {
        TreeSet<String> names = new TreeSet<>();
        for (RunResult run : runs) {
            for (ItemResult item : run.itemResults()) {
                for (EvalResult er : item.evalResults()) {
                    names.add(er.name());
                }
            }
        }
        return names;
    }

    /** First non-null candidate threshold per evaluator (consistent across a run for an evaluator). */
    private static Map<String, Double> candidateThresholds(List<RunResult> runs) {
        Map<String, Double> thresholds = new LinkedHashMap<>();
        for (RunResult run : runs) {
            for (ItemResult item : run.itemResults()) {
                for (EvalResult er : item.evalResults()) {
                    if (er.threshold() != null) {
                        thresholds.putIfAbsent(er.name(), er.threshold());
                    }
                }
            }
        }
        return thresholds;
    }

    private static boolean isDatasetItemKey(String key) {
        return key != null && !key.startsWith("item-");
    }

    private static double round6(double value) {
        return Math.round(value * PRECISION_SCALE) / PRECISION_SCALE;
    }
}
