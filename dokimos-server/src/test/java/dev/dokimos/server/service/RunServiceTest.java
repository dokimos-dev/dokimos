package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.server.dto.v1.AddItemsRequest;
import dev.dokimos.server.dto.v1.RunDetails;
import dev.dokimos.server.dto.v1.RunSummary;
import dev.dokimos.server.dto.v1.UpdateRunRequest;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.IngestedBatch;
import dev.dokimos.server.entity.ItemResult;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.entity.RunStatus;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.repository.IngestedBatchRepository;
import dev.dokimos.server.repository.ItemResultRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class RunServiceTest {

    @Mock
    private ExperimentRunRepository runRepository;

    @Mock
    private ItemResultRepository itemResultRepository;

    @Mock
    private IngestedBatchRepository ingestedBatchRepository;

    private RunService runService;

    @BeforeEach
    void setUp() {
        runService = new RunService(runRepository, itemResultRepository, ingestedBatchRepository, new ObjectMapper());
    }

    @Test
    void createRun_shouldSaveAndReturnRun() {
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        Map<String, Object> config = Map.of("key", "value");

        when(runRepository.save(any(ExperimentRun.class))).thenAnswer(inv -> {
            ExperimentRun run = inv.getArgument(0);
            setField(run, "id", UUID.randomUUID());
            return run;
        });

        ExperimentRun result = runService.createRun(experiment, config);

        assertThat(result.getExperiment()).isEqualTo(experiment);
        assertThat(result.getConfig()).isEqualTo(config);
        assertThat(result.getStatus()).isEqualTo(RunStatus.RUNNING);
        verify(runRepository).save(any(ExperimentRun.class));
    }

    @Test
    void updateRun_shouldUpdateStatusToSuccess() {
        UUID runId = UUID.randomUUID();
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run = createRun(experiment, RunStatus.RUNNING);
        setField(run, "id", runId);

        when(runRepository.findByIdForUpdate(runId)).thenReturn(Optional.of(run));
        when(runRepository.save(any(ExperimentRun.class))).thenAnswer(inv -> inv.getArgument(0));

        runService.updateRun(runId, new UpdateRunRequest(RunStatus.SUCCESS));

        ArgumentCaptor<ExperimentRun> captor = ArgumentCaptor.forClass(ExperimentRun.class);
        verify(runRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(RunStatus.SUCCESS);
        assertThat(captor.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    void updateRun_shouldMaterializeCountsMatchingLiveComputation() {
        UUID runId = UUID.randomUUID();
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run = createRun(experiment, RunStatus.RUNNING);
        setField(run, "id", runId);

        when(runRepository.findByIdForUpdate(runId)).thenReturn(Optional.of(run));
        when(runRepository.save(any(ExperimentRun.class))).thenAnswer(inv -> inv.getArgument(0));
        when(itemResultRepository.countByRun(run)).thenReturn(10L);
        when(itemResultRepository.countItemsWithAllEvalsPassed(run)).thenReturn(7L);

        runService.updateRun(runId, new UpdateRunRequest(RunStatus.SUCCESS));

        ArgumentCaptor<ExperimentRun> captor = ArgumentCaptor.forClass(ExperimentRun.class);
        verify(runRepository).save(captor.capture());
        ExperimentRun saved = captor.getValue();

        // Materialized fields must hold the computed counts (10 items, 7 passing, 0.7 pass rate).
        assertThat(saved.getItemCount()).isEqualTo(10);
        assertThat(saved.getPassedCount()).isEqualTo(7);
        assertThat(saved.getPassRate()).isEqualTo(0.7);
    }

    @Test
    void updateRun_shouldMaterializeNullPassRateForZeroItems() {
        UUID runId = UUID.randomUUID();
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run = createRun(experiment, RunStatus.RUNNING);
        setField(run, "id", runId);

        when(runRepository.findByIdForUpdate(runId)).thenReturn(Optional.of(run));
        when(runRepository.save(any(ExperimentRun.class))).thenAnswer(inv -> inv.getArgument(0));
        when(itemResultRepository.countByRun(run)).thenReturn(0L);

        runService.updateRun(runId, new UpdateRunRequest(RunStatus.SUCCESS));

        ArgumentCaptor<ExperimentRun> captor = ArgumentCaptor.forClass(ExperimentRun.class);
        verify(runRepository).save(captor.capture());
        assertThat(captor.getValue().getItemCount()).isZero();
        assertThat(captor.getValue().getPassRate()).isNull();
    }

    @Test
    void addItems_shouldPersistEvalMetadata() {
        UUID runId = UUID.randomUUID();
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run = createRun(experiment, RunStatus.RUNNING);
        setField(run, "id", runId);

        when(runRepository.findByIdForUpdate(runId)).thenReturn(Optional.of(run));

        Map<String, Object> evalMetadata = Map.of("model", "gpt-4", "tokens", 42);
        AddItemsRequest request = new AddItemsRequest(List.of(new AddItemsRequest.ItemData(
                Map.of("input", "q"),
                Map.of("output", "a"),
                Map.of("output", "a"),
                List.of(new AddItemsRequest.EvalData("exact-match", 1.0, 0.9, true, "Correct", evalMetadata)),
                true)));

        runService.addItems(runId, request, null);

        List<ItemResult> saved = captureSavedItems();
        assertThat(saved.get(0).getEvalResults().get(0).getMetadata()).isEqualTo(evalMetadata);
    }

    @Test
    void updateRun_shouldNotSetCompletedAtForRunningStatus() {
        UUID runId = UUID.randomUUID();
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run = createRun(experiment, RunStatus.RUNNING);
        setField(run, "id", runId);

        when(runRepository.findByIdForUpdate(runId)).thenReturn(Optional.of(run));
        when(runRepository.save(any(ExperimentRun.class))).thenAnswer(inv -> inv.getArgument(0));

        runService.updateRun(runId, new UpdateRunRequest(RunStatus.RUNNING));

        ArgumentCaptor<ExperimentRun> captor = ArgumentCaptor.forClass(ExperimentRun.class);
        verify(runRepository).save(captor.capture());
        assertThat(captor.getValue().getCompletedAt()).isNull();
    }

    @Test
    void updateRun_shouldThrowWhenRunNotFound() {
        UUID runId = UUID.randomUUID();
        when(runRepository.findByIdForUpdate(runId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> runService.updateRun(runId, new UpdateRunRequest(RunStatus.SUCCESS)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Run not found");
    }

    @Test
    void updateRun_shouldThrowWhenIdIsNull() {
        assertThatThrownBy(() -> runService.updateRun(null, new UpdateRunRequest(RunStatus.SUCCESS)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Run ID cannot be null");
    }

    @Test
    void addItems_shouldSaveItemResults() {
        UUID runId = UUID.randomUUID();
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run = createRun(experiment, RunStatus.RUNNING);
        setField(run, "id", runId);

        when(runRepository.findByIdForUpdate(runId)).thenReturn(Optional.of(run));

        AddItemsRequest request = new AddItemsRequest(List.of(new AddItemsRequest.ItemData(
                Map.of("input", "What is 2+2?"),
                Map.of("output", "4"),
                Map.of("output", "4"),
                List.of(new AddItemsRequest.EvalData("exact-match", 1.0, 0.9, true, "Correct", Map.of())),
                true)));

        runService.addItems(runId, request, null);

        List<ItemResult> saved = captureSavedItems();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getInput()).isEqualTo("{\"input\":\"What is 2+2?\"}");
        assertThat(saved.get(0).getExpectedOutput()).isEqualTo("{\"output\":\"4\"}");
        assertThat(saved.get(0).getActualOutput()).isEqualTo("{\"output\":\"4\"}");
        assertThat(saved.get(0).getEvalResults()).hasSize(1);
    }

    @Test
    void addItems_shouldPersistAllItemsAndEvalsInOneCall() {
        UUID runId = UUID.randomUUID();
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run = createRun(experiment, RunStatus.RUNNING);
        setField(run, "id", runId);

        when(runRepository.findByIdForUpdate(runId)).thenReturn(Optional.of(run));

        AddItemsRequest request = new AddItemsRequest(List.of(
                new AddItemsRequest.ItemData(
                        Map.of("input", "q1"),
                        Map.of("output", "a1"),
                        Map.of("output", "a1"),
                        List.of(new AddItemsRequest.EvalData("exact-match", 1.0, 0.9, true, "ok", Map.of())),
                        true),
                new AddItemsRequest.ItemData(
                        Map.of("input", "q2"),
                        Map.of("output", "a2"),
                        Map.of("output", "wrong"),
                        List.of(
                                new AddItemsRequest.EvalData("exact-match", 0.0, 0.9, false, "no", Map.of()),
                                new AddItemsRequest.EvalData("relevance", 0.5, 0.9, false, "weak", Map.of())),
                        false)));

        runService.addItems(runId, request, null);

        List<ItemResult> saved = captureSavedItems();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getEvalResults()).hasSize(1);
        assertThat(saved.get(1).getEvalResults()).hasSize(2);
        // saveAll is the single persistence call (no per-item save loop).
        verify(itemResultRepository).saveAll(any());
    }

    @Test
    void addItems_shouldHandleItemsWithoutEvalResults() {
        UUID runId = UUID.randomUUID();
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run = createRun(experiment, RunStatus.RUNNING);
        setField(run, "id", runId);

        when(runRepository.findByIdForUpdate(runId)).thenReturn(Optional.of(run));

        AddItemsRequest request = new AddItemsRequest(List.of(new AddItemsRequest.ItemData(
                Map.of("input", "test"), Map.of("output", "expected"), Map.of("output", "actual"), null, false)));

        runService.addItems(runId, request, null);

        List<ItemResult> saved = captureSavedItems();
        assertThat(saved.get(0).getEvalResults()).isEmpty();
    }

    @Test
    void addItems_shouldThrowWhenRunIsTerminal() {
        UUID runId = UUID.randomUUID();
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run = createRun(experiment, RunStatus.SUCCESS);
        setField(run, "id", runId);

        when(runRepository.findByIdForUpdate(runId)).thenReturn(Optional.of(run));

        AddItemsRequest request = new AddItemsRequest(List.of(new AddItemsRequest.ItemData(
                Map.of("input", "q"), Map.of("output", "a"), Map.of("output", "a"), null, true)));

        assertThatThrownBy(() -> runService.addItems(runId, request, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot add items to a run that is not RUNNING");
    }

    @Test
    void addItems_shouldInsertOnceWhenSameIdempotencyKeyUsedTwice() {
        UUID runId = UUID.randomUUID();
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run = createRun(experiment, RunStatus.RUNNING);
        setField(run, "id", runId);

        when(runRepository.findByIdForUpdate(runId)).thenReturn(Optional.of(run));
        // First call sees no recorded key, second call sees the key recorded by the first.
        when(ingestedBatchRepository.existsByRunIdAndIdempotencyKey(runId, "key-1"))
                .thenReturn(false)
                .thenReturn(true);

        AddItemsRequest request = singleItemRequest();

        runService.addItems(runId, request, "key-1");
        runService.addItems(runId, request, "key-1");

        // Items are inserted only on the first call; the retry is a no-op.
        verify(itemResultRepository, times(1)).saveAll(any());
        // The dedup record is written exactly once, in the same call that inserted the items.
        verify(ingestedBatchRepository, times(1)).save(any(IngestedBatch.class));
    }

    @Test
    void addItems_shouldInsertTwiceForDifferentIdempotencyKeys() {
        UUID runId = UUID.randomUUID();
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run = createRun(experiment, RunStatus.RUNNING);
        setField(run, "id", runId);

        when(runRepository.findByIdForUpdate(runId)).thenReturn(Optional.of(run));
        when(ingestedBatchRepository.existsByRunIdAndIdempotencyKey(eq(runId), any()))
                .thenReturn(false);

        AddItemsRequest request = singleItemRequest();

        runService.addItems(runId, request, "key-1");
        runService.addItems(runId, request, "key-2");

        verify(itemResultRepository, times(2)).saveAll(any());
        verify(ingestedBatchRepository, times(2)).save(any(IngestedBatch.class));
    }

    @Test
    void addItems_shouldInsertWithoutDedupRecordForNullKey() {
        UUID runId = UUID.randomUUID();
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run = createRun(experiment, RunStatus.RUNNING);
        setField(run, "id", runId);

        when(runRepository.findByIdForUpdate(runId)).thenReturn(Optional.of(run));

        runService.addItems(runId, singleItemRequest(), null);

        // Backward compatible path: items are inserted, no dedup lookup or record happens.
        verify(itemResultRepository, times(1)).saveAll(any());
        verify(ingestedBatchRepository, never()).existsByRunIdAndIdempotencyKey(any(), any());
        verify(ingestedBatchRepository, never()).save(any(IngestedBatch.class));
    }

    private AddItemsRequest singleItemRequest() {
        return new AddItemsRequest(List.of(new AddItemsRequest.ItemData(
                Map.of("input", "q"),
                Map.of("output", "a"),
                Map.of("output", "a"),
                List.of(new AddItemsRequest.EvalData("exact-match", 1.0, 0.9, true, "ok", Map.of())),
                true)));
    }

    @Test
    void listRuns_shouldReturnRunSummaries() {
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run1 = createRun(experiment, RunStatus.SUCCESS);
        ExperimentRun run2 = createRun(experiment, RunStatus.FAILED);
        setMaterializedCounts(run1, 10, 8);
        setMaterializedCounts(run2, 5, 2);

        when(runRepository.findByExperimentOrderByStartedAtDesc(experiment)).thenReturn(List.of(run1, run2));

        List<RunSummary> result = runService.listRuns(experiment);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.get(0).passRate()).isEqualTo(0.8);
        assertThat(result.get(1).status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.get(1).passRate()).isEqualTo(0.4);
    }

    @Test
    void listRuns_shouldReturnNullPassRateForEmptyRuns() {
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run = createRun(experiment, RunStatus.SUCCESS);
        setMaterializedCounts(run, 0, 0);

        when(runRepository.findByExperimentOrderByStartedAtDesc(experiment)).thenReturn(List.of(run));

        List<RunSummary> result = runService.listRuns(experiment);

        assertThat(result.get(0).passRate()).isNull();
    }

    @Test
    void getRunDetails_shouldReturnDetails() {
        UUID runId = UUID.randomUUID();
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run = createRun(experiment, RunStatus.SUCCESS);
        setField(run, "id", runId);
        setMaterializedCounts(run, 10, 8);

        Pageable pageable = PageRequest.of(0, 50);
        Page<ItemResult> emptyPage = new PageImpl<>(List.of());

        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        when(itemResultRepository.findByRunOrderByCreatedAtAsc(run, pageable)).thenReturn(emptyPage);

        RunDetails result = runService.getRunDetails(runId, pageable);

        assertThat(result.id()).isEqualTo(runId);
        assertThat(result.experimentName()).isEqualTo("my-experiment");
        assertThat(result.projectName()).isEqualTo("my-project");
        assertThat(result.totalItems()).isEqualTo(10);
        assertThat(result.passedItems()).isEqualTo(8);
        assertThat(result.passRate()).isEqualTo(0.8);
    }

    @Test
    void getRunDetails_shouldThrowWhenRunNotFound() {
        UUID runId = UUID.randomUUID();
        when(runRepository.findById(runId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> runService.getRunDetails(runId, PageRequest.of(0, 50)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Run not found");
    }

    @Test
    void deleteRun_shouldDeleteWhenFound() {
        UUID runId = UUID.randomUUID();
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run = createRun(experiment, RunStatus.SUCCESS);
        setField(run, "id", runId);

        when(runRepository.findById(runId)).thenReturn(Optional.of(run));

        runService.deleteRun(runId);

        verify(runRepository).delete(run);
    }

    @Test
    void deleteRun_shouldThrowWhenNotFound() {
        UUID runId = UUID.randomUUID();
        when(runRepository.findById(runId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> runService.deleteRun(runId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Run not found");
    }

    @SuppressWarnings("unchecked")
    private List<ItemResult> captureSavedItems() {
        ArgumentCaptor<List<ItemResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemResultRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private Project createProject(String name) {
        Project project = new Project(name);
        setField(project, "id", UUID.randomUUID());
        setField(project, "createdAt", Instant.now());
        return project;
    }

    private Experiment createExperiment(Project project, String name) {
        Experiment experiment = new Experiment(project, name);
        setField(experiment, "id", UUID.randomUUID());
        setField(experiment, "createdAt", Instant.now());
        return experiment;
    }

    private ExperimentRun createRun(Experiment experiment, RunStatus status) {
        ExperimentRun run = new ExperimentRun(experiment, Map.of());
        setField(run, "id", UUID.randomUUID());
        setField(run, "status", status);
        setField(run, "startedAt", Instant.now());
        return run;
    }

    private void setMaterializedCounts(ExperimentRun run, int itemCount, int passedCount) {
        setField(run, "itemCount", itemCount);
        setField(run, "passedCount", passedCount);
        setField(run, "passRate", itemCount > 0 ? (double) passedCount / itemCount : null);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
