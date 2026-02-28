package dev.dokimos.core.evaluators.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.core.BaseEvaluator;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.EvaluationException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Evaluates tool description quality using a mix of rule-based checks and optional LLM checks.
 * <p>
 * Performs 13 checks across two categories:
 * <p>
 * <b>Rule-based (always run):</b>
 * <ul>
 *   <li>{@code input_arguments_clarity}: Each parameter has a "description" key</li>
 *   <li>{@code input_arguments_types}: Each parameter has a "type" key</li>
 *   <li>{@code max_num_input_arguments}: Total params ≤ maxInputArgs (default 5)</li>
 *   <li>{@code max_optional_input_arguments}: Optional params ≤ maxOptionalArgs (default 3)</li>
 * </ul>
 * <p>
 * <b>LLM-based (require judge):</b>
 * <ul>
 *   <li>{@code general_structure}: Description includes purpose, inputs, and output</li>
 *   <li>{@code has_examples}: Description includes usage examples</li>
 *   <li>{@code has_usage_notes}: Description includes notes/limitations/caveats</li>
 *   <li>{@code intent_over_implementation}: Communicates what, not how</li>
 *   <li>{@code clarity}: Avoids obscure/ambiguous terms</li>
 *   <li>{@code redundancy}: Avoids redundant information</li>
 *   <li>{@code input_arguments_enum}: Applicable args include enumeration values</li>
 *   <li>{@code input_arguments_format}: Applicable args include format specs</li>
 *   <li>{@code return_statement_quality}: Output information is clearly described</li>
 * </ul>
 * <p>
 * Without a judge LLM, only the 4 rule-based checks run.
 * Score is based on checks that actually ran.
 */
