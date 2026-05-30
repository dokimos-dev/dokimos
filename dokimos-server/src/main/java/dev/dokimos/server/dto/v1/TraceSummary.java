package dev.dokimos.server.dto.v1;

import dev.dokimos.server.entity.Trace;
import java.time.Instant;
import java.util.UUID;

/** Compact view of a {@link Trace} for the list endpoint, without its spans. */
public record TraceSummary(
        UUID id,
        String traceId,
        UUID projectId,
        String rootSpanName,
        int spanCount,
        Instant createdAt,
        Instant expiresAt) {

    public static TraceSummary from(Trace trace) {
        return new TraceSummary(
                trace.getId(),
                trace.getTraceId(),
                trace.getProjectId(),
                trace.getRootSpanName(),
                trace.getSpanCount(),
                trace.getCreatedAt(),
                trace.getExpiresAt());
    }
}
