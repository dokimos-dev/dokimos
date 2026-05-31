package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Dataset;
import dev.dokimos.server.tenant.ScopedRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.List;
import java.util.Optional;

/** Entity-specific scoped finders for {@link Dataset}. */
public interface DatasetRepositoryFragment extends ScopedRepository<Dataset> {

    /** Finds a dataset by name within the scope. */
    Optional<Dataset> findByName(String name, TenantScope scope);

    /** Finds a dataset by name within the scope under a pessimistic write lock, for version creation. */
    Optional<Dataset> findByNameForUpdate(String name, TenantScope scope);

    /** Lists datasets visible under the scope, newest first. */
    List<Dataset> findAllOrdered(TenantScope scope);

    /**
     * Returns whether a dataset with the name exists within the scope. Names are unique per tenant, so
     * the create guard checks within the caller's scope and is not a cross-tenant oracle.
     */
    boolean existsByName(String name, TenantScope scope);
}
