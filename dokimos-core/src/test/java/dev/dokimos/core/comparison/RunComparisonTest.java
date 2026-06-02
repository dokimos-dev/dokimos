package dev.dokimos.core.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.Example;
import dev.dokimos.core.ItemResult;
import dev.dokimos.core.RunResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunComparisonTest {

    private static final String EVALUATOR = "correctness";

    @Test
    void shouldFindNoRegressionsForIdenticalSides() {
        List<RunResult> side = continuousRun(0.8, 0.7, 0.9, 0.6, 0.85, 0.75, 0.95, 0.65);

        RunComparisonResult result = RunComparison.create().compare(side, copy(side));

        assertThat(result.passRateDelta()).isEqualTo(0.0);
        assertThat(result.hasRegressions()).isFalse();
        assertThat(result.regressedCount()).isZero();
        assertThat(result.improvedCount()).isZero();
        assertThat(result.items()).allSatisfy(item -> assertThat(item.status()).isEqualTo(ComparisonStatus.UNCHANGED));
        assertThat(result.evaluatorDeltas())
                .allSatisfy(d -> assertThat(d.status()).isEqualTo(ComparisonStatus.UNCHANGED));
    }

    @Test
    void shouldDetectConsistentRegression() {
        List<RunResult> baseline = continuousRun(0.9, 0.92, 0.88, 0.95, 0.91, 0.89, 0.93, 0.9, 0.94, 0.87);
        List<RunResult> candidate = continuousRun(0.5, 0.52, 0.48, 0.55, 0.51, 0.49, 0.53, 0.5, 0.54, 0.47);

        RunComparisonResult result = RunComparison.create().compare(baseline, candidate);

        assertThat(result.hasRegressions()).isTrue();
        EvaluatorDelta delta = result.evaluatorDeltas().get(0);
        assertThat(delta.status()).isEqualTo(ComparisonStatus.REGRESSED);
        assertThat(delta.significance().significant()).isTrue();
        assertThat(delta.delta()).isLessThan(0.0);
        assertThat(result.regressions()).contains(delta);
        assertThat(result.significantRegressedCount()).isEqualTo(1);
    }

    @Test
    void shouldNotFlagTinyNoisyWobble() {
        // Symmetric jitter around zero: tiny magnitude and statistically insignificant.
        List<RunResult> baseline = continuousRun(0.80, 0.81, 0.79, 0.80, 0.82, 0.78, 0.80, 0.81);
        List<RunResult> candidate = continuousRun(0.81, 0.80, 0.80, 0.79, 0.81, 0.79, 0.81, 0.80);

        RunComparisonResult result = RunComparison.create().compare(baseline, candidate);

        assertThat(result.hasRegressions()).isFalse();
        assertThat(result.significantRegressedCount()).isZero();
        // Significance-gated: noisy items must not be counted as regressions.
        assertThat(result.regressedCount()).isZero();
        EvaluatorDelta delta = result.evaluatorDeltas().get(0);
        assertThat(delta.status()).isEqualTo(ComparisonStatus.UNCHANGED);
    }

    @Test
    void shouldDetectConsistentImprovement() {
        List<RunResult> baseline = continuousRun(0.5, 0.52, 0.48, 0.55, 0.51, 0.49, 0.53, 0.5, 0.54, 0.47);
        List<RunResult> candidate = continuousRun(0.9, 0.92, 0.88, 0.95, 0.91, 0.89, 0.93, 0.9, 0.94, 0.87);

        RunComparisonResult result = RunComparison.create().compare(baseline, candidate);

        EvaluatorDelta delta = result.evaluatorDeltas().get(0);
        assertThat(delta.status()).isEqualTo(ComparisonStatus.IMPROVED);
        assertThat(delta.significance().significant()).isTrue();
        assertThat(delta.delta()).isGreaterThan(0.0);
        assertThat(result.improvements()).contains(delta);
        assertThat(result.significantImprovedCount()).isEqualTo(1);
        assertThat(result.hasRegressions()).isFalse();
    }

    @Test
    void shouldHandleEvaluatorPresentInOnlyOneSide() {
        // Baseline item has both evaluators, candidate item only has "correctness".
        ItemResult base = new ItemResult(
                Example.of("q", "a"),
                Map.of(),
                List.of(EvalResult.success("correctness", 0.8, ""), EvalResult.success("relevance", 0.9, "")));
        ItemResult cand =
                new ItemResult(Example.of("q", "a"), Map.of(), List.of(EvalResult.success("correctness", 0.8, "")));

        List<RunResult> baseline = List.of(new RunResult(0, List.of(base, base)));
        List<RunResult> candidate = List.of(new RunResult(0, List.of(cand, cand)));

        RunComparisonResult result = RunComparison.create().compare(baseline, candidate);

        // relevance only ever appears on the baseline side, so it is reported UNCHANGED without a test.
        EvaluatorDelta relevance = result.evaluatorDeltas().stream()
                .filter(d -> d.evaluatorName().equals("relevance"))
                .findFirst()
                .orElseThrow();
        assertThat(relevance.status()).isEqualTo(ComparisonStatus.UNCHANGED);
        assertThat(relevance.significance().significant()).isFalse();
        assertThat(result.hasRegressions()).isFalse();
    }

    @Test
    void shouldHandleEmptyBaseline() {
        List<RunResult> candidate = continuousRun(0.8, 0.7);

        RunComparisonResult result = RunComparison.create().compare(List.of(), candidate);

        assertThat(result.addedCount()).isEqualTo(2);
        assertThat(result.removedCount()).isZero();
        assertThat(result.baselinePassRate()).isEqualTo(0.0);
        assertThat(result.hasRegressions()).isFalse();
        assertThat(result.items()).allSatisfy(i -> assertThat(i.status()).isEqualTo(ComparisonStatus.ADDED));
    }

    @Test
    void shouldHandleEmptyCandidate() {
        List<RunResult> baseline = continuousRun(0.8, 0.7);

        RunComparisonResult result = RunComparison.create().compare(baseline, List.of());

        assertThat(result.removedCount()).isEqualTo(2);
        assertThat(result.addedCount()).isZero();
        assertThat(result.candidatePassRate()).isEqualTo(0.0);
        assertThat(result.hasRegressions()).isFalse();
        assertThat(result.items()).allSatisfy(i -> assertThat(i.status()).isEqualTo(ComparisonStatus.REMOVED));
    }

    @Test
    void shouldUseMcNemarForSingleRunBinaryFlips() {
        // 12 items pass-to-fail (b), 2 items fail-to-pass (c). Strongly significant.
        List<ItemResult> baseItems = new ArrayList<>();
        List<ItemResult> candItems = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            baseItems.add(binaryItem(true));
            candItems.add(binaryItem(false));
        }
        for (int i = 0; i < 2; i++) {
            baseItems.add(binaryItem(false));
            candItems.add(binaryItem(true));
        }
        // some concordant pairs that do not affect b/c
        for (int i = 0; i < 5; i++) {
            baseItems.add(binaryItem(true));
            candItems.add(binaryItem(true));
        }

        List<RunResult> baseline = List.of(new RunResult(0, baseItems));
        List<RunResult> candidate = List.of(new RunResult(0, candItems));

        RunComparisonResult result = RunComparison.create().compare(baseline, candidate);

        assertThat(result.passRateSignificance().method()).isEqualTo("mcnemar");
        assertThat(result.passRateSignificance().significant()).isTrue();
        assertThat(result.passRateSignificance().ciLow()).isNull();
        assertThat(result.passRateDelta()).isLessThan(0.0);
        // The pass rate is a first-class regression signal even though no evaluator score moved.
        assertThat(result.passRateRegressed()).isTrue();
        assertThat(result.hasRegressions()).isTrue();
        assertThat(result.regressedCount()).isGreaterThan(0);
    }

    @Test
    void shouldNotBeSignificantWithBalancedMcNemar() {
        // b == c, no net flip direction: not significant.
        List<ItemResult> baseItems = new ArrayList<>();
        List<ItemResult> candItems = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            baseItems.add(binaryItem(true));
            candItems.add(binaryItem(false));
        }
        for (int i = 0; i < 3; i++) {
            baseItems.add(binaryItem(false));
            candItems.add(binaryItem(true));
        }

        List<RunResult> baseline = List.of(new RunResult(0, baseItems));
        List<RunResult> candidate = List.of(new RunResult(0, candItems));

        RunComparisonResult result = RunComparison.create().compare(baseline, candidate);

        assertThat(result.passRateSignificance().method()).isEqualTo("mcnemar");
        assertThat(result.passRateSignificance().significant()).isFalse();
    }

    @Test
    void shouldUsePermutationAndAggregatePassProbabilityAcrossReps() {
        // Single item, 3 reps: passes 2 of 3 on baseline, 0 of 3 on candidate.
        ItemResult pass = binaryItem(true);
        ItemResult fail = binaryItem(false);

        List<RunResult> baseline = List.of(
                new RunResult(0, List.of(pass)), new RunResult(1, List.of(pass)), new RunResult(2, List.of(fail)));
        List<RunResult> candidate = List.of(
                new RunResult(0, List.of(fail)), new RunResult(1, List.of(fail)), new RunResult(2, List.of(fail)));

        RunComparisonResult result = RunComparison.create().compare(baseline, candidate);

        assertThat(result.passRateSignificance().method()).isEqualTo("permutation");
        ItemComparison item = result.items().get(0);
        assertThat(item.baselinePassProbability()).isCloseTo(0.666667, within(1e-6));
        assertThat(item.candidatePassProbability()).isEqualTo(0.0);
    }

    @Test
    void shouldUsePermutationForMultiRunConsistentRegression() {
        // 3 reps per side, 6 shared items, candidate consistently lower: exercises the permutation
        // path (not the <2-paired-items early exit) and should be significant.
        double[] baseItems = {0.90, 0.88, 0.92, 0.91, 0.89, 0.93};
        double[] candItems = {0.50, 0.48, 0.52, 0.51, 0.49, 0.53};

        RunComparison engine =
                RunComparison.builder().itemKey(item -> item.example().input()).build();

        List<RunResult> baseline = continuousReps(3, baseItems);
        List<RunResult> candidate = continuousReps(3, candItems);

        RunComparisonResult result = engine.compare(baseline, candidate);

        assertThat(result.passRateSignificance().method()).isEqualTo("permutation");
        assertThat(result.passRateSignificance().significant()).isTrue();
        EvaluatorDelta delta = result.evaluatorDeltas().get(0);
        assertThat(delta.status()).isEqualTo(ComparisonStatus.REGRESSED);
        assertThat(delta.significance().significant()).isTrue();
        assertThat(result.hasRegressions()).isTrue();
        // Every shared item dropped well beyond epsilon on a significant evaluator.
        assertThat(result.regressedCount()).isEqualTo(6);
    }

    @Test
    void shouldNotFlagMultiRunNoisyMultiItem() {
        // 3 reps per side, 6 shared items, symmetric jitter around zero: permutation path, not
        // significant, and no item gated as a regression.
        double[] baseItems = {0.80, 0.81, 0.79, 0.80, 0.82, 0.78};
        double[] candItems = {0.81, 0.79, 0.80, 0.79, 0.81, 0.79};

        RunComparison engine =
                RunComparison.builder().itemKey(item -> item.example().input()).build();

        List<RunResult> baseline = continuousReps(3, baseItems);
        List<RunResult> candidate = continuousReps(3, candItems);

        RunComparisonResult result = engine.compare(baseline, candidate);

        assertThat(result.passRateSignificance().method()).isEqualTo("permutation");
        assertThat(result.passRateSignificance().significant()).isFalse();
        assertThat(result.hasRegressions()).isFalse();
        assertThat(result.regressedCount()).isZero();
        assertThat(result.improvedCount()).isZero();
        assertThat(result.items()).allSatisfy(i -> assertThat(i.status()).isEqualTo(ComparisonStatus.UNCHANGED));
    }

    @Test
    void shouldAggregatePassProbabilityAcrossRepsOnMultiItemPermutationPath() {
        // Two shared items so the permutation path runs (not the single-item early exit). The first
        // item passes 2 of 3 reps on baseline, the second passes 0 of 3, and both fail on candidate.
        ItemResult passA = binaryItemKeyed("a", true);
        ItemResult failA = binaryItemKeyed("a", false);
        ItemResult failB = binaryItemKeyed("b", false);

        RunComparison engine =
                RunComparison.builder().itemKey(item -> item.example().input()).build();

        List<RunResult> baseline = List.of(
                new RunResult(0, List.of(passA, failB)),
                new RunResult(1, List.of(passA, failB)),
                new RunResult(2, List.of(failA, failB)));
        List<RunResult> candidate = List.of(
                new RunResult(0, List.of(failA, failB)),
                new RunResult(1, List.of(failA, failB)),
                new RunResult(2, List.of(failA, failB)));

        RunComparisonResult result = engine.compare(baseline, candidate);

        assertThat(result.passRateSignificance().method()).isEqualTo("permutation");
        ItemComparison itemA = byKey(result, "a");
        assertThat(itemA.baselinePassProbability()).isCloseTo(0.666667, within(1e-6));
        assertThat(itemA.candidatePassProbability()).isEqualTo(0.0);
    }

    @Test
    void shouldClassifyAddedAndRemovedItemsByKey() {
        ItemResult shared = continuousItem("shared", 0.8);
        ItemResult onlyBase = continuousItem("only-base", 0.7);
        ItemResult onlyCand = continuousItem("only-cand", 0.6);

        // Pair by example input so position does not matter.
        RunComparison engine =
                RunComparison.builder().itemKey(item -> item.example().input()).build();

        List<RunResult> baseline = List.of(new RunResult(0, List.of(shared, onlyBase)));
        List<RunResult> candidate = List.of(new RunResult(0, List.of(shared, onlyCand)));

        RunComparisonResult result = engine.compare(baseline, candidate);

        assertThat(result.addedCount()).isEqualTo(1);
        assertThat(result.removedCount()).isEqualTo(1);

        ItemComparison added = byKey(result, "only-cand");
        assertThat(added.status()).isEqualTo(ComparisonStatus.ADDED);
        assertThat(added.baselinePassProbability()).isNull();
        assertThat(added.candidatePassProbability()).isNotNull();

        ItemComparison removed = byKey(result, "only-base");
        assertThat(removed.status()).isEqualTo(ComparisonStatus.REMOVED);
        assertThat(removed.candidatePassProbability()).isNull();
        assertThat(removed.baselinePassProbability()).isNotNull();
    }

    @Test
    void shouldBeDeterministicAcrossRuns() {
        List<RunResult> baseline = continuousRun(0.8, 0.6, 0.7, 0.9, 0.5, 0.65, 0.75, 0.55);
        List<RunResult> candidate = continuousRun(0.6, 0.5, 0.55, 0.7, 0.4, 0.5, 0.6, 0.45);

        RunComparisonResult first = RunComparison.create().compare(baseline, candidate);
        RunComparisonResult second = RunComparison.create().compare(baseline, candidate);

        assertThat(first.passRateSignificance().pValue())
                .isEqualTo(second.passRateSignificance().pValue());
        assertThat(first.evaluatorDeltas().get(0).significance().pValue())
                .isEqualTo(second.evaluatorDeltas().get(0).significance().pValue());
        assertThat(first.evaluatorDeltas().get(0).significance().ciLow())
                .isEqualTo(second.evaluatorDeltas().get(0).significance().ciLow());
    }

    @Test
    void shouldDetectPassFlip() {
        ItemResult basePass = binaryItem(true);
        ItemResult candFail = binaryItem(false);

        List<RunResult> baseline = List.of(new RunResult(0, List.of(basePass)));
        List<RunResult> candidate = List.of(new RunResult(0, List.of(candFail)));

        RunComparisonResult result = RunComparison.create().compare(baseline, candidate);

        assertThat(result.items().get(0).passFlip()).isTrue();
    }

    @Test
    void shouldNotReportPassFlipWhenBothPass() {
        List<RunResult> baseline = List.of(new RunResult(0, List.of(binaryItem(true))));
        List<RunResult> candidate = List.of(new RunResult(0, List.of(binaryItem(true))));

        RunComparisonResult result = RunComparison.create().compare(baseline, candidate);

        assertThat(result.items().get(0).passFlip()).isFalse();
    }

    @Test
    void shouldGateOnPassRateWhenEvaluatorScoresUnchanged() {
        // Single run per side. Scores are identical between sides (no evaluator movement), but a
        // strong majority of items flip pass->fail. McNemar should be significant and drive the gate.
        List<ItemResult> baseItems = new ArrayList<>();
        List<ItemResult> candItems = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            // Same score on both sides, but pass on baseline and fail on candidate (threshold flip).
            baseItems.add(thresholdItem("q" + i, 0.7, 0.6)); // 0.7 >= 0.6 threshold -> pass
            candItems.add(thresholdItem("q" + i, 0.7, 0.8)); // 0.7 < 0.8 threshold -> fail
        }
        for (int i = 0; i < 5; i++) {
            baseItems.add(thresholdItem("p" + i, 0.9, 0.6)); // pass on both sides
            candItems.add(thresholdItem("p" + i, 0.9, 0.6));
        }

        RunComparison engine =
                RunComparison.builder().itemKey(item -> item.example().input()).build();

        List<RunResult> baseline = List.of(new RunResult(0, baseItems));
        List<RunResult> candidate = List.of(new RunResult(0, candItems));

        RunComparisonResult result = engine.compare(baseline, candidate);

        // No evaluator score moved, so the per-evaluator verdict is UNCHANGED.
        assertThat(result.evaluatorDeltas())
                .allSatisfy(d -> assertThat(d.status()).isEqualTo(ComparisonStatus.UNCHANGED));
        assertThat(result.significantRegressedCount()).isZero();
        // Scores are continuous, so the pass-rate test uses the permutation path; the pass rate alone
        // still drives the regression verdict.
        assertThat(result.passRateSignificance().method()).isEqualTo("permutation");
        assertThat(result.passRateRegressed()).isTrue();
        assertThat(result.hasRegressions()).isTrue();
        assertThat(result.regressedCount()).isEqualTo(12);
        ItemComparison flipped = byKey(result, "q0");
        assertThat(flipped.passFlip()).isTrue();
        assertThat(flipped.status()).isEqualTo(ComparisonStatus.REGRESSED);
    }

    @Test
    void shouldAveragePassProbabilityOverRunsContainingKeyWhenRepsMissing() {
        // Key "a" is present in all 3 baseline runs (passes 2 of 3). Key "b" is present in only 2 of
        // the 3 runs and passes both times: its pass-probability must be 1.0, averaged over the 2 runs
        // that contain it, not 2/3.
        RunComparison engine =
                RunComparison.builder().itemKey(item -> item.example().input()).build();

        List<RunResult> baseline = List.of(
                new RunResult(0, List.of(binaryItemKeyed("a", true), binaryItemKeyed("b", true))),
                new RunResult(1, List.of(binaryItemKeyed("a", true), binaryItemKeyed("b", true))),
                new RunResult(2, List.of(binaryItemKeyed("a", false))));
        List<RunResult> candidate = List.of(
                new RunResult(0, List.of(binaryItemKeyed("a", true), binaryItemKeyed("b", true))),
                new RunResult(1, List.of(binaryItemKeyed("a", true), binaryItemKeyed("b", true))),
                new RunResult(2, List.of(binaryItemKeyed("a", false))));

        RunComparisonResult result = engine.compare(baseline, candidate);

        ItemComparison itemA = byKey(result, "a");
        assertThat(itemA.baselinePassProbability()).isCloseTo(0.666667, within(1e-6));
        ItemComparison itemB = byKey(result, "b");
        assertThat(itemB.baselinePassProbability()).isEqualTo(1.0);
    }

    @Test
    void shouldRejectDuplicateKeyWithinOneRun() {
        RunComparison engine =
                RunComparison.builder().itemKey(item -> item.example().input()).build();

        List<RunResult> baseline =
                List.of(new RunResult(0, List.of(binaryItemKeyed("a", true), binaryItemKeyed("a", false))));
        List<RunResult> candidate = List.of(new RunResult(0, List.of(binaryItemKeyed("a", true))));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> engine.compare(baseline, candidate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate item key");
    }

    @Test
    void shouldRejectInvalidAlpha() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> RunComparison.builder().alpha(0.0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alpha");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> RunComparison.builder().alpha(1.0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alpha");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> RunComparison.builder().alpha(-0.1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeEpsilon() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> RunComparison.builder().epsilon(-0.001).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("epsilon");
    }

    @Test
    void shouldRejectInvalidIterationCounts() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> RunComparison.builder().permutationIterations(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permutationIterations");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> RunComparison.builder().bootstrapIterations(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bootstrapIterations");
    }

    @Test
    void shouldNotUseMcNemarForSingleRunContinuousScores() {
        // A single run per side but with continuous (non-binary) evaluator scores. The McNemar path
        // would binarize on passProbability and discard the magnitude, so the continuous permutation
        // path must be chosen instead.
        List<RunResult> baseline = continuousRun(0.81, 0.79, 0.82, 0.78, 0.80, 0.83, 0.77, 0.80);
        List<RunResult> candidate = continuousRun(0.80, 0.80, 0.81, 0.79, 0.79, 0.82, 0.78, 0.81);

        RunComparisonResult result = RunComparison.create().compare(baseline, candidate);

        assertThat(result.passRateSignificance().method()).isEqualTo("permutation");
    }

    @Test
    void shouldStillUseMcNemarForGenuinelyBinarySingleRun() {
        // Genuinely binary single run: behavior must be unchanged and still route through McNemar.
        List<ItemResult> baseItems = new ArrayList<>();
        List<ItemResult> candItems = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            baseItems.add(binaryItem(true));
            candItems.add(binaryItem(false));
        }
        for (int i = 0; i < 2; i++) {
            baseItems.add(binaryItem(false));
            candItems.add(binaryItem(true));
        }

        RunComparisonResult result = RunComparison.create()
                .compare(List.of(new RunResult(0, baseItems)), List.of(new RunResult(0, candItems)));

        assertThat(result.passRateSignificance().method()).isEqualTo("mcnemar");
        assertThat(result.passRateSignificance().significant()).isTrue();
    }

    @Test
    void shouldPairOnlyOverlapWhenPositionPairedRunsDifferInLength() {
        // No itemKey: items pair by position. Baseline has 3 items, candidate has 2. The overlapping
        // two positions pair, and the single trailing baseline item surfaces as REMOVED rather than
        // crashing or mis-aligning.
        List<ItemResult> baseItems =
                List.of(continuousItem("p0", 0.8), continuousItem("p1", 0.7), continuousItem("p2", 0.6));
        List<ItemResult> candItems = List.of(continuousItem("c0", 0.8), continuousItem("c1", 0.7));

        RunComparisonResult result = RunComparison.create()
                .compare(List.of(new RunResult(0, baseItems)), List.of(new RunResult(0, candItems)));

        // Two overlapping positions paired, one trailing baseline item unmatched (REMOVED).
        assertThat(result.removedCount()).isEqualTo(1);
        assertThat(result.addedCount()).isZero();
        assertThat(result.items()).hasSize(3);
        assertThat(result.items().stream()
                        .filter(i -> i.status() == ComparisonStatus.REMOVED)
                        .count())
                .isEqualTo(1L);
        // The trailing position is the one reported unmatched, not a mis-paired earlier index.
        ItemComparison removed = result.items().stream()
                .filter(i -> i.status() == ComparisonStatus.REMOVED)
                .findFirst()
                .orElseThrow();
        assertThat(removed.key()).isEqualTo("item-2");
    }

    @Test
    void shouldReportExtraCandidatePositionsAsAddedWhenCandidateLonger() {
        List<ItemResult> baseItems = List.of(continuousItem("p0", 0.8), continuousItem("p1", 0.7));
        List<ItemResult> candItems =
                List.of(continuousItem("c0", 0.8), continuousItem("c1", 0.7), continuousItem("c2", 0.6));

        RunComparisonResult result = RunComparison.create()
                .compare(List.of(new RunResult(0, baseItems)), List.of(new RunResult(0, candItems)));

        assertThat(result.addedCount()).isEqualTo(1);
        assertThat(result.removedCount()).isZero();
        ItemComparison added = result.items().stream()
                .filter(i -> i.status() == ComparisonStatus.ADDED)
                .findFirst()
                .orElseThrow();
        assertThat(added.key()).isEqualTo("item-2");
    }

    private static List<RunResult> continuousRun(double... scores) {
        List<ItemResult> items = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            items.add(continuousItem("q" + i, scores[i]));
        }
        return List.of(new RunResult(0, items));
    }

    private static ItemResult continuousItem(String input, double score) {
        return new ItemResult(Example.of(input, "a"), Map.of(), List.of(EvalResult.of(EVALUATOR, score, 0.7, "")));
    }

    /** Builds {@code reps} identical runs, each holding one keyed item per score. */
    private static List<RunResult> continuousReps(int reps, double... scores) {
        List<RunResult> runs = new ArrayList<>();
        for (int r = 0; r < reps; r++) {
            List<ItemResult> items = new ArrayList<>();
            for (int i = 0; i < scores.length; i++) {
                items.add(continuousItem("q" + i, scores[i]));
            }
            runs.add(new RunResult(r, items));
        }
        return runs;
    }

    /** Item with a fixed score but a threshold that decides pass/fail. */
    private static ItemResult thresholdItem(String input, double score, double threshold) {
        return new ItemResult(
                Example.of(input, "a"), Map.of(), List.of(EvalResult.of(EVALUATOR, score, threshold, "")));
    }

    private static ItemResult binaryItemKeyed(String input, boolean pass) {
        return new ItemResult(
                Example.of(input, "a"),
                Map.of(),
                List.of(pass ? EvalResult.success(EVALUATOR, 1.0, "") : EvalResult.failure(EVALUATOR, 0.0, "")));
    }

    private static ItemResult binaryItem(boolean pass) {
        return new ItemResult(
                Example.of("q", "a"),
                Map.of(),
                List.of(pass ? EvalResult.success(EVALUATOR, 1.0, "") : EvalResult.failure(EVALUATOR, 0.0, "")));
    }

    private static List<RunResult> copy(List<RunResult> runs) {
        List<RunResult> out = new ArrayList<>();
        for (RunResult run : runs) {
            out.add(new RunResult(run.runIndex(), new ArrayList<>(run.itemResults())));
        }
        return out;
    }

    private static ItemComparison byKey(RunComparisonResult result, String key) {
        return result.items().stream()
                .filter(i -> i.key().equals(key))
                .findFirst()
                .orElseThrow();
    }
}
