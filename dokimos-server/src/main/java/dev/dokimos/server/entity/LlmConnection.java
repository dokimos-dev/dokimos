package dev.dokimos.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A named, reusable pointer to an OpenAI-compatible chat completions endpoint used by the server-side
 * judge. Exactly one credential source is set: {@code credentialRef} names an environment variable
 * (or external path) that holds the key, or {@code encryptedApiKey} carries an inline key encrypted at
 * rest. The entity never exposes raw key material; decryption is the responsibility of the credential
 * service.
 */
@Entity
@Table(name = "llm_connections")
public class LlmConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    @Column(nullable = false)
    private String model;

    @Column(name = "credential_ref")
    private String credentialRef;

    @Column(name = "encrypted_api_key", columnDefinition = "text")
    private String encryptedApiKey;

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

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
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
