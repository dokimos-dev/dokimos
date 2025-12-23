package dev.dokimos.core;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluator that checks if the actual output matches a regular expression pattern.
 */
public class RegexEvaluator extends BaseEvaluator {
    private final String pattern;
    private final boolean ignoreCase;

    private RegexEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        this.pattern = builder.pattern;
        this.ignoreCase = builder.ignoreCase;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        String actualOutput = Objects.requireNonNull(
                testCase.actualOutput(),
                "`actualOutput` cannot be null"
        );

        int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
        Pattern compiledPattern = Pattern.compile(pattern, flags);
        Matcher matcher = compiledPattern.matcher(actualOutput);

        double score;
        String reason;

        if (matcher.find()) {
            score = 1.0;
            reason = "The actual output matches the pattern.";
        } else {
            score = 0.0;
            reason = "The actual output does not match the pattern.";
        }

        return EvalResult.builder()
                .name(name)
                .score(score)
                .threshold(threshold)
                .reason(reason)
                .build();
    }

    public static class Builder {
        private String name = "Regex Match";
        private String pattern;
        private boolean ignoreCase = false;
        private double threshold = 1.0;
        private List<EvalTestCaseParam> evaluationParams = List.of(
                EvalTestCaseParam.ACTUAL_OUTPUT
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
         * Sets the regular expression pattern.
         *
         * @param pattern the regex pattern
         * @return this builder
         */
        public Builder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        /**
         * Sets whether to ignore case when matching.
         *
         * @param ignoreCase true to ignore case
         * @return this builder
         */
        public Builder ignoreCase(boolean ignoreCase) {
            this.ignoreCase = ignoreCase;
            return this;
        }

        /**
         * Sets which test case parameters to evaluate.
         *
         * @param params the parameters to evaluate
         * @return this builder
         */
        public Builder evaluationParams(List<EvalTestCaseParam> params) {
            this.evaluationParams = params;
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
         * Builds the evaluator.
         *
         * @return a new regex evaluator
         */
        public RegexEvaluator build() {
            return new RegexEvaluator(this);
        }
    }

}
