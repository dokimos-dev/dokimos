package dev.dokimos.server.dto.v1;

import dev.dokimos.server.entity.LlmConnection;
import dev.dokimos.server.entity.LlmConnectionProtocol;
import java.time.Instant;
import java.util.UUID;

/**
 * Public view of an {@link LlmConnection}. Never carries raw or encrypted key material:
 * {@code hasInlineKey} reports whether an inline key is stored, and {@code credentialRef} is populated
 * only when an external credential reference is in use. {@code protocol} reports the API the endpoint
 * speaks.
 */
public record LlmConnectionView(
        UUID id,
        String name,
        String baseUrl,
        String model,
        LlmConnectionProtocol protocol,
        String credentialRef,
        boolean hasInlineKey,
        Instant createdAt) {

    public static LlmConnectionView from(LlmConnection connection) {
        return new LlmConnectionView(
                connection.getId(),
                connection.getName(),
                connection.getBaseUrl(),
                connection.getModel(),
                connection.getProtocol(),
                connection.getCredentialRef(),
                connection.hasInlineKey(),
                connection.getCreatedAt());
    }
}
