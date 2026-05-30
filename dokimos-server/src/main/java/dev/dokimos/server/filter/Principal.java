package dev.dokimos.server.filter;

import dev.dokimos.server.tenant.TenantScope;

/**
 * Authenticated caller.
 *
 * @param id stable identifier for the caller (an API key id, or {@code "system"} for the default
 *     unscoped principal)
 * @param role privilege level granted to the caller
 * @param tenantId tenant the principal belongs to, or {@code null} when no tenant applies
 */
public record Principal(String id, Role role, String tenantId) {

    private static final String SYSTEM_ID = "system";

    /**
     * System principal used when no API key authentication is configured, or for reads in an open
     * deployment. Carries {@link Role#ADMIN} and no tenant so existing single-key and no-key
     * deployments behave exactly as before.
     */
    public static Principal system() {
        return new Principal(SYSTEM_ID, Role.ADMIN, null);
    }

    /**
     * Principal for an unauthenticated reader in an authenticated deployment. Carries the lowest role
     * so open read endpoints still serve it, while endpoints that require a higher role (such as API
     * key management) reject it.
     */
    public static Principal anonymous() {
        return new Principal("anonymous", Role.VIEWER, null);
    }

    /**
     * Returns whether this is the system principal. The system principal (no-key mode and the legacy
     * single {@code DOKIMOS_API_KEY}) is the only principal that maps to an unrestricted tenant scope, so
     * existing single-tenant and no-key deployments keep seeing and stamping every row exactly as before.
     *
     * <p>This is identified by the system id rather than {@code tenantId == null}, because an anonymous
     * keyless reader also carries a null tenant yet must resolve to shared-only, not unrestricted.
     *
     * @return true when this is the system principal
     */
    public boolean isSystem() {
        return SYSTEM_ID.equals(id);
    }

    /**
     * Resolves the {@link TenantScope} this principal reads and writes under. The system principal maps
     * to {@link TenantScope#unrestricted()} (every row, null stamp); every other principal (a scoped key
     * or an anonymous keyless reader) maps to {@link TenantScope#scoped(String)} on its own tenant, which
     * for a null tenant collapses to shared-only.
     *
     * @return the tenant scope for this principal
     */
    public TenantScope tenantScope() {
        return isSystem() ? TenantScope.unrestricted() : TenantScope.scoped(tenantId);
    }
}
