package dev.dokimos.server.dto.v1;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to register an alert webhook for a project. The {@code url} receives the alert payload; the
 * optional {@code secret} signs the request body with HMAC-SHA256. A webhook defaults to enabled when
 * {@code enabled} is null.
 *
 * @param url the HTTP endpoint to POST alerts to (required)
 * @param secret an optional HMAC signing secret, or null/blank to send unsigned
 * @param enabled whether the webhook is active; null is treated as enabled
 */
public record CreateAlertWebhookRequest(@NotBlank String url, String secret, Boolean enabled) {}
