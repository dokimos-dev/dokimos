package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.ExperimentSummary;
import dev.dokimos.server.dto.v1.TrendData;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.entity.RunStatus;
import dev.dokimos.server.repository.ExperimentRepository;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExperimentService {

    private final ExperimentRepository experimentRepository;
    private final ExperimentRunRepository runRepository;

    public ExperimentService(ExperimentRepository experimentRepository, ExperimentRunRepository runRepository) {
        this.experimentRepository = experimentRepository;
        this.runRepository = runRepository;
    }

    /**
     * Resolves an experiment of the project by name within the scope, creating it stamped with the
     * scope's tenant when absent. The parent project was already loaded scoped, so this never reaches a
     * foreign tenant.
     *
     * @param project the owning project
     * @param name the experiment name
     * @param scope the tenant scope of the caller
     * @return the existing or newly created experiment
     */
    @Transactional
    public Experiment getOrCreateExperiment(@NonNull Project project, @NonNull String name, TenantScope scope) {
        return experimentRepository.findByProjectAndName(project, name, scope).orElseGet(() -> {
            Experiment experiment = new Experiment(project, name);
            experiment.setTenantId(scope.stampTenantId());
            return experimentRepository.save(experiment);
        });
    }

    @Transactional(readOnly = true)
    @NonNull
    public List<ExperimentSummary> listExperiments(Project project, TenantScope scope) {
        List<Experiment> experiments = experimentRepository.findByProject(project, scope);
        List<ExperimentSummary> summaries = new ArrayList<>();

        for (Experiment experiment : experiments) {
            ExperimentSummary.LatestRunInfo latestRunInfo = null;

            var latestRun = runRepository.findFirstByExperiment(experiment, scope);
            if (latestRun.isPresent()) {
                ExperimentRun run = latestRun.get();
                Double passRate = calculatePassRate(run);
                latestRunInfo =
                        new ExperimentSummary.LatestRunInfo(run.getId(), run.getStatus(), passRate, run.getStartedAt());
            }

            summaries.add(new ExperimentSummary(
                    experiment.getId(), experiment.getName(), experiment.getCreatedAt(), latestRunInfo));
        }

        return summaries;
    }

    @Transactional(readOnly = true)
    public Experiment getExperiment(UUID experimentId, TenantScope scope) {
        if (experimentId == null) {
            throw new IllegalArgumentException("Experiment ID cannot be null");
        }
        return experimentRepository
                .findById(experimentId, scope)
                .orElseThrow(() -> new IllegalArgumentException("Experiment not found: " + experimentId));
    }

    /** Deletes an experiment visible under the scope; FKs cascade to its runs, items, and evals. */
    @Transactional
    public void deleteExperiment(UUID experimentId, TenantScope scope) {
        Experiment experiment = getExperiment(experimentId, scope);
        experimentRepository.delete(experiment);
    }

    @Transactional(readOnly = true)
    public TrendData getTrends(UUID experimentId, int limit, TenantScope scope) {
        Experiment experiment = getExperiment(experimentId, scope);
        List<ExperimentRun> runs =
                runRepository.findCompletedRunsByExperiment(experiment, PageRequest.of(0, limit), scope);

        List<TrendData.RunPoint> points = new ArrayList<>();
        for (ExperimentRun run : runs) {
            // Terminal-only query, so materialized counts are populated.
            long totalItems = run.getItemCount();
            long passedItems = run.getPassedCount();
            double passRate = run.getPassRate() != null ? run.getPassRate() : 0.0;

            points.add(new TrendData.RunPoint(run.getId(), run.getStartedAt(), passRate, totalItems, passedItems));
        }

        Collections.reverse(points);

        return new TrendData(experiment.getName(), experiment.getProject().getName(), points);
    }

    private Double calculatePassRate(ExperimentRun run) {
        if (run.getStatus() == RunStatus.RUNNING) {
            return null;
        }
        return run.getPassRate();
    }
}
