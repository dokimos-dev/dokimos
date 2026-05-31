package dev.dokimos.server.controller.v1;

import dev.dokimos.server.dto.v1.AlertWebhookView;
import dev.dokimos.server.dto.v1.CreateAlertWebhookRequest;
import dev.dokimos.server.dto.v1.UpdateAlertWebhookRequest;
import dev.dokimos.server.service.AlertWebhookService;
import dev.dokimos.server.tenant.TenantScopeResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD over a project's regression-alert webhooks. Writes pass through the existing auth seam; reads
 * are open. The signing secret is never returned in any response.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/alert-webhooks")
public class AlertWebhookController {

    private final AlertWebhookService webhookService;

    public AlertWebhookController(AlertWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /** Registers a webhook for the project. Returns 201 with a {@code Location} header. */
    @PostMapping
    public ResponseEntity<AlertWebhookView> createAlertWebhook(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateAlertWebhookRequest request,
            HttpServletRequest http) {
        AlertWebhookView view = webhookService.create(projectId, request, TenantScopeResolver.scope(http));
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/alert-webhooks/" + view.id()))
                .body(view);
    }

    /** Lists the project's webhooks. The signing secret is never included. */
    @GetMapping
    public List<AlertWebhookView> listAlertWebhooks(@PathVariable UUID projectId, HttpServletRequest http) {
        return webhookService.list(projectId, TenantScopeResolver.scope(http));
    }

    /** Returns one webhook, or 404 if the project or webhook does not exist or belongs to another tenant. */
    @GetMapping("/{webhookId}")
    public AlertWebhookView getAlertWebhook(
            @PathVariable UUID projectId, @PathVariable UUID webhookId, HttpServletRequest http) {
        return webhookService.get(projectId, webhookId, TenantScopeResolver.scope(http));
    }

    /** Updates a webhook. Returns 404 if the project or webhook does not exist or belongs to another tenant. */
    @PutMapping("/{webhookId}")
    public AlertWebhookView updateAlertWebhook(
            @PathVariable UUID projectId,
            @PathVariable UUID webhookId,
            @Valid @RequestBody UpdateAlertWebhookRequest request,
            HttpServletRequest http) {
        return webhookService.update(projectId, webhookId, request, TenantScopeResolver.scope(http));
    }

    /** Deletes a webhook. Returns 404 if the project or webhook does not exist or belongs to another tenant. */
    @DeleteMapping("/{webhookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAlertWebhook(
            @PathVariable UUID projectId, @PathVariable UUID webhookId, HttpServletRequest http) {
        webhookService.delete(projectId, webhookId, TenantScopeResolver.scope(http));
    }
}
