package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.AlertWebhookView;
import dev.dokimos.server.dto.v1.CreateAlertWebhookRequest;
import dev.dokimos.server.dto.v1.UpdateAlertWebhookRequest;
import dev.dokimos.server.entity.AlertWebhook;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.repository.AlertWebhookRepository;
import dev.dokimos.server.repository.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages a project's regression-alert webhooks. Responses never carry the signing secret; only
 * whether one is configured is exposed. All operations are scoped to a project so a webhook can only
 * be read or mutated through its owning project.
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
     * Registers a webhook for a project.
     *
     * @param projectId the project to attach the webhook to
     * @param request the webhook definition
     * @return the public view of the saved webhook
     * @throws IllegalArgumentException if the project does not exist (mapped to 404)
     */
    @Transactional
    public AlertWebhookView create(UUID projectId, CreateAlertWebhookRequest request) {
        Project project = loadProject(projectId);
        boolean enabled = request.enabled() == null || request.enabled();
        String secret = blankToNull(request.secret());
        AlertWebhook webhook = new AlertWebhook(project, request.url(), secret, enabled);
        return AlertWebhookView.from(webhookRepository.save(webhook));
    }

    /**
     * Lists a project's webhooks in creation order.
     *
     * @throws IllegalArgumentException if the project does not exist (mapped to 404)
     */
    @Transactional(readOnly = true)
    public List<AlertWebhookView> list(UUID projectId) {
        Project project = loadProject(projectId);
        return webhookRepository.findByProjectOrderByCreatedAtAsc(project).stream()
                .map(AlertWebhookView::from)
                .toList();
    }

    /**
     * Returns one of a project's webhooks.
     *
     * @throws IllegalArgumentException if the project or webhook does not exist, or the webhook
     *     belongs to another project (mapped to 404)
     */
    @Transactional(readOnly = true)
    public AlertWebhookView get(UUID projectId, UUID webhookId) {
        return AlertWebhookView.from(loadWebhookInProject(projectId, webhookId));
    }

    /**
     * Replaces a webhook's url and enabled flag, and optionally its secret. A blank secret keeps the
     * existing secret so a receiver never loses a configured secret by accident.
     *
     * @throws IllegalArgumentException if the project or webhook does not exist, or the webhook
     *     belongs to another project (mapped to 404)
     */
    @Transactional
    public AlertWebhookView update(UUID projectId, UUID webhookId, UpdateAlertWebhookRequest request) {
        AlertWebhook webhook = loadWebhookInProject(projectId, webhookId);
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
     * @throws IllegalArgumentException if the project or webhook does not exist, or the webhook
     *     belongs to another project (mapped to 404)
     */
    @Transactional
    public void delete(UUID projectId, UUID webhookId) {
        AlertWebhook webhook = loadWebhookInProject(projectId, webhookId);
        webhookRepository.delete(webhook);
    }

    private AlertWebhook loadWebhookInProject(UUID projectId, UUID webhookId) {
        loadProject(projectId);
        AlertWebhook webhook = webhookRepository
                .findById(webhookId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook not found: " + webhookId));
        if (!webhook.getProject().getId().equals(projectId)) {
            throw new IllegalArgumentException("Webhook does not belong to project " + projectId);
        }
        return webhook;
    }

    private Project loadProject(UUID projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID cannot be null");
        }
        return projectRepository
                .findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
    }

    private static String blankToNull(String value) {
        return value != null && !value.isBlank() ? value : null;
    }
}
