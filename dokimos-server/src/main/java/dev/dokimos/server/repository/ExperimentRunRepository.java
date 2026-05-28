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
}
