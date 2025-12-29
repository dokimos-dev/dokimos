package dev.dokimos.server.dto.v1;

import dev.dokimos.server.entity.RunStatus;

import java.time.Instant;
import java.util.UUID;

public record ExperimentSummary(
        UUID id,
        String name,
        Instant createdAt,
        LatestRunInfo latestRun
) {
    public record LatestRunInfo(
            UUID runId,
            RunStatus status,
            Double passRate,
            Instant startedAt
    ) {
    }
}
