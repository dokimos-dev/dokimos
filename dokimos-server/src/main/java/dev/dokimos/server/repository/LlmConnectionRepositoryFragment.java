package dev.dokimos.server.repository;

import dev.dokimos.server.entity.LlmConnection;
import dev.dokimos.server.tenant.ScopedRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.List;
import java.util.Optional;

/** Entity-specific scoped finders for {@link LlmConnection}. */
public interface LlmConnectionRepositoryFragment extends ScopedRepository<LlmConnection> {

    /** Finds a connection by name within the scope. */
    Optional<LlmConnection> findByName(String name, TenantScope scope);

    /** Lists connections visible under the scope, newest first. */
    List<LlmConnection> findAllOrdered(TenantScope scope);

    /**
     * Returns whether a connection with the name exists within the scope. Names are unique per tenant, so
     * the create and rename guards check within the caller's scope and are not a cross-tenant oracle.
     */
    boolean existsByName(String name, TenantScope scope);
}
