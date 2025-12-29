package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.ExperimentSummary;
import dev.dokimos.server.dto.v1.TrendData;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.entity.RunStatus;
import dev.dokimos.server.repository.ExperimentRepository;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.repository.ItemResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExperimentServiceTest {

    @Mock
    private ExperimentRepository experimentRepository;

    @Mock
    private ExperimentRunRepository runRepository;

    @Mock
    private ItemResultRepository itemResultRepository;

    private ExperimentService experimentService;

    @BeforeEach
    void setUp() {
        experimentService = new ExperimentService(experimentRepository, runRepository, itemResultRepository);
    }

    @Test
    void getOrCreateExperiment_shouldReturnExisting() {
        Project project = createProject("my-project");
        Experiment existing = createExperiment(project, "my-experiment");
        when(experimentRepository.findByProjectAndName(project, "my-experiment"))
                .thenReturn(Optional.of(existing));

        Experiment result = experimentService.getOrCreateExperiment(project, "my-experiment");

        assertThat(result).isEqualTo(existing);
        verify(experimentRepository, never()).save(any());
    }

    @Test
    void getOrCreateExperiment_shouldCreateNew() {
        Project project = createProject("my-project");
        when(experimentRepository.findByProjectAndName(project, "new-experiment"))
                .thenReturn(Optional.empty());
        when(experimentRepository.save(any(Experiment.class))).thenAnswer(inv -> inv.getArgument(0));

        Experiment result = experimentService.getOrCreateExperiment(project, "new-experiment");

        assertThat(result.getName()).isEqualTo("new-experiment");
        verify(experimentRepository).save(any(Experiment.class));
    }

    @Test
    void getExperiment_shouldReturnExperiment() {
        UUID experimentId = UUID.randomUUID();
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        setField(experiment, "id", experimentId);
        when(experimentRepository.findById(experimentId)).thenReturn(Optional.of(experiment));

        Experiment result = experimentService.getExperiment(experimentId);

        assertThat(result).isEqualTo(experiment);
    }

    @Test
    void getExperiment_shouldThrowWhenNotFound() {
        UUID experimentId = UUID.randomUUID();
        when(experimentRepository.findById(experimentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentService.getExperiment(experimentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Experiment not found");
    }

    @Test
    void getExperiment_shouldThrowWhenIdIsNull() {
        assertThatThrownBy(() -> experimentService.getExperiment(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Experiment ID cannot be null");
    }

    @Test
    void listExperiments_shouldReturnSummariesWithLatestRun() {
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run = createRun(experiment, RunStatus.SUCCESS);

        when(experimentRepository.findByProjectOrderByCreatedAtDesc(project))
                .thenReturn(List.of(experiment));
        when(runRepository.findFirstByExperimentOrderByStartedAtDesc(experiment))
                .thenReturn(Optional.of(run));
        when(itemResultRepository.countByRun(run)).thenReturn(10L);
        when(itemResultRepository.countItemsWithAllEvalsPassed(run)).thenReturn(8L);

        List<ExperimentSummary> result = experimentService.listExperiments(project);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("my-experiment");
        assertThat(result.get(0).latestRun()).isNotNull();
        assertThat(result.get(0).latestRun().status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.get(0).latestRun().passRate()).isEqualTo(0.8);
    }

    @Test
    void listExperiments_shouldReturnSummariesWithoutRun() {
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");

        when(experimentRepository.findByProjectOrderByCreatedAtDesc(project))
                .thenReturn(List.of(experiment));
        when(runRepository.findFirstByExperimentOrderByStartedAtDesc(experiment))
                .thenReturn(Optional.empty());

        List<ExperimentSummary> result = experimentService.listExperiments(project);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).latestRun()).isNull();
    }

    @Test
    void listExperiments_shouldReturnNullPassRateForRunningExperiment() {
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        ExperimentRun run = createRun(experiment, RunStatus.RUNNING);

        when(experimentRepository.findByProjectOrderByCreatedAtDesc(project))
                .thenReturn(List.of(experiment));
        when(runRepository.findFirstByExperimentOrderByStartedAtDesc(experiment))
                .thenReturn(Optional.of(run));

        List<ExperimentSummary> result = experimentService.listExperiments(project);

        assertThat(result.get(0).latestRun().passRate()).isNull();
    }

    @Test
    void getTrends_shouldReturnTrendData() {
        UUID experimentId = UUID.randomUUID();
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        setField(experiment, "id", experimentId);
        ExperimentRun run1 = createRun(experiment, RunStatus.SUCCESS);
        ExperimentRun run2 = createRun(experiment, RunStatus.SUCCESS);

        when(experimentRepository.findById(experimentId)).thenReturn(Optional.of(experiment));
        when(runRepository.findCompletedRunsByExperiment(eq(experiment), any(PageRequest.class)))
                .thenReturn(List.of(run1, run2));
        when(itemResultRepository.countByRun(run1)).thenReturn(10L);
        when(itemResultRepository.countItemsWithAllEvalsPassed(run1)).thenReturn(8L);
        when(itemResultRepository.countByRun(run2)).thenReturn(5L);
        when(itemResultRepository.countItemsWithAllEvalsPassed(run2)).thenReturn(5L);

        TrendData result = experimentService.getTrends(experimentId, 20);

        assertThat(result.experimentName()).isEqualTo("my-experiment");
        assertThat(result.runs()).hasSize(2);
    }

    @Test
    void getTrends_shouldReturnZeroPassRateForEmptyRuns() {
        UUID experimentId = UUID.randomUUID();
        Project project = createProject("my-project");
        Experiment experiment = createExperiment(project, "my-experiment");
        setField(experiment, "id", experimentId);
        ExperimentRun run = createRun(experiment, RunStatus.SUCCESS);

        when(experimentRepository.findById(experimentId)).thenReturn(Optional.of(experiment));
        when(runRepository.findCompletedRunsByExperiment(eq(experiment), any(PageRequest.class)))
                .thenReturn(List.of(run));
        when(itemResultRepository.countByRun(run)).thenReturn(0L);
        when(itemResultRepository.countItemsWithAllEvalsPassed(run)).thenReturn(0L);

        TrendData result = experimentService.getTrends(experimentId, 20);

        assertThat(result.runs().get(0).passRate()).isEqualTo(0.0);
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
