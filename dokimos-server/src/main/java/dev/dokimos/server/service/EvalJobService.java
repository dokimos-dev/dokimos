package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.EnqueueJudgeRequest;
import dev.dokimos.server.dto.v1.EvalJobView;
import dev.dokimos.server.entity.EvalJob;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.LlmConnection;
import dev.dokimos.server.entity.RunStatus;
import dev.dokimos.server.repository.EvalJobRepository;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.repository.LlmConnectionRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enqueues server-side judge jobs and reads the jobs registered for a run. Enqueuing transitions the
 * run to {@link RunStatus#EVALUATING} so materialized pass-rate fields stay deferred until the worker
 * finishes scoring.
 */
@Service
public class EvalJobService {

    private final EvalJobRepository jobRepository;
    private final ExperimentRunRepository runRepository;
    private final LlmConnectionRepository connectionRepository;

    public EvalJobService(
            EvalJobRepository jobRepository,
            ExperimentRunRepository runRepository,
            LlmConnectionRepository connectionRepository) {
        this.jobRepository = jobRepository;
        this.runRepository = runRepository;
        this.connectionRepository = connectionRepository;
    }

    /**
     * Enqueues a judge job for a run and moves the run into the evaluating state. The run row is locked
     * for the transition so it serializes against item ingestion and completion.
     *
     * @param runId   the run to score
     * @param request the connection and evaluator configuration
     * @return the public view of the created job
     * @throws IllegalArgumentException if the run or connection does not exist (mapped to 404)
     * @throws IllegalStateException if the run already has a job for this evaluator (mapped to 409)
     */
    @Transactional
    public EvalJobView enqueue(UUID runId, EnqueueJudgeRequest request, TenantScope scope) {
        ExperimentRun run = runRepository
                .findByIdForUpdate(runId, scope)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));

        LlmConnection connection = connectionRepository
                .findById(request.connectionId(), scope)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + request.connectionId()));

        if (jobRepository.existsByRunAndEvaluatorName(run, request.evaluatorName())) {
            throw new IllegalStateException(
                    "A judge job already exists for evaluator '" + request.evaluatorName() + "' on run " + runId);
        }

        EvalJob job = new EvalJob(run, connection, request.evaluatorName(), request.criteria());
        job.setEvaluationParams(String.join(",", request.evaluationParams()));
        job.setMinScore(request.minScore());
        job.setMaxScore(request.maxScore());
        job.setThreshold(request.threshold());
        EvalJob saved = jobRepository.save(job);

        run.setStatus(RunStatus.EVALUATING);
        runRepository.save(run);

        return EvalJobView.from(saved);
    }

    /**
     * Lists the judge jobs registered for a run, oldest first.
     *
     * @throws IllegalArgumentException if the run does not exist (mapped to 404)
     */
    @Transactional(readOnly = true)
    public List<EvalJobView> getJobsForRun(UUID runId, TenantScope scope) {
        ExperimentRun run = runRepository
                .findById(runId, scope)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
        return jobRepository.findByRunOrderByCreatedAtAsc(run).stream()
                .map(EvalJobView::from)
                .toList();
    }
}
