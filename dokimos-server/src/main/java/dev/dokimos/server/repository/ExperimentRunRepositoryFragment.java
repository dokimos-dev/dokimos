package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.tenant.ScopedRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/** Entity-specific scoped finders for {@link ExperimentRun}. */
public interface ExperimentRunRepositoryFragment extends ScopedRepository<ExperimentRun> {

    /** Lists an experiment's runs newest first, within the scope. */
    List<ExperimentRun> findByExperiment(Experiment experiment, TenantScope scope);

    /** Returns the most recent run of an experiment within the scope. */
    Optional<ExperimentRun> findFirstByExperiment(Experiment experiment, TenantScope scope);

    /** Loads a run by id within the scope under a write lock, to serialize ingestion against completion. */
    Optional<ExperimentRun> findByIdForUpdate(UUID id, TenantScope scope);

    /** Returns an experiment's terminal (SUCCESS or FAILED) runs newest first, within the scope. */
    List<ExperimentRun> findCompletedRunsByExperiment(Experiment experiment, Pageable pageable, TenantScope scope);

    /**
     * SUCCESS baseline candidates for automatic gate resolution, within the scope, newest first. Excludes
     * the candidate run, requires the same dataset version (a null {@code datasetVersionId} matches
     * ad-hoc to ad-hoc), and filters by {@code branch} when non-null.
     */
    List<ExperimentRun> findBaselineCandidates(
            Experiment experiment,
            UUID candidateId,
            UUID datasetVersionId,
            String branch,
            Pageable pageable,
            TenantScope scope);
}
