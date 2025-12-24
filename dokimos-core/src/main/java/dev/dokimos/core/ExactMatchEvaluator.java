package dev.dokimos.core;

import java.util.List;
import java.util.Objects;

/**
 * Evaluator that checks for exact string match between actual and expected outputs.
 */
public class ExactMatchEvaluator extends BaseEvaluator {

    private ExactMatchEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
    }

    /**
     * Creates a new builder for constructing exact match evaluators.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        double score;
        String reason;

        if (Objects.equals(testCase.expectedOutput(), testCase.actualOutput())) {
            score = 1.0;
            reason = "The actual and expected outputs are exact matches.";

        } else {
            score = 0.0;
            reason = "The actual and expected outputs are different.";
        }

        return EvalResult.builder()
                .name(name)
                .score(score)
                .threshold(threshold)
                .reason(reason)
                .build();
    }

    public static class Builder {
        private String name = "Exact Match";
        private double threshold = 1.0;
        private List<EvalTestCaseParam> evaluationParams = List.of(
                EvalTestCaseParam.ACTUAL_OUTPUT,
                EvalTestCaseParam.EXPECTED_OUTPUT
        );

        /**
         * Sets the evaluator name.
         *
         * @param name the evaluator name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets which test case parameters to use for the evaluation.
         *
         * @param params the parameters to use for the evaluation
         * @return this builder
         */
        public Builder evaluationParams(List<EvalTestCaseParam> params) {
            this.evaluationParams = List.copyOf(params);
            return this;
        }

        /**
         * Sets the minimum score threshold for success.
         *
         * @param threshold the threshold value
         * @return this builder
         */
        public Builder threshold(double threshold) {
            this.threshold = threshold;
            return this;
        }

        public ExactMatchEvaluator build() {
            return new ExactMatchEvaluator(this);
        }
    }

}
