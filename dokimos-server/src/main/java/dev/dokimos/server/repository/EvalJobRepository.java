package dev.dokimos.server.repository;

import dev.dokimos.server.entity.EvalJob;
import dev.dokimos.server.entity.EvalJobStatus;
import dev.dokimos.server.entity.ExperimentRun;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface EvalJobRepository extends JpaRepository<EvalJob, UUID> {

    boolean existsByRunAndEvaluatorName(ExperimentRun run, String evaluatorName);

    List<EvalJob> findByRunOrderByCreatedAtAsc(ExperimentRun run);

    /**
     * Selects the oldest pending job below the retry ceiling and locks its row so the polling worker
     * can claim it atomically. {@code SKIP LOCKED} lets concurrent workers each pick a distinct job
     * instead of blocking on the same row; callers pass a one-row {@link Pageable}.
     *
     * @param maxAttempts the retry ceiling; jobs at or above this count are skipped
     * @param pageable    a one-row page request
     * @return the locked candidate job, if any
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT j FROM EvalJob j
            WHERE j.status = dev.dokimos.server.entity.EvalJobStatus.PENDING
            AND j.attemptCount < :maxAttempts
            ORDER BY j.createdAt ASC
            """)
    List<EvalJob> findClaimableJobs(@Param("maxAttempts") int maxAttempts, Pageable pageable);

    default Optional<EvalJob> claimNext(int maxAttempts) {
        return findClaimableJobs(maxAttempts, Pageable.ofSize(1)).stream().findFirst();
    }

    List<EvalJob> findByStatus(EvalJobStatus status);
}
