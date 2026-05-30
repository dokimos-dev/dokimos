package dev.dokimos.server.tenant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Common tenant-scoped operations shared by every scoped repository. A scoped repository extends Spring
 * Data's empty {@code Repository<T, UUID>} plus this fragment, so the dangerous inherited finders
 * ({@code findById(id)}, {@code findAll()}, {@code getReferenceById}, {@code getById}) do not exist and an
 * unscoped load does not compile.
 *
 * <p>Every read takes a {@link TenantScope}. Request paths pass the principal's scope; background workers
 * pass {@link TenantScope#unrestricted()} explicitly. Writes are stamped by the service from the resolved
 * scope before {@link #save(Object)} is called, so persistence itself stays scope-agnostic.
 *
 * @param <T> the scoped entity type
 */
public interface ScopedRepository<T> {

    /**
     * Loads an entity by id only if it is visible under the scope. A row of another tenant returns empty,
     * which the service maps to a 404 so existence is not leaked.
     *
     * @param id the entity id
     * @param scope the tenant scope
     * @return the entity if visible, otherwise empty
     */
    Optional<T> findById(UUID id, TenantScope scope);

    /**
     * Lists every entity visible under the scope.
     *
     * @param scope the tenant scope
     * @return the visible entities
     */
    List<T> findAll(TenantScope scope);

    /**
     * Counts the entities visible under the scope.
     *
     * @param scope the tenant scope
     * @return the count of visible rows
     */
    long count(TenantScope scope);

    /**
     * Persists a new or updated entity. The caller stamps the tenant id from the resolved scope before
     * calling this.
     *
     * @param entity the entity to persist
     * @param <S> the concrete entity subtype
     * @return the managed, persisted entity
     */
    <S extends T> S save(S entity);

    /**
     * Persists all the given entities. The caller stamps each from the resolved scope first.
     *
     * @param entities the entities to persist
     * @param <S> the concrete entity subtype
     * @return the managed, persisted entities
     */
    <S extends T> List<S> saveAll(Iterable<S> entities);

    /**
     * Removes an entity. Services load through {@link #findById(UUID, TenantScope)} first, so a delete
     * cannot reach across tenants.
     *
     * @param entity the entity to remove
     */
    void delete(T entity);
}
