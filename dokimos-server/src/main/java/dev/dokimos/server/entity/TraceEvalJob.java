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
 * A unit of online scoring work: score one ingested {@link TraceSpan}'s derived output against one
 * {@link TraceEvalRule}'s judge configuration. A background worker claims a pending job, calls the judge,
 * and records the score. The configuration is snapshotted onto the job at enqueue time so a later rule
 * edit does not change in-flight work. At most one job exists per {@code (span, rule)} pair.
 */
@Entity
@Table(
        name = "trace_eval_jobs",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_trace_eval_job_span_rule",
                        columnNames = {"span_pk", "rule_id"}))
public class TraceEvalJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "span_pk", nullable = false)
    private TraceSpan span;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private TraceEvalRule rule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    private LlmConnection connection;

    @Column(name = "tenant_id")
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TraceEvalJobStatus status;

    @Column(name = "evaluator_name", nullable = false)
    private String evaluatorName;

    @Column(nullable = false, columnDefinition = "text")
    private String criteria;

    @Column(name = "min_score", nullable = false)
    private double minScore;

    @Column(name = "max_score", nullable = false)
    private double maxScore;

    private Double threshold;

    private Double score;

    private Boolean success;

    @Column(columnDefinition = "text")
    private String reason;

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

    protected TraceEvalJob() {}

    public TraceEvalJob(TraceSpan span, TraceEvalRule rule) {
        this.span = span;
        this.rule = rule;
        this.connection = rule.getConnection();
        this.tenantId = span.getTrace() != null ? span.getTrace().getTenantId() : null;
        this.status = TraceEvalJobStatus.PENDING;
        this.evaluatorName = rule.getEvaluatorName();
        this.criteria = rule.getCriteria();
        this.minScore = rule.getMinScore();
        this.maxScore = rule.getMaxScore();
        this.threshold = rule.getThreshold();
        this.attemptCount = 0;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public TraceSpan getSpan() {
        return span;
    }

    public TraceEvalRule getRule() {
        return rule;
    }

    public LlmConnection getConnection() {
        return connection;
    }

    public String getTenantId() {
        return tenantId;
    }

    public TraceEvalJobStatus getStatus() {
        return status;
    }

    public void setStatus(TraceEvalJobStatus status) {
        this.status = status;
    }

    public String getEvaluatorName() {
        return evaluatorName;
    }

    public String getCriteria() {
        return criteria;
    }

    public double getMinScore() {
        return minScore;
    }

    public double getMaxScore() {
        return maxScore;
    }

    public Double getThreshold() {
        return threshold;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
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
