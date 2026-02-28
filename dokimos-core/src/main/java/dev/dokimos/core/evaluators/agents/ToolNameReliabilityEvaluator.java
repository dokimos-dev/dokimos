package dev.dokimos.core.evaluators.agents;

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
 * Evaluates tool naming quality using a mix of rule-based checks and optional LLM checks.
 * <p>
 * Checks performed:
 * <ul>
 *   <li>{@code snakecase_format} (rule): Name uses strict snake_case</li>
 *   <li>{@code clarity} (LLM): Purpose is clear from the name alone</li>
 *   <li>{@code conciseness} (rule): Name has at most 7 underscore-separated segments</li>
 *   <li>{@code name_order} (LLM): Name follows operation_system_entity_data ordering</li>
 *   <li>{@code intent_over_implementation} (mixed): Name communicates what, not how</li>
 * </ul>
 * <p>
 * Without a judge LLM, only rule-based checks ({@code snakecase_format}, {@code conciseness},
 * and the blocklist portion of {@code intent_over_implementation}) run.
 * Score is based on checks that actually ran.
 */
public class ToolNameReliabilityEvaluator extends BaseEvaluator {

    private static final Pattern SNAKE_CASE = Pattern.compile("^[a-z][a-z0-9]*(_[a-z0-9]+)*$");
    private static final int MAX_SEGMENTS = 7;
    private static final List<String> IMPLEMENTATION_BLOCKLIST =
            List.of("_with_llm", "_via_api", "_from_s3", "_using_bq", "_using_sdk", "_with_gpt4");

    private final String toolsKey;
    private final JudgeLM judge;

    private ToolNameReliabilityEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        this.toolsKey = builder.toolsKey;
        this.judge = builder.judge;
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
            throw new EvaluationException("ToolNameReliabilityEvaluator requires '%s' in metadata".formatted(toolsKey));
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
            Map<String, Object> result = evaluateToolName(tool);
            perToolResults.add(result);

            @SuppressWarnings("unchecked")
            Map<String, Object> checks = (Map<String, Object>) result.get("checks");
            long ran =
                    checks.values().stream().filter(Boolean.class::isInstance).count();
            long passed = checks.values().stream().filter(Boolean.TRUE::equals).count();
            totalScore += ran > 0 ? (double) passed / ran : 1.0;
        }

        double score = totalScore / tools.size();
        String reason = String.format("Average naming quality: %.1f%% across %d tools.", score * 100, tools.size());

        return EvalResult.builder()
                .name(name)
                .score(score)
                .threshold(threshold)
                .reason(reason)
                .metadata(Map.of("perToolResults", perToolResults))
                .build();
    }

    private Map<String, Object> evaluateToolName(ToolDefinition tool) {
        Map<String, Object> checks = new LinkedHashMap<>();

        // 1. snakecase_format (rule)
        checks.put("snakecase_format", SNAKE_CASE.matcher(tool.name()).matches());

        // 2. conciseness (rule): at most MAX_SEGMENTS underscore-separated segments
        checks.put("conciseness", tool.name().split("_").length <= MAX_SEGMENTS);

        // 3. intent_over_implementation (mixed): blocklist always, LLM if available
        boolean blocklistPass = IMPLEMENTATION_BLOCKLIST.stream()
                .noneMatch(suffix -> tool.name().toLowerCase().contains(suffix));
        if (judge != null) {
            // Blocklist is a quick pre-check; LLM does the full semantic check
            // Both must pass
            Map<String, Integer> llmResults = batchLlmChecks(tool);
            checks.put(
                    "intent_over_implementation",
                    blocklistPass && llmResults.getOrDefault("intent_over_implementation", 0) == 1);
            checks.put("clarity", llmResults.getOrDefault("clarity", 0) == 1);
            checks.put("name_order", llmResults.getOrDefault("name_order", 0) == 1);
        } else {
            // Without judge, only the blocklist portion runs
            checks.put("intent_over_implementation", blocklistPass);
            checks.put("clarity", "skipped");
            checks.put("name_order", "skipped");
        }

        return Map.of("toolName", tool.name(), "checks", checks);
    }

    private Map<String, Integer> batchLlmChecks(ToolDefinition tool) {
        String prompt = String.format(
                "Evaluate this tool name against quality criteria. Tool name: '%s', description: '%s'.\n\n"
                        + "For each criterion, respond with pass (1) or fail (0):\n"
                        + "1. clarity: Is the high-level purpose clear from the name itself without reading the description? "
                        + "Does it avoid low-level identifiers, obscure/ambiguous/vague terms, and redundant words?\n"
                        + "2. name_order: Does the name follow the pattern operation_system_entity_data "
                        + "(e.g. get_booking_property_name)?\n"
                        + "3. intent_over_implementation: Does the name communicate what the tool does, not how? "
                        + "Does it avoid output formats, input requirements, or implementation details "
                        + "(e.g. with_llm, via_api, from_s3)?\n\n"
                        + "Respond ONLY as JSON (no markdown): "
                        + "{\"clarity\": 0/1, \"name_order\": 0/1, \"intent_over_implementation\": 0/1}",
                tool.name(), tool.description());

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

        for (String key : List.of("clarity", "name_order", "intent_over_implementation")) {
            // Match "key": 1 or "key": 0
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
        private String name = "Tool Name Reliability";
        private double threshold = 0.8;
        private List<EvalTestCaseParam> evaluationParams = List.of();
        private String toolsKey = "tools";
        private JudgeLM judge;

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
         * Without a judge, only rule-based checks run (snakecase_format, conciseness,
         * and blocklist portion of intent_over_implementation).
         *
         * @param judge the judge LLM
         * @return this builder
         */
        public Builder judge(JudgeLM judge) {
            this.judge = judge;
            return this;
        }

        /**
         * Builds the evaluator.
         *
         * @return a new evaluator
         */
        public ToolNameReliabilityEvaluator build() {
            return new ToolNameReliabilityEvaluator(this);
        }
    }
}
