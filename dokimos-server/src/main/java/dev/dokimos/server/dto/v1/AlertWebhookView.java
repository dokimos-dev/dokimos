package dev.dokimos.server.dto.v1;

import dev.dokimos.server.entity.AlertWebhook;
import java.time.Instant;
import java.util.UUID;

/**
 * Public view of an {@link AlertWebhook}. Never carries the signing secret: {@code hasSecret} reports
 * only whether one is configured.
 */
public record AlertWebhookView(
        UUID id, UUID projectId, String url, boolean hasSecret, boolean enabled, Instant createdAt) {

    public static AlertWebhookView from(AlertWebhook webhook) {
        return new AlertWebhookView(
                webhook.getId(),
                webhook.getProject().getId(),
                webhook.getUrl(),
                webhook.hasSecret(),
                webhook.isEnabled(),
                webhook.getCreatedAt());
    }
}
