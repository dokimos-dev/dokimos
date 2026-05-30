package dev.dokimos.server.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.dokimos.core.comparison.RunComparisonResult;
import dev.dokimos.core.comparison.SignificanceResult;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.entity.RunStatus;
import dev.dokimos.server.repository.ExperimentRunRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class RegressionAlertServiceTest {

    @Mock
    private ExperimentRunRepository runRepository;

    @Mock
    private ComparisonSupport comparisonSupport;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private RegressionAlertService service;

    @BeforeEach
    void setUp() {
        service = new RegressionAlertService(runRepository, comparisonSupport, eventPublisher);
    }

    @Test
    void shouldPublishEventOnSignificantRegression() {
        Project project = project("acme");
        Experiment experiment = experiment(project, "qa-suite");
        ExperimentRun candidate = run(experiment, RunStatus.SUCCESS);
        ExperimentRun baseline = run(experiment, RunStatus.SUCCESS);

        when(runRepository.findBaselineCandidates(
                        eq(experiment), eq(candidate.getId()), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(baseline));
        RunComparisonResult comparison = comparison(0.9, 0.6, true);
        when(comparisonSupport.compare(baseline, candidate))
                .thenReturn(new ComparisonSupport.ComparisonOutcome(comparison, "positional", List.of(), List.of()));

        service.evaluateOnCompletion(candidate);

        ArgumentCaptor<RegressionAlertEvent> captor = ArgumentCaptor.forClass(RegressionAlertEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        RegressionAlertEvent event = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(event.projectId()).isEqualTo(project.getId());
        org.assertj.core.api.Assertions.assertThat(event.payload().projectName())
                .isEqualTo("acme");
        org.assertj.core.api.Assertions.assertThat(event.payload().runId()).isEqualTo(candidate.getId());
        org.assertj.core.api.Assertions.assertThat(event.payload().baselineRunId())
                .isEqualTo(baseline.getId());
        org.assertj.core.api.Assertions.assertThat(event.payload().baselinePassRate())
                .isEqualTo(0.9);
        org.assertj.core.api.Assertions.assertThat(event.payload().candidatePassRate())
                .isEqualTo(0.6);
    }

    @Test
    void shouldNotPublishWhenRegressionNotSignificant() {
        Project project = project("acme");
        Experiment experiment = experiment(project, "qa-suite");
        ExperimentRun candidate = run(experiment, RunStatus.SUCCESS);
        ExperimentRun baseline = run(experiment, RunStatus.SUCCESS);

        when(runRepository.findBaselineCandidates(
                        eq(experiment), eq(candidate.getId()), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(baseline));
        when(comparisonSupport.compare(baseline, candidate))
                .thenReturn(new ComparisonSupport.ComparisonOutcome(
                        comparison(0.9, 0.88, false), "positional", List.of(), List.of()));

        service.evaluateOnCompletion(candidate);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldNotPublishWhenNoBaseline() {
        Project project = project("acme");
        Experiment experiment = experiment(project, "qa-suite");
        ExperimentRun candidate = run(experiment, RunStatus.SUCCESS);

        when(runRepository.findBaselineCandidates(
                        eq(experiment), eq(candidate.getId()), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        service.evaluateOnCompletion(candidate);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldSwallowFailuresAndNeverThrow() {
        Project project = project("acme");
        Experiment experiment = experiment(project, "qa-suite");
        ExperimentRun candidate = run(experiment, RunStatus.SUCCESS);

        when(runRepository.findBaselineCandidates(any(), any(), any(), any(), any(Pageable.class)))
                .thenThrow(new RuntimeException("db down"));

        // Must not propagate: run completion has already committed materialized counts.
        service.evaluateOnCompletion(candidate);

        verify(eventPublisher, never()).publishEvent(any());
    }

    private RunComparisonResult comparison(double baselineRate, double candidateRate, boolean significant) {
        SignificanceResult sig =
                new SignificanceResult("two-proportion", significant ? 0.01 : 0.4, significant, null, null);
        boolean regressed = candidateRate < baselineRate;
        return new RunComparisonResult(
                baselineRate,
                candidateRate,
                candidateRate - baselineRate,
                sig,
                regressed,
                false,
                0,
                3,
                7,
                0,
                0,
                0,
                0,
                List.of(),
                List.of());
    }

    private Project project(String name) {
        Project project = new Project(name);
        setField(project, "id", UUID.randomUUID());
        return project;
    }

    private Experiment experiment(Project project, String name) {
        Experiment experiment = new Experiment(project, name);
        setField(experiment, "id", UUID.randomUUID());
        return experiment;
    }

    private ExperimentRun run(Experiment experiment, RunStatus status) {
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
