package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExperimentRunRepository extends JpaRepository<ExperimentRun, UUID> {

    List<ExperimentRun> findByExperimentOrderByStartedAtDesc(Experiment experiment);

    List<ExperimentRun> findByExperimentOrderByStartedAtDesc(Experiment experiment, Pageable pageable);

    Optional<ExperimentRun> findFirstByExperimentOrderByStartedAtDesc(Experiment experiment);

    @Query("""
            SELECT r FROM ExperimentRun r
            WHERE r.experiment = :experiment
            AND r.status IN ('SUCCESS', 'FAILED')
            ORDER BY r.startedAt DESC
            """)
    List<ExperimentRun> findCompletedRunsByExperiment(Experiment experiment, Pageable pageable);
}