public class ToolDescriptionReliabilityEvaluator extends BaseEvaluator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final List<String> LLM_CHECK_KEYS = List.of(
            "general_structure",
            "has_examples",
            "has_usage_notes",
            "intent_over_implementation",
            "clarity",
            "redundancy",
            "input_arguments_enum",
            "input_arguments_format",
            "return_statement_quality");

    private final String toolsKey;
    private final JudgeLM judge;
    private final int maxOptionalArgs;
    private final int maxInputArgs;

    private ToolDescriptionReliabilityEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        this.toolsKey = builder.toolsKey;
        this.judge = builder.judge;
        this.maxOptionalArgs = builder.maxOptionalArgs;
        this.maxInputArgs = builder.maxInputArgs;
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
            Map<String, Object> result = evaluateToolDescription(tool);
            perToolResults.add(result);

            @SuppressWarnings("unchecked")
            Map<String, Object> checks = (Map<String, Object>) result.get("checks");
            long ran =
                    checks.values().stream().filter(Boolean.class::isInstance).count();
            long passed = checks.values().stream().filter(Boolean.TRUE::equals).count();
            totalScore += ran > 0 ? (double) passed / ran : 1.0;
        }

        double score = totalScore / tools.size();
        String reason =
                String.format("Average description quality: %.1f%% across %d tools.", score * 100, tools.size());

        return EvalResult.builder()
                .name(name)
                .score(score)
                .threshold(threshold)
                .reason(reason)
                .metadata(Map.of("perToolResults", perToolResults))
                .build();
    }

    private Map<String, Object> evaluateToolDescription(ToolDefinition tool) {
        Map<String, Object> checks = new LinkedHashMap<>();

        // Rule-based checks (always run)

        // 1. input_arguments_clarity: every parameter has a "description"
        checks.put("input_arguments_clarity", allParamsHaveKey(tool, "description"));

        // 2. input_arguments_types: every parameter has a "type"
        checks.put("input_arguments_types", allParamsHaveKey(tool, "type"));

        // 3. max_num_input_arguments: total params <= maxInputArgs
        checks.put("max_num_input_arguments", tool.parameterNames().size() <= maxInputArgs);

        // 4. max_optional_input_arguments: optional params <= maxOptionalArgs
        checks.put("max_optional_input_arguments", countOptionalParams(tool) <= maxOptionalArgs);

        // LLM-based checks
        if (judge != null) {
            Map<String, Integer> llmResults = batchLlmChecks(tool);
            for (String key : LLM_CHECK_KEYS) {
                checks.put(key, llmResults.getOrDefault(key, 0) == 1);
            }
        } else {
            for (String key : LLM_CHECK_KEYS) {
                checks.put(key, "skipped");
            }
        }

        return Map.of("toolName", tool.name(), "checks", checks);
    }

    private boolean allParamsHaveKey(ToolDefinition tool, String key) {
        Set<String> paramNames = tool.parameterNames();
        if (paramNames.isEmpty()) return true;

        for (String param : paramNames) {
            Map<String, Object> schema = tool.parameterSchema(param);
            if (schema.isEmpty() || !schema.containsKey(key)) {
                return false;
            }
        }
        return true;
    }

    private int countOptionalParams(ToolDefinition tool) {
        Set<String> allParams = tool.parameterNames();
        List<String> required = tool.requiredParameters();
        long requiredInProperties =
                required.stream().filter(allParams::contains).count();
        return Math.max(0, allParams.size() - (int) requiredInProperties);
    }

    private Map<String, Integer> batchLlmChecks(ToolDefinition tool) {
        String schemaJson;
        try {
            schemaJson = OBJECT_MAPPER.writeValueAsString(tool.inputSchema());
        } catch (Exception e) {
            schemaJson = tool.inputSchema().toString();
        }
        String prompt = String.format(
                "Evaluate this tool specification against quality criteria.\n"
                        + "Tool name: '%s'\n"
                        + "Description: '%s'\n"
                        + "Input schema: %s\n\n"
                        + "For each criterion, respond with pass (1) or fail (0):\n"
                        + "1. general_structure: Does the description include the tool's purpose, its input arguments, and the expected output?\n"
                        + "2. has_examples: Does the description include examples of how to use the tool?\n"
                        + "3. has_usage_notes: Does the description include notes on how/when to use the tool (e.g. limitations, caveats)?\n"
                        + "4. intent_over_implementation: Does the description communicate what the tool does, not how it does it or other implementation details?\n"
                        + "5. clarity: Does the description avoid obscure or ambiguous terms, low-level or very specific identifiers?\n"
                        + "6. redundancy: Does the description avoid redundant information that is not useful to understand what the tool does, how to use it and how to call it?\n"
                        + "7. input_arguments_enum: Do input arguments include enumeration values where applicable?\n"
                        + "8. input_arguments_format: Do input arguments include format specification where applicable (e.g. for dates, ids, urls)?\n"
                        + "9. return_statement_quality: Does the description make clear what information the output will contain?\n\n"
                        + "Respond ONLY as JSON (no markdown): "
                        + "{\"general_structure\": 0/1, \"has_examples\": 0/1, \"has_usage_notes\": 0/1, "
                        + "\"intent_over_implementation\": 0/1, \"clarity\": 0/1, \"redundancy\": 0/1, "
                        + "\"input_arguments_enum\": 0/1, \"input_arguments_format\": 0/1, \"return_statement_quality\": 0/1}",
                tool.name(), tool.description(), schemaJson);

        String response = judge.generate(prompt).trim();
        return parseLlmJson(response);
    }

    static Map<String, Integer> parseLlmJson(String response) {
        Map<String, Integer> results = new HashMap<>();
        // Strip markdown fences if present
        String cleaned = response;
        if (cleaned.contains("```")) {
            cleaned = cleaned.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        }
        cleaned = cleaned.trim();

        for (String key : LLM_CHECK_KEYS) {
            String pattern = "\"" + key + "\"\\s*:\\s*([01])";
            var matcher = Pattern.compile(pattern).matcher(cleaned);
            if (matcher.find()) {
                results.put(key, Integer.parseInt(matcher.group(1)));
            }
        }
        return results;
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
        private int maxInputArgs = 5;

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
         * Sets an optional judge LLM for semantic checks.
         * Without a judge, only rule-based checks run (4 out of 13).
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
         * Default: 3.
         *
         * @param maxOptionalArgs the maximum count
         * @return this builder
         */
        public Builder maxOptionalArgs(int maxOptionalArgs) {
            this.maxOptionalArgs = maxOptionalArgs;
            return this;
        }

        /**
         * Sets the maximum number of total input parameters allowed per tool.
         * Default: 5.
         *
         * @param maxInputArgs the maximum count
         * @return this builder
         */
        public Builder maxInputArgs(int maxInputArgs) {
            this.maxInputArgs = maxInputArgs;
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
