package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Dataset;
import dev.dokimos.server.tenant.ScopedRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.List;
import java.util.Optional;

/** Entity-specific scoped finders for {@link Dataset}. */
public interface DatasetRepositoryFragment extends ScopedRepository<Dataset> {

    /**
     * Finds a dataset by name within the scope.
     *
     * @param name the dataset name
     * @param scope the tenant scope
     * @return the dataset if visible, otherwise empty
     */
    Optional<Dataset> findByName(String name, TenantScope scope);

    /**
     * Finds a dataset by name within the scope, under a pessimistic write lock. Used by version creation
     * so the lock is acquired atomically with the scoped lookup.
     *
     * @param name the dataset name
     * @param scope the tenant scope
     * @return the locked dataset if visible, otherwise empty
     */
    Optional<Dataset> findByNameForUpdate(String name, TenantScope scope);

    /**
     * Lists datasets visible under the scope, newest first.
     *
     * @param scope the tenant scope
     * @return the visible datasets
     */
    List<Dataset> findAllOrdered(TenantScope scope);

    /**
     * Returns whether any dataset has the given name, ignoring tenant. Dataset names are globally unique
     * (the DB constraint is unchanged), so the create guard checks globally.
     *
     * @param name the candidate name
     * @return true when a dataset with that name already exists in any tenant
     */
    boolean existsByName(String name);
}
