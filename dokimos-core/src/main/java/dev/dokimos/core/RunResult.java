package dev.dokimos.core;

import java.util.List;

/**
 * Results from a single run of an experiment.
 * <p>
 * When an experiment is configured with multiple runs, each run produces
 * a {@code RunResult} containing the evaluation results for that run. The
 * parent {@link ExperimentResult} aggregates these across all runs.
 *
 * @param runIndex    the zero-based index of this run
 * @param itemResults results for each dataset example in this run
 */
public record RunResult(
        int runIndex,
        List<ItemResult> itemResults) {
    private static final double PRECISION_SCALE = 1_000_000.0;

    public RunResult {
        itemResults = itemResults != null ? List.copyOf(itemResults) : List.of();
    }

    private static double round(double value) {
        return Math.round(value * PRECISION_SCALE) / PRECISION_SCALE;
    }

    /**
     * Returns the total number of items evaluated in this run.
     *
     * @return the total count
     */
    public int totalCount() {
        return itemResults.size();
    }

    /**
     * Returns the number of items that passed all evaluations in this run.
     *
     * @return the pass count
     */
    public long passCount() {
        return itemResults.stream().filter(ItemResult::success).count();
    }

    /**
     * Returns the number of items that failed at least one evaluation in this run.
     *
     * @return the fail count
     */
    public long failCount() {
        return totalCount() - passCount();
    }

    /**
     * Returns the proportion of items that passed in this run.
     *
     * @return the pass rate between 0.0 and 1.0
     */
    public double passRate() {
        return round(totalCount() > 0 ? (double) passCount() / totalCount() : 0.0);
    }

    /**
     * Returns the average score for the specified evaluator across all items in
     * this run.
     *
     * @param evaluatorName the evaluator's name
     * @return the computed average score
     */
    public double averageScore(String evaluatorName) {
        return round(itemResults.stream()
                .flatMap(item -> item.evalResults().stream())
                .filter(res -> res.name().equals(evaluatorName))
                .mapToDouble(EvalResult::score)
                .average()
                .orElse(0.0));
    }
}
