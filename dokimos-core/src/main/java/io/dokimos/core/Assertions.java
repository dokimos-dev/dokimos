package io.dokimos.core;

import java.util.List;

public class Assertions {

    private Assertions() {}

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

    public static void assertEval(EvalTestCase testCase, Evaluator... evaluators) {
        assertEval(testCase, List.of(evaluators));
    }

}
