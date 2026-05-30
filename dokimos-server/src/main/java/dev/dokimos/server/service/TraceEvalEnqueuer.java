package dev.dokimos.server.service;

import dev.dokimos.server.entity.Trace;
import dev.dokimos.server.entity.TraceEvalJob;
import dev.dokimos.server.entity.TraceEvalRule;
import dev.dokimos.server.entity.TraceSpan;
import dev.dokimos.server.repository.TraceEvalJobRepository;
import dev.dokimos.server.repository.TraceEvalRuleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Decides which online eval jobs to create for a freshly ingested trace and persists them. A job is
 * enqueued for every (span, enabled rule) pair where the span matches the rule and the span has a
 * non-blank derived output to score. The matching logic lives on {@link TraceEvalRule#matches} and is
 * unit tested directly.
 */
@Component
public class TraceEvalEnqueuer {

    private final TraceEvalRuleRepository ruleRepository;
    private final TraceEvalJobRepository jobRepository;

    public TraceEvalEnqueuer(TraceEvalRuleRepository ruleRepository, TraceEvalJobRepository jobRepository) {
        this.ruleRepository = ruleRepository;
        this.jobRepository = jobRepository;
    }

    /**
     * Enqueues online eval jobs for a persisted trace. Does nothing when the trace has no resolved
     * project, since rules are scoped per project. Must be called inside the ingestion transaction so
     * the spans are managed entities.
     *
     * @param trace the persisted trace with managed spans
     * @return the number of jobs enqueued
     */
    public int enqueueForTrace(Trace trace) {
        UUID projectId = trace.getProjectId();
        if (projectId == null) {
            return 0;
        }
        List<TraceEvalRule> rules = ruleRepository.findByProjectIdAndEnabledTrue(projectId);
        if (rules.isEmpty()) {
            return 0;
        }
        int enqueued = 0;
        for (TraceSpan span : trace.getSpans()) {
            for (TraceEvalRule rule : rules) {
                if (rule.matches(span) && hasScorableOutput(span)) {
                    jobRepository.save(new TraceEvalJob(span, rule));
                    enqueued++;
                }
            }
        }
        return enqueued;
    }

    private static boolean hasScorableOutput(TraceSpan span) {
        return span.getOutputText() != null && !span.getOutputText().isBlank();
    }
}
