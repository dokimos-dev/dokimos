package dev.dokimos.server.service;

import dev.dokimos.core.Example;
import dev.dokimos.core.RunResult;
import dev.dokimos.core.comparison.RunComparison;
import dev.dokimos.core.comparison.RunComparisonResult;
import dev.dokimos.server.entity.DatasetVersion;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.ItemResult;
import dev.dokimos.server.entity.RunStatus;
import dev.dokimos.server.repository.ItemResultRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Shared entity-to-core conversion and run-comparison invocation for the regression gate and the
 * per-case diff view. Both surfaces show the same comparison, so they must agree on how items are
 * converted, how the pairing strategy is decided, and how the core {@link RunComparison} engine is
 * configured. This component owns that single comparison path so the two services cannot drift.
 *
 * <p>Pairing strategy: items are paired by dataset item id only when both runs share a dataset
 * version AND every loaded item on both sides carries a non-null dataset item id. A dataset-linked
 * run can still hold unlinked items (stored against a stale dataset id), whose null id would
 * collapse to a colliding positional fallback key inside the engine, mis-pairing items or throwing
 * a duplicate-key error. When any item is unlinked, pairing falls back to positional.
 */
@Component
public class ComparisonSupport {

    private static final Logger log = LoggerFactory.getLogger(ComparisonSupport.class);

    private final ItemResultRepository itemResultRepository;

    public ComparisonSupport(ItemResultRepository itemResultRepository) {
        this.itemResultRepository = itemResultRepository;
    }

    /**
     * Result of comparing two runs: the engine output, the pairing strategy, and the runs' loaded
     * item entities so callers (the diff view) can derive per-case input text without re-querying.
     * The item lists are in the order the engine used to assign positional keys.
     *
     * @param result        the core comparison result
     * @param pairing       how items were paired: {@code dataset_item_id} or {@code positional}
     * @param baselineItems the baseline run's loaded items, in pairing order
     * @param candidateItems the candidate run's loaded items, in pairing order
     */
    public record ComparisonOutcome(
            RunComparisonResult result,
            String pairing,
            List<ItemResult> baselineItems,
            List<ItemResult> candidateItems) {}

    /**
     * Compares a baseline run against a candidate run with the core engine, deciding the pairing
     * strategy from the runs' dataset versions and item links. Each run's items are loaded once and
     * returned on the outcome so callers can reuse them.
     *
     * @param baseline  the baseline run
     * @param candidate the candidate run
     * @return the comparison result, pairing strategy, and loaded items
     */
    public ComparisonOutcome compare(ExperimentRun baseline, ExperimentRun candidate) {
        List<ItemResult> baselineItems = itemResultRepository.findByRunWithEvals(baseline);
        List<ItemResult> candidateItems = itemResultRepository.findByRunWithEvals(candidate);
        RunResult candidateResult = toRunResult(candidateItems);
        RunResult baselineResult = toRunResult(baselineItems);

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

        return new ComparisonOutcome(
                comparison, pairById ? "dataset_item_id" : "positional", baselineItems, candidateItems);
    }

    /**
     * Converts a server run plus its item and eval results into a single core {@link RunResult} at
     * run index 0. Items are loaded with a fetch-join to avoid an N+1 over the lazy eval collection,
     * ordered by {@code createdAt} for a stable positional pairing fallback.
     *
     * @param run the run to convert
     * @return the core run result
     */
    public RunResult toRunResult(ExperimentRun run) {
        return toRunResult(itemResultRepository.findByRunWithEvals(run));
    }

    /**
     * Converts already-loaded item entities into a core {@link RunResult} at run index 0, preserving
     * their order so positional pairing keys line up with the source list.
     *
     * @param items the run's items, with evals loaded
     * @return the core run result
     */
    public RunResult toRunResult(List<ItemResult> items) {
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

    /**
     * True when every item in the run carries a non-null dataset item id.
     *
     * @param run the run to inspect
     * @return whether all items are dataset-linked
     */
    public boolean allItemsLinked(RunResult run) {
        return !run.itemResults().isEmpty()
                && run.itemResults().stream().allMatch(ir -> ir.example().datasetItemId() != null);
    }

    /**
     * True when a comparison key is a dataset item id rather than a positional fallback. Positional
     * keys are emitted by the engine as {@code item-<index>}; anything else is a dataset item id.
     *
     * @param key the comparison key
     * @return whether the key is a dataset item id
     */
    public boolean isDatasetItemKey(String key) {
        return key != null && !key.startsWith("item-");
    }

    /**
     * A run can be gated, diffed, or used as a baseline only once it has reached a terminal status.
     *
     * @param run   the run to check
     * @param label a human-readable label for the run, used in the error message
     * @throws IllegalStateException if the run is not in a terminal SUCCESS/FAILED status
     */
    public void requireTerminal(ExperimentRun run, String label) {
        RunStatus status = run.getStatus();
        if (status != RunStatus.SUCCESS && status != RunStatus.FAILED) {
            throw new IllegalStateException(
                    label + " is not in a terminal SUCCESS/FAILED status: " + run.getId() + " (" + status + ")");
        }
    }

    /**
     * Loads a run by id and asserts it belongs to the given experiment.
     *
     * @param runId      the run id
     * @param experiment the experiment the run must belong to
     * @param label      a human-readable label for the run, used in error messages
     * @param runRepository the run repository
     * @param scope the tenant scope; a run of another tenant is invisible and surfaces as not found
     * @return the loaded run
     * @throws IllegalArgumentException if the run does not exist or belongs to another experiment
     */
    public ExperimentRun getRunInExperiment(
            UUID runId,
            Experiment experiment,
            String label,
            dev.dokimos.server.repository.ExperimentRunRepository runRepository,
            dev.dokimos.server.tenant.TenantScope scope) {
        if (runId == null) {
            throw new IllegalArgumentException(label + " ID cannot be null");
        }
        ExperimentRun run = runRepository
                .findById(runId, scope)
                .orElseThrow(() -> new IllegalArgumentException(label + " not found: " + runId));
        UUID runExperimentId =
                Optional.ofNullable(run.getExperiment()).map(Experiment::getId).orElse(null);
        if (!Objects.equals(runExperimentId, experiment.getId())) {
            throw new IllegalArgumentException(label + " does not belong to experiment " + experiment.getId());
        }
        return run;
    }

    /**
     * The dataset version id of a run, or null for ad-hoc runs.
     *
     * @param run the run
     * @return the dataset version id, or null
     */
    public UUID datasetVersionId(ExperimentRun run) {
        DatasetVersion version = run.getDatasetVersion();
        return version != null ? version.getId() : null;
    }

    private static Map<String, Object> nullToEmpty(Map<String, Object> map) {
        return map != null ? map : Map.of();
    }
}
