package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.dokimos.core.JudgeLM;
import dev.dokimos.server.config.JudgeProperties;
import dev.dokimos.server.entity.EvalJob;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.LlmConnection;
import dev.dokimos.server.judge.JudgeCallException;
import dev.dokimos.server.service.JudgeJobTransactions.ItemSnapshot;
import dev.dokimos.server.service.JudgeJobTransactions.ScoredResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JudgeWorkerTest {

    @Mock
    private JudgeJobTransactions transactions;

    @Mock
    private LlmCredentialService credentialService;

    private JudgeProperties properties;
    private UUID jobId;
    private UUID runId;
    private EvalJob job;
    private LlmConnection connection;

    @BeforeEach
    void setUp() {
        properties = new JudgeProperties();
        jobId = UUID.randomUUID();
        runId = UUID.randomUUID();
        connection = new LlmConnection("conn", "https://api.example.com", "gpt-4");
        ExperimentRun run = mock(ExperimentRun.class);
        org.mockito.Mockito.lenient().when(run.getId()).thenReturn(runId);
        job = new EvalJob(run, connection, "judge", "is correct");
        job.setEvaluationParams("ACTUAL_OUTPUT");
        setId(job, jobId);
    }

    private JudgeWorker worker(JudgeLM judge) {
        BiFunction<LlmConnection, String, JudgeLM> factory = (c, key) -> judge;
        return new JudgeWorker(transactions, credentialService, properties, factory);
    }

    @Test
    void fullCyclePersistsResultsAndFinalizesRun() {
        JudgeLM judge = prompt -> "{\"score\": 0.9, \"reason\": \"good\"}";
        when(transactions.claimNextJob(anyInt())).thenReturn(Optional.of(job));
        when(credentialService.resolveKey(connection)).thenReturn("sk-test");
        when(transactions.loadUnevaluatedPage(eq(runId), eq("judge"), any(UUID.class), anyInt()))
                .thenReturn(List.of(snapshot(), snapshot()))
                .thenReturn(List.of());

        worker(judge).poll();

        ArgumentCaptor<List<ScoredResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactions).persistPage(eq(jobId), captor.capture(), any(UUID.class));
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().get(0).evalResult().getScore()).isEqualTo(0.9);
        assertThat(captor.getValue().get(0).evalResult().isSuccess()).isTrue();
        verify(transactions).markSucceeded(jobId, runId);
        verify(transactions, never()).recordFailure(any(), any(), anyBoolean(), anyInt());
    }

    @Test
    void retryableFailureIsRecordedAsRetryable() {
        JudgeLM judge = prompt -> {
            throw new JudgeCallException(503, "service unavailable");
        };
        when(transactions.claimNextJob(anyInt())).thenReturn(Optional.of(job));
        when(credentialService.resolveKey(connection)).thenReturn("sk-test");
        when(transactions.loadUnevaluatedPage(eq(runId), eq("judge"), any(UUID.class), anyInt()))
                .thenReturn(List.of(snapshot()));

        worker(judge).poll();

        verify(transactions).recordFailure(eq(jobId), any(), eq(true), eq(properties.getMaxAttempts()));
        verify(transactions, never()).markSucceeded(any(), any());
    }

    @Test
    void nonRetryableFailureIsRecordedAsTerminal() {
        JudgeLM judge = prompt -> {
            throw new JudgeCallException(401, "unauthorized");
        };
        when(transactions.claimNextJob(anyInt())).thenReturn(Optional.of(job));
        when(credentialService.resolveKey(connection)).thenReturn("sk-test");
        when(transactions.loadUnevaluatedPage(eq(runId), eq("judge"), any(UUID.class), anyInt()))
                .thenReturn(List.of(snapshot()));

        worker(judge).poll();

        verify(transactions).recordFailure(eq(jobId), any(), eq(false), eq(properties.getMaxAttempts()));
    }

    @Test
    void noClaimableJobDoesNothing() {
        when(transactions.claimNextJob(anyInt())).thenReturn(Optional.empty());

        worker(prompt -> "{}").poll();

        verify(transactions, never()).loadUnevaluatedPage(any(), any(), any(), anyInt());
        verify(transactions, never()).markSucceeded(any(), any());
    }

    @Test
    void malformedJudgeResponseStillScoresAsFailureAndSucceeds() {
        JudgeLM judge = prompt -> "not valid json";
        when(transactions.claimNextJob(anyInt())).thenReturn(Optional.of(job));
        when(credentialService.resolveKey(connection)).thenReturn("sk-test");
        when(transactions.loadUnevaluatedPage(eq(runId), eq("judge"), any(UUID.class), anyInt()))
                .thenReturn(List.of(snapshot()))
                .thenReturn(List.of());

        worker(judge).poll();

        ArgumentCaptor<List<ScoredResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactions).persistPage(eq(jobId), captor.capture(), any(UUID.class));
        assertThat(captor.getValue().get(0).evalResult().isSuccess()).isFalse();
        verify(transactions).markSucceeded(jobId, runId);
    }

    private ItemSnapshot snapshot() {
        return new ItemSnapshot(UUID.randomUUID(), Map.of("q", "x"), Map.of("a", "y"), Map.of("a", "y"));
    }

    private static void setId(EvalJob job, UUID id) {
        try {
            var field = EvalJob.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(job, id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
