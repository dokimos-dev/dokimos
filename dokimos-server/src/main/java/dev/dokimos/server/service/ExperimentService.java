package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.ExperimentSummary;
import dev.dokimos.server.dto.v1.TrendData;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.entity.RunStatus;
import dev.dokimos.server.repository.ExperimentRepository;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.repository.ItemResultRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class ExperimentService {

    private final ExperimentRepository experimentRepository;
    private final ExperimentRunRepository runRepository;
    private final ItemResultRepository itemResultRepository;

    public ExperimentService(ExperimentRepository experimentRepository,
            ExperimentRunRepository runRepository,
            ItemResultRepository itemResultRepository) {
        this.experimentRepository = experimentRepository;
        this.runRepository = runRepository;
        this.itemResultRepository = itemResultRepository;
    }

    @Transactional
    public Experiment getOrCreateExperiment(@NonNull Project project, @NonNull String name) {
        return experimentRepository.findByProjectAndName(project, name)
                .orElseGet(() -> experimentRepository.save(new Experiment(project, name)));
    }

    @Transactional(readOnly = true)
    @NonNull
    public List<ExperimentSummary> listExperiments(Project project) {
        List<Experiment> experiments = experimentRepository.findByProjectOrderByCreatedAtDesc(project);
        List<ExperimentSummary> summaries = new ArrayList<>();

        for (Experiment experiment : experiments) {
            ExperimentSummary.LatestRunInfo latestRunInfo = null;

            var latestRun = runRepository.findFirstByExperimentOrderByStartedAtDesc(experiment);
            if (latestRun.isPresent()) {
                ExperimentRun run = latestRun.get();
                Double passRate = calculatePassRate(run);
                latestRunInfo = new ExperimentSummary.LatestRunInfo(
                        run.getId(),
                        run.getStatus(),
                        passRate,
                        run.getStartedAt());
            }

            summaries.add(new ExperimentSummary(
                    experiment.getId(),
                    experiment.getName(),
                    experiment.getCreatedAt(),
                    latestRunInfo));
        }

        return summaries;
    }

    @Transactional(readOnly = true)
    public Experiment getExperiment(UUID experimentId) {
        if (experimentId == null) {
            throw new IllegalArgumentException("Experiment ID cannot be null");
        }
        return experimentRepository.findById(experimentId)
                .orElseThrow(() -> new IllegalArgumentException("Experiment not found: " + experimentId));
    }

    @Transactional(readOnly = true)
    public TrendData getTrends(UUID experimentId, int limit) {
        Experiment experiment = getExperiment(experimentId);
        List<ExperimentRun> runs = runRepository.findCompletedRunsByExperiment(
                experiment, PageRequest.of(0, limit));

        List<TrendData.RunPoint> points = new ArrayList<>();
        for (ExperimentRun run : runs) {
            long totalItems = itemResultRepository.countByRun(run);
            long passedItems = itemResultRepository.countItemsWithAllEvalsPassed(run);
            double passRate = totalItems > 0 ? (double) passedItems / totalItems : 0.0;

            points.add(new TrendData.RunPoint(
                    run.getId(),
                    run.getStartedAt(),
                    passRate,
                    totalItems,
                    passedItems));
        }

        // Reverse to get chronological order
        Collections.reverse(points);

        return new TrendData(experiment.getName(), points);
    }

    private Double calculatePassRate(ExperimentRun run) {
        if (run.getStatus() == RunStatus.RUNNING) {
            return null;
        }
        long totalItems = itemResultRepository.countByRun(run);
        if (totalItems == 0) {
            return null;
        }
        long passedItems = itemResultRepository.countItemsWithAllEvalsPassed(run);
        return (double) passedItems / totalItems;
    }
}
