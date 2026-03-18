package dev.dokimos.mcp.store;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Persistent record of a single evaluation run.
 *
 * @param id            unique run identifier
 * @param timestamp     when the run completed
 * @param experimentName name given to the experiment
 * @param datasetName   name of the dataset used
 * @param datasetPath   filesystem path to the dataset
 * @param modelConfig   model name, temperature, etc.
 * @param passRate      overall pass rate (0.0 to 1.0)
 * @param totalCount    number of examples evaluated
 * @param passCount     number of passing examples
 * @param failCount     number of failing examples
 * @param averageScores per evaluator average scores
 * @param items         per example details
 */
public record RunRecord(
        String id,
        Instant timestamp,
        String experimentName,
        String datasetName,
        String datasetPath,
        Map<String, Object> modelConfig,
        double passRate,
        int totalCount,
        int passCount,
        int failCount,
        Map<String, Double> averageScores,
        List<ItemDetail> items) {

    /**
     * Detail for a single evaluated example.
     *
     * @param input          the input query
     * @param expectedOutput the expected answer
     * @param actualOutput   the model's answer
     * @param success        whether all evaluators passed
     * @param evaluations    per evaluator results
     */
    public record ItemDetail(
            String input, String expectedOutput, String actualOutput, boolean success, List<EvalDetail> evaluations) {}

    /**
     * Single evaluator result for one example.
     *
     * @param evaluator evaluator name
     * @param score     numeric score
     * @param success   pass or fail
     * @param reason    explanation
     */
    public record EvalDetail(String evaluator, double score, boolean success, String reason) {}
}
