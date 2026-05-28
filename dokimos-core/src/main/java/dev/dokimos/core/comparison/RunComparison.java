package dev.dokimos.core.comparison;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.ItemResult;
import dev.dokimos.core.RunResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Regression-comparison engine that compares a baseline set of runs against a candidate set.
 * <p>
 * Each side may contain one or more runs (repetitions). Items are grouped by an item-identity key,
 * aggregated across repetitions into a per-item pass-probability and per-evaluator mean, then
 * paired across sides by key. The engine emits per-evaluator and overall deltas classified as
 * IMPROVED, REGRESSED, or UNCHANGED, each backed by a significance test.
 * <p>
 * For single-run binary outcomes the pass-rate test uses McNemar's test with continuity correction;
 * otherwise a paired sign-flip permutation test with a bootstrap percentile confidence interval. A
 * change is flagged only when {@code |delta| > epsilon} and the test is significant at {@code alpha}.
 * <p>
 * Randomized procedures are deterministic for a fixed seed and evaluator set; the shared
 * {@link Random} is consumed in evaluator-name order, so adding or removing evaluators shifts
 * p-values of the others.
 */
public final class RunComparison {

    private static final double PRECISION_SCALE = 1_000_000.0;

    private final double epsilon;
    private final double alpha;
    private final long seed;
    private final int permutationIterations;
    private final int bootstrapIterations;
    private final Function<ItemResult, String> itemKey;

    private RunComparison(Builder builder) {
        this.epsilon = builder.epsilon;
        this.alpha = builder.alpha;
        this.seed = builder.seed;
        this.permutationIterations = builder.permutationIterations;
        this.bootstrapIterations = builder.bootstrapIterations;
        this.itemKey = builder.itemKey;
    }

    /** New builder with default configuration. */
    public static Builder builder() {
        return new Builder();
    }

    /** Engine with default settings. */
    public static RunComparison create() {
        return builder().build();
    }

    private static double round(double value) {
        return Math.round(value * PRECISION_SCALE) / PRECISION_SCALE;
    }

