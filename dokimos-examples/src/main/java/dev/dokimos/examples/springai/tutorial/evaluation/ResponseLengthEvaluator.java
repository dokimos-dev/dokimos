package dev.dokimos.examples.springai.tutorial.evaluation;

import dev.dokimos.core.BaseEvaluator;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;

import java.util.List;

/**
 * Custom evaluator that checks if the response length is within acceptable bounds.
 *
 * <p>This is an example of a domain-specific evaluator that does not require
 * an LLM judge. It demonstrates how to create deterministic evaluators for
 * specific quality requirements.
 */
public class ResponseLengthEvaluator extends BaseEvaluator {

    private final int minWords;
    private final int maxWords;

    public ResponseLengthEvaluator(int minWords, int maxWords) {
        super("Response Length", 1.0, List.of(EvalTestCaseParam.ACTUAL_OUTPUT));
        this.minWords = minWords;
        this.maxWords = maxWords;
    }

    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        String output = testCase.actualOutput();
        int wordCount = output.split("\\s+").length;

        boolean withinBounds = wordCount >= minWords && wordCount <= maxWords;
        double score = withinBounds ? 1.0 : 0.0;
        String reason = String.format(
                "Response has %d words (expected %d-%d)",
                wordCount, minWords, maxWords);

        return EvalResult.builder()
                .name(name())
                .score(score)
                .threshold(threshold())
                .reason(reason)
                .build();
    }
}
