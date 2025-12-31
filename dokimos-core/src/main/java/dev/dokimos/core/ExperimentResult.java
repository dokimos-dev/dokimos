package dev.dokimos.core;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aggregated results from an experiment.
 * <p>
 * When an experiment is run multiple times, this class aggregates the results
 * across all runs. Methods like {@link #averageScore(String)} and
 * {@link #passRate()}
 * return values averaged across all runs.
 *
 * @param name        the experiment name
 * @param description the experiment description
 * @param metadata    experiment metadata
 * @param runResults  results for each run of the experiment
 */
public record ExperimentResult(
        String name,
        String description,
        Map<String, Object> metadata,
        List<RunResult> runResults) {
    private static final double PRECISION_SCALE = 1_000_000.0; // 6 decimal places

    public ExperimentResult {
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        runResults = runResults != null ? List.copyOf(runResults) : List.of();
    }

    private static double round(double value) {
        return Math.round(value * PRECISION_SCALE) / PRECISION_SCALE;
    }

    /**
     * Returns all item results across all runs.
     * <p>
     * For single-run experiments, this returns the same results as accessing
     * the first run's item results directly.
     *
     * @return all item results flattened across runs
     */
    public List<ItemResult> itemResults() {
        return runResults.stream()
                .flatMap(run -> run.itemResults().stream())
                .toList();
    }

    /**
     * Returns the individual run results for detailed analysis.
     *
     * @return list of run results
     */
    public List<RunResult> runs() {
        return runResults;
    }

    /**
     * Returns the number of runs performed.
     *
     * @return the run count
     */
    public int runCount() {
        return runResults.size();
    }

    /**
     * Returns the total number of items evaluated per run.
     * <p>
     * This returns the count from the first run. All runs evaluate
     * the same dataset, so this value is consistent across runs.
     *
     * @return the total count per run
     */
    public int totalCount() {
        return runResults.isEmpty() ? 0 : runResults.get(0).totalCount();
    }

    /**
     * Returns the average number of items that passed across all runs.
     *
     * @return the average pass count
     */
    public double passCount() {
        return round(runResults.stream()
                .mapToLong(RunResult::passCount)
                .average()
                .orElse(0.0));
    }

    /**
     * Returns the average number of items that failed across all runs.
     *
     * @return the average fail count
     */
    public double failCount() {
        return round(totalCount() - passCount());
    }

    /**
     * Returns the average pass rate across all runs.
     *
     * @return the pass rate between 0.0 and 1.0
     */
    public double passRate() {
        return round(runResults.stream()
                .mapToDouble(RunResult::passRate)
                .average()
                .orElse(0.0));
    }

    /**
     * Returns the average score for the specified evaluator across all runs.
     * <p>
     * This first computes the average score within each run, then averages
     * those values across all runs.
     *
     * @param evaluatorName the evaluator's name
     * @return the computed average score
     */
    public double averageScore(String evaluatorName) {
        return round(runResults.stream()
                .mapToDouble(run -> run.averageScore(evaluatorName))
                .average()
                .orElse(0.0));
    }

    /**
     * Returns the sample standard deviation of scores for the specified
     * evaluator across runs.
     * <p>
     * This measures how much the average score varies between runs. A high standard
     * deviation suggests instability in your task or evaluator outputs.
     * <p>
     * Uses sample standard deviation (N-1 denominator) since runs represent a
     * sample of potential outcomes, not the complete population.
     * <p>
     * For single-run experiments, this returns 0.0.
     *
     * @param evaluatorName the evaluator's name
     * @return the standard deviation, or 0.0 for single-run experiments
     */
    public double scoreStdDev(String evaluatorName) {
        if (runResults.size() <= 1) {
            return 0.0;
        }

        double[] scores = runResults.stream()
                .mapToDouble(run -> run.averageScore(evaluatorName))
                .toArray();

        double mean = 0.0;
        for (double score : scores) {
            mean += score;
        }
        mean /= scores.length;

        double sumSquaredDiffs = 0.0;
        for (double score : scores) {
            double diff = score - mean;
            sumSquaredDiffs += diff * diff;
        }

        return round(Math.sqrt(sumSquaredDiffs / (scores.length - 1)));
    }

    /**
     * Returns the names of all evaluators used in this experiment.
     *
     * @return set of evaluator names
     */
    public Set<String> evaluatorNames() {
        return itemResults().stream()
                .flatMap(item -> item.evalResults().stream())
                .map(EvalResult::name)
                .collect(Collectors.toSet());
    }
}
