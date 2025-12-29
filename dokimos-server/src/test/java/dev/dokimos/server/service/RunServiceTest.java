package dev.dokimos.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.server.dto.v1.AddItemsRequest;
import dev.dokimos.server.dto.v1.RunDetails;
import dev.dokimos.server.dto.v1.RunSummary;
import dev.dokimos.server.dto.v1.UpdateRunRequest;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.ItemResult;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.entity.RunStatus;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.repository.ItemResultRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunServiceTest {

    @Mock
    private ExperimentRunRepository runRepository;

    @Mock
    private ItemResultRepository itemResultRepository;

    private RunService runService;

    @BeforeEach
    void setUp() {
        runService = new RunService(runRepository, itemResultRepository, new ObjectMapper());
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

        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        when(runRepository.save(any(ExperimentRun.class))).thenAnswer(inv -> inv.getArgument(0));

        runService.updateRun(runId, new UpdateRunRequest(RunStatus.SUCCESS));

        ArgumentCaptor<ExperimentRun> captor = ArgumentCaptor.forClass(ExperimentRun.class);
        verify(runRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(RunStatus.SUCCESS);
        assertThat(captor.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    void updateRun_shouldNotSetCompletedAtForRunningStatus() {
        UUID runId = UUID.randomUUID();
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run = createRun(experiment, RunStatus.RUNNING);
        setField(run, "id", runId);

        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        when(runRepository.save(any(ExperimentRun.class))).thenAnswer(inv -> inv.getArgument(0));

        runService.updateRun(runId, new UpdateRunRequest(RunStatus.RUNNING));

        ArgumentCaptor<ExperimentRun> captor = ArgumentCaptor.forClass(ExperimentRun.class);
        verify(runRepository).save(captor.capture());
        assertThat(captor.getValue().getCompletedAt()).isNull();
    }

    @Test
    void updateRun_shouldThrowWhenRunNotFound() {
        UUID runId = UUID.randomUUID();
        when(runRepository.findById(runId)).thenReturn(Optional.empty());

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

        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        when(itemResultRepository.save(any(ItemResult.class))).thenAnswer(inv -> inv.getArgument(0));

        AddItemsRequest request = new AddItemsRequest(List.of(
                new AddItemsRequest.ItemData(
                        Map.of("input", "What is 2+2?"),
                        Map.of("output", "4"),
                        Map.of("output", "4"),
                        List.of(new AddItemsRequest.EvalData("exact-match", 1.0, 0.9, true, "Correct", Map.of())),
                        true
                )
        ));

        runService.addItems(runId, request);

        ArgumentCaptor<ItemResult> captor = ArgumentCaptor.forClass(ItemResult.class);
        verify(itemResultRepository).save(captor.capture());
        assertThat(captor.getValue().getInput()).isEqualTo("What is 2+2?");
        assertThat(captor.getValue().getEvalResults()).hasSize(1);
    }

    @Test
    void addItems_shouldHandleItemsWithoutEvalResults() {
        UUID runId = UUID.randomUUID();
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run = createRun(experiment, RunStatus.RUNNING);
        setField(run, "id", runId);

        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        when(itemResultRepository.save(any(ItemResult.class))).thenAnswer(inv -> inv.getArgument(0));

        AddItemsRequest request = new AddItemsRequest(List.of(
                new AddItemsRequest.ItemData(
                        Map.of("input", "test"),
                        Map.of("output", "expected"),
                        Map.of("output", "actual"),
                        null,
                        false
                )
        ));

        runService.addItems(runId, request);

        ArgumentCaptor<ItemResult> captor = ArgumentCaptor.forClass(ItemResult.class);
        verify(itemResultRepository).save(captor.capture());
        assertThat(captor.getValue().getEvalResults()).isEmpty();
    }

    @Test
    void listRuns_shouldReturnRunSummaries() {
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run1 = createRun(experiment, RunStatus.SUCCESS);
        ExperimentRun run2 = createRun(experiment, RunStatus.FAILED);

        when(runRepository.findByExperimentOrderByStartedAtDesc(experiment))
                .thenReturn(List.of(run1, run2));
        when(itemResultRepository.countByRun(run1)).thenReturn(10L);
        when(itemResultRepository.countItemsWithAllEvalsPassed(run1)).thenReturn(8L);
        when(itemResultRepository.countByRun(run2)).thenReturn(5L);
        when(itemResultRepository.countItemsWithAllEvalsPassed(run2)).thenReturn(2L);

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

        when(runRepository.findByExperimentOrderByStartedAtDesc(experiment))
                .thenReturn(List.of(run));
        when(itemResultRepository.countByRun(run)).thenReturn(0L);
        when(itemResultRepository.countItemsWithAllEvalsPassed(run)).thenReturn(0L);

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

        Pageable pageable = PageRequest.of(0, 50);
        Page<ItemResult> emptyPage = new PageImpl<>(List.of());

        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        when(itemResultRepository.countByRun(run)).thenReturn(10L);
        when(itemResultRepository.countItemsWithAllEvalsPassed(run)).thenReturn(8L);
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
