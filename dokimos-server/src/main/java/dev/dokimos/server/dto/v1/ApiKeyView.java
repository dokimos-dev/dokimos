package dev.dokimos.server.dto.v1;

import dev.dokimos.server.entity.ApiKey;
import dev.dokimos.server.filter.Role;
import java.time.Instant;
import java.util.UUID;

/**
 * Metadata view of an {@link ApiKey}. Never carries the raw key or its hash: it exposes only the
 * identifying fields needed to manage keys. The raw key is available exactly once, from
 * {@link CreatedApiKeyView} at creation time.
 */
public record ApiKeyView(
        UUID id, String name, Role role, String tenantId, boolean enabled, Instant createdAt, Instant lastUsedAt) {

    public static ApiKeyView from(ApiKey key) {
        return new ApiKeyView(
                key.getId(),
                key.getName(),
                key.getRole(),
                key.getTenantId(),
                key.isEnabled(),
                key.getCreatedAt(),
                key.getLastUsedAt());
    }
}