    /**
     * Compares baseline runs against candidate runs. Either list may be empty.
     *
     * @throws NullPointerException if either argument is null
     */
    public RunComparisonResult compare(List<RunResult> baseline, List<RunResult> candidate) {
        if (baseline == null || candidate == null) {
            throw new NullPointerException("baseline and candidate must not be null");
        }

        Map<String, ItemAggregate> baselineItems = aggregate(baseline);
        Map<String, ItemAggregate> candidateItems = aggregate(candidate);

        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(baselineItems.keySet());
        allKeys.addAll(candidateItems.keySet());

        Random random = new Random(seed);

        List<Double> baselinePairedProbs = new ArrayList<>();
        List<Double> candidatePairedProbs = new ArrayList<>();
        // Per-evaluator paired scores across items present on both sides.
        Map<String, List<Double>> evaluatorBaseline = new LinkedHashMap<>();
        Map<String, List<Double>> evaluatorCandidate = new LinkedHashMap<>();
        Set<String> evaluatorNames = new TreeSet<>();

        int added = 0;
        int removed = 0;

        // First pass: emit ADDED/REMOVED, collect paired data. Shared-item classification is
        // deferred until overall per-evaluator significance is known.
        List<ItemComparison> items = new ArrayList<>();
        List<PairedItem> pairedItems = new ArrayList<>();

        for (String key : allKeys) {
            ItemAggregate base = baselineItems.get(key);
            ItemAggregate cand = candidateItems.get(key);

            if (base == null) {
                items.add(new ItemComparison(
                        key,
                        ComparisonStatus.ADDED,
                        null,
                        round(cand.passProbability),
                        false,
                        evaluatorDeltasForMissingSide(cand, false)));
                added++;
                continue;
            }
            if (cand == null) {
                items.add(new ItemComparison(
                        key,
                        ComparisonStatus.REMOVED,
                        round(base.passProbability),
                        null,
                        false,
                        evaluatorDeltasForMissingSide(base, true)));
                removed++;
                continue;
            }

            baselinePairedProbs.add(base.passProbability);
            candidatePairedProbs.add(cand.passProbability);

            boolean passFlip = Math.round(base.passProbability) != Math.round(cand.passProbability);

            Set<String> itemEvaluators = new TreeSet<>();
            itemEvaluators.addAll(base.evaluatorMeans.keySet());
            itemEvaluators.addAll(cand.evaluatorMeans.keySet());
            for (String ev : itemEvaluators) {
                evaluatorNames.add(ev);
                Double bMean = base.evaluatorMeans.get(ev);
                Double cMean = cand.evaluatorMeans.get(ev);
                if (bMean != null && cMean != null) {
                    evaluatorBaseline
                            .computeIfAbsent(ev, k -> new ArrayList<>())
                            .add(bMean);
                    evaluatorCandidate
                            .computeIfAbsent(ev, k -> new ArrayList<>())
                            .add(cMean);
                }
            }

            // Reserve output slot; status and deltas are filled in after the gating pass.
            int slot = items.size();
            items.add(null);
            pairedItems.add(new PairedItem(slot, key, base, cand, passFlip, itemEvaluators));
        }

        boolean binarySingleRun = isBinarySingleRun(baseline) && isBinarySingleRun(candidate);
        SignificanceResult passRateSignificance = passRateSignificance(
                baselinePairedProbs,
                candidatePairedProbs,
                baselineItems,
                candidateItems,
                allKeys,
                binarySingleRun,
                random);

        // Top-line rate covers full item sets (paired plus unpaired); the significance test above
        // ran on shared items only.
        double baselinePassRate = meanOf(baselineItems);
        double candidatePassRate = meanOf(candidateItems);
        double passRateDelta = round(candidatePassRate - baselinePassRate);

        // Overall pass rate is a first-class regression signal: a significant drop beyond epsilon
        // counts even when no evaluator score moved (binary flips with unchanged scores).
        boolean passRateSignificant = passRateSignificance != null && passRateSignificance.significant();
        boolean passRateRegressed = passRateSignificant && passRateDelta < -epsilon;
        boolean passRateImproved = passRateSignificant && passRateDelta > epsilon;

        List<EvaluatorDelta> overallEvaluatorDeltas = new ArrayList<>();
        // Overall verdict per evaluator; gates per-item classification.
        Map<String, EvaluatorDelta> overallByEvaluator = new LinkedHashMap<>();
        Set<String> significantRegressedEvaluators = new LinkedHashSet<>();
        Set<String> significantImprovedEvaluators = new LinkedHashSet<>();
        int significantImproved = 0;
        int significantRegressed = 0;
        for (String ev : evaluatorNames) {
            List<Double> bScores = evaluatorBaseline.get(ev);
            List<Double> cScores = evaluatorCandidate.get(ev);
            if (bScores == null || cScores == null || bScores.isEmpty()) {
                // Evaluator never co-occurred on a shared item; report UNCHANGED with no test.
                EvaluatorDelta delta = new EvaluatorDelta(
                        ev,
                        null,
                        null,
                        0.0,
                        ComparisonStatus.UNCHANGED,
                        SignificanceResult.notSignificant("permutation"));
                overallEvaluatorDeltas.add(delta);
                overallByEvaluator.put(ev, delta);
                continue;
            }
            EvaluatorDelta delta = evaluatorDelta(ev, bScores, cScores, random);
            overallEvaluatorDeltas.add(delta);
            overallByEvaluator.put(ev, delta);
            boolean significant =
                    delta.significance() != null && delta.significance().significant();
            if (significant) {
                if (delta.status() == ComparisonStatus.IMPROVED) {
                    significantImproved++;
                    significantImprovedEvaluators.add(ev);
                } else if (delta.status() == ComparisonStatus.REGRESSED) {
                    significantRegressed++;
                    significantRegressedEvaluators.add(ev);
                }
            }
        }

        // Second pass: classify each shared item against the gated overall verdicts.
        int improved = 0;
        int regressed = 0;
        int unchanged = 0;
        for (PairedItem paired : pairedItems) {
            ItemComparison comparison = classifyPairedItem(
                    paired,
                    overallByEvaluator,
                    significantRegressedEvaluators,
                    significantImprovedEvaluators,
                    passRateRegressed,
                    passRateImproved);
            items.set(paired.slot(), comparison);
            switch (comparison.status()) {
                case REGRESSED -> regressed++;
                case IMPROVED -> improved++;
                default -> unchanged++;
            }
        }

        return new RunComparisonResult(
                round(baselinePassRate),
                round(candidatePassRate),
                passRateDelta,
                passRateSignificance,
                passRateRegressed,
                passRateImproved,
                improved,
                regressed,
                unchanged,
                added,
                removed,
                significantImproved,
                significantRegressed,
                overallEvaluatorDeltas,
                items);
    }

