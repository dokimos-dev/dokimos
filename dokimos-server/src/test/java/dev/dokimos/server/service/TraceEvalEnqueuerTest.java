package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.dokimos.server.entity.Trace;
import dev.dokimos.server.entity.TraceEvalJob;
import dev.dokimos.server.entity.TraceEvalRule;
import dev.dokimos.server.entity.TraceMatchType;
import dev.dokimos.server.entity.TraceSpan;
import dev.dokimos.server.repository.TraceEvalJobRepository;
import dev.dokimos.server.repository.TraceEvalRuleRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TraceEvalEnqueuerTest {

    @Mock
    private TraceEvalRuleRepository ruleRepository;

    @Mock
    private TraceEvalJobRepository jobRepository;

    private TraceEvalEnqueuer enqueuer;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        enqueuer = new TraceEvalEnqueuer(ruleRepository, jobRepository);
        projectId = UUID.randomUUID();
    }

    private Trace traceWith(TraceSpan... spans) {
        Trace trace = new Trace("t1", Instant.now(), Instant.now().plusSeconds(60));
        trace.setProjectId(projectId);
        for (TraceSpan span : spans) {
            trace.addSpan(span);
        }
        return trace;
    }

    private TraceSpan span(String name, String output) {
        TraceSpan span = new TraceSpan("t1", "s-" + name, name, Instant.now());
        span.setOutputText(output);
        return span;
    }

    private TraceEvalRule rule(String name) {
        return new TraceEvalRule(
                projectId, name, TraceMatchType.SPAN_NAME, "llm.generate", null, "judge", "is correct");
    }

    @Test
    void enqueuesJobForMatchingSpanWithOutput() {
        when(ruleRepository.findByProjectIdAndEnabledTrue(projectId)).thenReturn(List.of(rule("r1")));

        int enqueued = enqueuer.enqueueForTrace(traceWith(span("llm.generate", "an answer")));

        assertThat(enqueued).isEqualTo(1);
        verify(jobRepository).save(org.mockito.ArgumentMatchers.any(TraceEvalJob.class));
    }

    @Test
    void skipsSpanWithoutOutput() {
        when(ruleRepository.findByProjectIdAndEnabledTrue(projectId)).thenReturn(List.of(rule("r1")));

        int enqueued = enqueuer.enqueueForTrace(traceWith(span("llm.generate", "  ")));

        assertThat(enqueued).isZero();
        verify(jobRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsNonMatchingSpan() {
        when(ruleRepository.findByProjectIdAndEnabledTrue(projectId)).thenReturn(List.of(rule("r1")));

        int enqueued = enqueuer.enqueueForTrace(traceWith(span("db.query", "rows")));

        assertThat(enqueued).isZero();
        verify(jobRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNothingWhenTraceHasNoProject() {
        Trace trace = new Trace("t1", Instant.now(), Instant.now().plusSeconds(60));
        trace.addSpan(span("llm.generate", "x"));

        int enqueued = enqueuer.enqueueForTrace(trace);

        assertThat(enqueued).isZero();
        verify(ruleRepository, never()).findByProjectIdAndEnabledTrue(org.mockito.ArgumentMatchers.any());
    }
}
