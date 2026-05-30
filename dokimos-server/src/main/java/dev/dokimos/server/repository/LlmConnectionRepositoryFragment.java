package dev.dokimos.server.repository;

import dev.dokimos.server.entity.LlmConnection;
import dev.dokimos.server.tenant.ScopedRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.List;
import java.util.Optional;

/** Entity-specific scoped finders for {@link LlmConnection}. */
public interface LlmConnectionRepositoryFragment extends ScopedRepository<LlmConnection> {

    /**
     * Finds a connection by name within the scope.
     *
     * @param name the connection name
     * @param scope the tenant scope
     * @return the connection if visible, otherwise empty
     */
    Optional<LlmConnection> findByName(String name, TenantScope scope);

    /**
     * Lists connections visible under the scope, newest first.
     *
     * @param scope the tenant scope
     * @return the visible connections
     */
    List<LlmConnection> findAllOrdered(TenantScope scope);

    /**
     * Returns whether a connection with the given name exists within the scope. Connection names are
     * unique per tenant, so the create and rename guards check within the caller's scope rather than
     * globally, and a scoped existence check is not a cross-tenant oracle.
     *
     * @param name the candidate name
     * @param scope the tenant scope
     * @return true when a connection with that name is visible under the scope
     */
    boolean existsByName(String name, TenantScope scope);
}
