package dev.dokimos.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * A unit of server-side scoring work: score every not-yet-evaluated item of a run with a single
 * evaluator configuration, using a registered {@link LlmConnection}. A background worker claims a
 * pending job, scores items in seek-keyed pages, and finalizes the run when the job completes. At most
 * one job exists per {@code (run, evaluatorName)} pair.
 */
@Entity
@Table(
        name = "eval_jobs",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_eval_job_run_evaluator",
                        columnNames = {"run_id", "evaluator_name"}))
public class EvalJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private ExperimentRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    private LlmConnection connection;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvalJobStatus status;

    @Column(name = "evaluator_name", nullable = false)
    private String evaluatorName;

    @Column(nullable = false, columnDefinition = "text")
    private String criteria;

    @Column(name = "evaluation_params", nullable = false)
    private String evaluationParams;

    @Column(name = "min_score", nullable = false)
    private double minScore;

    @Column(name = "max_score", nullable = false)
    private double maxScore;

    private Double threshold;

    @Column(name = "last_item_id")
    private UUID lastItemId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected EvalJob() {}

    public EvalJob(ExperimentRun run, LlmConnection connection, String evaluatorName, String criteria) {
        this.run = run;
        this.connection = connection;
        this.evaluatorName = evaluatorName;
        this.criteria = criteria;
        this.status = EvalJobStatus.PENDING;
        this.minScore = 0.0;
        this.maxScore = 1.0;
        this.attemptCount = 0;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public ExperimentRun getRun() {
        return run;
    }

    public LlmConnection getConnection() {
        return connection;
    }

    public EvalJobStatus getStatus() {
        return status;
    }

    public void setStatus(EvalJobStatus status) {
        this.status = status;
    }

    public String getEvaluatorName() {
        return evaluatorName;
    }

    public String getCriteria() {
        return criteria;
    }

    public String getEvaluationParams() {
        return evaluationParams;
    }

    public void setEvaluationParams(String evaluationParams) {
        this.evaluationParams = evaluationParams;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public double getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(double maxScore) {
        this.maxScore = maxScore;
    }

    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public UUID getLastItemId() {
        return lastItemId;
    }

    public void setLastItemId(UUID lastItemId) {
        this.lastItemId = lastItemId;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
