package dev.dokimos.server.repository;

import dev.dokimos.server.entity.AlertWebhook;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.tenant.ScopedRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.List;
import java.util.UUID;

/** Entity-specific scoped finders for {@link AlertWebhook}. */
public interface AlertWebhookRepositoryFragment extends ScopedRepository<AlertWebhook> {

    /** Lists a project's webhooks oldest first, within the scope. */
    List<AlertWebhook> findByProject(Project project, TenantScope scope);

    /**
     * Enabled webhooks of a project regardless of tenant, the set dispatched to on a regressing run. The
     * dispatcher runs off the request thread, so it lists unrestricted.
     */
    List<AlertWebhook> findByProjectIdAndEnabledTrue(UUID projectId);
}
