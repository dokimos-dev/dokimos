package io.dokimos.core;

import java.util.List;
import java.util.Objects;

public class ExactMatchEvaluator extends BaseEvaluator {

    private ExactMatchEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
    }

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

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder evaluationParams(List<EvalTestCaseParam> params) {
            this.evaluationParams = params;
            return this;
        }

        public Builder threshold(double threshold) {
            this.threshold = threshold;
            return this;
        }

        public ExactMatchEvaluator build() {
            return new ExactMatchEvaluator(this);
        }
    }

}
