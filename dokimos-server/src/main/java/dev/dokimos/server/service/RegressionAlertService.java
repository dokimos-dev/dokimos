package dev.dokimos.server.service;

import dev.dokimos.core.comparison.RunComparisonResult;
import dev.dokimos.server.dto.v1.RegressionAlertPayload;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.repository.ExperimentRunRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Computes, inside the run-completion transaction, whether a just-completed run regressed against its
 * baseline, and publishes a {@link RegressionAlertEvent} when it did. The event is delivered to the
 * project's webhooks only after the transaction commits (see {@link AlertWebhookDispatcher}), so the
 * decision is part of completion but the network I/O is not.
 *
 * <p>The baseline is resolved exactly as the CI gate resolves it (the most recent SUCCESS run of the
 * same experiment, scoped by dataset version and git branch), and the comparison uses the same shared
 * {@link ComparisonSupport} path, so an alert fires on the same regression the gate would fail on. An
 * alert fires only when the pass rate both regressed and the drop is statistically significant, which
 * keeps noise from minor run-to-run jitter off the webhook.
 */
@Service
public class RegressionAlertService {

    private static final Logger log = LoggerFactory.getLogger(RegressionAlertService.class);

    private final ExperimentRunRepository runRepository;
    private final ComparisonSupport comparisonSupport;
    private final ApplicationEventPublisher eventPublisher;

    public RegressionAlertService(
            ExperimentRunRepository runRepository,
            ComparisonSupport comparisonSupport,
            ApplicationEventPublisher eventPublisher) {
        this.runRepository = runRepository;
        this.comparisonSupport = comparisonSupport;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Evaluates a completed run for a significant pass-rate regression and, if found, publishes a
     * regression alert event. Never throws: any failure to resolve or compare is logged and swallowed
     * so the surrounding run-completion transaction is unaffected.
     *
     * @param run the run that just reached a terminal status
     */
    public void evaluateOnCompletion(ExperimentRun run) {
        try {
            ExperimentRun baseline = resolveBaseline(run);
            if (baseline == null) {
                return;
            }
            RunComparisonResult comparison =
                    comparisonSupport.compare(baseline, run).result();
            if (!isSignificantRegression(comparison)) {
                return;
            }
            eventPublisher.publishEvent(toEvent(run, baseline, comparison));
        } catch (Exception e) {
            // Alerting is best-effort and must never affect run completion.
            log.warn("Regression alert evaluation failed for run {}: {}", run.getId(), e.getMessage());
        }
    }

    private ExperimentRun resolveBaseline(ExperimentRun candidate) {
        Experiment experiment = candidate.getExperiment();
        List<ExperimentRun> candidates = runRepository.findBaselineCandidates(
                experiment,
                candidate.getId(),
                comparisonSupport.datasetVersionId(candidate),
                candidate.getGitBranch(),
                PageRequest.of(0, 1),
                dev.dokimos.server.tenant.TenantScope.unrestricted());
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private boolean isSignificantRegression(RunComparisonResult comparison) {
        boolean significant = comparison.passRateSignificance() != null
                && comparison.passRateSignificance().significant();
        return comparison.passRateRegressed() && significant;
    }

    private RegressionAlertEvent toEvent(
            ExperimentRun candidate, ExperimentRun baseline, RunComparisonResult comparison) {
        Experiment experiment = candidate.getExperiment();
        Project project = experiment.getProject();
        UUID baselineRunId = baseline.getId();
        RegressionAlertPayload payload = new RegressionAlertPayload(
                project.getName(),
                experiment.getId(),
                experiment.getName(),
                candidate.getId(),
                baselineRunId,
                comparison.baselinePassRate(),
                comparison.candidatePassRate(),
                comparison.passRateDelta(),
                comparison.regressedCount());
        return new RegressionAlertEvent(project.getId(), payload);
    }
}