    private SignificanceResult passRateSignificance(
            List<Double> baselineProbs,
            List<Double> candidateProbs,
            Map<String, ItemAggregate> baselineItems,
            Map<String, ItemAggregate> candidateItems,
            Set<String> allKeys,
            boolean binarySingleRun,
            Random random) {
        if (baselineProbs.size() < 2) {
            return SignificanceResult.notSignificant(binarySingleRun ? "mcnemar" : "permutation");
        }

        if (binarySingleRun) {
            int b = 0;
            int c = 0;
            for (String key : allKeys) {
                ItemAggregate base = baselineItems.get(key);
                ItemAggregate cand = candidateItems.get(key);
                if (base == null || cand == null) {
                    continue;
                }
                boolean basePass = base.passProbability >= 0.5;
                boolean candPass = cand.passProbability >= 0.5;
                if (basePass && !candPass) {
                    b++;
                } else if (!basePass && candPass) {
                    c++;
                }
            }
            double p = Statistics.mcnemarPValue(b, c);
            return new SignificanceResult("mcnemar", round(p), p < alpha, null, null);
        }

        double[] deltas = pairedDeltas(baselineProbs, candidateProbs);
        double p = Statistics.permutationPValue(deltas, permutationIterations, random);
        double[] ci = Statistics.bootstrapMeanCi(deltas, bootstrapIterations, random);
        return new SignificanceResult(
                "permutation", round(p), p < alpha, ci != null ? ci[0] : null, ci != null ? ci[1] : null);
    }

    private EvaluatorDelta evaluatorDelta(String name, List<Double> baseline, List<Double> candidate, Random random) {
        double baselineMean = mean(baseline);
        double candidateMean = mean(candidate);
        double delta = candidateMean - baselineMean;

        double[] deltas = pairedDeltas(baseline, candidate);
        double p = Statistics.permutationPValue(deltas, permutationIterations, random);
        double[] ci = Statistics.bootstrapMeanCi(deltas, bootstrapIterations, random);
        boolean significant = p < alpha;
        SignificanceResult sig = new SignificanceResult(
                "permutation", round(p), significant, ci != null ? ci[0] : null, ci != null ? ci[1] : null);

        ComparisonStatus status = classify(delta, significant);
        return new EvaluatorDelta(name, round(baselineMean), round(candidateMean), round(delta), status, sig);
    }

    // Classifies a shared item against the overall (significance-gated) verdicts. An item is
    // REGRESSED when a significant-regressed evaluator dropped beyond epsilon on it, or when the
    // pass rate is significantly regressed and the item flipped to failing; IMPROVED is symmetric.
    // Regression takes precedence. Otherwise UNCHANGED.
    private ItemComparison classifyPairedItem(
            PairedItem paired,
            Map<String, EvaluatorDelta> overallByEvaluator,
            Set<String> significantRegressedEvaluators,
            Set<String> significantImprovedEvaluators,
            boolean passRateRegressed,
            boolean passRateImproved) {
        ItemAggregate base = paired.base();
        ItemAggregate cand = paired.cand();

        List<EvaluatorDelta> itemDeltas = new ArrayList<>();
        boolean itemRegressed = false;
        boolean itemImproved = false;
        for (String ev : paired.evaluators()) {
            Double bMean = base.evaluatorMeans.get(ev);
            Double cMean = cand.evaluatorMeans.get(ev);
            EvaluatorDelta overall = overallByEvaluator.get(ev);
            itemDeltas.add(itemEvaluatorDelta(ev, bMean, cMean, overall));

            if (bMean == null || cMean == null) {
                continue;
            }
            double delta = cMean - bMean;
            if (Math.abs(delta) <= epsilon) {
                continue;
            }
            if (delta < 0 && significantRegressedEvaluators.contains(ev)) {
                itemRegressed = true;
            } else if (delta > 0 && significantImprovedEvaluators.contains(ev)) {
                itemImproved = true;
            }
        }

        // Significant pass-rate move promotes a pass/fail flip to a first-class item verdict.
        if (paired.passFlip()) {
            boolean passDrop = base.passProbability > cand.passProbability;
            if (passRateRegressed && passDrop) {
                itemRegressed = true;
            } else if (passRateImproved && !passDrop) {
                itemImproved = true;
            }
        }

        ComparisonStatus status;
        if (itemRegressed) {
            status = ComparisonStatus.REGRESSED;
        } else if (itemImproved) {
            status = ComparisonStatus.IMPROVED;
        } else {
            status = ComparisonStatus.UNCHANGED;
        }

        return new ItemComparison(
                paired.key(),
                status,
                round(base.passProbability),
                round(cand.passProbability),
                paired.passFlip(),
                itemDeltas);
    }

