package dev.dokimos.server.filter;

import dev.dokimos.server.config.ApiKeyProperties;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * {@link Authenticator} backed by a single configured API key. Reproduces the original filter rules
 * exactly:
 * <ul>
 *   <li>auth disabled (no key configured) returns the system principal,</li>
 *   <li>read-only methods (GET, HEAD, OPTIONS) return the system principal,</li>
 *   <li>a write carrying a valid {@code Bearer} key returns the system principal,</li>
 *   <li>anything else is rejected (empty).</li>
 * </ul>
 */
@Component
public class ApiKeyAuthenticator implements Authenticator {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> READ_METHODS =
            Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name());

    private final ApiKeyProperties apiKeyProperties;

    public ApiKeyAuthenticator(ApiKeyProperties apiKeyProperties) {
        this.apiKeyProperties = apiKeyProperties;
    }

    @Override
    public Optional<Principal> authenticate(String method, String authorizationHeader) {
        if (!apiKeyProperties.isAuthEnabled()) {
            return Optional.of(Principal.system());
        }

        if (READ_METHODS.contains(method)) {
            return Optional.of(Principal.system());
        }

        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }

        String providedKey = authorizationHeader.substring(BEARER_PREFIX.length());
        if (!apiKeyProperties.getApiKey().equals(providedKey)) {
            return Optional.empty();
        }

        return Optional.of(Principal.system());
    }
}
