package dev.dokimos.core.evaluators.agents;

import dev.dokimos.core.BaseEvaluator;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.EvaluationException;

import java.util.*;

/**
 * Evaluates tool description quality using a mix of rule-based and optional LLM checks.
 * <p>
 * This is a glass-box evaluator for tool reliability. Checks include:
 * <ul>
 *   <li>Non-empty: Description is not blank</li>
 *   <li>Length: Description between 10–500 characters</li>
 *   <li>Input args documented: All required parameters have descriptions in the schema</li>
 *   <li>Max optional args: No more than N optional parameters (default: 3)</li>
 *   <li>Clarity: Description is clear and unambiguous (LLM-assisted if judge provided)</li>
 * </ul>
 */
public class ToolDescriptionReliabilityEvaluator extends BaseEvaluator {

    private final String toolsKey;
    private final JudgeLM judge;
    private final int maxOptionalArgs;

    private ToolDescriptionReliabilityEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        this.toolsKey = builder.toolsKey;
        this.judge = builder.judge;
        this.maxOptionalArgs = builder.maxOptionalArgs;
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
        Object rawTools = testCase.metadata().get(toolsKey);
        if (rawTools == null) {
            throw new EvaluationException(
                    "ToolDescriptionReliabilityEvaluator requires '%s' in metadata".formatted(toolsKey));
        }

        List<ToolDefinition> tools = castToolDefinitions(rawTools);

        if (tools.isEmpty()) {
            return EvalResult.builder()
                    .name(name)
                    .score(1.0)
                    .threshold(threshold)
                    .reason("No tools to evaluate.")
                    .build();
        }

        List<Map<String, Object>> perToolResults = new ArrayList<>();
        double totalScore = 0;

        for (ToolDefinition tool : tools) {
            Map<String, Object> checks = evaluateToolDescription(tool);
            perToolResults.add(checks);

            @SuppressWarnings("unchecked")
            Map<String, Boolean> checkResults = (Map<String, Boolean>) checks.get("checks");
            long passed = checkResults.values().stream().filter(v -> v).count();
            totalScore += (double) passed / checkResults.size();
        }

        double score = totalScore / tools.size();
        String reason = String.format("Average description quality: %.1f%% across %d tools.",
                score * 100, tools.size());

        return EvalResult.builder()
                .name(name)
                .score(score)
                .threshold(threshold)
                .reason(reason)
                .metadata(Map.of("perToolResults", perToolResults))
                .build();
    }

    private Map<String, Object> evaluateToolDescription(ToolDefinition tool) {
        Map<String, Boolean> checks = new LinkedHashMap<>();

        // 1. Non-empty
        checks.put("nonEmpty", tool.description() != null && !tool.description().isBlank());

        // 2. Length: 10–500 characters
        int descLen = tool.description() != null ? tool.description().length() : 0;
        checks.put("lengthOk", descLen >= 10 && descLen <= 500);

        // 3. Input args documented: required parameters have descriptions in schema
        checks.put("argsDocumented", areRequiredParamsDocumented(tool));

        // 4. Max optional args
        checks.put("maxOptionalArgs", countOptionalParams(tool) <= maxOptionalArgs);

        // 5. Clarity (LLM-assisted or simple heuristic)
        if (judge != null) {
            checks.put("clarity", llmCheckClarity(tool));
        } else {
            // Heuristic: description contains at least 2 words
            checks.put("clarity", tool.description() != null
                    && tool.description().trim().split("\\s+").length >= 2);
        }

        return Map.of(
                "toolName", tool.name(),
                "checks", checks
        );
    }

    private boolean areRequiredParamsDocumented(ToolDefinition tool) {
        List<String> required = tool.requiredParameters();
        if (required.isEmpty()) return true;

        for (String param : required) {
            Map<String, Object> schema = tool.parameterSchema(param);
            if (schema.isEmpty() || !schema.containsKey("description")) {
                return false;
            }
        }
        return true;
    }

    private int countOptionalParams(ToolDefinition tool) {
        Set<String> allParams = tool.parameterNames();
        List<String> required = tool.requiredParameters();
        long requiredInProperties = required.stream().filter(allParams::contains).count();
        return Math.max(0, allParams.size() - (int) requiredInProperties);
    }

    private boolean llmCheckClarity(ToolDefinition tool) {
        String prompt = String.format(
                "Is the following tool description clear and unambiguous?\n"
                        + "Tool name: '%s'\nDescription: '%s'\n"
                        + "Answer with ONLY 'yes' or 'no'. 'yes' means it IS clear.",
                tool.name(), tool.description()
        );
        String response = judge.generate(prompt).trim().toLowerCase();
        return response.startsWith("yes");
    }

    @SuppressWarnings("unchecked")
    private List<ToolDefinition> castToolDefinitions(Object raw) {
        if (raw instanceof List<?> list) {
            if (list.isEmpty()) return List.of();
            if (list.get(0) instanceof ToolDefinition) {
                return (List<ToolDefinition>) raw;
            }
            if (list.get(0) instanceof Map) {
                return list.stream()
                        .map(item -> ToolDefinition.fromMap((Map<String, Object>) item))
                        .toList();
            }
        }
        throw new EvaluationException("Expected a List of ToolDefinition objects for key '%s'".formatted(toolsKey));
    }

    /**
     * Builder for constructing the evaluator.
     */
    public static class Builder {
        private String name = "Tool Description Reliability";
        private double threshold = 0.8;
        private List<EvalTestCaseParam> evaluationParams = List.of();
        private String toolsKey = "tools";
        private JudgeLM judge;
        private int maxOptionalArgs = 3;

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
         * Sets the metadata key for tool definitions.
         *
         * @param toolsKey the key
         * @return this builder
         */
        public Builder toolsKey(String toolsKey) {
            this.toolsKey = toolsKey;
            return this;
        }

        /**
         * Sets an optional judge LLM for semantic clarity checks.
         * If not provided, only rule-based checks are run.
         *
         * @param judge the judge LLM
         * @return this builder
         */
        public Builder judge(JudgeLM judge) {
            this.judge = judge;
            return this;
        }

        /**
         * Sets the maximum number of optional parameters allowed per tool.
         *
         * @param maxOptionalArgs the maximum count
         * @return this builder
         */
        public Builder maxOptionalArgs(int maxOptionalArgs) {
            this.maxOptionalArgs = maxOptionalArgs;
            return this;
        }

        /**
         * Builds the evaluator.
         *
         * @return a new evaluator
         */
        public ToolDescriptionReliabilityEvaluator build() {
            return new ToolDescriptionReliabilityEvaluator(this);
        }
    }
}
