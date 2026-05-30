package dev.dokimos.server.service;

import dev.dokimos.server.entity.TraceEvalJob;
import dev.dokimos.server.entity.TraceEvalJobStatus;
import dev.dokimos.server.repository.TraceEvalJobRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional steps of the trace eval worker, each in its own committed transaction so the worker
 * can make the LLM HTTP call between them without holding a database transaction open. Splitting these
 * out of the worker also makes {@code REQUIRES_NEW} boundaries effective, since self-invocation would
 * otherwise bypass the proxy. Mirrors {@link JudgeJobTransactions}.
 */
@Component
public class TraceEvalJobTransactions {

    private final TraceEvalJobRepository jobRepository;

    public TraceEvalJobTransactions(TraceEvalJobRepository jobRepository) {
        this.jobRepository = jobRepository;
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
     * Claims the oldest pending job below the retry ceiling: locks its row, marks it CLAIMED, stamps the
     * claim time, and increments the attempt count. Commits before returning so the lock is not held
     * during the HTTP work that follows. The connection and span are initialized while the session is
     * open since the worker reads them after the transaction closes.
     *
     * @param maxAttempts the retry ceiling
     * @return a snapshot of the claimed job, or empty if none is available
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedJob> claimNextJob(int maxAttempts) {
        Optional<TraceEvalJob> claimed = jobRepository.claimNext(maxAttempts);
        return claimed.map(job -> {
            job.setStatus(TraceEvalJobStatus.CLAIMED);
            job.setClaimedAt(Instant.now());
            job.setAttemptCount(job.getAttemptCount() + 1);
            jobRepository.save(job);
            Hibernate.initialize(job.getConnection());
            String output = job.getSpan().getOutputText();
            String input = job.getSpan().getInputText();
            return new ClaimedJob(job, input, output);
        });
    }

    /**
     * Marks a job SUCCEEDED and records its score.
     *
     * @param jobId   the job that finished scoring
     * @param score   the numeric score
     * @param success the pass/fail decision
     * @param reason  the judge's reasoning
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(UUID jobId, double score, boolean success, String reason) {
        TraceEvalJob job = jobRepository.getReferenceById(jobId);
        job.setStatus(TraceEvalJobStatus.SUCCEEDED);
        job.setScore(score);
        job.setSuccess(success);
        job.setReason(reason);
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);
    }

    /**
     * Records a failure. When the failure is retryable and the attempt ceiling has not been reached, the
     * job is returned to PENDING for a later poll; otherwise it is marked FAILED.
     *
     * @param jobId       the job that failed
     * @param error       the error message to record
     * @param retryable   whether the failure is worth retrying
     * @param maxAttempts the retry ceiling
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID jobId, String error, boolean retryable, int maxAttempts) {
        TraceEvalJob job = jobRepository.getReferenceById(jobId);
        job.setLastError(error);
        if (retryable && job.getAttemptCount() < maxAttempts) {
            job.setStatus(TraceEvalJobStatus.PENDING);
            jobRepository.save(job);
            return;
        }
        job.setStatus(TraceEvalJobStatus.FAILED);
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);
    }

    /** A claimed job paired with the span text snapshots the worker scores after the transaction closes. */
    public record ClaimedJob(TraceEvalJob job, String inputText, String outputText) {}
}
