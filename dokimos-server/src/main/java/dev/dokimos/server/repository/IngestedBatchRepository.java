package dev.dokimos.server.repository;

import dev.dokimos.server.entity.IngestedBatch;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestedBatchRepository extends JpaRepository<IngestedBatch, IngestedBatch.IngestedBatchId> {

    /**
     * Checks whether a batch with the given idempotency key has already been committed for the run.
     *
     * @param runId the run id
     * @param idempotencyKey the idempotency key
     * @return true if a matching batch record exists
     */
    boolean existsByRunIdAndIdempotencyKey(UUID runId, String idempotencyKey);
}
