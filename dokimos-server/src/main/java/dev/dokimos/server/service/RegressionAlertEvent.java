package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.RegressionAlertPayload;
import java.util.UUID;

/**
 * Published inside the run-completion transaction when a run regresses, and consumed only after that
 * transaction commits. Carrying the resolved {@code projectId} and the ready-to-send payload keeps the
 * after-commit listener free of further entity loads on a possibly closed persistence context.
 *
 * @param projectId the project whose enabled webhooks should receive the alert
 * @param payload the alert body to deliver
 */
public record RegressionAlertEvent(UUID projectId, RegressionAlertPayload payload) {}
