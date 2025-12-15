package io.dokimos.core;

import java.util.List;

public class LLMJudgeEvaluator implements Evaluator {
    private final String name;
    private final String criteria;
    private final List<EvalTestCaseParam> evaluationParams;
    private final double threshold;
    private final JudgeLM judge;

    private LLMJudgeEvaluator(Builder builder) {
        this.name = builder.name;
        this.criteria = builder.criteria;
        this.evaluationParams = List.copyOf(builder.evaluationParams);
        this.threshold = builder.threshold;
        this.judge = builder.judge;
    }

    @Override
    public EvalResult evaluate(EvalTestCase testCase) {
        String prompt = buildPrompt(testCase);
        String response = judge.generate(prompt);
        return parseResponse(response);
    }

    private String buildPrompt(EvalTestCase testCase) {
        var sb = new StringBuilder();
        sb.append("Evaluate the following based on this criteria: ").append(criteria).append("\n\n");

        for (var param : evaluationParams) {
            switch (param) {
                case INPUT -> sb.append("Input: ").append(testCase.input()).append("\n");
                case ACTUAL_OUTPUT -> sb.append("Actual Output: ").append(testCase.actualOutput()).append("\n");
                case EXPECTED_OUTPUT -> sb.append("Expected Output: ").append(testCase.expectedOutput()).append("\n");
                case RETRIEVAL_CONTEXT -> sb.append("Context: ").append(testCase.retrievalContext()).append("\n");
            }
        }

        sb.append("\nProvide a score between 0.0 and 1.0, and a brief reasoning. ");
        // TODO: Structured outputs?
        sb.append("Respond in JSON format: {\"score\": <number>, \"reason\": \"<explanation>\"}");
        return sb.toString();
    }

    private EvalResult parseResponse(String response) {
        double score;
        String reason;

        try {

        var scoreMatcher = java.util.regex.Pattern.compile("\"score\"\\s*:\\s*(\\d+\\.?\\d*)").matcher(response);
        if (scoreMatcher.find()) {
            score = Double.parseDouble(scoreMatcher.group(1));
        }  else {
            throw new IllegalArgumentException("No score found in LLM response.");
        }

        var reasonMatcher = java.util.regex.Pattern.compile("\"reason\"\\s*:\\s*\"([^\"]+)\"").matcher(response);
        if (reasonMatcher.find()) {
            reason = reasonMatcher.group(1);
        } else {
            reason = "No reason provided.";
        }

        return EvalResult.builder()
                .name(name)
                .score(score)
                .threshold(threshold)
                .reason(reason)
                .build();
        } catch (Exception e) {
            return EvalResult.failure(name, 0.0, "Failed to parse LLM response: " + e.getMessage());
        }
    }

    @Override
    public String name() { return name; }

    @Override
    public double threshold() { return threshold; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String name;
        private String criteria;
        private List<EvalTestCaseParam> evaluationParams = List.of();
        private double threshold = 0.5;
        private JudgeLM judge;

        public Builder name(String name) { this.name = name; return this; }
        public Builder criteria(String criteria) { this.criteria = criteria; return this; }
        public Builder evaluationParams(List<EvalTestCaseParam> params) { this.evaluationParams = params; return this; }
        public Builder threshold(double threshold) { this.threshold = threshold; return this; }
        public Builder judge(JudgeLM judge) { this.judge = judge; return this; }

        public LLMJudgeEvaluator build() {
            if (judge == null) throw new IllegalStateException("JudgeLM is required");
            return new LLMJudgeEvaluator(this);
        }
    }
}
