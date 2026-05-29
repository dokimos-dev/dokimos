package dev.dokimos.server.dto.v1;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DatasetDetails(
        UUID id, String name, String description, Instant createdAt, Instant updatedAt, List<VersionSummary> versions) {
    public record VersionSummary(
            UUID id, int version, String description, int itemCount, Instant createdAt, String createdBy) {}
}
