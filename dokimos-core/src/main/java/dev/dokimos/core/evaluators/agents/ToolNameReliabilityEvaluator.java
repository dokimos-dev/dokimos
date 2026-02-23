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
 * This is a glass-box evaluator for tool reliability. Checks include:
 * <ul>
 *   <li>Format: Uses snake_case or camelCase consistently</li>
 *   <li>Length: Name between 2–64 characters</li>
 *   <li>Verb-prefixed: Starts with an action verb</li>
 *   <li>No ambiguity: Does not use generic names (LLM-assisted if judge provided)</li>
 *   <li>Descriptive: Name clearly indicates purpose (LLM-assisted if judge provided)</li>
 * </ul>
 */
public class ToolNameReliabilityEvaluator extends BaseEvaluator {

    private static final Pattern SNAKE_CASE = Pattern.compile("^[a-z][a-z0-9]*(_[a-z0-9]+)*$");
    private static final Pattern CAMEL_CASE = Pattern.compile("^[a-z][a-zA-Z0-9]*$");
    private static final Set<String> ACTION_VERBS = Set.of(
            "get", "search", "create", "update", "delete", "list", "check", "find",
            "fetch", "set", "add", "remove", "send", "validate", "verify", "calculate",
            "compute", "generate", "convert", "parse", "format", "filter", "sort",
            "book", "cancel", "submit", "query", "lookup", "retrieve", "save", "load"
    );
    private static final Set<String> GENERIC_NAMES = Set.of(
            "process", "handle", "do_thing", "doThing", "run", "execute", "perform",
            "do_stuff", "doStuff", "action", "operation", "task", "method", "function",
            "helper", "util", "tool"
    );

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
            throw new EvaluationException(
                    "ToolNameReliabilityEvaluator requires '%s' in metadata".formatted(toolsKey));
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
            Map<String, Object> checks = evaluateToolName(tool);
            perToolResults.add(checks);

            @SuppressWarnings("unchecked")
            Map<String, Boolean> checkResults = (Map<String, Boolean>) checks.get("checks");
            long passed = checkResults.values().stream().filter(v -> v).count();
            totalScore += (double) passed / checkResults.size();
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
        Map<String, Boolean> checks = new LinkedHashMap<>();

        // 1. Format check: snake_case or camelCase
        checks.put("format", SNAKE_CASE.matcher(tool.name()).matches()
                || CAMEL_CASE.matcher(tool.name()).matches());

        // 2. Length check: 2–64 characters
        checks.put("length", tool.name().length() >= 2 && tool.name().length() <= 64);

        // 3. Verb-prefixed check
        checks.put("verbPrefixed", isVerbPrefixed(tool.name()));

        // 4. No ambiguity check (LLM has final say if provided, else rule-based)
        boolean notAmbiguous;
        if (judge != null) {
            notAmbiguous = llmCheckNotAmbiguous(tool);
        } else {
            notAmbiguous = !GENERIC_NAMES.contains(tool.name());
        }
        checks.put("notAmbiguous", notAmbiguous);

        // 5. Descriptive check (optional LLM)
        if (judge != null) {
            checks.put("descriptive", llmCheckDescriptive(tool));
        } else {
            // Rule-based fallback: name is at least 2 parts (verb + noun)
            checks.put("descriptive", countNameParts(tool.name()) >= 2);
        }

        return Map.of(
                "toolName", tool.name(),
                "checks", checks
        );
    }

    private boolean isVerbPrefixed(String name) {
        String firstWord = extractFirstWord(name);
        return ACTION_VERBS.contains(firstWord.toLowerCase());
    }

    private String extractFirstWord(String name) {
        // Handle snake_case
        if (name.contains("_")) {
            return name.substring(0, name.indexOf('_'));
        }
        // Handle camelCase
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isUpperCase(c) && !sb.isEmpty()) break;
            sb.append(c);
        }
        return sb.toString();
    }

    private int countNameParts(String name) {
        if (name.contains("_")) {
            return name.split("_").length;
        }
        // Count camelCase parts
        int parts = 1;
        for (int i = 1; i < name.length(); i++) {
            if (Character.isUpperCase(name.charAt(i))) parts++;
        }
        return parts;
    }

    private boolean llmCheckNotAmbiguous(ToolDefinition tool) {
        String prompt = String.format(
                "Is the tool name '%s' (description: '%s') ambiguous or too generic? "
                        + "Answer with ONLY 'yes' or 'no'. 'yes' means it IS ambiguous.",
                tool.name(), tool.description()
        );
        String response = judge.generate(prompt).trim().toLowerCase();
        return response.startsWith("no");
    }

    private boolean llmCheckDescriptive(ToolDefinition tool) {
        String prompt = String.format(
                "Does the tool name '%s' clearly indicate what the tool does? "
                        + "The tool's description is: '%s'. "
                        + "Answer with ONLY 'yes' or 'no'. 'yes' means it IS descriptive.",
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
         * Builds the evaluator.
         *
         * @return a new evaluator
         */
        public ToolNameReliabilityEvaluator build() {
            return new ToolNameReliabilityEvaluator(this);
        }
    }
}
