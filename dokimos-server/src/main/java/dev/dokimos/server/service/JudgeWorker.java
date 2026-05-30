package dev.dokimos.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.server.config.JudgeProperties;
import dev.dokimos.server.entity.EvalJob;
import dev.dokimos.server.entity.EvalResult;
import dev.dokimos.server.entity.LlmConnection;
import dev.dokimos.server.judge.JudgeCallException;
import dev.dokimos.server.judge.JudgeScorer;
import dev.dokimos.server.judge.OpenAiCompatibleJudge;
import dev.dokimos.server.service.JudgeJobTransactions.ItemSnapshot;
import dev.dokimos.server.service.JudgeJobTransactions.ScoredResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Background worker that drains the judge job queue. Each poll claims at most one pending job in its
 * own transaction, scores the run's not-yet-evaluated items in seek-keyed pages with the LLM call made
 * outside any database transaction, persists each page of results in its own transaction, and on
 * completion finalizes the run. Failures are recorded with retry for transient errors and a terminal
 * FAILED for non-retryable ones or once the attempt ceiling is reached.
 */
@Component
public class JudgeWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(JudgeWorker.class);
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private final JudgeJobTransactions transactions;
    private final LlmCredentialService credentialService;
    private final JudgeProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BiFunction<LlmConnection, String, JudgeLM> judgeFactory;

    @org.springframework.beans.factory.annotation.Autowired
    public JudgeWorker(
            JudgeJobTransactions transactions, LlmCredentialService credentialService, JudgeProperties properties) {
        this(
                transactions,
                credentialService,
                properties,
                (connection, key) -> new OpenAiCompatibleJudge(connection.getBaseUrl(), connection.getModel(), key));
    }

    JudgeWorker(
            JudgeJobTransactions transactions,
            LlmCredentialService credentialService,
            JudgeProperties properties,
            BiFunction<LlmConnection, String, JudgeLM> judgeFactory) {
        this.transactions = transactions;
        this.credentialService = credentialService;
        this.properties = properties;
        this.judgeFactory = judgeFactory;
    }

    /** Polls for and processes one job per cycle. The fixed delay is read from {@code dokimos.judge.poll-interval-ms}. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${dokimos.judge.poll-interval-ms:5000}")
    public void poll() {
        Optional<EvalJob> claimed = transactions.claimNextJob(properties.getMaxAttempts());
        claimed.ifPresent(this::process);
    }

    private void process(EvalJob job) {
        UUID jobId = job.getId();
        UUID runId = job.getRun().getId();
        String evaluatorName = job.getEvaluatorName();
        try {
            JudgeScorer scorer = buildScorer(job);
            UUID cursor = job.getLastItemId() != null ? job.getLastItemId() : ZERO_UUID;
            int pageSize = properties.getPageSize();

            List<ItemSnapshot> page = transactions.loadUnevaluatedPage(runId, evaluatorName, cursor, pageSize);
            while (!page.isEmpty()) {
                List<ScoredResult> scored = new ArrayList<>(page.size());
                UUID lastItemId = cursor;
                for (ItemSnapshot item : page) {
                    JudgeScorer.ScoreOutcome outcome = scorer.score(
                            render(item.input()), render(item.expectedOutput()), render(item.actualOutput()));
                    EvalResult result = new EvalResult(
                            evaluatorName, outcome.score(), job.getThreshold(), outcome.success(), outcome.reason());
                    scored.add(new ScoredResult(item.id(), result));
                    lastItemId = item.id();
                }
                transactions.persistPage(jobId, scored, lastItemId);
                cursor = lastItemId;
                page = transactions.loadUnevaluatedPage(runId, evaluatorName, cursor, pageSize);
            }

            transactions.markSucceeded(jobId, runId);
            LOGGER.info("Judge job {} succeeded for run {}", jobId, runId);
        } catch (JudgeCallException e) {
            LOGGER.warn("Judge job {} failed with HTTP status {}: {}", jobId, e.getHttpStatus(), e.getMessage());
            transactions.recordFailure(jobId, e.getMessage(), e.isRetryable(), properties.getMaxAttempts());
        } catch (Exception e) {
            LOGGER.error("Judge job {} failed", jobId, e);
            transactions.recordFailure(jobId, e.getMessage(), false, properties.getMaxAttempts());
        }
    }

    private JudgeScorer buildScorer(EvalJob job) {
        LlmConnection connection = job.getConnection();
        String key = credentialService.resolveKey(connection);
        JudgeLM judge = judgeFactory.apply(connection, key);
        return new JudgeScorer(
                judge,
                job.getCriteria(),
                parseParams(job.getEvaluationParams()),
                job.getMinScore(),
                job.getMaxScore(),
                job.getThreshold());
    }

    private List<EvalTestCaseParam> parseParams(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(EvalTestCaseParam::valueOf)
                .toList();
    }

    private String render(Map<String, Object> value) {
        if (value == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
