package dev.dokimos.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.server.dto.v1.AddItemsRequest;
import dev.dokimos.server.dto.v1.CreateRunRequest;
import dev.dokimos.server.dto.v1.RunDetails;
import dev.dokimos.server.dto.v1.RunSummary;
import dev.dokimos.server.dto.v1.UpdateRunRequest;
import dev.dokimos.server.entity.EvalResult;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.ItemResult;
import dev.dokimos.server.entity.RunStatus;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.repository.ItemResultRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunService {

    private final ExperimentRunRepository runRepository;
    private final ItemResultRepository itemResultRepository;
    private final ObjectMapper objectMapper;

    public RunService(
            ExperimentRunRepository runRepository,
            ItemResultRepository itemResultRepository,
            ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.itemResultRepository = itemResultRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExperimentRun createRun(Experiment experiment, Map<String, Object> config) {
        ExperimentRun run = new ExperimentRun(experiment, config);
        return runRepository.save(run);
    }

    /**
     * Creates a run and persists the provenance fields carried by the request (run name, git SHA,
     * git branch, who triggered it).
     *
     * @param experiment the experiment the run belongs to
     * @param request the create request supplying config metadata and provenance fields
     * @return the persisted run
     */
    @Transactional
    public ExperimentRun createRun(Experiment experiment, CreateRunRequest request) {
        ExperimentRun run = new ExperimentRun(experiment, request.metadata());
        run.setName(request.name());
        run.setGitSha(request.gitSha());
        run.setGitBranch(request.gitBranch());
        run.setTriggeredBy(request.triggeredBy());
        return runRepository.save(run);
    }

    /**
     * Adds item results to a run. Only permitted while the run is still RUNNING. Once a run reaches
     * a terminal status, its materialized pass-rate fields have been written by the single writer
     * ({@link #updateRun}); accepting late items (from a retry or a race) would silently leave those
     * fields stale, so this method rejects the request instead.
     *
     * <p>The run row is loaded with a pessimistic write lock for the duration of the item inserts.
     * This serializes ingestion against completion ({@link #updateRun}): a concurrent {@code updateRun}
     * on the same run blocks until this batch commits, so the counts it materializes always reflect the
     * fully committed item set and cannot go stale.
     *
     * @param runId the run to add items to
     * @param request the items to add
     * @throws IllegalStateException if the run is not in the RUNNING status
     */
    @Transactional
    public void addItems(UUID runId, AddItemsRequest request) {
        ExperimentRun run = getRunForUpdate(runId);
        if (run.getStatus() != RunStatus.RUNNING) {
            throw new IllegalStateException("Cannot add items to a run that is not RUNNING: " + runId);
        }

        for (AddItemsRequest.ItemData itemData : request.items()) {
            String input = extractText(itemData.inputs());
            String expectedOutput = extractText(itemData.expectedOutputs());
            String actualOutput = extractText(itemData.actualOutputs());

            ItemResult item = new ItemResult(run, input, expectedOutput, actualOutput, itemData.inputs());

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

            itemResultRepository.save(item);
        }
    }

    /**
     * Updates a run's status. When the run reaches a terminal status (anything other than RUNNING),
     * this method is the single writer of the materialized pass-rate fields: it computes the item
     * count, passed count, and pass rate once and persists them on the run. No other code path
     * writes these fields.
     *
     * <p>The run row is loaded with a pessimistic write lock, so this method blocks until any in-flight
     * {@link #addItems} batch on the same run has committed. Materializing counts from the now-consistent
     * item set guarantees they cannot go stale due to a late item insert racing completion.
     *
     * @param runId the run to update
     * @param request the requested status
     */
    @Transactional
    public void updateRun(UUID runId, UpdateRunRequest request) {
        ExperimentRun run = getRunForUpdate(runId);
        run.setStatus(request.status());
        if (request.status() != RunStatus.RUNNING) {
            run.setCompletedAt(Instant.now());
            materializeCounts(run);
        }
        runRepository.save(run);
    }

    private void materializeCounts(ExperimentRun run) {
        long totalItems = itemResultRepository.countByRun(run);
        long passedItems = itemResultRepository.countItemsWithAllEvalsPassed(run);
        run.setItemCount((int) totalItems);
        run.setPassedCount((int) passedItems);
        run.setPassRate(totalItems > 0 ? (double) passedItems / totalItems : null);
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
        if (run.getStatus() == RunStatus.RUNNING) {
            totalItems = itemResultRepository.countByRun(run);
            passedItems = itemResultRepository.countItemsWithAllEvalsPassed(run);
            passRate = totalItems > 0 ? (double) passedItems / totalItems : null;
        } else {
            totalItems = run.getItemCount();
            passedItems = run.getPassedCount();
            passRate = run.getPassRate();
        }

        Page<ItemResult> itemPage = itemResultRepository.findByRunOrderByCreatedAtAsc(run, pageable);
        Page<RunDetails.ItemSummary> itemSummaries = itemPage.map(this::toItemSummary);

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
        if (run.getStatus() == RunStatus.RUNNING) {
            totalItems = itemResultRepository.countByRun(run);
            passedItems = itemResultRepository.countItemsWithAllEvalsPassed(run);
            passRate = totalItems > 0 ? (double) passedItems / totalItems : null;
        } else {
            totalItems = run.getItemCount();
            passedItems = run.getPassedCount();
            passRate = run.getPassRate();
        }

        return new RunSummary(
                run.getId(),
                run.getStatus(),
                run.getConfig(),
                totalItems,
                passedItems,
                passRate,
                run.getStartedAt(),
                run.getCompletedAt());
    }

    private RunDetails.ItemSummary toItemSummary(ItemResult item) {
        List<RunDetails.EvalSummary> evalSummaries = item.getEvalResults().stream()
                .map(e -> new RunDetails.EvalSummary(
                        e.getId(), e.getEvaluatorName(), e.getScore(), e.getThreshold(), e.isSuccess(), e.getReason()))
                .toList();

        return new RunDetails.ItemSummary(
                item.getId(),
                item.getInput(),
                item.getExpectedOutput(),
                item.getActualOutput(),
                item.getMetadata(),
                evalSummaries,
                item.getCreatedAt());
    }

    /**
     * Serializes a field map to its JSON string representation for storage. If serialization fails,
     * an unchecked exception is thrown rather than falling back to {@code map.toString()}, which
     * would persist invalid JSON.
     *
     * @param map the field map to serialize, may be null or empty
     * @return the JSON string, or null when the map is null or empty
     * @throws IllegalStateException if the map cannot be serialized to JSON
     */
    private String extractText(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize map to JSON", e);
        }
    }
}
