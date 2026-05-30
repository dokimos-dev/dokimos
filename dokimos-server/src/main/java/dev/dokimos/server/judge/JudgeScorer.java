package dev.dokimos.server.judge;

import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.JudgeLM;
import java.util.List;

/**
 * Drives a single judge scoring: builds the prompt from a criteria and the selected parameters, calls
 * the underlying {@link JudgeLM}, and parses the response. A failed parse yields a non-successful
 * outcome with the failure reason rather than throwing; an HTTP failure propagates as a
 * {@link JudgeCallException} so the worker can apply retry logic.
 */
public final class JudgeScorer {

    private final JudgeLM judge;
    private final String criteria;
    private final List<EvalTestCaseParam> params;
    private final double minScore;
    private final double maxScore;
    private final Double threshold;

    public JudgeScorer(
            JudgeLM judge,
            String criteria,
            List<EvalTestCaseParam> params,
            double minScore,
            double maxScore,
            Double threshold) {
        this.judge = judge;
        this.criteria = criteria;
        this.params = List.copyOf(params);
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.threshold = threshold;
    }

    /**
     * Scores one item.
     *
     * @param input          the rendered input value
     * @param expectedOutput the rendered expected output value
     * @param actualOutput   the rendered actual output value
     * @return the score, reason, and pass/fail decision
     * @throws JudgeCallException if the underlying judge call fails
     */
    public ScoreOutcome score(String input, String expectedOutput, String actualOutput) {
        String prompt =
                JudgePromptBuilder.build(criteria, params, minScore, maxScore, input, expectedOutput, actualOutput);
        String response = judge.generate(prompt);
        JudgeResponseParser.ParsedScore parsed = JudgeResponseParser.parse(response);
        if (!parsed.parsed()) {
            return new ScoreOutcome(0.0, parsed.reason(), false);
        }
        boolean success = threshold == null || parsed.score() >= threshold;
        return new ScoreOutcome(parsed.score(), parsed.reason(), success);
    }

    /** The result of scoring one item: a numeric score, the judge's reasoning, and the pass decision. */
    public record ScoreOutcome(double score, String reason, boolean success) {}
}
