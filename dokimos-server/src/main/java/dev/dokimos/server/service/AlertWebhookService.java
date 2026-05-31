package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.AlertWebhookView;
import dev.dokimos.server.dto.v1.CreateAlertWebhookRequest;
import dev.dokimos.server.dto.v1.UpdateAlertWebhookRequest;
import dev.dokimos.server.entity.AlertWebhook;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.repository.AlertWebhookRepository;
import dev.dokimos.server.repository.ProjectRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages a project's regression-alert webhooks. Responses never carry the signing secret; only
 * whether one is configured is exposed. All operations are scoped to a project so a webhook can only
 * be read or mutated through its owning project, and every load is tenant-scoped so a cross-tenant id
 * surfaces as a 404.
 */
@Service
public class AlertWebhookService {

    private final AlertWebhookRepository webhookRepository;
    private final ProjectRepository projectRepository;

    public AlertWebhookService(AlertWebhookRepository webhookRepository, ProjectRepository projectRepository) {
        this.webhookRepository = webhookRepository;
        this.projectRepository = projectRepository;
    }

    /**
     * Registers a webhook for a project, stamped with the project's tenant.
     *
     * @param projectId the project to attach the webhook to
     * @param request the webhook definition
     * @param scope the tenant scope of the caller
     * @return the public view of the saved webhook
     * @throws IllegalArgumentException if the project does not exist under the scope (mapped to 404)
     */
    @Transactional
    public AlertWebhookView create(UUID projectId, CreateAlertWebhookRequest request, TenantScope scope) {
        Project project = loadProject(projectId, scope);
        boolean enabled = request.enabled() == null || request.enabled();
        String secret = blankToNull(request.secret());
        AlertWebhook webhook = new AlertWebhook(project, request.url(), secret, enabled);
        webhook.setTenantId(project.getTenantId());
        return AlertWebhookView.from(webhookRepository.save(webhook));
    }

    /**
     * Lists a project's webhooks in creation order.
     *
     * @throws IllegalArgumentException if the project does not exist under the scope (mapped to 404)
     */
    @Transactional(readOnly = true)
    public List<AlertWebhookView> list(UUID projectId, TenantScope scope) {
        Project project = loadProject(projectId, scope);
        return webhookRepository.findByProject(project, scope).stream()
                .map(AlertWebhookView::from)
                .toList();
    }

    /**
     * Returns one of a project's webhooks.
     *
     * @throws IllegalArgumentException if the project or webhook does not exist under the scope, or the
     *     webhook belongs to another project (mapped to 404)
     */
    @Transactional(readOnly = true)
    public AlertWebhookView get(UUID projectId, UUID webhookId, TenantScope scope) {
        return AlertWebhookView.from(loadWebhookInProject(projectId, webhookId, scope));
    }

    /**
     * Replaces a webhook's url and enabled flag, and optionally its secret. A blank secret keeps the
     * existing secret so a receiver never loses a configured secret by accident.
     *
     * @throws IllegalArgumentException if the project or webhook does not exist under the scope, or the
     *     webhook belongs to another project (mapped to 404)
     */
    @Transactional
    public AlertWebhookView update(
            UUID projectId, UUID webhookId, UpdateAlertWebhookRequest request, TenantScope scope) {
        AlertWebhook webhook = loadWebhookInProject(projectId, webhookId, scope);
        webhook.setUrl(request.url());
        webhook.setEnabled(request.enabled() == null || request.enabled());
        String secret = blankToNull(request.secret());
        if (secret != null) {
            webhook.setSecret(secret);
        }
        webhook.touchUpdatedAt();
        return AlertWebhookView.from(webhookRepository.save(webhook));
    }

    /**
     * Deletes one of a project's webhooks.
     *
     * @throws IllegalArgumentException if the project or webhook does not exist under the scope, or the
     *     webhook belongs to another project (mapped to 404)
     */
    @Transactional
    public void delete(UUID projectId, UUID webhookId, TenantScope scope) {
        AlertWebhook webhook = loadWebhookInProject(projectId, webhookId, scope);
        webhookRepository.delete(webhook);
    }

    private AlertWebhook loadWebhookInProject(UUID projectId, UUID webhookId, TenantScope scope) {
        loadProject(projectId, scope);
        AlertWebhook webhook = webhookRepository
                .findById(webhookId, scope)
                .orElseThrow(() -> new IllegalArgumentException("Webhook not found: " + webhookId));
        if (!webhook.getProject().getId().equals(projectId)) {
            throw new IllegalArgumentException("Webhook does not belong to project " + projectId);
        }
        return webhook;
    }

    private Project loadProject(UUID projectId, TenantScope scope) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID cannot be null");
        }
        return projectRepository
                .findById(projectId, scope)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
    }

    private static String blankToNull(String value) {
        return value != null && !value.isBlank() ? value : null;
    }
}
