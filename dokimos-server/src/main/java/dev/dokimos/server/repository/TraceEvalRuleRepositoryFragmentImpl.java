package dev.dokimos.server.repository;

import dev.dokimos.server.entity.TraceEvalRule;
import dev.dokimos.server.tenant.AbstractScopedRepository;
import dev.dokimos.server.tenant.TenantPredicate;
import dev.dokimos.server.tenant.TenantScope;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.UUID;

/** Tenant-scoped implementation of the {@link TraceEvalRule} finders. */
public class TraceEvalRuleRepositoryFragmentImpl extends AbstractScopedRepository<TraceEvalRule>
        implements TraceEvalRuleRepositoryFragment {

    public TraceEvalRuleRepositoryFragmentImpl() {
        super(TraceEvalRule.class);
    }

    @Override
    public List<TraceEvalRule> findByProjectId(UUID projectId, TenantScope scope) {
        return finder().findWhere(
                        scope,
                        (cb, root) -> cb.equal(root.get("projectId"), projectId),
                        (cb, root) -> List.of(cb.asc(root.get("createdAt"))));
    }

    @Override
    public List<TraceEvalRule> findByProjectIdAndEnabledTrue(UUID projectId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<TraceEvalRule> query = cb.createQuery(TraceEvalRule.class);
        Root<TraceEvalRule> root = query.from(TraceEvalRule.class);
        query.select(root).where(cb.and(cb.equal(root.get("projectId"), projectId), cb.isTrue(root.get("enabled"))));
        return entityManager.createQuery(query).getResultList();
    }

    @Override
    public boolean existsByProjectIdAndName(UUID projectId, String name, TenantScope scope) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<TraceEvalRule> root = query.from(TraceEvalRule.class);
        query.select(cb.count(root))
                .where(cb.and(
                        cb.equal(root.get("projectId"), projectId),
                        cb.equal(root.get("name"), name),
                        TenantPredicate.forScope(cb, root.get("tenantId"), scope)));
        return entityManager.createQuery(query).getSingleResult() > 0;
    }
}
