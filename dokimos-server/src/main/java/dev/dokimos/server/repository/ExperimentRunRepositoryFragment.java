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

    /**
     * Lists an experiment's runs newest first, within the scope.
     *
     * @param experiment the owning experiment
     * @param scope the tenant scope
     * @return the visible runs, newest first
     */
    List<ExperimentRun> findByExperiment(Experiment experiment, TenantScope scope);

    /**
     * Returns the most recent run of an experiment within the scope.
     *
     * @param experiment the owning experiment
     * @param scope the tenant scope
     * @return the latest visible run, or empty
     */
    Optional<ExperimentRun> findFirstByExperiment(Experiment experiment, TenantScope scope);

    /**
     * Loads a run by id under a pessimistic write lock, within the scope. Used to serialize ingestion
     * against completion.
     *
     * @param id the run id
     * @param scope the tenant scope
     * @return the locked run if visible, otherwise empty
     */
    Optional<ExperimentRun> findByIdForUpdate(UUID id, TenantScope scope);

    /**
     * Returns an experiment's terminal (SUCCESS or FAILED) runs newest first, within the scope.
     *
     * @param experiment the owning experiment
     * @param pageable the page request
     * @param scope the tenant scope
     * @return the terminal runs, newest first, bounded by the page
     */
    List<ExperimentRun> findCompletedRunsByExperiment(Experiment experiment, Pageable pageable, TenantScope scope);

    /**
     * Resolves SUCCESS baseline candidates for automatic gate resolution, within the scope. Excludes the
     * candidate run, requires the same dataset version (the null branch matches ad-hoc to ad-hoc), and
     * filters by git branch when non-null. Ordered by start time descending.
     *
     * @param experiment the experiment to scope to
     * @param candidateId the candidate run id to exclude
     * @param datasetVersionId the candidate's dataset version id, or null for ad-hoc runs
     * @param branch a git branch to filter by, or null to ignore branch
     * @param pageable a one-row page request
     * @param scope the tenant scope
     * @return matching SUCCESS baseline candidates, newest first
     */
    List<ExperimentRun> findBaselineCandidates(
            Experiment experiment,
            UUID candidateId,
            UUID datasetVersionId,
            String branch,
            Pageable pageable,
            TenantScope scope);
}
