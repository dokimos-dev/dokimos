package dev.dokimos.server.repository;

import dev.dokimos.server.entity.AlertWebhook;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.tenant.AbstractScopedRepository;
import dev.dokimos.server.tenant.TenantScope;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.UUID;

/** Tenant-scoped implementation of the {@link AlertWebhook} finders. */
public class AlertWebhookRepositoryFragmentImpl extends AbstractScopedRepository<AlertWebhook>
        implements AlertWebhookRepositoryFragment {

    public AlertWebhookRepositoryFragmentImpl() {
        super(AlertWebhook.class);
    }

    @Override
    public List<AlertWebhook> findByProject(Project project, TenantScope scope) {
        return finder().findWhere(
                        scope,
                        (cb, root) -> cb.equal(root.get("project"), project),
                        (cb, root) -> List.of(cb.asc(root.get("createdAt"))));
    }

    @Override
    public List<AlertWebhook> findByProjectIdAndEnabledTrue(UUID projectId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<AlertWebhook> query = cb.createQuery(AlertWebhook.class);
        Root<AlertWebhook> root = query.from(AlertWebhook.class);
        query.select(root)
                .where(cb.and(cb.equal(root.get("project").get("id"), projectId), cb.isTrue(root.get("enabled"))));
        return entityManager.createQuery(query).getResultList();
    }
}
