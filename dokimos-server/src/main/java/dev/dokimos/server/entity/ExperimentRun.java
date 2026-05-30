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
@Table(name = "experiment_runs")
public class ExperimentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "experiment_id", nullable = false)
    private Experiment experiment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> config;

    @Column(name = "name")
    private String name;

    @Column(name = "git_sha")
    private String gitSha;

    @Column(name = "git_branch")
    private String gitBranch;

    @Column(name = "triggered_by")
    private String triggeredBy;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "passed_count", nullable = false)
    private int passedCount;

    @Column(name = "pass_rate")
    private Double passRate;

    @Column(name = "total_tokens_in")
    private Long totalTokensIn;

    @Column(name = "total_tokens_out")
    private Long totalTokensOut;

    @Column(name = "total_cost_usd")
    private Double totalCostUsd;

    @Column(name = "avg_latency_ms")
    private Double avgLatencyMs;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_version_id")
    private DatasetVersion datasetVersion;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant completedAt;

    @OneToMany(mappedBy = "run")
    private List<ItemResult> items = new ArrayList<>();

    protected ExperimentRun() {}

    public ExperimentRun(Experiment experiment, Map<String, Object> config) {
        this.experiment = experiment;
        this.config = config;
        this.status = RunStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Experiment getExperiment() {
        return experiment;
    }

    public RunStatus getStatus() {
        return status;
    }

    public void setStatus(RunStatus status) {
        this.status = status;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public List<ItemResult> getItems() {
        return items;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGitSha() {
        return gitSha;
    }

    public void setGitSha(String gitSha) {
        this.gitSha = gitSha;
    }

    public String getGitBranch() {
        return gitBranch;
    }

    public void setGitBranch(String gitBranch) {
        this.gitBranch = gitBranch;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public int getPassedCount() {
        return passedCount;
    }

    public void setPassedCount(int passedCount) {
        this.passedCount = passedCount;
    }

    public Double getPassRate() {
        return passRate;
    }

    public void setPassRate(Double passRate) {
        this.passRate = passRate;
    }

    public Long getTotalTokensIn() {
        return totalTokensIn;
    }

    public void setTotalTokensIn(Long totalTokensIn) {
        this.totalTokensIn = totalTokensIn;
    }

    public Long getTotalTokensOut() {
        return totalTokensOut;
    }

    public void setTotalTokensOut(Long totalTokensOut) {
        this.totalTokensOut = totalTokensOut;
    }

    public Double getTotalCostUsd() {
        return totalCostUsd;
    }

    public void setTotalCostUsd(Double totalCostUsd) {
        this.totalCostUsd = totalCostUsd;
    }

    public Double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public void setAvgLatencyMs(Double avgLatencyMs) {
        this.avgLatencyMs = avgLatencyMs;
    }

    public DatasetVersion getDatasetVersion() {
        return datasetVersion;
    }

    public void setDatasetVersion(DatasetVersion datasetVersion) {
        this.datasetVersion = datasetVersion;
    }
}
