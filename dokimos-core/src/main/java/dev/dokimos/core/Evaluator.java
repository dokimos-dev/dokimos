package dev.dokimos.core;

/**
 * Evaluates test cases and produces scored results.
 */
public interface Evaluator {
    /**
     * Evaluates the test case and returns a scored result.
     *
     * @param testCase the test case to evaluate
     * @return the evaluation result
     */
    EvalResult evaluate(EvalTestCase testCase);

    /**
     * Returns the evaluator name.
     *
     * @return the evaluator name
     */
    String name();

    /**
     * Returns the minimum score threshold for success.
     *
     * @return the threshold value
     */
    double threshold();
}