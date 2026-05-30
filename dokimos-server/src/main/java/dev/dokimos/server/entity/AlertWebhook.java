package dev.dokimos.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A configured HTTP endpoint that receives a JSON alert when a completed run of one of its project's
 * experiments shows a significant pass-rate regression against its baseline. An optional {@code secret}
 * is used to sign the request body with HMAC-SHA256; the entity never exposes the secret in its public
 * view. A disabled webhook is skipped during dispatch.
 */
@Entity
@Table(name = "alert_webhooks")
public class AlertWebhook {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(columnDefinition = "text")
    private String secret;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AlertWebhook() {}

    public AlertWebhook(Project project, String url, String secret, boolean enabled) {
        Instant now = Instant.now();
        this.project = project;
        this.url = url;
        this.secret = secret;
        this.enabled = enabled;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    /** Returns true when a signing secret is configured. */
    public boolean hasSecret() {
        return secret != null && !secret.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /** Stamps the webhook as just modified. */
    public void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
