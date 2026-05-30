package dev.dokimos.server.filter;

import dev.dokimos.server.config.ApiKeyProperties;
import dev.dokimos.server.entity.ApiKey;
import dev.dokimos.server.repository.ApiKeyRepository;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link Authenticator} backed by two credential sources that coexist for backward compatibility: the
 * single legacy key configured via {@code DOKIMOS_API_KEY} (which maps to {@link Role#ADMIN}), and the
 * scoped keys stored in {@code api_keys} (each hashed, each carrying its own role and tenant).
 *
 * <p>The deployment is in <em>authenticated mode</em> when either a legacy key is configured or at least
 * one enabled scoped key exists. Outside authenticated mode the behavior is unchanged from before this
 * feature: reads and writes both pass through as the {@linkplain Principal#system() system principal}.
 * In authenticated mode reads stay open (matching the prior default that reads are always allowed) while
 * writes require a valid {@code Bearer} credential. A presented raw key is hashed with SHA-256 and
 * matched against enabled keys; the resolved principal carries the key's role and tenant.
 */
@Component
public class ApiKeyAuthenticator implements Authenticator {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> READ_METHODS =
            Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name());

    private final ApiKeyProperties apiKeyProperties;
    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyAuthenticator(ApiKeyProperties apiKeyProperties, ApiKeyRepository apiKeyRepository) {
        this.apiKeyProperties = apiKeyProperties;
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    @Transactional
    public Optional<Principal> authenticate(String method, String authorizationHeader) {
        boolean legacyKeyConfigured = apiKeyProperties.isAuthEnabled();
        boolean scopedKeysExist = apiKeyRepository.existsByEnabledTrue();
        boolean authenticatedMode = legacyKeyConfigured || scopedKeysExist;

        if (!authenticatedMode) {
            return Optional.of(Principal.system());
        }

        if (READ_METHODS.contains(method)) {
            return Optional.of(Principal.system());
        }

        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }

        String providedKey = authorizationHeader.substring(BEARER_PREFIX.length());

        if (legacyKeyConfigured && apiKeyProperties.getApiKey().equals(providedKey)) {
            return Optional.of(Principal.system());
        }

        String keyHash = ApiKeyHasher.sha256Hex(providedKey);
        return apiKeyRepository.findByKeyHashAndEnabledTrue(keyHash).map(this::toPrincipal);
    }

    private Principal toPrincipal(ApiKey key) {
        key.markUsed();
        String id = key.getId() != null ? key.getId().toString() : null;
        return new Principal(id, key.getRole(), key.getTenantId());
    }
}
