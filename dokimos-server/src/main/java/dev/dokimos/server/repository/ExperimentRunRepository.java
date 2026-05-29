package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExperimentRunRepository extends JpaRepository<ExperimentRun, UUID> {

    List<ExperimentRun> findByExperimentOrderByStartedAtDesc(Experiment experiment);

    List<ExperimentRun> findByExperimentOrderByStartedAtDesc(Experiment experiment, Pageable pageable);

    Optional<ExperimentRun> findFirstByExperimentOrderByStartedAtDesc(Experiment experiment);

    /**
     * Loads a run while acquiring a pessimistic write lock on its row. Used to serialize item
     * ingestion ({@link dev.dokimos.server.service.RunService#addItems}) against run completion
     * ({@link dev.dokimos.server.service.RunService#updateRun}) so the materialized counts written at
     * completion cannot be left stale by a concurrent in-flight item batch.
     *
     * @param id the run id
     * @return the run, if present, with its row locked for the remainder of the transaction
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ExperimentRun r where r.id = :id")
    Optional<ExperimentRun> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            SELECT r FROM ExperimentRun r
            WHERE r.experiment = :experiment
            AND r.status IN ('SUCCESS', 'FAILED')
            ORDER BY r.startedAt DESC
            """)
    List<ExperimentRun> findCompletedRunsByExperiment(Experiment experiment, Pageable pageable);

    /**
     * Resolves the most recent SUCCESS run of an experiment to serve as a gate baseline under
     * automatic resolution. Excludes the candidate run, requires the same dataset version (the
     * {@code IS NULL} branch allows ad-hoc runs to baseline against other ad-hoc runs), and when
     * {@code branch} is non-null restricts to that git branch. Ordered by {@code startedAt}
     * descending; callers pass a one-row {@link Pageable}.
     *
     * <p>FAILED runs are excluded from automatic resolution: a FAILED run may have a truncated item
     * set and a 0 or null materialized pass rate, which would distort the comparison. A caller may
     * still pass an explicit {@code baselineRunId} pointing at a FAILED run, as long as that run is
     * terminal; that path does not go through this query.
     *
     * @param experiment       the experiment to scope to
     * @param candidateId      the candidate run id to exclude
     * @param datasetVersionId the candidate's dataset version id, or null for ad-hoc runs
     * @param branch           a git branch to filter by, or null to ignore branch
     * @param pageable         a one-row page request
     * @return matching SUCCESS baseline candidates, newest first
     */
    @Query("""
            SELECT r FROM ExperimentRun r
            WHERE r.experiment = :experiment
            AND r.id <> :candidateId
            AND r.status = 'SUCCESS'
            AND (
                (:datasetVersionId IS NULL AND r.datasetVersion IS NULL)
                OR r.datasetVersion.id = :datasetVersionId
            )
            AND (:branch IS NULL OR r.gitBranch = :branch)
            ORDER BY r.startedAt DESC
            """)
    List<ExperimentRun> findBaselineCandidates(
            @Param("experiment") Experiment experiment,
            @Param("candidateId") UUID candidateId,
            @Param("datasetVersionId") UUID datasetVersionId,
            @Param("branch") String branch,
            Pageable pageable);
}
