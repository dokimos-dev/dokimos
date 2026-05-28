package dev.dokimos.server.filter;

import java.util.Optional;

/** Strategy that decides whether an incoming request is allowed and which principal made it. */
public interface Authenticator {

    /**
     * Authenticates a request.
     *
     * @param authorizationHeader value of the {@code Authorization} header, or {@code null} when absent
     * @return the principal for an allowed request, or empty to reject
     */
    Optional<Principal> authenticate(String method, String authorizationHeader);
}
