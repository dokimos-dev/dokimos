package dev.dokimos.server.service;

import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.server.config.TraceProperties;
import dev.dokimos.server.entity.LlmConnection;
import dev.dokimos.server.entity.TraceEvalJob;
import dev.dokimos.server.judge.JudgeCallException;
import dev.dokimos.server.judge.JudgeScorer;
import dev.dokimos.server.judge.OpenAiCompatibleJudge;
import dev.dokimos.server.service.TraceEvalJobTransactions.ClaimedJob;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background worker that drains the online trace eval queue. Each poll claims at most one pending job in
 * its own transaction, calls the judge outside any database transaction, and records the score (or a
 * sanitized failure with retry for transient errors) in its own transaction. Reuses the same judge
 * scoring machinery as the experiment-side {@link JudgeWorker}: poll-and-claim, REQUIRES_NEW tx
 * boundaries, retry ceiling, and credential-sanitizing error handling.
 */
@Component
public class TraceEvalWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(TraceEvalWorker.class);

    private final TraceEvalJobTransactions transactions;
    private final LlmCredentialService credentialService;
    private final TraceProperties properties;
    private final BiFunction<LlmConnection, String, JudgeLM> judgeFactory;

    @Autowired
    public TraceEvalWorker(
            TraceEvalJobTransactions transactions, LlmCredentialService credentialService, TraceProperties properties) {
        this(
                transactions,
                credentialService,
                properties,
                (connection, key) -> new OpenAiCompatibleJudge(connection.getBaseUrl(), connection.getModel(), key));
    }

    TraceEvalWorker(
            TraceEvalJobTransactions transactions,
            LlmCredentialService credentialService,
            TraceProperties properties,
            BiFunction<LlmConnection, String, JudgeLM> judgeFactory) {
        this.transactions = transactions;
        this.credentialService = credentialService;
        this.properties = properties;
        this.judgeFactory = judgeFactory;
    }

    /** Polls for and processes one job per cycle. The fixed delay is read from {@code dokimos.trace.eval.poll-interval-ms}. */
    @Scheduled(fixedDelayString = "${dokimos.trace.eval.poll-interval-ms:5000}")
    public void poll() {
        transactions.recoverStaleClaims(
                Instant.now().minusMillis(properties.getEval().getClaimTimeoutMs()));
        Optional<ClaimedJob> claimed =
                transactions.claimNextJob(properties.getEval().getMaxAttempts());
        claimed.ifPresent(this::process);
    }

    private void process(ClaimedJob claimed) {
        TraceEvalJob job = claimed.job();
        UUID jobId = job.getId();
        int maxAttempts = properties.getEval().getMaxAttempts();
        try {
            JudgeScorer scorer = buildScorer(job);
            // The expected output is empty: an online trace eval is reference-free, scoring the span's
            // derived output against the rule's criteria. Only ACTUAL_OUTPUT (and INPUT when present) is
            // surfaced to the judge prompt.
            JudgeScorer.ScoreOutcome outcome =
                    scorer.score(nullToEmpty(claimed.inputText()), "", nullToEmpty(claimed.outputText()));
            transactions.markSucceeded(jobId, outcome.score(), outcome.success(), outcome.reason());
            LOGGER.info("Trace eval job {} succeeded", jobId);
        } catch (JudgeCallException e) {
            LOGGER.warn("Trace eval job {} failed with HTTP status {}: {}", jobId, e.getHttpStatus(), e.getMessage());
            transactions.recordFailure(jobId, sanitize(e.getMessage()), e.isRetryable(), maxAttempts);
        } catch (Exception e) {
            LOGGER.error("Trace eval job {} failed", jobId, e);
            transactions.recordFailure(jobId, sanitize(e.getMessage()), false, maxAttempts);
        }
    }

    private JudgeScorer buildScorer(TraceEvalJob job) {
        LlmConnection connection = job.getConnection();
        String key = credentialService.resolveKey(connection);
        JudgeLM judge = judgeFactory.apply(connection, key);
        return new JudgeScorer(
                judge,
                job.getCriteria(),
                List.of(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT),
                job.getMinScore(),
                job.getMaxScore(),
                job.getThreshold());
    }

    /**
     * Scrubs anything resembling an authorization header or bearer token from an error string before it
     * is persisted and exposed over the API, and caps its length, so a low-level failure cannot leak
     * credential material into {@code trace_eval_jobs.last_error}.
     */
    private static String sanitize(String message) {
        if (message == null) {
            return null;
        }
        String scrubbed = message.replaceAll("(?i)(authorization|bearer)\\s*\\S+", "$1 [redacted]");
        return scrubbed.length() > 500 ? scrubbed.substring(0, 500) : scrubbed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
