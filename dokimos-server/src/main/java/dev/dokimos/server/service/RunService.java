package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.AddItemsRequest;
import dev.dokimos.server.dto.v1.AnnotationView;
import dev.dokimos.server.dto.v1.CreateRunRequest;
import dev.dokimos.server.dto.v1.RunDetails;
import dev.dokimos.server.dto.v1.RunSummary;
import dev.dokimos.server.dto.v1.UpdateRunRequest;
import dev.dokimos.server.entity.Annotation;
import dev.dokimos.server.entity.DatasetItem;
import dev.dokimos.server.entity.DatasetVersion;
import dev.dokimos.server.entity.EvalResult;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.IngestedBatch;
import dev.dokimos.server.entity.ItemResult;
import dev.dokimos.server.entity.RunStatus;
import dev.dokimos.server.repository.AnnotationRepository;
import dev.dokimos.server.repository.DatasetItemRepository;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.repository.IngestedBatchRepository;
import dev.dokimos.server.repository.ItemResultRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunService.class);

    private final ExperimentRunRepository runRepository;
    private final ItemResultRepository itemResultRepository;
    private final IngestedBatchRepository ingestedBatchRepository;
    private final DatasetService datasetService;
    private final DatasetItemRepository datasetItemRepository;
    private final AnnotationRepository annotationRepository;

    public RunService(
            ExperimentRunRepository runRepository,
            ItemResultRepository itemResultRepository,
            IngestedBatchRepository ingestedBatchRepository,
            DatasetService datasetService,
            DatasetItemRepository datasetItemRepository,
            AnnotationRepository annotationRepository) {
        this.runRepository = runRepository;
        this.itemResultRepository = itemResultRepository;
        this.ingestedBatchRepository = ingestedBatchRepository;
        this.datasetService = datasetService;
        this.datasetItemRepository = datasetItemRepository;
        this.annotationRepository = annotationRepository;
    }

    @Transactional
    public ExperimentRun createRun(Experiment experiment, Map<String, Object> config) {
        ExperimentRun run = new ExperimentRun(experiment, config);
        return runRepository.save(run);
    }

    /**
     * Creates a run and persists its provenance fields (name, git SHA, branch, triggered_by) plus an
     * optional link to the dataset version it executed against. The dataset linkage is the foundation
     * for the per-case diff and CI gate: runs paired by {@code (datasetVersionId, ordinal)} share
     * stable item identities across executions.
     *
     * @throws IllegalArgumentException if only one of {@code datasetName} / {@code datasetVersion} is
     *     supplied, or if the referenced dataset version does not exist
     */
    @Transactional
    public ExperimentRun createRun(Experiment experiment, CreateRunRequest request) {
        ExperimentRun run = new ExperimentRun(experiment, request.metadata());
        run.setName(request.name());
        run.setGitSha(request.gitSha());
        run.setGitBranch(request.gitBranch());
        run.setTriggeredBy(request.triggeredBy());

        DatasetVersion datasetVersion = resolveDatasetVersion(request);
        if (datasetVersion != null) {
            run.setDatasetVersion(datasetVersion);
        }

        return runRepository.save(run);
    }

    private DatasetVersion resolveDatasetVersion(CreateRunRequest request) {
        boolean hasName =
                request.datasetName() != null && !request.datasetName().isBlank();
        boolean hasVersion = request.datasetVersion() != null;
        if (hasName != hasVersion) {
            throw new IllegalArgumentException("datasetName and datasetVersion must be set together");
        }
        if (!hasName) {
            return null;
        }
        return datasetService.getVersion(request.datasetName(), request.datasetVersion());
    }

    /**
     * Adds item results to a run; only allowed while the run is RUNNING. A pessimistic lock on the
     * run row serializes ingestion against {@link #updateRun} so materialized counts stay consistent.
     * When {@code idempotencyKey} is non-null, a previously committed batch with the same
     * {@code (runId, idempotencyKey)} returns a no-op; the composite PK on {@code ingested_batches}
     * is the backstop.
     *
     * @throws IllegalStateException if the run is not RUNNING
     */
    @Transactional
    public void addItems(UUID runId, AddItemsRequest request, String idempotencyKey) {
        ExperimentRun run = getRunForUpdate(runId);
        if (run.getStatus() != RunStatus.RUNNING) {
            throw new IllegalStateException("Cannot add items to a run that is not RUNNING: " + runId);
        }

        if (idempotencyKey != null && ingestedBatchRepository.existsByRunIdAndIdempotencyKey(runId, idempotencyKey)) {
            return;
        }

        // Batch-load the referenced dataset items in one query rather than a PK select per item.
        // A stale id (its dataset version was deleted between resolve and report) is simply absent
        // from the map: the FK is SET NULL, so an unlinked result is a valid point-in-time record,
        // not a reason to fail the whole batch.
        Map<UUID, DatasetItem> datasetItemsById = loadDatasetItems(request.items());

        List<ItemResult> items = new ArrayList<>(request.items().size());
        for (AddItemsRequest.ItemData itemData : request.items()) {
            ItemResult item = new ItemResult(
                    run, itemData.inputs(), itemData.expectedOutputs(), itemData.actualOutputs(), itemData.metadata());

            item.setTokensIn(itemData.tokensIn());
            item.setTokensOut(itemData.tokensOut());
            item.setCostUsd(itemData.costUsd());
            item.setLatencyMs(itemData.latencyMs());

            if (itemData.datasetItemId() != null) {
                DatasetItem datasetItem = datasetItemsById.get(itemData.datasetItemId());
                if (datasetItem != null) {
                    item.setDatasetItem(datasetItem);
                } else {
                    LOGGER.warn(
                            "Dataset item {} not found for run {}; storing item result without linkage",
                            itemData.datasetItemId(),
                            runId);
                }
            }

            if (itemData.evalResults() != null) {
                for (AddItemsRequest.EvalData evalData : itemData.evalResults()) {
                    EvalResult eval = new EvalResult(
                            evalData.name(),
                            evalData.score(),
                            evalData.threshold(),
                            evalData.success(),
                            evalData.reason());
                    eval.setMetadata(evalData.metadata());
                    item.addEvalResult(eval);
                }
            }

            items.add(item);
        }

        itemResultRepository.saveAll(items);

        if (idempotencyKey != null) {
            ingestedBatchRepository.save(new IngestedBatch(runId, idempotencyKey, Instant.now()));
        }
    }

    /** Deletes a run; FKs cascade to its item and eval results. */
    @Transactional
    public void deleteRun(UUID runId) {
        ExperimentRun run = getRun(runId);
        runRepository.delete(run);
    }

    /**
     * Updates a run's status. On a terminal status this method is the sole writer of the
     * materialized pass-rate fields; the pessimistic lock on the run row blocks until any in-flight
     * {@link #addItems} batch commits.
     */
    @Transactional
    public void updateRun(UUID runId, UpdateRunRequest request) {
        ExperimentRun run = getRunForUpdate(runId);
        if (run.getStatus() == RunStatus.EVALUATING) {
            throw new IllegalStateException(
                    "Run " + runId + " is evaluating; its status is managed by the judge worker");
        }
        run.setStatus(request.status());
        if (request.status() != RunStatus.RUNNING) {
            run.setCompletedAt(Instant.now());
            materializeCounts(run);
        }
        runRepository.save(run);
    }

    /**
     * Finalizes a run whose judge job has finished scoring: re-computes the materialized pass-rate
     * fields and moves the run to SUCCESS. The run row is locked so the finalization serializes against
     * any concurrent ingestion or status update.
     *
     * @param runId the run to finalize
     * @throws IllegalArgumentException if the run does not exist
     */
    @Transactional
    public void finalizeEvaluatedRun(UUID runId) {
        ExperimentRun run = getRunForUpdate(runId);
        if (run.getStatus() != RunStatus.EVALUATING) {
            return;
        }
        materializeCounts(run);
        run.setStatus(RunStatus.SUCCESS);
        run.setCompletedAt(Instant.now());
        runRepository.save(run);
    }

    /**
     * Moves an evaluating run to FAILED when its judge job has terminally failed, so the run does not
     * stay stuck in EVALUATING. No-op if the run is no longer EVALUATING.
     *
     * @param runId the run to fail
     * @throws IllegalArgumentException if the run does not exist
     */
    @Transactional
    public void failEvaluatedRun(UUID runId) {
        ExperimentRun run = getRunForUpdate(runId);
        if (run.getStatus() != RunStatus.EVALUATING) {
            return;
        }
        materializeCounts(run);
        run.setStatus(RunStatus.FAILED);
        run.setCompletedAt(Instant.now());
        runRepository.save(run);
    }

    private void materializeCounts(ExperimentRun run) {
        long totalItems = itemResultRepository.countByRun(run);
        long passedItems = itemResultRepository.countItemsWithAllEvalsPassed(run);
        run.setItemCount((int) totalItems);
        run.setPassedCount((int) passedItems);
        run.setPassRate(totalItems > 0 ? (double) passedItems / totalItems : null);
        run.setTotalTokensIn(itemResultRepository.sumTokensInByRun(run));
        run.setTotalTokensOut(itemResultRepository.sumTokensOutByRun(run));
        run.setTotalCostUsd(itemResultRepository.sumCostByRun(run));
        run.setAvgLatencyMs(itemResultRepository.avgLatencyByRun(run));
    }

    @Transactional(readOnly = true)
    public List<RunSummary> listRuns(Experiment experiment) {
        List<ExperimentRun> runs = runRepository.findByExperimentOrderByStartedAtDesc(experiment);
        return runs.stream().map(this::toRunSummary).toList();
    }

    @Transactional(readOnly = true)
    public RunDetails getRunDetails(UUID runId, Pageable pageable) {
        ExperimentRun run = getRun(runId);
        Experiment experiment = run.getExperiment();

        long totalItems;
        long passedItems;
        Double passRate;
        if (run.getStatus() == RunStatus.RUNNING || run.getStatus() == RunStatus.EVALUATING) {
            totalItems = itemResultRepository.countByRun(run);
            passedItems = itemResultRepository.countItemsWithAllEvalsPassed(run);
            passRate = totalItems > 0 ? (double) passedItems / totalItems : null;
        } else {
            totalItems = run.getItemCount();
            passedItems = run.getPassedCount();
            passRate = run.getPassRate();
        }

        Page<ItemResult> itemPage = itemResultRepository.findByRunOrderByCreatedAtAsc(run, pageable);
        Map<UUID, AnnotationView> annotationsByItemId = loadAnnotations(itemPage.getContent());
        Page<RunDetails.ItemSummary> itemSummaries =
                itemPage.map(item -> toItemSummary(item, annotationsByItemId.get(item.getId())));

        DatasetVersion datasetVersion = run.getDatasetVersion();
        UUID datasetVersionId = datasetVersion != null ? datasetVersion.getId() : null;
        Integer datasetVersionNumber = datasetVersion != null ? datasetVersion.getVersion() : null;

        return new RunDetails(
                run.getId(),
                experiment.getId(),
                experiment.getName(),
                experiment.getProject().getName(),
                run.getStatus(),
                run.getConfig(),
                totalItems,
                passedItems,
                passRate,
                run.getStartedAt(),
                run.getCompletedAt(),
                datasetVersionId,
                datasetVersionNumber,
                run.getTotalTokensIn(),
                run.getTotalTokensOut(),
                run.getTotalCostUsd(),
                run.getAvgLatencyMs(),
                itemSummaries);
    }

    private ExperimentRun getRun(UUID runId) {
        if (runId == null) {
            throw new IllegalArgumentException("Run ID cannot be null");
        }
        return runRepository.findById(runId).orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
    }

    private ExperimentRun getRunForUpdate(UUID runId) {
        if (runId == null) {
            throw new IllegalArgumentException("Run ID cannot be null");
        }
        return runRepository
                .findByIdForUpdate(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
    }

    private RunSummary toRunSummary(ExperimentRun run) {
        long totalItems;
        long passedItems;
        Double passRate;
        if (run.getStatus() == RunStatus.RUNNING || run.getStatus() == RunStatus.EVALUATING) {
            totalItems = itemResultRepository.countByRun(run);
            passedItems = itemResultRepository.countItemsWithAllEvalsPassed(run);
            passRate = totalItems > 0 ? (double) passedItems / totalItems : null;
        } else {
            totalItems = run.getItemCount();
            passedItems = run.getPassedCount();
            passRate = run.getPassRate();
        }

        DatasetVersion datasetVersion = run.getDatasetVersion();
        UUID datasetVersionId = datasetVersion != null ? datasetVersion.getId() : null;
        Integer datasetVersionNumber = datasetVersion != null ? datasetVersion.getVersion() : null;

        return new RunSummary(
                run.getId(),
                run.getStatus(),
                run.getConfig(),
                totalItems,
                passedItems,
                passRate,
                run.getStartedAt(),
                run.getCompletedAt(),
                datasetVersionId,
                datasetVersionNumber,
                run.getTotalTokensIn(),
                run.getTotalTokensOut(),
                run.getTotalCostUsd(),
                run.getAvgLatencyMs());
    }

    private Map<UUID, DatasetItem> loadDatasetItems(List<AddItemsRequest.ItemData> items) {
        List<UUID> ids = items.stream()
                .map(AddItemsRequest.ItemData::datasetItemId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, DatasetItem> byId = new java.util.HashMap<>();
        for (DatasetItem datasetItem : datasetItemRepository.findAllById(ids)) {
            byId.put(datasetItem.getId(), datasetItem);
        }
        return byId;
    }

    private RunDetails.ItemSummary toItemSummary(ItemResult item, AnnotationView annotation) {
        List<RunDetails.EvalSummary> evalSummaries = item.getEvalResults().stream()
                .map(e -> new RunDetails.EvalSummary(
                        e.getId(), e.getEvaluatorName(), e.getScore(), e.getThreshold(), e.isSuccess(), e.getReason()))
                .toList();

        DatasetItem datasetItem = item.getDatasetItem();
        UUID datasetItemId = datasetItem != null ? datasetItem.getId() : null;

        return new RunDetails.ItemSummary(
                item.getId(),
                item.getInput(),
                item.getExpectedOutput(),
                item.getActualOutput(),
                item.getMetadata(),
                evalSummaries,
                item.getCreatedAt(),
                datasetItemId,
                annotation,
                item.getTokensIn(),
                item.getTokensOut(),
                item.getCostUsd(),
                item.getLatencyMs());
    }

    /**
     * Batch-loads the annotations for a page of item results in one query and keys them by item
     * result id, so surfacing the annotation per item never fans out into a query per row.
     */
    private Map<UUID, AnnotationView> loadAnnotations(List<ItemResult> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = items.stream().map(ItemResult::getId).toList();
        Map<UUID, AnnotationView> byItemId = new HashMap<>();
        for (Annotation annotation : annotationRepository.findByItemResultIdIn(ids)) {
            byItemId.put(annotation.getItemResult().getId(), AnnotationView.from(annotation));
        }
        return byItemId;
    }
}
