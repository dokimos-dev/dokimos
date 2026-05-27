package dev.dokimos.server.filter;

import java.util.Optional;

/**
 * Strategy that decides whether an incoming request is allowed and, if so, which principal made it.
 * Isolating this decision from {@link ApiKeyAuthFilter} lets future authorization schemes (for
 * example tenant-scoped RBAC) be added additively without touching the filter wiring.
 */
public interface Authenticator {

    /**
     * Authenticates a request.
     *
     * @param method the HTTP method of the request
     * @param authorizationHeader the value of the {@code Authorization} header, or {@code null} when absent
     * @return the principal for an allowed request, or {@link Optional#empty()} when the request must be rejected
     */
    Optional<Principal> authenticate(String method, String authorizationHeader);
}
