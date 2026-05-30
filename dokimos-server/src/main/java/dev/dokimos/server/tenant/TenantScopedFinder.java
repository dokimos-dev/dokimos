package dev.dokimos.server.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Reusable tenant-scoped query helper shared by every scoped repository implementation. It owns the
 * {@link EntityManager} and applies the {@link TenantPredicate} so individual repositories never touch
 * the entity manager themselves and never expose an unscoped finder.
 *
 * <p>This class is the only place outside a repository that holds an {@code EntityManager}; the ArchUnit
 * backstop forbids services from depending on {@code EntityManager} directly, and forbids tenant
 * repositories from extending {@code CrudRepository}/{@code JpaRepository}, so the scope predicate cannot
 * be bypassed by an inherited finder.
 *
 * @param <T> the scoped entity type
 */
public class TenantScopedFinder<T> {

    private final EntityManager entityManager;
    private final Class<T> entityType;

    /**
     * Creates a finder for the given entity type.
     *
     * @param entityManager the JPA entity manager
     * @param entityType the scoped entity class
     */
    public TenantScopedFinder(EntityManager entityManager, Class<T> entityType) {
        this.entityManager = entityManager;
        this.entityType = entityType;
    }

    /**
     * Loads an entity by id, applying the scope predicate so a row of another tenant is invisible
     * (returns empty rather than throwing or leaking existence).
     *
     * @param id the entity id
     * @param scope the tenant scope
     * @return the entity if visible under the scope, otherwise empty
     */
    public Optional<T> findById(UUID id, TenantScope scope) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityType);
        Root<T> root = query.from(entityType);
        Predicate idMatch = cb.equal(root.get("id"), id);
        query.select(root).where(cb.and(idMatch, tenantPredicate(cb, root, scope)));
        return entityManager.createQuery(query).setMaxResults(1).getResultList().stream()
                .findFirst();
    }

    /**
     * Loads an entity by id under a pessimistic write lock, applying the scope predicate. Used to
     * serialize concurrent writers (for example run ingestion against run completion) while keeping the
     * load tenant-scoped.
     *
     * @param id the entity id
     * @param scope the tenant scope
     * @return the locked entity if visible under the scope, otherwise empty
     */
    public Optional<T> findByIdForUpdate(UUID id, TenantScope scope) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityType);
        Root<T> root = query.from(entityType);
        Predicate idMatch = cb.equal(root.get("id"), id);
        query.select(root).where(cb.and(idMatch, tenantPredicate(cb, root, scope)));
        return entityManager
                .createQuery(query)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    /**
     * Returns the first entity matching an extra predicate and visible under the scope, under a
     * pessimistic write lock. Used by version creation to lock a dataset by name while staying scoped.
     *
     * @param scope the tenant scope
     * @param extra builds the extra predicate (for example a name match)
     * @return the locked entity, or empty
     */
    public Optional<T> findFirstForUpdate(TenantScope scope, BiFunction<CriteriaBuilder, Root<T>, Predicate> extra) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityType);
        Root<T> root = query.from(entityType);
        query.select(root).where(cb.and(tenantPredicate(cb, root, scope), extra.apply(cb, root)));
        return entityManager
                .createQuery(query)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    /**
     * Lists every entity visible under the scope, with an optional ordering.
     *
     * @param scope the tenant scope
     * @param order builds the order list, or null for no ordering
     * @return the visible entities
     */
    public List<T> findAll(TenantScope scope, BiFunction<CriteriaBuilder, Root<T>, List<Order>> order) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityType);
        Root<T> root = query.from(entityType);
        query.select(root).where(tenantPredicate(cb, root, scope));
        if (order != null) {
            query.orderBy(order.apply(cb, root));
        }
        return entityManager.createQuery(query).getResultList();
    }

    /**
     * Lists entities matching an extra predicate and visible under the scope, with an optional ordering.
     *
     * @param scope the tenant scope
     * @param extra builds an extra predicate (for example a parent-id match), or null for none
     * @param order builds the order list, or null for no ordering
     * @return the matching, visible entities
     */
    public List<T> findWhere(
            TenantScope scope,
            BiFunction<CriteriaBuilder, Root<T>, Predicate> extra,
            BiFunction<CriteriaBuilder, Root<T>, List<Order>> order) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityType);
        Root<T> root = query.from(entityType);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(tenantPredicate(cb, root, scope));
        if (extra != null) {
            predicates.add(extra.apply(cb, root));
        }
        query.select(root).where(cb.and(predicates.toArray(new Predicate[0])));
        if (order != null) {
            query.orderBy(order.apply(cb, root));
        }
        return entityManager.createQuery(query).getResultList();
    }

    /**
     * Returns the first entity matching an extra predicate and visible under the scope, ordered as given.
     *
     * @param scope the tenant scope
     * @param extra builds an extra predicate, or null for none
     * @param order builds the order list, or null for no ordering
     * @return the first matching entity, or empty
     */
    public Optional<T> findFirst(
            TenantScope scope,
            BiFunction<CriteriaBuilder, Root<T>, Predicate> extra,
            BiFunction<CriteriaBuilder, Root<T>, List<Order>> order) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityType);
        Root<T> root = query.from(entityType);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(tenantPredicate(cb, root, scope));
        if (extra != null) {
            predicates.add(extra.apply(cb, root));
        }
        query.select(root).where(cb.and(predicates.toArray(new Predicate[0])));
        if (order != null) {
            query.orderBy(order.apply(cb, root));
        }
        return entityManager.createQuery(query).setMaxResults(1).getResultList().stream()
                .findFirst();
    }

    /**
     * Returns a page of entities matching an extra predicate and visible under the scope, ordered as
     * given. The total count honors the same predicates so paging metadata is correct.
     *
     * @param scope the tenant scope
     * @param extra builds an extra predicate, or null for none
     * @param order builds the order list, or null for no ordering
     * @param pageable the page request
     * @return the requested page of visible entities
     */
    public org.springframework.data.domain.Page<T> findPage(
            TenantScope scope,
            BiFunction<CriteriaBuilder, Root<T>, Predicate> extra,
            BiFunction<CriteriaBuilder, Root<T>, List<Order>> order,
            org.springframework.data.domain.Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityType);
        Root<T> root = query.from(entityType);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(tenantPredicate(cb, root, scope));
        if (extra != null) {
            predicates.add(extra.apply(cb, root));
        }
        Predicate[] where = predicates.toArray(new Predicate[0]);
        query.select(root).where(cb.and(where));
        if (order != null) {
            query.orderBy(order.apply(cb, root));
        }
        List<T> content = entityManager
                .createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<T> countRoot = countQuery.from(entityType);
        List<Predicate> countPredicates = new ArrayList<>();
        countPredicates.add(tenantPredicate(cb, countRoot, scope));
        if (extra != null) {
            countPredicates.add(extra.apply(cb, countRoot));
        }
        countQuery.select(cb.count(countRoot)).where(cb.and(countPredicates.toArray(new Predicate[0])));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    /**
     * Counts the entities visible under the scope.
     *
     * @param scope the tenant scope
     * @return the count of visible rows
     */
    public long count(TenantScope scope) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<T> root = query.from(entityType);
        query.select(cb.count(root)).where(tenantPredicate(cb, root, scope));
        return entityManager.createQuery(query).getSingleResult();
    }

    private Predicate tenantPredicate(CriteriaBuilder cb, Root<T> root, TenantScope scope) {
        return TenantPredicate.forScope(cb, root.get("tenantId"), scope);
    }
}
