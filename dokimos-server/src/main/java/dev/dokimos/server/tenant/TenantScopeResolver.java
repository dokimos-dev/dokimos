package dev.dokimos.server.tenant;

import dev.dokimos.server.filter.ApiKeyAuthFilter;
import dev.dokimos.server.filter.Principal;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Derives the {@link TenantScope} and principal id for the current request from the {@link Principal}
 * the auth filter placed on the request attribute.
 *
 * <p>This is the single seam controllers use so the principal-to-scope mapping lives in one place. When
 * no principal is present (a code path that does not pass through the auth filter, which only runs for
 * {@code /api/v1/**}), the request is treated as the system principal so behavior matches an open,
 * unauthenticated deployment.
 */
public final class TenantScopeResolver {

    private TenantScopeResolver() {}

    /**
     * Resolves the principal behind a request, falling back to the system principal when the auth filter
     * did not set one.
     *
     * @param request the current request
     * @return the resolved principal, never null
     */
    public static Principal principal(HttpServletRequest request) {
        Object attr = request.getAttribute(ApiKeyAuthFilter.PRINCIPAL_ATTRIBUTE);
        return attr instanceof Principal principal ? principal : Principal.system();
    }

    /**
     * Resolves the tenant scope the current request reads and writes under.
     *
     * @param request the current request
     * @return the tenant scope for the request
     */
    public static TenantScope scope(HttpServletRequest request) {
        return principal(request).tenantScope();
    }

    /**
     * Resolves the principal id of the current request, or {@code null} when no principal is present.
     * Used to stamp {@code created_by} fields.
     *
     * @param request the current request
     * @return the principal id, or {@code null}
     */
    public static String principalId(HttpServletRequest request) {
        Object attr = request.getAttribute(ApiKeyAuthFilter.PRINCIPAL_ATTRIBUTE);
        return attr instanceof Principal principal ? principal.id() : null;
    }
}
