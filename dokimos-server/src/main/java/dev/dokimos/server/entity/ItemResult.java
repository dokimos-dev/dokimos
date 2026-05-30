package dev.dokimos.server.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "item_results")
public class ItemResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private ExperimentRun run;

    /**
     * The dataset item this result was evaluated against, or null for ad-hoc runs and legacy rows.
     * Lets a per-case run comparison pair items by stable id across executions.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_item_id")
    private DatasetItem datasetItem;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> input;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> expectedOutput;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> actualOutput;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "tokens_in")
    private Integer tokensIn;

    @Column(name = "tokens_out")
    private Integer tokensOut;

    @Column(name = "cost_usd")
    private Double costUsd;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "itemResult", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EvalResult> evalResults = new ArrayList<>();

    protected ItemResult() {}

    public ItemResult(
            ExperimentRun run,
            Map<String, Object> input,
            Map<String, Object> expectedOutput,
            Map<String, Object> actualOutput,
            Map<String, Object> metadata) {
        this.run = run;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.actualOutput = actualOutput;
        this.metadata = metadata;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public ExperimentRun getRun() {
        return run;
    }

    public DatasetItem getDatasetItem() {
        return datasetItem;
    }

    public void setDatasetItem(DatasetItem datasetItem) {
        this.datasetItem = datasetItem;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public Map<String, Object> getExpectedOutput() {
        return expectedOutput;
    }

    public Map<String, Object> getActualOutput() {
        return actualOutput;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Integer getTokensIn() {
        return tokensIn;
    }

    public void setTokensIn(Integer tokensIn) {
        this.tokensIn = tokensIn;
    }

    public Integer getTokensOut() {
        return tokensOut;
    }

    public void setTokensOut(Integer tokensOut) {
        this.tokensOut = tokensOut;
    }

    public Double getCostUsd() {
        return costUsd;
    }

    public void setCostUsd(Double costUsd) {
        this.costUsd = costUsd;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<EvalResult> getEvalResults() {
        return evalResults;
    }

    public void addEvalResult(EvalResult evalResult) {
        evalResults.add(evalResult);
        evalResult.setItemResult(this);
    }
}
