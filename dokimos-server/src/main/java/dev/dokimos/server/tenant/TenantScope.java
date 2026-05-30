package dev.dokimos.server.tenant;

/**
 * Immutable tenant visibility used by every scoped repository read and by service-side write stamping.
 *
 * <p>Two shapes exist:
 *
 * <ul>
 *   <li>{@link #unrestricted()} applies no tenant predicate. Reads see every row regardless of tenant,
 *       and writes stamp {@code null} (a shared row). This is the scope of the system principal (no-key
 *       and legacy single-key deployments) and of background workers, so existing single-tenant and
 *       no-key behavior is preserved exactly.
 *   <li>{@link #scoped(String)} applies the predicate {@code tenant_id = :tenantId OR tenant_id IS NULL},
 *       so a tenant sees its own rows plus shared (null-tenant) rows. Writes stamp {@code tenantId}. A
 *       {@code null} tenant id collapses to shared-only: the predicate becomes {@code tenant_id IS NULL}
 *       and writes stamp {@code null}. This is the scope of a scoped API key and of an anonymous keyless
 *       reader (which resolves to {@code scoped(null)}).
 * </ul>
 *
 * <p>Because the scope is a required parameter on every scoped finder, there is no unscoped overload a
 * caller can reach by accident: an unscoped load does not compile.
 *
 * @param restricted whether a tenant predicate applies; {@code false} for {@link #unrestricted()}
 * @param tenantId the tenant to filter and stamp by when {@code restricted} is true, possibly {@code null}
 *     for shared-only
 */
public record TenantScope(boolean restricted, String tenantId) {

    private static final TenantScope UNRESTRICTED = new TenantScope(false, null);

    /**
     * Returns the scope that applies no tenant predicate. Reads see every row; writes stamp {@code null}.
     *
     * @return the unrestricted scope
     */
    public static TenantScope unrestricted() {
        return UNRESTRICTED;
    }

    /**
     * Returns a scope restricted to the given tenant. Reads see {@code tenant_id = tenantId OR tenant_id
     * IS NULL}; writes stamp {@code tenantId}. A {@code null} tenant id yields a shared-only scope (reads
     * see only null-tenant rows, writes stamp {@code null}).
     *
     * @param tenantId the tenant to scope to, or {@code null} for shared-only
     * @return a restricted scope for the tenant
     */
    public static TenantScope scoped(String tenantId) {
        return new TenantScope(true, tenantId);
    }

    /**
     * Returns the tenant id to stamp on a newly written row. This is {@code null} for the unrestricted
     * scope (shared) and the scope's tenant id otherwise (which may itself be {@code null} for the
     * shared-only scope).
     *
     * @return the tenant id to stamp, or {@code null} for a shared row
     */
    public String stampTenantId() {
        return restricted ? tenantId : null;
    }
}
