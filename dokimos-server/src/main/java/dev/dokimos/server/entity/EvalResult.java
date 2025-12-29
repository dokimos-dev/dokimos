package dev.dokimos.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "eval_results")
public class EvalResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_result_id", nullable = false)
    private ItemResult itemResult;

    @Column(nullable = false)
    private String evaluatorName;

    @Column(nullable = false)
    private double score;

    private Double threshold;

    @Column(nullable = false)
    private boolean success;

    @Column(columnDefinition = "text")
    private String reason;

    protected EvalResult() {
    }

    public EvalResult(String evaluatorName, double score, Double threshold, boolean success, String reason) {
        this.evaluatorName = evaluatorName;
        this.score = score;
        this.threshold = threshold;
        this.success = success;
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public ItemResult getItemResult() {
        return itemResult;
    }

    void setItemResult(ItemResult itemResult) {
        this.itemResult = itemResult;
    }

    public String getEvaluatorName() {
        return evaluatorName;
    }

    public double getScore() {
        return score;
    }

    public Double getThreshold() {
        return threshold;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getReason() {
        return reason;
    }
}
