package dev.dokimos.server.dto.v1;

import dev.dokimos.server.entity.ApiKey;

/**
 * Response returned exactly once when a key is minted. It carries the raw {@code key} so the caller can
 * record it; the server stores only the key's hash and can never reproduce this value. Every later read
 * uses {@link ApiKeyView}, which omits the key entirely.
 *
 * @param key the raw API key, shown only here and never again
 * @param apiKey metadata about the stored key
 */
public record CreatedApiKeyView(String key, ApiKeyView apiKey) {

    public static CreatedApiKeyView of(String rawKey, ApiKey entity) {
        return new CreatedApiKeyView(rawKey, ApiKeyView.from(entity));
    }
}
