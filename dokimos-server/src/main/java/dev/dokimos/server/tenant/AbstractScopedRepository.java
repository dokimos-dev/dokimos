package dev.dokimos.server.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Base implementation of {@link ScopedRepository}. It owns the {@link EntityManager} and delegates scoped
 * reads to a {@link TenantScopedFinder}; fragment implementations extend this, pass their entity class
 * up, and add the entity-specific finders. Confining the {@code EntityManager} to this layer is what lets
 * the architecture backstop forbid services from holding one and bypassing the scope predicate.
 *
 * @param <T> the scoped entity type
 */
public abstract class AbstractScopedRepository<T> implements ScopedRepository<T> {

    @PersistenceContext
    protected EntityManager entityManager;

    private final Class<T> entityType;
    private TenantScopedFinder<T> finder;

    protected AbstractScopedRepository(Class<T> entityType) {
        this.entityType = entityType;
    }

    /** Returns the finder, created lazily so it binds to the injected entity manager. */
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

    /** Chooses persist over merge: a null {@code id} means the entity has not been persisted yet. */
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
