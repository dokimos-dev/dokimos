package dev.dokimos.core.evaluators;

import dev.dokimos.core.BaseEvaluator;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;
import java.util.List;
import java.util.Objects;

/**
 * Evaluator that checks for exact string match between actual and expected outputs.
 *
 * <p>By default the comparison is strict. It can optionally ignore leading and trailing
 * whitespace ({@link Builder#trimWhiteSpace(boolean)}) and letter case
 * ({@link Builder#ignoreCase(boolean)}).
 */
public class ExactMatchEvaluator extends BaseEvaluator {

    private final boolean ignoreCase;
    private final boolean trimWhiteSpace;

    private ExactMatchEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        this.ignoreCase = builder.ignoreCase;
        this.trimWhiteSpace = builder.trimWhiteSpace;
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
        String expectedOutput = testCase.expectedOutput();
        String actualOutput = testCase.actualOutput();

        if (trimWhiteSpace) {
            expectedOutput = expectedOutput == null ? null : expectedOutput.trim();
            actualOutput = actualOutput == null ? null : actualOutput.trim();
        }

        boolean matches = ignoreCase && expectedOutput != null
                ? expectedOutput.equalsIgnoreCase(actualOutput)
                : Objects.equals(expectedOutput, actualOutput);

        if (matches) {
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

    /**
     * Builder for {@link ExactMatchEvaluator}.
     */
    public static class Builder {
        private String name = "Exact Match";
        private double threshold = 1.0;
        private List<EvalTestCaseParam> evaluationParams =
                List.of(EvalTestCaseParam.ACTUAL_OUTPUT, EvalTestCaseParam.EXPECTED_OUTPUT);
        private boolean trimWhiteSpace = false;
        private boolean ignoreCase = false;

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

        /**
         * Sets whether leading and trailing whitespace is removed from both the actual and
         * expected outputs before comparing them. Whitespace inside the strings is preserved.
         * Defaults to {@code false}.
         *
         * @param trimWhiteSpace {@code true} to trim both outputs before comparison
         * @return this builder
         */
        public Builder trimWhiteSpace(boolean trimWhiteSpace) {
            this.trimWhiteSpace = trimWhiteSpace;
            return this;
        }

        /**
         * Sets whether the comparison ignores letter case. Defaults to {@code false}.
         *
         * @param ignoreCase {@code true} to compare the outputs case-insensitively
         * @return this builder
         */
        public Builder ignoreCase(boolean ignoreCase) {
            this.ignoreCase = ignoreCase;
            return this;
        }

        /**
         * Builds the evaluator.
         *
         * @return a new {@link ExactMatchEvaluator}
         */
        public ExactMatchEvaluator build() {
            return new ExactMatchEvaluator(this);
        }
    }
}
