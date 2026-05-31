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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A single example within a {@link DatasetVersion}. The {@code ordinal} preserves the caller's input
 * order and is unique within a version; runs pair items across versions by {@code (versionId, ordinal)}
 * or by the stable item id.
 */
@Entity
@Table(
        name = "dataset_items",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_dataset_item_ordinal",
                    columnNames = {"dataset_version_id", "ordinal"})
        })
public class DatasetItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_version_id", nullable = false)
    private DatasetVersion datasetVersion;

    @Column(nullable = false)
    private int ordinal;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> inputs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expected_outputs", columnDefinition = "jsonb")
    private Map<String, Object> expectedOutputs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "tenant_id")
    private String tenantId;

    protected DatasetItem() {}

    public DatasetItem(
            DatasetVersion datasetVersion,
            int ordinal,
            Map<String, Object> inputs,
            Map<String, Object> expectedOutputs,
            Map<String, Object> metadata) {
        this.datasetVersion = datasetVersion;
        this.ordinal = ordinal;
        this.inputs = inputs;
        this.expectedOutputs = expectedOutputs;
        this.metadata = metadata;
    }

    public UUID getId() {
        return id;
    }

    public DatasetVersion getDatasetVersion() {
        return datasetVersion;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public Map<String, Object> getExpectedOutputs() {
        return expectedOutputs;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
