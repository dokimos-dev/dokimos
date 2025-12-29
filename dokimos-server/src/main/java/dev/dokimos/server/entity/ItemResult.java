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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "item_results")
public class ItemResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private ExperimentRun run;

    @Column(columnDefinition = "text", nullable = false)
    private String input;

    @Column(columnDefinition = "text")
    private String expectedOutput;

    @Column(columnDefinition = "text", nullable = false)
    private String actualOutput;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "itemResult", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EvalResult> evalResults = new ArrayList<>();

    protected ItemResult() {
    }

    public ItemResult(ExperimentRun run, String input, String expectedOutput, String actualOutput,
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

    public String getInput() {
        return input;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public String getActualOutput() {
        return actualOutput;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
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
