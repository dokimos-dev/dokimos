package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.dokimos.server.dto.v1.EnqueueJudgeRequest;
import dev.dokimos.server.dto.v1.EvalJobView;
import dev.dokimos.server.entity.EvalJob;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.LlmConnection;
import dev.dokimos.server.entity.RunStatus;
import dev.dokimos.server.repository.EvalJobRepository;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.repository.LlmConnectionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvalJobServiceTest {

    @Mock
    private EvalJobRepository jobRepository;

    @Mock
    private ExperimentRunRepository runRepository;

    @Mock
    private LlmConnectionRepository connectionRepository;

    private EvalJobService service;

    private UUID runId;
    private UUID connectionId;
    private ExperimentRun run;
    private LlmConnection connection;

    @BeforeEach
    void setUp() {
        service = new EvalJobService(jobRepository, runRepository, connectionRepository);
        runId = UUID.randomUUID();
        connectionId = UUID.randomUUID();
        run = mock(ExperimentRun.class);
        connection = mock(LlmConnection.class);
    }

    private EnqueueJudgeRequest request() {
        return new EnqueueJudgeRequest(connectionId, "judge", "is correct", List.of("ACTUAL_OUTPUT"), 0.0, 1.0, 0.5);
    }

    @Test
    void enqueueCreatesPendingJobAndMovesRunToEvaluating() {
        when(runRepository.findByIdForUpdate(runId)).thenReturn(Optional.of(run));
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
        when(jobRepository.existsByRunAndEvaluatorName(run, "judge")).thenReturn(false);
        when(jobRepository.save(any(EvalJob.class))).thenAnswer(inv -> inv.getArgument(0));

        EvalJobView view = service.enqueue(runId, request());

        assertThat(view.evaluatorName()).isEqualTo("judge");
        verify(run).setStatus(RunStatus.EVALUATING);

        ArgumentCaptor<EvalJob> jobCaptor = ArgumentCaptor.forClass(EvalJob.class);
        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getEvaluationParams()).isEqualTo("ACTUAL_OUTPUT");
        verify(runRepository).save(run);
    }

    @Test
    void enqueueRejectsDuplicateEvaluator() {
        when(runRepository.findByIdForUpdate(runId)).thenReturn(Optional.of(run));
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
        when(jobRepository.existsByRunAndEvaluatorName(run, "judge")).thenReturn(true);

        assertThatThrownBy(() -> service.enqueue(runId, request())).isInstanceOf(IllegalStateException.class);

        verify(jobRepository, never()).save(any());
    }

    @Test
    void enqueueRejectsMissingRun() {
        when(runRepository.findByIdForUpdate(runId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enqueue(runId, request())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enqueueRejectsMissingConnection() {
        when(runRepository.findByIdForUpdate(runId)).thenReturn(Optional.of(run));
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enqueue(runId, request())).isInstanceOf(IllegalArgumentException.class);
    }
}
