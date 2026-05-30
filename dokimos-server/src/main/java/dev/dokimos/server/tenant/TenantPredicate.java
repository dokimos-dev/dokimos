package dev.dokimos.server.tenant;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

/**
 * Builds the SQL/JPQL tenant predicate for a {@link TenantScope} against a {@code tenant_id} path.
 *
 * <p>The rule is uniform across every scoped entity:
 *
 * <ul>
 *   <li>unrestricted: no predicate (always true), so every row is visible.
 *   <li>scoped(tenantId) with a non-null tenant: {@code tenant_id = :tenantId OR tenant_id IS NULL}
 *       (own rows plus shared rows).
 *   <li>scoped(null) (anonymous): {@code tenant_id IS NULL} (shared rows only).
 * </ul>
 */
public final class TenantPredicate {

    private TenantPredicate() {}

    /**
     * Builds the criteria predicate for the scope over the given {@code tenant_id} path.
     *
     * @param cb the criteria builder
     * @param tenantIdPath the path to the entity's {@code tenant_id} attribute
     * @param scope the tenant scope to enforce
     * @return the predicate to AND into the query's restriction
     */
    public static Predicate forScope(CriteriaBuilder cb, Path<String> tenantIdPath, TenantScope scope) {
        if (!scope.restricted()) {
            return cb.conjunction();
        }
        if (scope.tenantId() == null) {
            return cb.isNull(tenantIdPath);
        }
        return cb.or(cb.equal(tenantIdPath, scope.tenantId()), cb.isNull(tenantIdPath));
    }
}
