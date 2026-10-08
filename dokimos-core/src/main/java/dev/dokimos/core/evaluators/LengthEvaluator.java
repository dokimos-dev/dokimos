package dev.dokimos.core.evaluators;

import dev.dokimos.core.BaseEvaluator;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;
import java.util.List;
import java.util.Objects;

/**
 * Evaluator that checks if the length of the actual output falls within an inclusive
 * {@code [minimumOutputLength, maximumOutputLength]} range.
 *
 * <p>Length is measured in Unicode code points rather than UTF-16 {@code char} units, so a
 * supplementary character such as an emoji counts as one, not two.
 */
public class LengthEvaluator extends BaseEvaluator {
    private final int maximumOutputLength;
    private final int minimumOutputLength;

    private LengthEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        this.maximumOutputLength = builder.maximumOutputLength;
        this.minimumOutputLength = builder.minimumOutputLength;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        String actualOutput = Objects.requireNonNull(testCase.actualOutput(), "`actualOutput` cannot be null");
        int actualOutputLength = actualOutput.codePointCount(0, actualOutput.length());

        double score;
        String reason;

        if (actualOutputLength < minimumOutputLength) {
            score = 0.0;
            reason = "Output length " + actualOutputLength + " is less than the minimum length " + minimumOutputLength
                    + ".";
        } else if (actualOutputLength > maximumOutputLength) {
            score = 0.0;
            reason = "Output length " + actualOutputLength + " is greater than the maximum length "
                    + maximumOutputLength + ".";
        } else {
            score = 1.0;
            reason = "Output length " + actualOutputLength + " is within the range [" + minimumOutputLength + ", "
                    + maximumOutputLength + "].";
        }

        return EvalResult.builder()
                .name(name)
                .score(score)
                .threshold(threshold)
                .reason(reason)
                .build();
    }

    public static class Builder {
        private String name = "Length";
        private double threshold = 1.0;
        private List<EvalTestCaseParam> evaluationParams = List.of(EvalTestCaseParam.ACTUAL_OUTPUT);
        private Integer maximumOutputLength;
        private int minimumOutputLength = 0;

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
         * Sets the minimum score threshold for success.
         *
         * @param threshold the threshold value
         * @return this builder
         */
        public Builder threshold(double threshold) {
            this.threshold = threshold;
            return this;
        }

        /**
         * Sets which test case parameters to evaluate.
         *
         * @param evaluationParams the parameters to evaluate
         * @return this builder
         */
        public Builder evaluationParams(List<EvalTestCaseParam> evaluationParams) {
            this.evaluationParams = List.copyOf(evaluationParams);
            return this;
        }

        /**
         * Sets the maximum allowed output length (inclusive). Required.
         *
         * @param maximumOutputLength the maximum length in code points, at least 1
         * @return this builder
         */
        public Builder maximumOutputLength(int maximumOutputLength) {
            this.maximumOutputLength = maximumOutputLength;
            return this;
        }

        /**
         * Sets the minimum allowed output length (inclusive). Defaults to 0.
         *
         * @param minimumOutputLength the minimum length in code points, at least 0
         * @return this builder
         */
        public Builder minimumOutputLength(int minimumOutputLength) {
            this.minimumOutputLength = minimumOutputLength;
            return this;
        }

        /**
         * Builds the evaluator.
         *
         * @return a new length evaluator
         * @throws IllegalStateException if the maximum length is missing or less than
         *                               1, the minimum length is
         *                               negative, or the minimum length exceeds the
         *                               maximum length
         */
        public LengthEvaluator build() {
            if (maximumOutputLength == null || maximumOutputLength < 1) {
                throw new IllegalStateException(
                        "A maximum output length of at least 1 is required; call .maximumOutputLength(...) before build()");
            }
            if (minimumOutputLength < 0) {
                throw new IllegalStateException("minimumOutputLength cannot be negative");
            }
            if (minimumOutputLength > maximumOutputLength) {
                throw new IllegalStateException("minimumOutputLength (" + minimumOutputLength
                        + ") cannot be greater than maximumOutputLength (" + maximumOutputLength + ")");
            }
            return new LengthEvaluator(this);
        }
    }
}
