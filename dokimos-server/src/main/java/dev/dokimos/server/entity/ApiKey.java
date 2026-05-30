package dev.dokimos.server.entity;

import dev.dokimos.server.filter.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A scoped API key used to authenticate write requests against {@code /api/v1/**}. Only the SHA-256 hex
 * hash of the key is persisted ({@code keyHash}); the raw key is shown to the caller exactly once at
 * creation and never stored. Each key carries a {@link Role} that bounds what the caller may do, and an
 * optional tenant. Disabled keys are rejected at authentication time without being deleted, so a key can
 * be revoked while its audit trail (created_at, last_used_at) is preserved.
 */
@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected ApiKey() {}

    /**
     * Creates an enabled key.
     *
     * @param keyHash SHA-256 hex hash of the raw key (never the raw key)
     * @param name human-readable label for the key
     * @param role privilege level granted to callers presenting this key
     * @param tenantId tenant the key belongs to, or {@code null} for an unscoped key
     */
    public ApiKey(String keyHash, String name, Role role, String tenantId) {
        this.keyHash = keyHash;
        this.name = name;
        this.role = role;
        this.tenantId = tenantId;
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getName() {
        return name;
    }

    public Role getRole() {
        return role;
    }

    public String getTenantId() {
        return tenantId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Disables the key so future authentication attempts with it are rejected. */
    public void disable() {
        this.enabled = false;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    /** Records that the key was just used to authenticate a request. */
    public void markUsed() {
        this.lastUsedAt = Instant.now();
    }
}
