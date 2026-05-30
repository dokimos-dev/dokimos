package dev.dokimos.server.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Base implementation of {@link ScopedRepository} shared by every scoped repository's custom fragment
 * implementation. It owns the {@link EntityManager} and delegates scoped reads to a {@link
 * TenantScopedFinder}. Concrete fragment implementations extend this, pass their entity class up, and add
 * the entity-specific scoped finders.
 *
 * <p>Holding the {@code EntityManager} here, inside the repository layer, is what lets the ArchUnit
 * backstop forbid services from touching {@code EntityManager} directly: the only legitimate place to
 * build a query is a scoped repository, and the only finders a scoped repository exposes require a {@link
 * TenantScope}.
 *
 * @param <T> the scoped entity type
 */
public abstract class AbstractScopedRepository<T> implements ScopedRepository<T> {

    @PersistenceContext
    protected EntityManager entityManager;

    private final Class<T> entityType;
    private TenantScopedFinder<T> finder;

    /**
     * Creates the base for the given entity type.
     *
     * @param entityType the scoped entity class
     */
    protected AbstractScopedRepository(Class<T> entityType) {
        this.entityType = entityType;
    }

    /**
     * Returns the lazily created tenant-scoped finder bound to this repository's entity manager.
     *
     * @return the finder
     */
    protected TenantScopedFinder<T> finder() {
        if (finder == null) {
            finder = new TenantScopedFinder<>(entityManager, entityType);
        }
        return finder;
    }

    @Override
    public Optional<T> findById(UUID id, TenantScope scope) {
        return finder().findById(id, scope);
    }

    @Override
    public List<T> findAll(TenantScope scope) {
        return finder().findAll(scope, null);
    }

    @Override
    public long count(TenantScope scope) {
        return finder().count(scope);
    }

    @Override
    public <S extends T> S save(S entity) {
        if (entityManager.contains(entity)) {
            return entity;
        }
        if (isNew(entity)) {
            entityManager.persist(entity);
            return entity;
        }
        return entityManager.merge(entity);
    }

    @Override
    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        List<S> saved = new ArrayList<>();
        for (S entity : entities) {
            saved.add(save(entity));
        }
        return saved;
    }

    @Override
    public void delete(T entity) {
        entityManager.remove(entityManager.contains(entity) ? entity : entityManager.merge(entity));
    }

    /**
     * Returns whether the entity has not been persisted yet, used to choose persist over merge. Entities
     * generate their id on persist, so a null id means new. Reflection reads the {@code id} field once per
     * call, which is acceptable for the write volume here.
     *
     * @param entity the entity to test
     * @return true when the entity has no id yet
     */
    protected boolean isNew(Object entity) {
        try {
            var field = findIdField(entity.getClass());
            field.setAccessible(true);
            return field.get(entity) == null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read id of " + entity.getClass(), e);
        }
    }

    private static java.lang.reflect.Field findIdField(Class<?> type) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField("id");
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("id");
    }
}
