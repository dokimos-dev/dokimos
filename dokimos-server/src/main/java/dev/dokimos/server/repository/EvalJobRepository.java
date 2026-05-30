package dev.dokimos.server.repository;

import dev.dokimos.server.entity.EvalJob;
import dev.dokimos.server.entity.EvalJobStatus;
import dev.dokimos.server.entity.ExperimentRun;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvalJobRepository extends JpaRepository<EvalJob, UUID> {

    boolean existsByRunAndEvaluatorName(ExperimentRun run, String evaluatorName);

    /** Removes the queue records for a connection. The eval results those jobs produced are untouched. */
    @Modifying
    @Query("DELETE FROM EvalJob j WHERE j.connection.id = :connectionId")
    void deleteByConnectionId(@Param("connectionId") UUID connectionId);

    List<EvalJob> findByRunOrderByCreatedAtAsc(ExperimentRun run);

    /**
     * Atomically claims the oldest pending job below the retry ceiling using {@code FOR UPDATE SKIP
     * LOCKED}, so multiple worker instances each pick a distinct job instead of blocking on, or
     * double-processing, the same row. Must run inside a transaction.
     *
     * @param maxAttempts the retry ceiling; jobs at or above this count are skipped
     * @return the locked candidate job, if any
     */
    @Query(value = """
                    SELECT * FROM eval_jobs
                    WHERE status = 'PENDING' AND attempt_count < :maxAttempts
                    ORDER BY created_at ASC
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED
                    """, nativeQuery = true)
    Optional<EvalJob> claimNext(@Param("maxAttempts") int maxAttempts);

    /**
     * Returns jobs claimed before the cutoff to PENDING so a job orphaned by a crashed worker is
     * picked up again rather than stranded in CLAIMED forever. The attempt count is left as-is, so the
     * retry ceiling still bounds how many times a job is reclaimed.
     *
     * @param cutoff jobs claimed before this instant are requeued
     * @return the number of jobs requeued
     */
    @Modifying
    @Query("""
            UPDATE EvalJob j
            SET j.status = dev.dokimos.server.entity.EvalJobStatus.PENDING
            WHERE j.status = dev.dokimos.server.entity.EvalJobStatus.CLAIMED
            AND j.claimedAt < :cutoff
            """)
    int requeueStaleClaims(@Param("cutoff") Instant cutoff);

    List<EvalJob> findByStatus(EvalJobStatus status);
}
