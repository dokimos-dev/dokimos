package dev.dokimos.server.service;

import dev.dokimos.core.comparison.ComparisonStatus;
import dev.dokimos.core.comparison.ItemComparison;
import dev.dokimos.core.comparison.RunComparison;
import dev.dokimos.core.comparison.RunComparisonResult;
import dev.dokimos.server.dto.v1.GateRequest;
import dev.dokimos.server.dto.v1.GateResult;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.repository.ExperimentRepository;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates a CI regression gate by comparing an already-ingested candidate run against a resolved
 * baseline run with the core {@link RunComparison} engine and returning a pass/fail verdict. The
 * entity-to-core conversion, pairing decision, and engine invocation are shared with the per-case
 * diff view through {@link ComparisonSupport}.
 *
 * <p>Automatic baseline selection is scoped by experiment, the candidate's dataset version, and an
 * optional git branch. Other dimensions (evaluator set, judge model/prompt, thresholds, tenant) are
 * not yet part of baseline selection. A mismatched evaluator set across the two runs is handled by
 * the engine itself: an evaluator present on only one side co-occurs on no shared item and is
 * reported UNCHANGED, never a regression.
 *
 * <p>Known limitation (zero-eval items): the core {@link dev.dokimos.core.ItemResult#success()}
 * treats an item with no eval results as passing (an {@code allMatch} over an empty stream is
 * true), so the comparison engine counts a zero-eval item as passing. The server's SQL
 * {@code countItemsWithAllEvalsPassed} treats a zero-eval item as not passed. These two notions of
 * "passing" can therefore diverge for items that carry no eval results, a pre-existing core
 * semantic that is not addressed here.
 */
@Service
public class GateService {

    /** Cap on the number of regressed cases returned inline, to keep the PR comment bounded. */
    private static final int MAX_CASES = 50;

    private final ExperimentRepository experimentRepository;
    private final ExperimentRunRepository runRepository;
    private final ComparisonSupport comparisonSupport;

    public GateService(
            ExperimentRepository experimentRepository,
            ExperimentRunRepository runRepository,
            ComparisonSupport comparisonSupport) {
        this.experimentRepository = experimentRepository;
        this.runRepository = runRepository;
        this.comparisonSupport = comparisonSupport;
    }

    /**
     * Evaluates the regression gate for a candidate run within an experiment.
     *
     * @param experimentId the experiment the candidate belongs to
     * @param request      the gate request; {@code candidateRunId} is required
     * @return the gate verdict, which is PASS, FAIL, or NO_BASELINE
     * @throws IllegalArgumentException if the experiment or a referenced run does not exist or a run
     *     does not belong to the experiment (surfaces as 404)
     * @throws IllegalStateException if the candidate or an explicit baseline run is not terminal
     *     (surfaces as 409)
     */
    @Transactional(readOnly = true)
    public GateResult evaluateGate(UUID experimentId, GateRequest request, TenantScope scope) {
        Experiment experiment = getExperiment(experimentId, scope);

        ExperimentRun candidate = comparisonSupport.getRunInExperiment(
                request.candidateRunId(), experiment, "Candidate run", runRepository, scope);
        comparisonSupport.requireTerminal(candidate, "Candidate run");

        ExperimentRun baseline = resolveBaseline(experiment, candidate, request, scope);
        if (baseline == null) {
            return noBaseline(candidate);
        }

        ComparisonSupport.ComparisonOutcome outcome = comparisonSupport.compare(baseline, candidate);
        return toGateResult(outcome.result(), candidate, baseline, outcome.pairing());
    }

    private ExperimentRun resolveBaseline(
            Experiment experiment, ExperimentRun candidate, GateRequest request, TenantScope scope) {
        if (request.baselineRunId() != null) {
            ExperimentRun baseline = comparisonSupport.getRunInExperiment(
                    request.baselineRunId(), experiment, "Baseline run", runRepository, scope);
            comparisonSupport.requireTerminal(baseline, "Baseline run");
            return baseline;
        }

        List<ExperimentRun> candidates = runRepository.findBaselineCandidates(
                experiment,
                candidate.getId(),
                comparisonSupport.datasetVersionId(candidate),
                request.baselineBranch(),
                PageRequest.of(0, 1),
                scope);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /** First run (or no comparable predecessor) cannot regress, so the gate passes. */
    private GateResult noBaseline(ExperimentRun candidate) {
        return new GateResult(
                "NO_BASELINE",
                true,
                candidate.getId(),
                null,
                "none",
                null,
                candidate.getPassRate() != null ? candidate.getPassRate() : 0.0,
                null,
                false,
                0,
                0,
                0,
                0,
                0,
                List.of(),
                List.of(),
                false);
    }

    private GateResult toGateResult(
            RunComparisonResult comparison, ExperimentRun candidate, ExperimentRun baseline, String pairing) {
        boolean passed = !comparison.hasRegressions();
        String status = passed ? "PASS" : "FAIL";

        List<GateResult.RegressedEvaluator> regressedEvaluators = comparison.regressions().stream()
                .map(d -> new GateResult.RegressedEvaluator(
                        d.evaluatorName(),
                        d.baselineMean(),
                        d.candidateMean(),
                        d.delta(),
                        d.significance() != null ? d.significance().pValue() : 1.0))
                .toList();

        List<GateResult.RegressedCase> cases = regressedCases(comparison);
        boolean casesTruncated = comparison.regressedCount() > cases.size();

        boolean significant = comparison.passRateSignificance() != null
                && comparison.passRateSignificance().significant();

        return new GateResult(
                status,
                passed,
                candidate.getId(),
                baseline.getId(),
                pairing,
                comparison.baselinePassRate(),
                comparison.candidatePassRate(),
                comparison.passRateDelta(),
                significant,
                comparison.improvedCount(),
                comparison.regressedCount(),
                comparison.unchangedCount(),
                comparison.addedCount(),
                comparison.removedCount(),
                regressedEvaluators,
                cases,
                casesTruncated);
    }

    /**
     * Collects regressed items, capped at {@link #MAX_CASES} so the PR comment stays bounded on large
     * datasets. The authoritative total is {@code regressedCount}; the caller sets
     * {@code casesTruncated} when it exceeds the returned size.
     */
    private List<GateResult.RegressedCase> regressedCases(RunComparisonResult comparison) {
        List<GateResult.RegressedCase> cases = new ArrayList<>();
        for (ItemComparison item : comparison.items()) {
            if (item.status() != ComparisonStatus.REGRESSED) {
                continue;
            }
            List<GateResult.EvaluatorDrop> drops = item.evaluatorDeltas().stream()
                    .filter(d -> d.status() == ComparisonStatus.REGRESSED)
                    .map(d -> new GateResult.EvaluatorDrop(
                            d.evaluatorName(), d.baselineMean(), d.candidateMean(), d.delta()))
                    .toList();
            String key = item.key();
            String datasetItemId = comparisonSupport.isDatasetItemKey(key) ? key : null;
            cases.add(new GateResult.RegressedCase(datasetItemId, key, drops));
            if (cases.size() >= MAX_CASES) {
                break;
            }
        }
        return cases;
    }

    private Experiment getExperiment(UUID experimentId, TenantScope scope) {
        if (experimentId == null) {
            throw new IllegalArgumentException("Experiment ID cannot be null");
        }
        return experimentRepository
                .findById(experimentId, scope)
                .orElseThrow(() -> new IllegalArgumentException("Experiment not found: " + experimentId));
    }
}
