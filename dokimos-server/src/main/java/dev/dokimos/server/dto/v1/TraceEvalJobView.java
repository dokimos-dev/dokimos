package dev.dokimos.server.dto.v1;

import dev.dokimos.server.entity.TraceEvalJob;
import dev.dokimos.server.entity.TraceEvalJobStatus;
import java.time.Instant;
import java.util.UUID;

/** Public view of a {@link TraceEvalJob}, returned in the per-trace detail. */
public record TraceEvalJobView(
        UUID id,
        UUID spanId,
        UUID ruleId,
        String evaluatorName,
        TraceEvalJobStatus status,
        Double score,
        Boolean success,
        String reason,
        int attemptCount,
        String lastError,
        Instant createdAt,
        Instant completedAt) {

    public static TraceEvalJobView from(TraceEvalJob job) {
        return new TraceEvalJobView(
                job.getId(),
                job.getSpan().getId(),
                job.getRule().getId(),
                job.getEvaluatorName(),
                job.getStatus(),
                job.getScore(),
                job.getSuccess(),
                job.getReason(),
                job.getAttemptCount(),
                job.getLastError(),
                job.getCreatedAt(),
                job.getCompletedAt());
    }
}
