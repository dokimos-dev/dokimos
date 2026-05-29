package dev.dokimos.server.dto.v1;

import java.time.Instant;
import java.util.UUID;

public record DatasetSummary(
        UUID id,
        String name,
        String description,
        Integer latestVersion,
        Integer latestItemCount,
        Instant createdAt,
        Instant updatedAt) {}