    // Per-item evaluator delta whose significance mirrors the evaluator's overall verdict; the
    // p-value and CI are reused from the overall result.
    private EvaluatorDelta itemEvaluatorDelta(
            String name, Double baselineMean, Double candidateMean, EvaluatorDelta overall) {
        if (baselineMean == null || candidateMean == null) {
            // Evaluator present on only one side of this item; no test.
            return new EvaluatorDelta(
                    name,
                    baselineMean,
                    candidateMean,
                    0.0,
                    ComparisonStatus.UNCHANGED,
                    SignificanceResult.notSignificant("permutation"));
        }
        double delta = candidateMean - baselineMean;
        ComparisonStatus overallStatus = overall != null ? overall.status() : ComparisonStatus.UNCHANGED;
        boolean overallSignificant = overall != null
                && overall.significance() != null
                && overall.significance().significant()
                && (overallStatus == ComparisonStatus.IMPROVED || overallStatus == ComparisonStatus.REGRESSED);
        ComparisonStatus status;
        if (!overallSignificant || Math.abs(delta) <= epsilon) {
            status = ComparisonStatus.UNCHANGED;
        } else if (delta > 0 && overallStatus == ComparisonStatus.IMPROVED) {
            status = ComparisonStatus.IMPROVED;
        } else if (delta < 0 && overallStatus == ComparisonStatus.REGRESSED) {
            status = ComparisonStatus.REGRESSED;
        } else {
            status = ComparisonStatus.UNCHANGED;
        }
        SignificanceResult significance = overallSignificant && overall.significance() != null
                ? overall.significance()
                : SignificanceResult.notSignificant("permutation");
        return new EvaluatorDelta(name, round(baselineMean), round(candidateMean), round(delta), status, significance);
    }

    private List<EvaluatorDelta> evaluatorDeltasForMissingSide(ItemAggregate present, boolean baselineSide) {
        List<EvaluatorDelta> deltas = new ArrayList<>();
        for (Map.Entry<String, Double> entry : present.evaluatorMeans.entrySet()) {
            Double bMean = baselineSide ? round(entry.getValue()) : null;
            Double cMean = baselineSide ? null : round(entry.getValue());
            deltas.add(new EvaluatorDelta(
                    entry.getKey(),
                    bMean,
                    cMean,
                    0.0,
                    baselineSide ? ComparisonStatus.REMOVED : ComparisonStatus.ADDED,
                    SignificanceResult.notSignificant("permutation")));
        }
        return deltas;
    }

    private ComparisonStatus classify(double delta, boolean significant) {
        if (Math.abs(delta) <= epsilon || !significant) {
            return ComparisonStatus.UNCHANGED;
        }
        return delta > 0 ? ComparisonStatus.IMPROVED : ComparisonStatus.REGRESSED;
    }

