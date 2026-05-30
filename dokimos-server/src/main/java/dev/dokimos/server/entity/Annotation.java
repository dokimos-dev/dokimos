package dev.dokimos.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A human reviewer's verdict on a single {@link ItemResult}. Exactly one annotation exists per item
 * result, enforced by the unique constraint on {@code item_result_id}. An optional
 * {@code overriddenExpectedOutput} lets a reviewer correct the expected output captured by the run so
 * the corrected value can later be promoted into a dataset version.
 */
@Entity
@Table(
        name = "annotations",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_annotation_item_result",
                    columnNames = {"item_result_id"})
        })
public class Annotation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_result_id", nullable = false)
    private ItemResult itemResult;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnnotationVerdict verdict;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "overridden_expected_output", columnDefinition = "jsonb")
    private Map<String, Object> overriddenExpectedOutput;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Annotation() {}

    public Annotation(ItemResult itemResult, AnnotationVerdict verdict) {
        Instant now = Instant.now();
        this.itemResult = itemResult;
        this.verdict = verdict;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public ItemResult getItemResult() {
        return itemResult;
    }

    public AnnotationVerdict getVerdict() {
        return verdict;
    }

    public void setVerdict(AnnotationVerdict verdict) {
        this.verdict = verdict;
    }

    public Map<String, Object> getOverriddenExpectedOutput() {
        return overriddenExpectedOutput;
    }

    public void setOverriddenExpectedOutput(Map<String, Object> overriddenExpectedOutput) {
        this.overriddenExpectedOutput = overriddenExpectedOutput;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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
