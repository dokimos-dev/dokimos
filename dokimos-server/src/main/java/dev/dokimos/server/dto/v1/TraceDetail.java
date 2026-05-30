package dev.dokimos.server.dto.v1;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full view of one trace: its metadata, all of its spans, and the online eval jobs scoped to those spans. */
public record TraceDetail(
        UUID id,
        String traceId,
        UUID projectId,
        String tenantId,
        String rootSpanName,
        int spanCount,
        Long startTimeUnixNano,
        Long endTimeUnixNano,
        Instant createdAt,
        Instant expiresAt,
        List<SpanView> spans,
        List<TraceEvalJobView> evalJobs) {}
