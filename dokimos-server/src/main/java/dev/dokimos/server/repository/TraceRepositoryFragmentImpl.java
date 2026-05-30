package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Trace;
import dev.dokimos.server.tenant.AbstractScopedRepository;
import dev.dokimos.server.tenant.TenantScope;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Tenant-scoped implementation of the {@link Trace} finders plus the unrestricted ingestion paths. */
public class TraceRepositoryFragmentImpl extends AbstractScopedRepository<Trace> implements TraceRepositoryFragment {

    public TraceRepositoryFragmentImpl() {
        super(Trace.class);
    }

    @Override
    public Optional<Trace> findByTraceId(String traceId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Trace> query = cb.createQuery(Trace.class);
        Root<Trace> root = query.from(Trace.class);
        query.select(root).where(cb.equal(root.get("traceId"), traceId));
        return entityManager.createQuery(query).setMaxResults(1).getResultList().stream()
                .findFirst();
    }

    @Override
    public Page<Trace> findAllOrdered(Pageable pageable, TenantScope scope) {
        return finder().findPage(scope, null, (cb, root) -> List.of(cb.desc(root.get("createdAt"))), pageable);
    }

    @Override
    public Page<Trace> findByProjectId(UUID projectId, Pageable pageable, TenantScope scope) {
        return finder().findPage(
                        scope,
                        (cb, root) -> cb.equal(root.get("projectId"), projectId),
                        (cb, root) -> List.of(cb.desc(root.get("createdAt"))),
                        pageable);
    }

    @Override
    public int deleteExpired(Instant cutoff) {
        // Flush then delete then clear, mirroring the prior @Modifying(flushAutomatically,
        // clearAutomatically) so managed spans (with their perpetually dirty JSONB maps) are not
        // re-flushed against rows the cascade has already removed.
        entityManager.flush();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaDelete<Trace> delete = cb.createCriteriaDelete(Trace.class);
        Root<Trace> root = delete.from(Trace.class);
        delete.where(cb.lessThanOrEqualTo(root.get("expiresAt"), cutoff));
        int removed = entityManager.createQuery(delete).executeUpdate();
        entityManager.clear();
        return removed;
    }
}
