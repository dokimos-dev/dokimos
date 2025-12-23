package dev.dokimos.core;

import java.util.List;

/**
 * Assertion utilities for evaluation-based testing.
 * <p>
 * Evaluates test cases against configured evaluators and throws AssertionError
 * if any evaluation fails to meet its threshold.
 */
public class Assertions {

    private Assertions() {
    }

    /**
     * Asserts that the test case passes all evaluators.
     *
     * @param testCase   the test case to evaluate
     * @param evaluators the evaluators to run
     * @throws AssertionError if any evaluator score falls below its threshold
     */
    public static void assertEval(EvalTestCase testCase, List<Evaluator> evaluators) {
        for (var evaluator : evaluators) {
            var result = evaluator.evaluate(testCase);
            if (!result.success()) {
                throw new AssertionError(
                        "Evaluation '%s' failed: score=%.2f (threshold=%.2f), reason=%s"
                                .formatted(result.name(), result.score(), evaluator.threshold(), result.reason())
                );
            }
        }
    }

    /**
     * Asserts that the test case passes all evaluators.
     *
     * @param testCase   the test case to evaluate
     * @param evaluators the evaluators to run
     * @throws AssertionError if any evaluator score falls below its threshold
     */
    public static void assertEval(EvalTestCase testCase, Evaluator... evaluators) {
        assertEval(testCase, List.of(evaluators));
    }

}
