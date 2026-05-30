package dev.dokimos.server.dto.v1;

import dev.dokimos.server.filter.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request to mint a scoped API key. The server generates the raw key, hashes it, and stores only the
 * hash; the raw key is returned exactly once in the response.
 *
 * @param name human-readable label for the key (required)
 * @param role privilege level granted to callers presenting the key (required)
 * @param tenantId tenant the key is scoped to, or null for an unscoped key
 */
public record CreateApiKeyRequest(
        @NotBlank String name, @NotNull Role role, String tenantId) {}
