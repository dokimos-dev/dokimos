package dev.dokimos.server.filter;

import java.util.Optional;

/** Strategy that resolves the {@link Principal} behind an incoming request from its credential. */
public interface Authenticator {

    /**
     * Resolves the principal for a request based on its method and credential.
     *
     * <p>The method is honored so reads stay open in the default deployment: a read returns the
     * {@linkplain Principal#system() system principal} when no credential is presented. A write with no
     * configured authentication also returns the system principal (open mode). Otherwise a valid
     * {@code Bearer} credential resolves to the principal it identifies, and an invalid or missing
     * credential on a write returns empty (rejected). Authorization of the resolved principal against
     * the requested action is the caller's responsibility.
     *
     * @param method the HTTP method
     * @param authorizationHeader value of the {@code Authorization} header, or {@code null} when absent
     * @return the principal for an allowed request, or empty to reject
     */
    Optional<Principal> authenticate(String method, String authorizationHeader);
}
