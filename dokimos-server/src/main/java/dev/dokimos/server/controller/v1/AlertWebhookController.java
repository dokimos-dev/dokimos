package dev.dokimos.server.controller.v1;

import dev.dokimos.server.dto.v1.AlertWebhookView;
import dev.dokimos.server.dto.v1.CreateAlertWebhookRequest;
import dev.dokimos.server.dto.v1.UpdateAlertWebhookRequest;
import dev.dokimos.server.service.AlertWebhookService;
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
    public ResponseEntity<AlertWebhookView> create(
            @PathVariable UUID projectId, @Valid @RequestBody CreateAlertWebhookRequest request) {
        AlertWebhookView view = webhookService.create(projectId, request);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/alert-webhooks/" + view.id()))
                .body(view);
    }

    /** Lists the project's webhooks. The signing secret is never included. */
    @GetMapping
    public List<AlertWebhookView> list(@PathVariable UUID projectId) {
        return webhookService.list(projectId);
    }

    /** Returns one webhook, or 404 if the project or webhook does not exist. */
    @GetMapping("/{webhookId}")
    public AlertWebhookView get(@PathVariable UUID projectId, @PathVariable UUID webhookId) {
        return webhookService.get(projectId, webhookId);
    }

    /** Updates a webhook. Returns 404 if the project or webhook does not exist. */
    @PutMapping("/{webhookId}")
    public AlertWebhookView update(
            @PathVariable UUID projectId,
            @PathVariable UUID webhookId,
            @Valid @RequestBody UpdateAlertWebhookRequest request) {
        return webhookService.update(projectId, webhookId, request);
    }

    /** Deletes a webhook. Returns 404 if the project or webhook does not exist. */
    @DeleteMapping("/{webhookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID webhookId) {
        webhookService.delete(projectId, webhookId);
    }
}
