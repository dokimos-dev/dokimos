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
 * @param <T> the scoped entity type
 */
public class TenantScopedFinder<T> {

    private final EntityManager entityManager;
    private final Class<T> entityType;

    public TenantScopedFinder(EntityManager entityManager, Class<T> entityType) {
        this.entityManager = entityManager;
        this.entityType = entityType;
    }

    /** Loads an entity by id under the scope; a row of another tenant returns empty, never leaking existence. */
    public Optional<T> findById(UUID id, TenantScope scope) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityType);
        Root<T> root = query.from(entityType);
        Predicate idMatch = cb.equal(root.get("id"), id);
        query.select(root).where(cb.and(idMatch, tenantPredicate(cb, root, scope)));
        return entityManager.createQuery(query).setMaxResults(1).getResultList().stream()
                .findFirst();
    }

    /** Loads an entity by id under the scope and a pessimistic write lock, to serialize concurrent writers. */
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

    /** Returns the first entity matching {@code extra} under the scope and a pessimistic write lock. */
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

    /** Lists every entity visible under the scope, with an optional {@code order}. */
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

    /** Lists entities matching {@code extra} and visible under the scope, with an optional {@code order}. */
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

    /** Returns the first entity matching {@code extra} and visible under the scope, with an optional {@code order}. */
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
     * Returns a page of entities matching {@code extra} and visible under the scope. The total count
     * honors the same predicates so paging metadata is correct.
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

    /** Counts the entities visible under the scope. */
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
