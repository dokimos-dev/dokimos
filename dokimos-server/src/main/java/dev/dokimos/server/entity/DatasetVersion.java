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
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable snapshot of a {@link Dataset}. Each version owns an ordered list of items and the
 * materialized {@code itemCount} is written at create time so listings need no extra aggregate. The
 * {@code (datasetId, version)} pair is unique; the version number is monotonically increasing per
 * dataset.
 */
@Entity
@Table(
        name = "dataset_versions",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_dataset_version",
                    columnNames = {"dataset_id", "version"})
        })
public class DatasetVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private Dataset dataset;

    @Column(nullable = false)
    private int version;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    protected DatasetVersion() {}

    public DatasetVersion(Dataset dataset, int version, String description, String createdBy, int itemCount) {
        this.dataset = dataset;
        this.version = version;
        this.description = description;
        this.createdBy = createdBy;
        this.itemCount = itemCount;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Dataset getDataset() {
        return dataset;
    }

    public int getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public int getItemCount() {
        return itemCount;
    }
}
