package dev.dokimos.server.repository;

import dev.dokimos.server.entity.AlertWebhook;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.tenant.ScopedRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.List;
import java.util.UUID;

/** Entity-specific scoped finders for {@link AlertWebhook}. */
public interface AlertWebhookRepositoryFragment extends ScopedRepository<AlertWebhook> {

    /**
     * Lists a project's webhooks in creation order, within the scope.
     *
     * @param project the owning project
     * @param scope the tenant scope
     * @return the visible webhooks, oldest first
     */
    List<AlertWebhook> findByProject(Project project, TenantScope scope);

    /**
     * Resolves the enabled webhooks of a project regardless of tenant, the set dispatched to on a
     * regressing run. The dispatcher runs off the request thread, so it lists unrestricted.
     *
     * @param projectId the owning project id
     * @return the project's enabled webhooks
     */
    List<AlertWebhook> findByProjectIdAndEnabledTrue(UUID projectId);
}