    // Groups item results by key across a side's runs and aggregates pass-probability and
    // per-evaluator means. Keys must be unique within a single run (duplicates break pairing). A
    // key absent from some runs is averaged only over the runs containing it.
    private Map<String, ItemAggregate> aggregate(List<RunResult> runs) {
        Map<String, List<ItemResult>> grouped = new LinkedHashMap<>();
        for (RunResult run : runs) {
            int index = 0;
            Set<String> seenInRun = new LinkedHashSet<>();
            for (ItemResult item : run.itemResults()) {
                String key = resolveKey(item, index);
                if (!seenInRun.add(key)) {
                    throw new IllegalArgumentException("Duplicate item key '" + key
                            + "' within a single run; item keys must be unique per run because they identify"
                            + " dataset items and duplicates break pairing across sides");
                }
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
                index++;
            }
        }

        Map<String, ItemAggregate> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<ItemResult>> entry : grouped.entrySet()) {
            List<ItemResult> reps = entry.getValue();
            // Denominator is runs containing the key (one observation per run).
            long passing = reps.stream().filter(ItemResult::success).count();
            double passProbability = (double) passing / reps.size();

            Map<String, List<Double>> scoresByEvaluator = new LinkedHashMap<>();
            for (ItemResult rep : reps) {
                for (EvalResult er : rep.evalResults()) {
                    scoresByEvaluator
                            .computeIfAbsent(er.name(), k -> new ArrayList<>())
                            .add(er.score());
                }
            }
            Map<String, Double> evaluatorMeans = new LinkedHashMap<>();
            for (Map.Entry<String, List<Double>> e : scoresByEvaluator.entrySet()) {
                evaluatorMeans.put(e.getKey(), mean(e.getValue()));
            }
            result.put(entry.getKey(), new ItemAggregate(passProbability, evaluatorMeans));
        }
        return result;
    }

    private String resolveKey(ItemResult item, int index) {
        if (itemKey != null) {
            String key = itemKey.apply(item);
            if (key != null) {
                return key;
            }
        }
        return "item-" + index;
    }

    private boolean isBinarySingleRun(List<RunResult> runs) {
        return runs.size() == 1;
    }

    private static double[] pairedDeltas(List<Double> baseline, List<Double> candidate) {
        double[] deltas = new double[baseline.size()];
        for (int i = 0; i < deltas.length; i++) {
            deltas[i] = candidate.get(i) - baseline.get(i);
        }
        return deltas;
    }

    private static double mean(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    private static double meanOf(Map<String, ItemAggregate> items) {
        if (items.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (ItemAggregate a : items.values()) {
            sum += a.passProbability;
        }
        return sum / items.size();
    }

    /** Per-item aggregate over repetitions. */
    private record ItemAggregate(double passProbability, Map<String, Double> evaluatorMeans) {}

    /** Shared item awaiting significance-gated classification; {@code slot} indexes into the output list. */
    private record PairedItem(
            int slot, String key, ItemAggregate base, ItemAggregate cand, boolean passFlip, Set<String> evaluators) {}

    /** Builder for {@link RunComparison}. */
    public static final class Builder {
        private double epsilon = 0.001;
        private double alpha = 0.05;
        private long seed = 42L;
        private int permutationIterations = 10_000;
        private int bootstrapIterations = 10_000;
        private Function<ItemResult, String> itemKey = null;

        /** Minimum absolute delta below which a change counts as UNCHANGED. Default 0.001. */
        public Builder epsilon(double epsilon) {
            this.epsilon = epsilon;
            return this;
        }

        /** Significance threshold; a change is significant when its p-value is below alpha. Default 0.05. */
        public Builder alpha(double alpha) {
            this.alpha = alpha;
            return this;
        }

        /** Seed for permutation and bootstrap procedures. Default 42. */
        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        /** Permutation iteration count. Default 10000. */
        public Builder permutationIterations(int permutationIterations) {
            this.permutationIterations = permutationIterations;
            return this;
        }

        /** Bootstrap iteration count. Default 10000. */
        public Builder bootstrapIterations(int bootstrapIterations) {
            this.bootstrapIterations = bootstrapIterations;
            return this;
        }

        /**
         * Function that derives an item-identity key from an item result. When null (default),
         * items are paired by position index ("item-&lt;index&gt;").
         */
        public Builder itemKey(Function<ItemResult, String> itemKey) {
            this.itemKey = itemKey;
            return this;
        }

        /**
         * @throws IllegalArgumentException if alpha is not in (0, 1), epsilon is negative, or
         *     either iteration count is less than 1
         */
        public RunComparison build() {
            if (!(alpha > 0.0 && alpha < 1.0)) {
                throw new IllegalArgumentException("alpha must be in the open interval (0, 1), was " + alpha);
            }
            if (!(epsilon >= 0.0)) {
                throw new IllegalArgumentException("epsilon must be greater than or equal to 0, was " + epsilon);
            }
            if (permutationIterations < 1) {
                throw new IllegalArgumentException(
                        "permutationIterations must be at least 1, was " + permutationIterations);
            }
            if (bootstrapIterations < 1) {
                throw new IllegalArgumentException(
                        "bootstrapIterations must be at least 1, was " + bootstrapIterations);
            }
            return new RunComparison(this);
        }
    }
}
