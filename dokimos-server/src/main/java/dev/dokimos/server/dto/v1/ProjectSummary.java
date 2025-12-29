package dev.dokimos.server.dto.v1;

import java.time.Instant;
import java.util.UUID;

public record ProjectSummary(
                UUID id,
                String name,
                long experimentCount,
                Instant createdAt) {
}
