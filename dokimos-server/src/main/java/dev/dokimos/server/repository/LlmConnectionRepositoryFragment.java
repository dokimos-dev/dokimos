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
     * Returns whether any connection has the given name, ignoring tenant. Connection names are globally
     * unique (the DB constraint is unchanged), so the create and rename guards check globally.
     *
     * @param name the candidate name
     * @return true when a connection with that name already exists in any tenant
     */
    boolean existsByName(String name);
}
