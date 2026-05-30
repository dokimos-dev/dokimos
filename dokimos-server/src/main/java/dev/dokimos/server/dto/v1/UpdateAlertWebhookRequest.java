package dev.dokimos.server.dto.v1;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for updating an alert webhook. The {@code url} and {@code enabled} flag are replaced.
 * Supplying a non-blank {@code secret} replaces the signing secret; leaving it null/blank keeps the
 * existing secret (a secret is cleared only through an explicit empty-secret update path, which this
 * endpoint does not expose, so receivers never lose a configured secret by accident).
 *
 * @param url the HTTP endpoint to POST alerts to (required)
 * @param secret a new HMAC signing secret, or null/blank to keep the current secret
 * @param enabled whether the webhook is active; null is treated as enabled
 */
public record UpdateAlertWebhookRequest(@NotBlank String url, String secret, Boolean enabled) {}
