package dev.dokimos.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Base class for implementing concrete evaluators.
 * <p>
 * Provides parameter validation before evaluation and async evaluation support.
 */
public abstract class BaseEvaluator implements Evaluator {

    protected final String name;
    protected final double threshold;
    protected final List<EvalTestCaseParam> evaluationParams;

    /**
     * Constructs the base evaluator.
     *
     * @param name             the evaluator's name
     * @param threshold        the success threshold
     * @param evaluationParams the required test case parameters
     */
    protected BaseEvaluator(String name, double threshold, List<EvalTestCaseParam> evaluationParams) {
        this.name = name;
        this.threshold = threshold;
        this.evaluationParams = List.copyOf(evaluationParams);
    }

    @Override
    public final EvalResult evaluate(EvalTestCase testCase) {
        validateEvalParams(testCase);
        return runEvaluation(testCase);
    }

    private void validateEvalParams(EvalTestCase testCase) {
        for (EvalTestCaseParam param : evaluationParams) {
            Object value = switch (param) {
                case INPUT -> testCase.input();
                case ACTUAL_OUTPUT -> testCase.actualOutput();
                case EXPECTED_OUTPUT -> testCase.expectedOutput();
            };
            if (value == null) {
                throw new IllegalArgumentException(
                        String.format("Metric '%s' requires '%s' but it was null in the test case.", name, param)
                );
            }
        }
    }

    /**
     * Performs the evaluation logic.
     * Subclasses implement this to define specific evaluation behavior.
     *
     * @param testCase the test case to evaluate
     * @return the evaluation result
     */
    protected abstract EvalResult runEvaluation(EvalTestCase testCase);

    @Override
    public String name() {
        return name;
    }

    @Override
    public double threshold() {
        return threshold;
    }

    /**
     * Evaluates the test case asynchronously using the common fork-join pool.
     * <p>
     * This method allows for non-blocking evaluation, which is useful when
     * processing multiple test cases concurrently.
     *
     * @param testCase the test case to evaluate
     * @return a CompletableFuture that will complete with the evaluation result
     */
    public CompletableFuture<EvalResult> evaluateAsync(EvalTestCase testCase) {
        return CompletableFuture.supplyAsync(() -> evaluate(testCase));
    }

    /**
     * Evaluates the test case asynchronously using the provided executor.
     *
     * @param testCase the test case to evaluate
     * @param executor the executor to use for async execution
     * @return a CompletableFuture that will complete with the evaluation result
     */
    public CompletableFuture<EvalResult> evaluateAsync(EvalTestCase testCase, ExecutorService executor) {
        return CompletableFuture.supplyAsync(() -> evaluate(testCase), executor);
    }

}
