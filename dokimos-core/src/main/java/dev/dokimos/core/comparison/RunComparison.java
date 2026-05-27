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
 * Regression-comparison engine that compares a baseline set of runs against a candidate set of
 * runs.
 * <p>
 * Each side may contain one or more runs (repetitions). For each side the engine groups item
 * results by an item-identity key, aggregates across repetitions into a per-item pass-probability
 * (fraction of reps that passed) and a per-evaluator mean score, then pairs items across sides by
 * key. It computes per-evaluator and overall deltas, classifies each as IMPROVED, REGRESSED, or
 * UNCHANGED, and decides statistical significance.
 * <p>
 * When both sides have exactly one run and outcomes are binary pass/fail, the overall pass-rate
 * test uses McNemar's test (with continuity correction). Otherwise it uses a paired sign-flip
 * permutation test plus a bootstrap percentile confidence interval. Both randomized procedures are
 * deterministic for a given seed.
 * <p>
 * A change is only flagged as a real regression or improvement when its absolute delta exceeds the
 * configured epsilon and it is statistically significant; otherwise it is UNCHANGED. This avoids
 * flaky gating on small, noisy fluctuations.
 * <p>
 * Determinism holds for a fixed evaluator set: the shared seeded {@link Random} is consumed in
 * evaluator-name order, so adding or removing evaluators can shift the p-values of other evaluators.
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

    /**
     * Creates a new builder with default configuration.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates an engine with all default settings.
     *
     * @return a default engine
     */
    public static RunComparison create() {
        return builder().build();
    }

    private static double round(double value) {
        return Math.round(value * PRECISION_SCALE) / PRECISION_SCALE;
    }

    /**
     * Compares baseline runs against candidate runs.
     *
     * @param baseline the baseline runs (repetitions), may be empty
     * @param candidate the candidate runs (repetitions), may be empty
     * @return the comparison result
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
        // per-evaluator paired score deltas across items present in both sides for that evaluator
        Map<String, List<Double>> evaluatorBaseline = new LinkedHashMap<>();
        Map<String, List<Double>> evaluatorCandidate = new LinkedHashMap<>();
        Set<String> evaluatorNames = new TreeSet<>();

        int added = 0;
        int removed = 0;

        // First pass: emit ADDED/REMOVED items immediately and collect paired data. Per-item status
        // for shared items is deferred until overall per-evaluator significance is known, so that
        // classification is significance-gated rather than driven by raw per-item noise.
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

            // Reserve this item's slot in output order; status and deltas are filled after gating.
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

        // Top-line pass rate covers each side's full item set (paired plus unpaired), so it is
        // well-defined regardless of pairing. The significance test below runs on the paired items
        // only, since only shared cases can be pair-tested.
        double baselinePassRate = meanOf(baselineItems);
        double candidatePassRate = meanOf(candidateItems);
        double passRateDelta = round(candidatePassRate - baselinePassRate);

        // The overall pass rate is a first-class regression signal: a statistically significant drop
        // beyond epsilon is a regression even when no per-evaluator score moved (for example, binary
        // pass/fail flips with unchanged scores).
        boolean passRateSignificant = passRateSignificance != null && passRateSignificance.significant();
        boolean passRateRegressed = passRateSignificant && passRateDelta < -epsilon;
        boolean passRateImproved = passRateSignificant && passRateDelta > epsilon;

        List<EvaluatorDelta> overallEvaluatorDeltas = new ArrayList<>();
        // Overall per-evaluator verdict, used to gate per-item classification.
        Map<String, EvaluatorDelta> overallByEvaluator = new LinkedHashMap<>();
        Set<String> significantRegressedEvaluators = new LinkedHashSet<>();
        Set<String> significantImprovedEvaluators = new LinkedHashSet<>();
        int significantImproved = 0;
        int significantRegressed = 0;
        for (String ev : evaluatorNames) {
            List<Double> bScores = evaluatorBaseline.get(ev);
            List<Double> cScores = evaluatorCandidate.get(ev);
            if (bScores == null || cScores == null || bScores.isEmpty()) {
                // evaluator never appeared on both sides for the same item; report unchanged, no test
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

        // Second pass: classify each shared item, gated by overall per-evaluator significance.
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

    /**
     * Classifies a single shared item, gated by the overall significance verdicts.
     * <p>
     * An item is REGRESSED when (a) an evaluator that the overall comparison flagged as a significant
     * regression also dropped beyond epsilon on this item, or (b) the overall pass rate is
     * significantly regressed and this item flipped from passing to failing. It is IMPROVED
     * symmetrically (a significant-improved evaluator rose beyond epsilon on this item, or the overall
     * pass rate is significantly improved and this item flipped from failing to passing), with
     * regression taking precedence. Otherwise it is UNCHANGED. Each per-evaluator delta carries the
     * overall significance verdict for that evaluator, so noisy items are never flagged unless the
     * evaluator (or the overall pass rate) is significant across the whole comparison.
     */
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

        // A significant overall pass-rate move makes a pass/fail flip a first-class item verdict, even
        // when no evaluator score moved.
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

    /**
     * Builds a per-item evaluator delta whose significance reflects the evaluator's overall verdict.
     * An item's evaluator delta is significant iff the overall per-evaluator comparison for that
     * evaluator is significant; the per-item status mirrors that overall verdict and reuses the
     * overall significance result (p-value and confidence interval).
     */
    private EvaluatorDelta itemEvaluatorDelta(
            String name, Double baselineMean, Double candidateMean, EvaluatorDelta overall) {
        if (baselineMean == null || candidateMean == null) {
            // evaluator present on only one side of this item; report without a test
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

    /**
     * Groups item results by key across all runs of a side and aggregates pass-probability and
     * per-evaluator mean scores.
     * <p>
     * Each key must be unique within a single run, since a key identifies one dataset item and a run
     * is one repetition over the dataset. A duplicate key within one run is rejected because it would
     * break pairing across sides.
     * <p>
     * Aggregation averages one value per run on the side: a key's pass-probability is the number of
     * runs containing the key that passed it divided by the number of runs containing the key, and a
     * key's per-evaluator mean is the mean of one per-run value per run that contains the key. A key
     * present in fewer runs is therefore averaged only over the runs in which it appears, so missing
     * repetitions do not inflate the estimate.
     */
    private Map<String, ItemAggregate> aggregate(List<RunResult> runs) {
        // Preserve first-seen key order while collecting one observation per run for each key.
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
            // Denominator is the number of runs containing the key (one observation per run), so a key
            // present in fewer runs is averaged only over those runs.
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

    /** Per-item aggregate over repetitions: pass-probability plus per-evaluator mean scores. */
    private record ItemAggregate(double passProbability, Map<String, Double> evaluatorMeans) {}

    /**
     * A shared item awaiting significance-gated classification. The slot records its position in the
     * output item list so the final status can be filled in after overall significance is known.
     */
    private record PairedItem(
            int slot, String key, ItemAggregate base, ItemAggregate cand, boolean passFlip, Set<String> evaluators) {}

    /**
     * Builder for {@link RunComparison}.
     */
    public static final class Builder {
        private double epsilon = 0.001;
        private double alpha = 0.05;
        private long seed = 42L;
        private int permutationIterations = 10_000;
        private int bootstrapIterations = 10_000;
        private Function<ItemResult, String> itemKey = null;

        /**
         * Sets the minimum absolute delta below which a change is considered noise (UNCHANGED).
         *
         * @param epsilon the epsilon threshold (default 0.001)
         * @return this builder
         */
        public Builder epsilon(double epsilon) {
            this.epsilon = epsilon;
            return this;
        }

        /**
         * Sets the significance threshold. A change is significant when its p-value is below alpha.
         *
         * @param alpha the alpha level (default 0.05)
         * @return this builder
         */
        public Builder alpha(double alpha) {
            this.alpha = alpha;
            return this;
        }

        /**
         * Sets the random seed for permutation and bootstrap procedures so results are deterministic.
         *
         * @param seed the seed (default 42)
         * @return this builder
         */
        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Sets the number of permutation iterations.
         *
         * @param permutationIterations the iteration count (default 10000)
         * @return this builder
         */
        public Builder permutationIterations(int permutationIterations) {
            this.permutationIterations = permutationIterations;
            return this;
        }

        /**
         * Sets the number of bootstrap iterations.
         *
         * @param bootstrapIterations the iteration count (default 10000)
         * @return this builder
         */
        public Builder bootstrapIterations(int bootstrapIterations) {
            this.bootstrapIterations = bootstrapIterations;
            return this;
        }

        /**
         * Sets a custom function that derives an item-identity key from an item result. When null
         * (the default), items are paired by their position index ("item-&lt;index&gt;").
         *
         * @param itemKey the key function, or null for positional pairing
         * @return this builder
         */
        public Builder itemKey(Function<ItemResult, String> itemKey) {
            this.itemKey = itemKey;
            return this;
        }

        /**
         * Builds the configured engine.
         *
         * @return a new engine
         * @throws IllegalArgumentException if alpha is not in the open interval (0, 1), epsilon is
         *     negative, or either iteration count is less than 1
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
