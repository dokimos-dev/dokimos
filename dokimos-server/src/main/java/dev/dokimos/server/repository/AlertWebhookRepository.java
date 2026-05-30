package dev.dokimos.server.repository;

import dev.dokimos.server.entity.AlertWebhook;
import dev.dokimos.server.entity.Project;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertWebhookRepository extends JpaRepository<AlertWebhook, UUID> {

    List<AlertWebhook> findByProjectOrderByCreatedAtAsc(Project project);

    /** Resolves the enabled webhooks of a project, the set dispatched to on a regressing run. */
    List<AlertWebhook> findByProjectIdAndEnabledTrue(UUID projectId);
}
