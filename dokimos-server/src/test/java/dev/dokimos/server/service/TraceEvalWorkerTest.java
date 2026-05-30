package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.dokimos.core.JudgeLM;
import dev.dokimos.server.config.TraceProperties;
import dev.dokimos.server.entity.LlmConnection;
import dev.dokimos.server.entity.Trace;
import dev.dokimos.server.entity.TraceEvalJob;
import dev.dokimos.server.entity.TraceEvalRule;
import dev.dokimos.server.entity.TraceMatchType;
import dev.dokimos.server.entity.TraceSpan;
import dev.dokimos.server.judge.JudgeCallException;
import dev.dokimos.server.service.TraceEvalJobTransactions.ClaimedJob;
import java.time.Instant;
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
class TraceEvalWorkerTest {

    @Mock
    private TraceEvalJobTransactions transactions;

    @Mock
    private LlmCredentialService credentialService;

    private TraceProperties properties;
    private UUID jobId;
    private TraceEvalJob job;
    private LlmConnection connection;

    @BeforeEach
    void setUp() {
        properties = new TraceProperties();
        jobId = UUID.randomUUID();
        connection = new LlmConnection("conn", "https://api.example.com", "gpt-4");

        Trace trace = new Trace("t1", Instant.now(), Instant.now().plusSeconds(60));
        TraceSpan span = new TraceSpan("t1", "s1", "llm.generate", Instant.now());
        span.setOutputText("an answer");
        trace.addSpan(span);
        TraceEvalRule rule = new TraceEvalRule(
                UUID.randomUUID(), "r", TraceMatchType.SPAN_NAME, "llm.generate", connection, "judge", "is correct");
        job = new TraceEvalJob(span, rule);
        setId(job, jobId);
    }

    private TraceEvalWorker worker(JudgeLM judge) {
        BiFunction<LlmConnection, String, JudgeLM> factory = (c, key) -> judge;
        return new TraceEvalWorker(transactions, credentialService, properties, factory);
    }

    private ClaimedJob claimed() {
        return new ClaimedJob(job, "what is 2+2", "an answer");
    }

    @Test
    void successRecordsScore() {
        JudgeLM judge = prompt -> "{\"score\": 0.8, \"reason\": \"accurate\"}";
        when(transactions.claimNextJob(anyInt())).thenReturn(Optional.of(claimed()));
        when(credentialService.resolveKey(connection)).thenReturn("sk-test");

        worker(judge).poll();

        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
        verify(transactions).markSucceeded(eq(jobId), scoreCaptor.capture(), eq(true), eq("accurate"));
        assertThat(scoreCaptor.getValue()).isEqualTo(0.8);
        verify(transactions, never()).recordFailure(any(), any(), anyBoolean(), anyInt());
    }

    @Test
    void retryableFailureIsRecordedAsRetryable() {
        JudgeLM judge = prompt -> {
            throw new JudgeCallException(503, "service unavailable");
        };
        when(transactions.claimNextJob(anyInt())).thenReturn(Optional.of(claimed()));
        when(credentialService.resolveKey(connection)).thenReturn("sk-test");

        worker(judge).poll();

        verify(transactions)
                .recordFailure(
                        eq(jobId), any(), eq(true), eq(properties.getEval().getMaxAttempts()));
        verify(transactions, never()).markSucceeded(any(), anyDouble(), anyBoolean(), any());
    }

    @Test
    void nonRetryableFailureIsRecordedAsTerminal() {
        JudgeLM judge = prompt -> {
            throw new JudgeCallException(401, "unauthorized");
        };
        when(transactions.claimNextJob(anyInt())).thenReturn(Optional.of(claimed()));
        when(credentialService.resolveKey(connection)).thenReturn("sk-test");

        worker(judge).poll();

        verify(transactions)
                .recordFailure(
                        eq(jobId), any(), eq(false), eq(properties.getEval().getMaxAttempts()));
    }

    @Test
    void noClaimableJobDoesNothing() {
        when(transactions.claimNextJob(anyInt())).thenReturn(Optional.empty());

        worker(prompt -> "{}").poll();

        verify(transactions, never()).markSucceeded(any(), anyDouble(), anyBoolean(), any());
        verify(transactions, never()).recordFailure(any(), any(), anyBoolean(), anyInt());
    }

    @Test
    void malformedJudgeResponseScoresAsFailureButSucceeds() {
        JudgeLM judge = prompt -> "not valid json";
        when(transactions.claimNextJob(anyInt())).thenReturn(Optional.of(claimed()));
        when(credentialService.resolveKey(connection)).thenReturn("sk-test");

        worker(judge).poll();

        verify(transactions).markSucceeded(eq(jobId), anyDouble(), eq(false), any());
    }

    private static void setId(TraceEvalJob job, UUID id) {
        try {
            var field = TraceEvalJob.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(job, id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
