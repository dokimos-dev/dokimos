package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Trace;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TraceRepository extends JpaRepository<Trace, UUID> {

    Optional<Trace> findByTraceId(String traceId);

    Page<Trace> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Trace> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    /**
     * Deletes traces whose retention window has closed. Spans and trace eval jobs cascade via their
     * foreign keys, so a single delete clears the whole expired subtree.
     *
     * <p>{@code flushAutomatically} and {@code clearAutomatically} keep the persistence context
     * consistent with this database-level cascade: the context is flushed before the delete and cleared
     * after it, so managed spans (whose JSONB attribute map Hibernate treats as perpetually dirty) are
     * detached rather than re-flushed as updates against rows the cascade has already removed.
     *
     * @param cutoff traces with an expiry at or before this instant are removed
     * @return the number of traces deleted
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Trace t WHERE t.expiresAt <= :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
