package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.ApiKeyView;
import dev.dokimos.server.dto.v1.CreateApiKeyRequest;
import dev.dokimos.server.dto.v1.CreatedApiKeyView;
import dev.dokimos.server.entity.ApiKey;
import dev.dokimos.server.filter.ApiKeyHasher;
import dev.dokimos.server.repository.ApiKeyRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mints and manages scoped API keys. A raw key is generated from a CSPRNG, returned to the caller once,
 * and stored only as a SHA-256 hash. Reads and revocation operate on the hashed records and never expose
 * key material.
 */
@Service
public class ApiKeyService {

    private static final String KEY_PREFIX = "dok_";
    private static final int RAW_KEY_BYTES = 32;

    private final ApiKeyRepository apiKeyRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    /**
     * Generates a key, stores its hash, and returns the raw key exactly once.
     *
     * @param request the key definition
     * @return the created key's metadata plus the raw key (the only time it is exposed)
     */
    @Transactional
    public CreatedApiKeyView create(CreateApiKeyRequest request) {
        String rawKey = generateRawKey();
        String keyHash = ApiKeyHasher.sha256Hex(rawKey);
        ApiKey key = new ApiKey(keyHash, request.name(), request.role(), request.tenantId());
        return CreatedApiKeyView.of(rawKey, apiKeyRepository.save(key));
    }

    /** Lists every key as metadata only; key material is never included. */
    @Transactional(readOnly = true)
    public List<ApiKeyView> list() {
        return apiKeyRepository.findAll().stream().map(ApiKeyView::from).toList();
    }

    /**
     * Disables a key so future authentication attempts with it are rejected, keeping its record for
     * audit. Disabling an already-disabled key is a no-op.
     *
     * @param id the key to disable
     * @return the updated metadata
     * @throws IllegalArgumentException if no key has the id (mapped to 404)
     */
    @Transactional
    public ApiKeyView disable(UUID id) {
        ApiKey key = loadKey(id);
        key.disable();
        return ApiKeyView.from(apiKeyRepository.save(key));
    }

    /**
     * Permanently deletes a key.
     *
     * @param id the key to delete
     * @throws IllegalArgumentException if no key has the id (mapped to 404)
     */
    @Transactional
    public void delete(UUID id) {
        apiKeyRepository.delete(loadKey(id));
    }

    private ApiKey loadKey(UUID id) {
        return apiKeyRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("API key not found: " + id));
    }

    private String generateRawKey() {
        byte[] bytes = new byte[RAW_KEY_BYTES];
        secureRandom.nextBytes(bytes);
        return KEY_PREFIX + urlEncoder.encodeToString(bytes);
    }
}
