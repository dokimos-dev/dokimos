package dev.dokimos.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * A named, reusable pointer to an OpenAI-compatible endpoint used by the server-side judge. The
 * {@code protocol} selects whether that endpoint speaks the Responses API or Chat Completions. Exactly
 * one credential source is set: {@code credentialRef} names an environment variable
 * (or external path) that holds the key, or {@code encryptedApiKey} carries an inline key encrypted at
 * rest. The entity never exposes raw key material; decryption is the responsibility of the credential
 * service. The {@code name} is unique per tenant rather than globally, so two tenants can each own a
 * connection of the same name; the matching DB constraint plus a partial unique on the shared
 * (null-tenant) rows lives in migration V14.
 */
@Entity
@Table(
        name = "llm_connections",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_llm_connection_name_tenant",
                    columnNames = {"name", "tenant_id"})
        })
public class LlmConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    @Column(nullable = false)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LlmConnectionProtocol protocol = LlmConnectionProtocol.RESPONSES;

    @Column(name = "credential_ref")
    private String credentialRef;

    @Column(name = "encrypted_api_key", columnDefinition = "text")
    private String encryptedApiKey;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected LlmConnection() {}

    public LlmConnection(String name, String baseUrl, String model) {
        Instant now = Instant.now();
        this.name = name;
        this.baseUrl = baseUrl;
        this.model = model;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public LlmConnectionProtocol getProtocol() {
        return protocol;
    }

    public void setProtocol(LlmConnectionProtocol protocol) {
        this.protocol = protocol;
    }

    /** Stamps the connection as just modified. */
    public void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }

    public String getCredentialRef() {
        return credentialRef;
    }

    public void setCredentialRef(String credentialRef) {
        this.credentialRef = credentialRef;
    }

    public String getEncryptedApiKey() {
        return encryptedApiKey;
    }

    public void setEncryptedApiKey(String encryptedApiKey) {
        this.encryptedApiKey = encryptedApiKey;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /** Returns true when an inline encrypted key is stored rather than an external credential reference. */
    public boolean hasInlineKey() {
        return encryptedApiKey != null && !encryptedApiKey.isBlank();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
