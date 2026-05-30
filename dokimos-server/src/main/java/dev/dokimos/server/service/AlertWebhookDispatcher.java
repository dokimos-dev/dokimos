package dev.dokimos.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.server.entity.AlertWebhook;
import dev.dokimos.server.repository.AlertWebhookRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Delivers regression alerts to a project's enabled webhooks. Dispatch runs only after the
 * run-completion transaction commits ({@link TransactionalEventListener} on
 * {@link TransactionPhase#AFTER_COMMIT}) and on a separate thread ({@link Async}), so neither the
 * webhook lookup nor the HTTP POST can block, lengthen, or roll back run completion. Every delivery
 * failure is caught and logged; a failing or slow receiver never fails the run.
 *
 * <p>When a webhook has a signing secret, the request body is signed with HMAC-SHA256 and the
 * lowercase hex digest is sent in the {@code X-Dokimos-Signature} header so receivers can verify
 * authenticity.
 */
@Component
public class AlertWebhookDispatcher {

    /** Header carrying the lowercase hex HMAC-SHA256 of the request body. */
    static final String SIGNATURE_HEADER = "X-Dokimos-Signature";

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private static final Logger log = LoggerFactory.getLogger(AlertWebhookDispatcher.class);

    private final AlertWebhookRepository webhookRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public AlertWebhookDispatcher(AlertWebhookRepository webhookRepository, ObjectMapper objectMapper) {
        this(
                webhookRepository,
                objectMapper,
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
    }

    AlertWebhookDispatcher(AlertWebhookRepository webhookRepository, ObjectMapper objectMapper, HttpClient httpClient) {
        this.webhookRepository = webhookRepository;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    /**
     * Resolves the project's enabled webhooks and POSTs the alert to each. Runs after commit on an
     * async thread; opens its own read-only transaction to load the webhooks because the original
     * persistence context is gone by this point.
     *
     * @param event the regression alert published during run completion
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onRegressionAlert(RegressionAlertEvent event) {
        String body;
        try {
            body = objectMapper.writeValueAsString(event.payload());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize regression alert payload for project {}", event.projectId(), e);
            return;
        }

        List<AlertWebhook> webhooks = webhookRepository.findByProjectIdAndEnabledTrue(event.projectId());
        for (AlertWebhook webhook : webhooks) {
            deliver(webhook, body);
        }
    }

    private void deliver(AlertWebhook webhook, String body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(webhook.getUrl()))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (webhook.hasSecret()) {
                request.header(SIGNATURE_HEADER, sign(body, webhook.getSecret()));
            }
            HttpResponse<Void> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) {
                log.warn(
                        "Alert webhook {} returned status {} for project {}",
                        webhook.getId(),
                        response.statusCode(),
                        webhook.getProject().getId());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted delivering alert webhook {}", webhook.getId(), e);
        } catch (Exception e) {
            // A delivery failure must never propagate: the run has already completed and committed.
            log.warn("Failed to deliver alert webhook {}: {}", webhook.getId(), e.getMessage());
        }
    }

    /**
     * Computes the lowercase hex HMAC-SHA256 of the body under the secret.
     *
     * @param body the request body
     * @param secret the webhook signing secret
     * @return the lowercase hex signature
     */
    static String sign(String body, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign webhook payload", e);
        }
    }
}
