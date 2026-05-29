package dev.dokimos.server.service;

import dev.dokimos.core.Example;
import dev.dokimos.core.RunResult;
import dev.dokimos.core.comparison.ComparisonStatus;
import dev.dokimos.core.comparison.ItemComparison;
import dev.dokimos.core.comparison.RunComparison;
import dev.dokimos.core.comparison.RunComparisonResult;
import dev.dokimos.server.dto.v1.GateRequest;
import dev.dokimos.server.dto.v1.GateResult;
import dev.dokimos.server.entity.DatasetVersion;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.ItemResult;
import dev.dokimos.server.entity.RunStatus;
import dev.dokimos.server.repository.ExperimentRepository;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.repository.ItemResultRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates a CI regression gate by comparing an already-ingested candidate run against a resolved
 * baseline run with the core {@link RunComparison} engine and returning a pass/fail verdict.
 *
 * <p>Scoping note: this slice scopes the automatic baseline by experiment, the candidate's dataset
 * version, and an optional git branch. Fuller scoping from the plan (evaluator set, judge
 * model/prompt, thresholds, tenant) is deferred. A mismatched evaluator set across the two runs is
 * handled by the engine itself: an evaluator present on only one side co-occurs on no shared item
 * and is reported UNCHANGED, never a regression.
 *
 * <p>Known limitation (zero-eval items): the core {@link dev.dokimos.core.ItemResult#success()}
 * treats an item with no eval results as passing (an {@code allMatch} over an empty stream is
 * true), so the comparison engine counts a zero-eval item as passing. The server's SQL
 * {@code countItemsWithAllEvalsPassed} treats a zero-eval item as not passed. These two notions of
 * "passing" can therefore diverge for items that carry no eval results. This is a pre-existing core
 * semantic and is not changed in this slice.
 */
@Service
public class GateService {

    private static final Logger log = LoggerFactory.getLogger(GateService.class);

    /** Cap on the number of regressed cases returned inline, to keep the PR comment bounded. */
    private static final int MAX_CASES = 50;

    private final ExperimentRepository experimentRepository;
    private final ExperimentRunRepository runRepository;
    private final ItemResultRepository itemResultRepository;

    public GateService(
            ExperimentRepository experimentRepository,
            ExperimentRunRepository runRepository,
            ItemResultRepository itemResultRepository) {
        this.experimentRepository = experimentRepository;
        this.runRepository = runRepository;
        this.itemResultRepository = itemResultRepository;
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
    public GateResult evaluateGate(UUID experimentId, GateRequest request) {
        Experiment experiment = getExperiment(experimentId);

        ExperimentRun candidate = getRunInExperiment(request.candidateRunId(), experiment, "Candidate run");
        requireTerminal(candidate, "Candidate run");

        ExperimentRun baseline = resolveBaseline(experiment, candidate, request);
        if (baseline == null) {
            return noBaseline(candidate);
        }

        RunResult candidateResult = toRunResult(candidate);
        RunResult baselineResult = toRunResult(baseline);

        // Pair by dataset item id only when both runs share a dataset version AND every loaded item
        // on both sides has a non-null id. A dataset-linked run can still hold unlinked items (stored
        // against a stale dataset id), whose null id would collapse to a colliding positional fallback
        // key inside the engine, mis-pairing items or throwing a duplicate-key error. When any item is
        // unlinked, fall back to positional pairing.
        UUID candidateVersionId = datasetVersionId(candidate);
        UUID baselineVersionId = datasetVersionId(baseline);
        boolean sharedVersion = candidateVersionId != null && candidateVersionId.equals(baselineVersionId);
        boolean allLinked = allItemsLinked(candidateResult) && allItemsLinked(baselineResult);
        boolean pairById = sharedVersion && allLinked;
        if (sharedVersion && !allLinked) {
            log.warn(
                    "dataset-linked run has unlinked items; falling back to positional pairing"
                            + " (candidate={}, baseline={})",
                    candidate.getId(),
                    baseline.getId());
        }

        RunComparison.Builder builder = RunComparison.builder();
        if (pairById) {
            builder.itemKey(ir -> ir.example().datasetItemId());
        }
        RunComparisonResult comparison = builder.build().compare(List.of(baselineResult), List.of(candidateResult));

        return toGateResult(comparison, candidate, baseline, pairById ? "dataset_item_id" : "positional");
    }

    /** True when every item in the run carries a non-null dataset item id. */
    private static boolean allItemsLinked(RunResult run) {
        return run.itemResults().stream().allMatch(ir -> ir.example().datasetItemId() != null);
    }

    /** A run can be gated or used as a baseline only once it has reached a terminal status. */
    private static void requireTerminal(ExperimentRun run, String label) {
        RunStatus status = run.getStatus();
        if (status != RunStatus.SUCCESS && status != RunStatus.FAILED) {
            throw new IllegalStateException("Cannot gate a run that is not in a terminal SUCCESS/FAILED status: "
                    + run.getId() + " (" + status + ")");
        }
    }

    private ExperimentRun resolveBaseline(Experiment experiment, ExperimentRun candidate, GateRequest request) {
        if (request.baselineRunId() != null) {
            ExperimentRun baseline = getRunInExperiment(request.baselineRunId(), experiment, "Baseline run");
            requireTerminal(baseline, "Baseline run");
            return baseline;
        }

        List<ExperimentRun> candidates = runRepository.findBaselineCandidates(
                experiment,
                candidate.getId(),
                datasetVersionId(candidate),
                request.baselineBranch(),
                PageRequest.of(0, 1));
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
            String datasetItemId = isDatasetItemKey(key) ? key : null;
            cases.add(new GateResult.RegressedCase(datasetItemId, key, drops));
            if (cases.size() >= MAX_CASES) {
                break;
            }
        }
        return cases;
    }

    // Positional keys are emitted by the engine as "item-<index>"; anything else is a dataset item id.
    private boolean isDatasetItemKey(String key) {
        return key != null && !key.startsWith("item-");
    }

    /**
     * Converts a server run plus its item and eval results into a single core {@link RunResult} at
     * run index 0. Items are loaded with a fetch-join to avoid an N+1 over the lazy eval collection.
     */
    private RunResult toRunResult(ExperimentRun run) {
        List<ItemResult> items = itemResultRepository.findByRunWithEvals(run);
        List<dev.dokimos.core.ItemResult> coreItems = new ArrayList<>(items.size());
        for (ItemResult item : items) {
            String datasetItemId = item.getDatasetItem() != null
                    ? item.getDatasetItem().getId().toString()
                    : null;
            Example example = new Example(
                    nullToEmpty(item.getInput()),
                    nullToEmpty(item.getExpectedOutput()),
                    nullToEmpty(item.getMetadata()),
                    datasetItemId);
            List<dev.dokimos.core.EvalResult> coreEvals = item.getEvalResults().stream()
                    .map(e -> new dev.dokimos.core.EvalResult(
                            e.getEvaluatorName(),
                            e.getScore(),
                            e.getThreshold(),
                            e.isSuccess(),
                            e.getReason(),
                            nullToEmpty(e.getMetadata())))
                    .toList();
            coreItems.add(new dev.dokimos.core.ItemResult(example, nullToEmpty(item.getActualOutput()), coreEvals));
        }
        return new RunResult(0, coreItems);
    }

    private static Map<String, Object> nullToEmpty(Map<String, Object> map) {
        return map != null ? map : Map.of();
    }

    private static UUID datasetVersionId(ExperimentRun run) {
        DatasetVersion version = run.getDatasetVersion();
        return version != null ? version.getId() : null;
    }

    private Experiment getExperiment(UUID experimentId) {
        if (experimentId == null) {
            throw new IllegalArgumentException("Experiment ID cannot be null");
        }
        return experimentRepository
                .findById(experimentId)
                .orElseThrow(() -> new IllegalArgumentException("Experiment not found: " + experimentId));
    }

    private ExperimentRun getRunInExperiment(UUID runId, Experiment experiment, String label) {
        if (runId == null) {
            throw new IllegalArgumentException(label + " ID cannot be null");
        }
        ExperimentRun run = runRepository
                .findById(runId)
                .orElseThrow(() -> new IllegalArgumentException(label + " not found: " + runId));
        UUID runExperimentId =
                Optional.ofNullable(run.getExperiment()).map(Experiment::getId).orElse(null);
        if (!Objects.equals(runExperimentId, experiment.getId())) {
            throw new IllegalArgumentException(label + " does not belong to experiment " + experiment.getId());
        }
        return run;
    }
}
