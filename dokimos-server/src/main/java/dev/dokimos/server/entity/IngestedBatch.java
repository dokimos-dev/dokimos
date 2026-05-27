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
 * Records that a client item batch with a given idempotency key has been committed for a run. The
 * composite primary key {@code (runId, idempotencyKey)} makes a retried POST that already succeeded
 * server side a no-op: the second insert sees the existing row and skips re-inserting items.
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

    /**
     * Creates a record marking the given idempotency key as ingested for the given run.
     *
     * @param runId the run the batch belongs to
     * @param idempotencyKey the client-supplied idempotency key for the batch
     * @param createdAt when the batch was committed
     */
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

    /**
     * Composite primary key for {@link IngestedBatch}.
     */
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
