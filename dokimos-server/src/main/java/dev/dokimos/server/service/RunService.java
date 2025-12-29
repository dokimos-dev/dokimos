package dev.dokimos.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.server.dto.v1.AddItemsRequest;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RunService {

    private final ExperimentRunRepository runRepository;
    private final ItemResultRepository itemResultRepository;
    private final ObjectMapper objectMapper;

    public RunService(ExperimentRunRepository runRepository,
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

    @Transactional
    public void addItems(UUID runId, AddItemsRequest request) {
        ExperimentRun run = getRun(runId);

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
                            evalData.reason()
                    );
                    item.addEvalResult(eval);
                }
            }

            itemResultRepository.save(item);
        }
    }

    @Transactional
    public void updateRun(UUID runId, UpdateRunRequest request) {
        ExperimentRun run = getRun(runId);
        run.setStatus(request.status());
        if (request.status() != RunStatus.RUNNING) {
            run.setCompletedAt(Instant.now());
        }
        runRepository.save(run);
    }

    @Transactional(readOnly = true)
    public List<RunSummary> listRuns(Experiment experiment) {
        List<ExperimentRun> runs = runRepository.findByExperimentOrderByStartedAtDesc(experiment);
        return runs.stream()
                .map(this::toRunSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public RunDetails getRunDetails(UUID runId, Pageable pageable) {
        ExperimentRun run = getRun(runId);
        Experiment experiment = run.getExperiment();

        long totalItems = itemResultRepository.countByRun(run);
        long passedItems = itemResultRepository.countItemsWithAllEvalsPassed(run);
        Double passRate = totalItems > 0 ? (double) passedItems / totalItems : null;

        Page<ItemResult> itemPage = itemResultRepository.findByRunOrderByCreatedAtAsc(run, pageable);
        Page<RunDetails.ItemSummary> itemSummaries = itemPage.map(this::toItemSummary);

        return new RunDetails(
                run.getId(),
                experiment.getName(),
                experiment.getProject().getName(),
                run.getStatus(),
                run.getConfig(),
                totalItems,
                passedItems,
                passRate,
                run.getStartedAt(),
                run.getCompletedAt(),
                itemSummaries
        );
    }

    private ExperimentRun getRun(UUID runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
    }

    private RunSummary toRunSummary(ExperimentRun run) {
        long totalItems = itemResultRepository.countByRun(run);
        long passedItems = itemResultRepository.countItemsWithAllEvalsPassed(run);
        Double passRate = totalItems > 0 ? (double) passedItems / totalItems : null;

        return new RunSummary(
                run.getId(),
                run.getStatus(),
                run.getConfig(),
                totalItems,
                passedItems,
                passRate,
                run.getStartedAt(),
                run.getCompletedAt()
        );
    }

    private RunDetails.ItemSummary toItemSummary(ItemResult item) {
        List<RunDetails.EvalSummary> evalSummaries = item.getEvalResults().stream()
                .map(e -> new RunDetails.EvalSummary(
                        e.getId(),
                        e.getEvaluatorName(),
                        e.getScore(),
                        e.getThreshold(),
                        e.isSuccess(),
                        e.getReason()
                ))
                .toList();

        return new RunDetails.ItemSummary(
                item.getId(),
                item.getInput(),
                item.getExpectedOutput(),
                item.getActualOutput(),
                item.getMetadata(),
                evalSummaries,
                item.getCreatedAt()
        );
    }

    private String extractText(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        // Try common field names
        for (String key : List.of("input", "output", "text", "content", "value")) {
            if (map.containsKey(key)) {
                Object value = map.get(key);
                return value != null ? value.toString() : null;
            }
        }
        // Fallback to JSON representation
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return map.toString();
        }
    }
}
