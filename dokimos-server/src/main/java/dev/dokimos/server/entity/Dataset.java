package dev.dokimos.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * A named container that owns one or more immutable {@link DatasetVersion}s. The {@code name} is unique
 * per tenant rather than globally, so two tenants can each own a dataset of the same name. The matching
 * DB constraint plus a partial unique on the shared (null-tenant) rows lives in migration V14; the
 * {@code (name, tenant_id)} unique here keeps the Hibernate-generated test schema consistent with it.
 */
@Entity
@Table(
        name = "datasets",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_dataset_name_tenant",
                    columnNames = {"name", "tenant_id"})
        })
public class Dataset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Dataset() {}

    public Dataset(String name, String description) {
        Instant now = Instant.now();
        this.name = name;
        this.description = description;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }
}
