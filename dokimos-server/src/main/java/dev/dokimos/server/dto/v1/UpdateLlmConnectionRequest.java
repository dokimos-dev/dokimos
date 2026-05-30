package dev.dokimos.server.dto.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.dokimos.server.entity.LlmConnectionProtocol;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload for updating an LLM connection. The name, base URL, and model are replaced. Leaving both
 * credential fields blank keeps the existing key; supplying {@code apiKey} switches to (and encrypts)
 * a new inline key, and supplying {@code credentialRef} switches to an environment-backed key. At most
 * one credential field may be set. A non-null {@code protocol} replaces the API the endpoint speaks; a
 * null one keeps the current protocol.
 *
 * @param name the connection name (required)
 * @param baseUrl the OpenAI-compatible base URL (required)
 * @param model the model name (required)
 * @param protocol the API the endpoint speaks, or null to keep the current one
 * @param apiKey a new inline API key, or null/blank to keep the current credential
 * @param credentialRef a new environment variable name to read the key from, or null/blank to keep it
 */
public record UpdateLlmConnectionRequest(
        @NotBlank String name,
        @NotBlank String baseUrl,
        @NotBlank String model,
        LlmConnectionProtocol protocol,
        String apiKey,
        String credentialRef) {

    @AssertTrue(message = "at most one of apiKey or credentialRef may be set")
    @JsonIgnore
    public boolean isCredentialSourceValid() {
        boolean hasKey = apiKey != null && !apiKey.isBlank();
        boolean hasRef = credentialRef != null && !credentialRef.isBlank();
        return !(hasKey && hasRef);
    }
}
