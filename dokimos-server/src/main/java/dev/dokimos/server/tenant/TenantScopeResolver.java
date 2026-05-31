package dev.dokimos.server.tenant;

import dev.dokimos.server.filter.ApiKeyAuthFilter;
import dev.dokimos.server.filter.Principal;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Derives the {@link TenantScope} and principal id for the current request from the {@link Principal}
 * the auth filter placed on the request attribute.
 *
 * <p>This is the single seam controllers use, so the principal-to-scope mapping lives in one place. The
 * auth filter sets the principal on every {@code /api/v1/**} request that reaches a controller, so a
 * filtered request always carries one. The fallbacks here cover only a path that bypasses the filter, and
 * {@link #scope(HttpServletRequest)} fails closed there to a restricted, shared-only scope rather than
 * the unrestricted system scope, so an unfiltered request can never read another tenant's rows.
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
     * Resolves the tenant scope the current request reads and writes under, using the principal's own
     * scope and failing closed to a shared-only scope when no principal is present.
     *
     * @param request the current request
     * @return the tenant scope for the request, shared-only when no principal was set
     */
    public static TenantScope scope(HttpServletRequest request) {
        Object attr = request.getAttribute(ApiKeyAuthFilter.PRINCIPAL_ATTRIBUTE);
        return attr instanceof Principal principal ? principal.tenantScope() : TenantScope.scoped(null);
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
