package dev.dokimos.server.dto.v1;

import dev.dokimos.server.entity.EvalJob;
import dev.dokimos.server.entity.EvalJobStatus;
import java.time.Instant;
import java.util.UUID;

/** Public view of an {@link EvalJob}, returned by the enqueue endpoint and the per-run job listing. */
public record EvalJobView(
        UUID id,
        UUID runId,
        UUID connectionId,
        String evaluatorName,
        EvalJobStatus status,
        int attemptCount,
        String lastError,
        Instant createdAt,
        Instant claimedAt,
        Instant completedAt) {

    public static EvalJobView from(EvalJob job) {
        return new EvalJobView(
                job.getId(),
                job.getRun().getId(),
                job.getConnection().getId(),
                job.getEvaluatorName(),
                job.getStatus(),
                job.getAttemptCount(),
                job.getLastError(),
                job.getCreatedAt(),
                job.getClaimedAt(),
                job.getCompletedAt());
    }
}
