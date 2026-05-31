package dev.dokimos.core.evaluators.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.core.BaseEvaluator;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.evaluators.EvaluationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Detects tool execution failures by inspecting {@link ToolCall#result()}.
 * <p>
 * This is a deterministic, glass-box evaluator (no LLM). A tool call is considered
 * failed when any of the configured detectors fire:
 * <ul>
 *   <li>the result is {@code null} or blank (when {@code treatBlankAsError}, the default);</li>
 *   <li>the result is a JSON object with a non-null top-level {@code "error"} field
 *       (when {@code detectJsonErrorField}, the default);</li>
 *   <li>a custom {@link Predicate} supplied via {@link Builder#errorDetector(Predicate)} matches.</li>
 * </ul>
 * The score is the fraction of tool calls that did not fail. An empty tool-call list
 * scores {@code 1.0}, consistent with the other agent evaluators.
 */
public class ToolErrorEvaluator extends BaseEvaluator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String toolCallsKey;
    private final boolean treatBlankAsError;
    private final boolean detectJsonErrorField;
    private final Predicate<String> errorDetector;

    private ToolErrorEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        this.toolCallsKey = builder.toolCallsKey;
        this.treatBlankAsError = builder.treatBlankAsError;
        this.detectJsonErrorField = builder.detectJsonErrorField;
        this.errorDetector = builder.errorDetector;
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
            throw new EvaluationException("ToolErrorEvaluator requires '%s' in actualOutputs".formatted(toolCallsKey));
        }

        List<ToolCall> toolCalls = AgentEvalCasts.toolCalls(rawToolCalls, toolCallsKey);
        if (toolCalls.isEmpty()) {
            return EvalResult.builder()
                    .name(name)
                    .score(1.0)
                    .threshold(threshold)
                    .reason("No tool calls to inspect.")
                    .metadata(Map.of("results", List.of()))
                    .build();
        }

        List<Map<String, Object>> perCall = new ArrayList<>();
        int succeeded = 0;
        for (ToolCall call : toolCalls) {
            String reason = errorReason(call.result());
            boolean errored = reason != null;
            if (!errored) succeeded++;
            perCall.add(Map.of("toolName", call.name(), "errored", errored, "reason", errored ? reason : ""));
        }

        double score = (double) succeeded / toolCalls.size();
        String reason = "%d/%d tool calls succeeded.".formatted(succeeded, toolCalls.size());
        return EvalResult.builder()
                .name(name)
                .score(score)
                .threshold(threshold)
                .reason(reason)
                .metadata(Map.of("results", perCall))
                .build();
    }

    /**
     * Returns a human-readable reason if the result indicates an error, or null if it succeeded.
     */
    private String errorReason(String result) {
        if (result == null || result.isBlank()) {
            return treatBlankAsError ? "Result was null or blank." : null;
        }
        if (errorDetector != null && errorDetector.test(result)) {
            return "Matched custom error detector.";
        }
        if (detectJsonErrorField) {
            try {
                JsonNode node = OBJECT_MAPPER.readTree(result);
                if (node.isObject() && node.hasNonNull("error")) {
                    return "Result contains a top-level 'error' field: " + node.get("error");
                }
            } catch (Exception ignored) {
                // Not JSON: nothing to detect here.
            }
        }
        return null;
    }

    /**
     * Builder for constructing the evaluator.
     */
    public static class Builder {
        private String name = "Tool Error";
        private double threshold = 1.0;
        private List<EvalTestCaseParam> evaluationParams = List.of();
        private String toolCallsKey = "toolCalls";
        private boolean treatBlankAsError = true;
        private boolean detectJsonErrorField = true;
        private Predicate<String> errorDetector;

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
         * Controls whether a null or blank result counts as an error.
         *
         * @param treatBlankAsError true to treat blank results as errors (default true)
         * @return this builder
         */
        public Builder treatBlankAsError(boolean treatBlankAsError) {
            this.treatBlankAsError = treatBlankAsError;
            return this;
        }

        /**
         * Controls whether a JSON object with a top-level {@code "error"} field counts as an error.
         *
         * @param detectJsonErrorField true to detect JSON error fields (default true)
         * @return this builder
         */
        public Builder detectJsonErrorField(boolean detectJsonErrorField) {
            this.detectJsonErrorField = detectJsonErrorField;
            return this;
        }

        /**
         * Sets a custom predicate that flags a result string as an error.
         *
         * @param errorDetector the predicate (receives the raw result string)
         * @return this builder
         */
        public Builder errorDetector(Predicate<String> errorDetector) {
            this.errorDetector = errorDetector;
            return this;
        }

        /**
         * Builds the evaluator.
         *
         * @return a new evaluator
         */
        public ToolErrorEvaluator build() {
            return new ToolErrorEvaluator(this);
        }
    }
}
