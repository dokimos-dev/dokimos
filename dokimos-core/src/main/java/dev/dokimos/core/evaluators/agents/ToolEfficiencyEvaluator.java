package dev.dokimos.core.evaluators.agents;

import dev.dokimos.core.BaseEvaluator;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.evaluators.EvaluationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Measures how efficiently an agent used its tools by detecting redundant calls.
 * <p>
 * This is a deterministic, glass-box evaluator (no LLM). Two calls are redundant when
 * they share a name and their arguments match under the configured
 * {@link ArgumentMatcher} (default {@link ArgumentMatcher#tolerant()}). The score is
 * the ratio of distinct calls to total calls, so {@code 1.0} means no redundancy.
 * Consecutive identical calls are additionally flagged as a loop signal in the result
 * metadata. An empty tool-call list scores {@code 1.0}.
 * <p>
 * Efficiency is a signal, not a hard correctness gate: a legitimately repeated call
 * (for example a retry) lowers the score. Tune the threshold accordingly.
 */
public class ToolEfficiencyEvaluator extends BaseEvaluator {

    private final String toolCallsKey;
    private final ArgumentMatcher argumentMatcher;

    private ToolEfficiencyEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        this.toolCallsKey = builder.toolCallsKey;
        this.argumentMatcher = builder.argumentMatcher;
    }

    /**
     * Creates a new builder for constructing the evaluator.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        Object rawToolCalls = testCase.actualOutputs().get(toolCallsKey);
        if (rawToolCalls == null) {
            throw new EvaluationException(
                    "ToolEfficiencyEvaluator requires '%s' in actualOutputs".formatted(toolCallsKey));
        }

        List<ToolCall> toolCalls = AgentEvalCasts.toolCalls(rawToolCalls, toolCallsKey);
        if (toolCalls.isEmpty()) {
            return EvalResult.builder()
                    .name(name)
                    .score(1.0)
                    .threshold(threshold)
                    .reason("No tool calls to inspect.")
                    .metadata(Map.of("redundantCalls", List.of(), "consecutiveDuplicates", List.of()))
                    .build();
        }

        List<ToolCall> distinct = new ArrayList<>();
        List<String> redundant = new ArrayList<>();
        List<String> consecutive = new ArrayList<>();

        for (int i = 0; i < toolCalls.size(); i++) {
            ToolCall call = toolCalls.get(i);
            boolean isDuplicate = distinct.stream().anyMatch(seen -> sameCall(seen, call));
            if (isDuplicate) {
                redundant.add(call.name());
            } else {
                distinct.add(call);
            }
            if (i > 0 && sameCall(toolCalls.get(i - 1), call)) {
                consecutive.add(call.name());
            }
        }

        double score = (double) distinct.size() / toolCalls.size();
        String reason = "%d/%d calls were distinct (%d redundant)."
                .formatted(distinct.size(), toolCalls.size(), redundant.size());
        return EvalResult.builder()
                .name(name)
                .score(score)
                .threshold(threshold)
                .reason(reason)
                .metadata(Map.of(
                        "totalCalls",
                        toolCalls.size(),
                        "distinctCalls",
                        distinct.size(),
                        "redundantCalls",
                        redundant,
                        "consecutiveDuplicates",
                        consecutive))
                .build();
    }

    private boolean sameCall(ToolCall a, ToolCall b) {
        return a.name().equals(b.name()) && argumentMatcher.matches(a.arguments(), b.arguments());
    }

    /**
     * Builder for constructing the evaluator.
     */
    public static class Builder {
        private String name = "Tool Efficiency";
        private double threshold = 1.0;
        private List<EvalTestCaseParam> evaluationParams = List.of();
        private String toolCallsKey = "toolCalls";
        private ArgumentMatcher argumentMatcher = ArgumentMatcher.tolerant();

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
         * Sets which test case parameters to validate.
         *
         * @param params the parameters
         * @return this builder
         */
        public Builder evaluationParams(List<EvalTestCaseParam> params) {
            this.evaluationParams = List.copyOf(params);
            return this;
        }

        /**
         * Sets the key used to retrieve tool calls from actualOutputs.
         *
         * @param toolCallsKey the key
         * @return this builder
         */
        public Builder toolCallsKey(String toolCallsKey) {
            this.toolCallsKey = toolCallsKey;
            return this;
        }

        /**
         * Sets the argument matcher used to decide whether two calls are duplicates.
         *
         * @param argumentMatcher the argument matcher (default {@link ArgumentMatcher#tolerant()})
         * @return this builder
         */
        public Builder argumentMatcher(ArgumentMatcher argumentMatcher) {
            this.argumentMatcher = argumentMatcher;
            return this;
        }

        /**
         * Builds the evaluator.
         *
         * @return a new evaluator
         */
        public ToolEfficiencyEvaluator build() {
            return new ToolEfficiencyEvaluator(this);
        }
    }
}
