package io.dokimos.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public class LLMJudgeEvaluator extends BaseEvaluator {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final String criteria;
    private final double minScore;
    private final double maxScore;
    private final JudgeLM judge;

    private LLMJudgeEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        this.criteria = builder.criteria;
        this.minScore = builder.minScore;
        this.maxScore = builder.maxScore;
        this.judge = builder.judge;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
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
            }
        }

        sb.append("Provide a score between ")
                .append(minScore).append(" and ").append(maxScore)
                .append(", and a brief reasoning.");
        // TODO: Structured outputs?
        sb.append("Respond in JSON format: {\"score\": <number>, \"reason\": \"<explanation>\"}");
        return sb.toString();
    }

    private EvalResult parseResponse(String response) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(response);
            double score = node.get("score").asDouble();
            String reason = node.has("reason") ? node.get("reason").asText() : "No reason provided.";

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

    public static class Builder {
        private String name;
        private String criteria;
        private List<EvalTestCaseParam> evaluationParams = List.of();
        private double threshold = 0.5;
        private double minScore = 0.0;
        private double maxScore = 1.0;
        private JudgeLM judge;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder criteria(String criteria) {
            this.criteria = criteria;
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

        public Builder scoreRange(double min, double max) {
            this.minScore = min;
            this.maxScore = max;
            return this;
        }

        public Builder judge(JudgeLM judge) {
            this.judge = judge;
            return this;
        }

        public LLMJudgeEvaluator build() {
            if (evaluationParams.isEmpty()) {
                throw new IllegalStateException("LLM Judge requires at least one evaluation param");
            }
            if (judge == null) throw new IllegalStateException("JudgeLM is required");
            return new LLMJudgeEvaluator(this);
        }
    }
}
