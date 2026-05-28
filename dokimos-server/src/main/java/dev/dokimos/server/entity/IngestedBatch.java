package dev.dokimos.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Marks an item batch (identified by idempotency key) as committed for a run. The composite PK
 * {@code (runId, idempotencyKey)} makes a retried POST a no-op.
 */
@Entity
@Table(name = "ingested_batches")
@IdClass(IngestedBatch.IngestedBatchId.class)
public class IngestedBatch {

    @Id
    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Id
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private Instant createdAt;

    protected IngestedBatch() {}

    public IngestedBatch(UUID runId, String idempotencyKey, Instant createdAt) {
        this.runId = runId;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public UUID getRunId() {
        return runId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Composite primary key. */
    public static class IngestedBatchId implements Serializable {

        private UUID runId;
        private String idempotencyKey;

        public IngestedBatchId() {}

        public IngestedBatchId(UUID runId, String idempotencyKey) {
            this.runId = runId;
            this.idempotencyKey = idempotencyKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof IngestedBatchId other)) {
                return false;
            }
            return Objects.equals(runId, other.runId) && Objects.equals(idempotencyKey, other.idempotencyKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(runId, idempotencyKey);
        }
    }
}
