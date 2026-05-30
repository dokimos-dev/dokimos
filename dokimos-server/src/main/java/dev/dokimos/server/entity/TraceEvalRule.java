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
 * A per-project rule that enqueues an online judge evaluation when an ingested span matches. The match
 * is by span name or by an attribute key/value (see {@link TraceMatchType}). The rule carries the judge
 * configuration (evaluator name, criteria, score range, threshold) and the {@link LlmConnection} used to
 * call the judge. At most one rule per {@code (project, name)} pair.
 */
@Entity
@Table(
        name = "trace_eval_rules",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_trace_eval_rule_project_name",
                        columnNames = {"project_id", "name"}))
public class TraceEvalRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 32)
    private TraceMatchType matchType;

    @Column(name = "match_key")
    private String matchKey;

    @Column(name = "match_value", nullable = false, length = 512)
    private String matchValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    private LlmConnection connection;

    @Column(name = "evaluator_name", nullable = false)
    private String evaluatorName;

    @Column(nullable = false, columnDefinition = "text")
    private String criteria;

    @Column(name = "min_score", nullable = false)
    private double minScore;

    @Column(name = "max_score", nullable = false)
    private double maxScore;

    private Double threshold;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TraceEvalRule() {}

    public TraceEvalRule(
            UUID projectId,
            String name,
            TraceMatchType matchType,
            String matchValue,
            LlmConnection connection,
            String evaluatorName,
            String criteria) {
        Instant now = Instant.now();
        this.projectId = projectId;
        this.name = name;
        this.enabled = true;
        this.matchType = matchType;
        this.matchValue = matchValue;
        this.connection = connection;
        this.evaluatorName = evaluatorName;
        this.criteria = criteria;
        this.minScore = 0.0;
        this.maxScore = 1.0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Returns whether the given span matches this rule. For {@link TraceMatchType#SPAN_NAME} the span
     * name must equal the match value; for {@link TraceMatchType#ATTRIBUTE} the span attribute named by
     * the match key must be present and its string form must equal the match value.
     *
     * @param span the candidate span
     * @return true when the span satisfies the rule's match condition
     */
    public boolean matches(TraceSpan span) {
        if (matchType == TraceMatchType.SPAN_NAME) {
            return matchValue.equals(span.getName());
        }
        if (matchKey == null || span.getAttributes() == null) {
            return false;
        }
        Object value = span.getAttributes().get(matchKey);
        return value != null && matchValue.equals(String.valueOf(value));
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public TraceMatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(TraceMatchType matchType) {
        this.matchType = matchType;
    }

    public String getMatchKey() {
        return matchKey;
    }

    public void setMatchKey(String matchKey) {
        this.matchKey = matchKey;
    }

    public String getMatchValue() {
        return matchValue;
    }

    public void setMatchValue(String matchValue) {
        this.matchValue = matchValue;
    }

    public LlmConnection getConnection() {
        return connection;
    }

    public void setConnection(LlmConnection connection) {
        this.connection = connection;
    }

    public String getEvaluatorName() {
        return evaluatorName;
    }

    public void setEvaluatorName(String evaluatorName) {
        this.evaluatorName = evaluatorName;
    }

    public String getCriteria() {
        return criteria;
    }

    public void setCriteria(String criteria) {
        this.criteria = criteria;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Stamps the rule as just modified. */
    public void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }
}
