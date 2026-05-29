package dev.dokimos.server.dto.v1;

import java.time.Instant;
import java.util.UUID;

public record DatasetVersionDetails(
        UUID id,
        String datasetName,
        int version,
        String description,
        int itemCount,
        Instant createdAt,
        String createdBy) {}
