package dev.dokimos.core;

import java.util.List;
import java.util.Map;

/**
 * Aggregated results from an experiment run.
 *
 * @param name        the experiment name
 * @param description the experiment description
 * @param metadata    experiment metadata
 * @param itemResults results for each dataset item
 */
public record ExperimentResult(
        String name,
        String description,
        Map<String, Object> metadata,
        List<ItemResult> itemResults
) {
    public ExperimentResult {
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        itemResults = itemResults != null ? List.copyOf(itemResults) : List.of();
    }

    /**
     * Returns the total number of items evaluated.
     *
     * @return the total count
     */
    public int totalCount() {
        return itemResults.size();
    }

    /**
     * Returns the number of items that passed all evaluations.
     *
     * @return the pass count
     */
    public long passCount() {
        return itemResults.stream().filter(ItemResult::success).count();
    }

    /**
     * Returns the number of items that failed at least one evaluation.
     *
     * @return the fail count
     */
    public long failCount() {
        return totalCount() - passCount();
    }

    /**
     * Returns the proportion of items that passed.
     *
     * @return the pass rate between 0.0 and 1.0
     */
    public double passRate() {
        return totalCount() > 0 ? (double) passCount() / totalCount() : 0.0;
    }

    /**
     * Returns the average score for the specified evaluator across all results.
     *
     * @param evaluatorName the evaluator's name
     * @return the computed average score
     */
    public double averageScore(String evaluatorName) {
        return itemResults.stream()
                .flatMap(item -> item.evalResults().stream())
                .filter(res -> res.name().equals(evaluatorName))
                .mapToDouble(EvalResult::score)
                .average()
                .orElse(0.0);
    }

}
