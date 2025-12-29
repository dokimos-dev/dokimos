package dev.dokimos.server.dto.v1;

import dev.dokimos.server.entity.RunStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RunSummary(
                UUID id,
                RunStatus status,
                Map<String, Object> config,
                long itemCount,
                long passedCount,
                Double passRate,
                Instant startedAt,
                Instant completedAt) {
}
