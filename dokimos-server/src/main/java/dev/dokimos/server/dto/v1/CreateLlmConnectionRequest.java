package dev.dokimos.server.dto.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.dokimos.server.entity.LlmConnectionProtocol;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to register an OpenAI-compatible LLM connection. Exactly one of {@code apiKey} (supplied
 * inline and stored encrypted) or {@code credentialRef} (the name of an environment variable holding
 * the key) must be set. {@code protocol} selects the API the endpoint speaks; when omitted it defaults
 * to the Responses API.
 */
public record CreateLlmConnectionRequest(
        @NotBlank String name,
        @NotBlank String baseUrl,
        @NotBlank String model,
        LlmConnectionProtocol protocol,
        String apiKey,
        String credentialRef) {

    /**
     * Cross-field rule enforcing that exactly one credential source is supplied. Returning
     * {@code false} surfaces a 400 instead of letting the database check constraint produce a 409.
     */
    @AssertTrue(message = "exactly one of apiKey or credentialRef must be set")
    @JsonIgnore
    public boolean isCredentialSourceValid() {
        boolean hasKey = apiKey != null && !apiKey.isBlank();
        boolean hasRef = credentialRef != null && !credentialRef.isBlank();
        return hasKey != hasRef;
    }
}
