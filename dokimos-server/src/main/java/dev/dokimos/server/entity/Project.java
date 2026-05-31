package dev.dokimos.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A project groups experiments. The name is unique per tenant rather than globally, so two tenants can
 * each own a project of the same name (for example "default"). The matching DB constraint plus a partial
 * unique on the shared (null-tenant) rows lives in migration V14; the {@code (name, tenant_id)} unique
 * here keeps the Hibernate-generated test schema consistent with it.
 */
@Entity
@Table(
        name = "projects",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_project_name_tenant",
                    columnNames = {"name", "tenant_id"})
        })
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(name = "tenant_id")
    private String tenantId;

    @OneToMany(mappedBy = "project")
    private List<Experiment> experiments = new ArrayList<>();

    protected Project() {}

    public Project(String name) {
        this.name = name;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Experiment> getExperiments() {
        return experiments;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
