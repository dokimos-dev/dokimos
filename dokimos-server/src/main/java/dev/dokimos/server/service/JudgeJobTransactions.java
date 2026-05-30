package dev.dokimos.server.service;

import dev.dokimos.server.entity.EvalJob;
import dev.dokimos.server.entity.EvalJobStatus;
import dev.dokimos.server.entity.EvalResult;
import dev.dokimos.server.entity.ItemResult;
import dev.dokimos.server.repository.EvalJobRepository;
import dev.dokimos.server.repository.EvalResultRepository;
import dev.dokimos.server.repository.ItemResultRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional steps of the judge worker, each in its own committed transaction so the worker can
 * make the LLM HTTP call between them without holding a database transaction open. Splitting these out
 * of the worker also makes {@code REQUIRES_NEW} boundaries effective, since self-invocation would
 * otherwise bypass the proxy.
 */
@Component
public class JudgeJobTransactions {

    private final EvalJobRepository jobRepository;
    private final EvalResultRepository evalResultRepository;
    private final ItemResultRepository itemResultRepository;
    private final RunService runService;

    public JudgeJobTransactions(
            EvalJobRepository jobRepository,
            EvalResultRepository evalResultRepository,
            ItemResultRepository itemResultRepository,
            RunService runService) {
        this.jobRepository = jobRepository;
        this.evalResultRepository = evalResultRepository;
        this.itemResultRepository = itemResultRepository;
        this.runService = runService;
    }

    /**
     * Returns jobs orphaned by a crashed worker (claimed before the cutoff and never finished) to
     * PENDING so the next poll can reclaim them. The attempt count is left as-is, so the retry ceiling
     * still bounds reclaims.
     *
     * @param cutoff jobs claimed before this instant are requeued
     * @return the number of jobs requeued
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverStaleClaims(Instant cutoff) {
        return jobRepository.requeueStaleClaims(cutoff);
    }

    /**
     * Claims the oldest pending job below the retry ceiling: locks its row, marks it CLAIMED, stamps
     * the claim time, and increments the attempt count. Commits before returning so the lock is not
     * held during the HTTP work that follows.
     *
     * @param maxAttempts the retry ceiling
     * @return the claimed job, or empty if none is available
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<EvalJob> claimNextJob(int maxAttempts) {
        Optional<EvalJob> claimed = jobRepository.claimNext(maxAttempts);
        claimed.ifPresent(job -> {
            job.setStatus(EvalJobStatus.CLAIMED);
            job.setClaimedAt(Instant.now());
            job.setAttemptCount(job.getAttemptCount() + 1);
            jobRepository.save(job);
        });
        return claimed;
    }

    /**
     * Reads a seek-keyed page of items that have no result yet for the job's evaluator. Returns plain
     * snapshots so the worker can score them after the transaction closes without touching detached
     * entities.
     *
     * @param runId         the run whose items to scan
     * @param evaluatorName the evaluator whose results gate the scan
     * @param afterId       the seek cursor; the all-zero UUID for the first page
     * @param pageSize      the maximum number of items to return
     * @return the next page of unevaluated item snapshots, ordered by id
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<ItemSnapshot> loadUnevaluatedPage(UUID runId, String evaluatorName, UUID afterId, int pageSize) {
        return itemResultRepository
                .findItemsNotYetEvaluated(runId, evaluatorName, afterId, Pageable.ofSize(pageSize))
                .stream()
                .map(item -> new ItemSnapshot(
                        item.getId(), item.getInput(), item.getExpectedOutput(), item.getActualOutput()))
                .toList();
    }

    /**
     * Persists a page of eval results and advances the job's seek cursor in one transaction.
     *
     * @param jobId      the job being processed
     * @param results    the eval results to attach to their item results
     * @param lastItemId the id of the last item scored in this page
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistPage(UUID jobId, List<ScoredResult> results, UUID lastItemId) {
        for (ScoredResult scored : results) {
            ItemResult item = itemResultRepository.getReferenceById(scored.itemId());
            item.addEvalResult(scored.evalResult());
            evalResultRepository.save(scored.evalResult());
        }
        EvalJob job = jobRepository.getReferenceById(jobId);
        job.setLastItemId(lastItemId);
        jobRepository.save(job);
    }

    /**
     * Marks the job SUCCEEDED and finalizes its run (re-materializes counts, moves the run to SUCCESS).
     *
     * @param jobId the job that finished scoring
     * @param runId the run to finalize
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(UUID jobId, UUID runId) {
        EvalJob job = jobRepository.getReferenceById(jobId);
        job.setStatus(EvalJobStatus.SUCCEEDED);
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);
        runService.finalizeEvaluatedRun(runId);
    }

    /**
     * Records a failure. When the failure is retryable and the attempt ceiling has not been reached,
     * the job is returned to PENDING for a later poll; otherwise it is marked FAILED.
     *
     * @param jobId       the job that failed
     * @param error       the error message to record
     * @param retryable   whether the failure is worth retrying
     * @param maxAttempts the retry ceiling
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID jobId, String error, boolean retryable, int maxAttempts) {
        EvalJob job = jobRepository.getReferenceById(jobId);
        job.setLastError(error);
        if (retryable && job.getAttemptCount() < maxAttempts) {
            job.setStatus(EvalJobStatus.PENDING);
            jobRepository.save(job);
            return;
        }
        job.setStatus(EvalJobStatus.FAILED);
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);
        // A terminally failed job must not leave its run stuck in EVALUATING.
        runService.failEvaluatedRun(job.getRun().getId());
    }

    /** Pairs an eval result with the id of the item result it belongs to, for batch persistence. */
    public record ScoredResult(UUID itemId, EvalResult evalResult) {}

    /** A detached view of an item result carrying only the fields the judge prompt needs. */
    public record ItemSnapshot(
            UUID id, Map<String, Object> input, Map<String, Object> expectedOutput, Map<String, Object> actualOutput) {}
}
