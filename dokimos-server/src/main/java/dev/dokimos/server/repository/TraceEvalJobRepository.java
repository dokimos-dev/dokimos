package dev.dokimos.server.repository;

import dev.dokimos.server.entity.TraceEvalJob;
import dev.dokimos.server.entity.TraceEvalJobStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TraceEvalJobRepository extends JpaRepository<TraceEvalJob, UUID> {

    /** Jobs for the spans of one trace, used by the per-trace detail view. */
    @Query("SELECT j FROM TraceEvalJob j WHERE j.span.trace.id = :tracePk ORDER BY j.createdAt ASC")
    List<TraceEvalJob> findByTracePk(@Param("tracePk") UUID tracePk);

    List<TraceEvalJob> findByStatus(TraceEvalJobStatus status);

    /**
     * Atomically claims the oldest pending job below the retry ceiling using {@code FOR UPDATE SKIP
     * LOCKED}, so multiple worker instances each pick a distinct job instead of blocking on, or
     * double-processing, the same row. Must run inside a transaction.
     *
     * @param maxAttempts the retry ceiling; jobs at or above this count are skipped
     * @return the locked candidate job, if any
     */
    @Query(value = """
                    SELECT * FROM trace_eval_jobs
                    WHERE status = 'PENDING' AND attempt_count < :maxAttempts
                    ORDER BY created_at ASC
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED
                    """, nativeQuery = true)
    Optional<TraceEvalJob> claimNext(@Param("maxAttempts") int maxAttempts);

    /**
     * Returns jobs claimed before the cutoff to PENDING so a job orphaned by a crashed worker is picked
     * up again rather than stranded in CLAIMED forever. The attempt count is left as-is, so the retry
     * ceiling still bounds how many times a job is reclaimed.
     *
     * @param cutoff jobs claimed before this instant are requeued
     * @return the number of jobs requeued
     */
    @Modifying
    @Query("""
            UPDATE TraceEvalJob j
            SET j.status = dev.dokimos.server.entity.TraceEvalJobStatus.PENDING
            WHERE j.status = dev.dokimos.server.entity.TraceEvalJobStatus.CLAIMED
            AND j.claimedAt < :cutoff
            """)
    int requeueStaleClaims(@Param("cutoff") Instant cutoff);
}
