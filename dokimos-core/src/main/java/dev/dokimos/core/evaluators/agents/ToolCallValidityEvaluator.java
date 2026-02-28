package dev.dokimos.core.evaluators.agents;

import dev.dokimos.core.BaseEvaluator;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.EvaluationException;
import java.util.*;

/**
 * Validates that tool calls are syntactically correct per their JSON schema definitions.
 * <p>
 * This is a glass-box evaluator for tool proficiency that checks:
 * <ul>
 *   <li>Tool name exists in available tools</li>
 *   <li>All required parameters are present</li>
 *   <li>No unexpected parameters (if strict mode or {@code additionalProperties: false})</li>
 *   <li>Parameter types match schema types</li>
 *   <li>Enum values match if specified</li>
 * </ul>
 * No LLM is required for this evaluator.
 */
public class ToolCallValidityEvaluator extends BaseEvaluator {

    private final String toolCallsKey;
    private final String toolsKey;
    private final boolean strictMode;

    private ToolCallValidityEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        this.toolCallsKey = builder.toolCallsKey;
        this.toolsKey = builder.toolsKey;
        this.strictMode = builder.strictMode;
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
    @SuppressWarnings("unchecked")
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        Object rawToolCalls = testCase.actualOutputs().get(toolCallsKey);
        if (rawToolCalls == null) {
            throw new EvaluationException(
                    "ToolCallValidityEvaluator requires '%s' in actualOutputs".formatted(toolCallsKey));
        }

        Object rawTools = testCase.metadata().get(toolsKey);
        if (rawTools == null) {
            throw new EvaluationException("ToolCallValidityEvaluator requires '%s' in metadata".formatted(toolsKey));
        }

        List<ToolCall> toolCalls = castToolCalls(rawToolCalls);
        List<ToolDefinition> tools = castToolDefinitions(rawTools);

        if (toolCalls.isEmpty()) {
            return EvalResult.builder()
                    .name(name)
                    .score(1.0)
                    .threshold(threshold)
                    .reason("No tool calls to validate.")
                    .metadata(Map.of("validationResults", List.of()))
                    .build();
        }

        Map<String, ToolDefinition> toolMap = new HashMap<>();
        for (ToolDefinition tool : tools) {
            toolMap.put(tool.name(), tool);
        }

        List<Map<String, Object>> validationResults = new ArrayList<>();
        int validCount = 0;

        for (ToolCall call : toolCalls) {
            List<String> errors = validateToolCall(call, toolMap);
            boolean valid = errors.isEmpty();
            if (valid) validCount++;

            validationResults.add(Map.of(
                    "toolName", call.name(),
                    "valid", valid,
                    "errors", errors));
        }

        double score = (double) validCount / toolCalls.size();
        String reason = String.format("%d/%d tool calls are valid.", validCount, toolCalls.size());

        return EvalResult.builder()
                .name(name)
                .score(score)
                .threshold(threshold)
                .reason(reason)
                .metadata(Map.of("validationResults", validationResults))
                .build();
    }

    private List<String> validateToolCall(ToolCall call, Map<String, ToolDefinition> toolMap) {
        List<String> errors = new ArrayList<>();

        ToolDefinition def = toolMap.get(call.name());
        if (def == null) {
            errors.add("Unknown tool: '%s'".formatted(call.name()));
            return errors;
        }

        // Check required parameters
        for (String required : def.requiredParameters()) {
            if (!call.arguments().containsKey(required)) {
                errors.add("Missing required parameter: '%s'".formatted(required));
            }
        }

        // Check for unexpected parameters
        Set<String> definedParams = def.parameterNames();
        if (!definedParams.isEmpty()) {
            boolean disallowAdditional = strictMode || isAdditionalPropertiesFalse(def);
            if (disallowAdditional) {
                for (String argName : call.arguments().keySet()) {
                    if (!definedParams.contains(argName)) {
                        errors.add("Unexpected parameter: '%s'".formatted(argName));
                    }
                }
            }
        }

        // Check parameter types
        for (Map.Entry<String, Object> arg : call.arguments().entrySet()) {
            Map<String, Object> paramSchema = def.parameterSchema(arg.getKey());
            if (!paramSchema.isEmpty()) {
                validateType(arg.getKey(), arg.getValue(), paramSchema, errors);
                validateEnum(arg.getKey(), arg.getValue(), paramSchema, errors);
            }
        }

        return errors;
    }

    private boolean isAdditionalPropertiesFalse(ToolDefinition def) {
        Object additional = def.inputSchema().get("additionalProperties");
        return Boolean.FALSE.equals(additional);
    }

    private void validateType(String paramName, Object value, Map<String, Object> schema, List<String> errors) {
        Object schemaType = schema.get("type");
        if (schemaType == null) return;
        if (value == null) {
            errors.add("Parameter '%s' expected type '%s' but got null".formatted(paramName, schemaType));
            return;
        }

        String type = schemaType.toString();
        boolean valid =
                switch (type) {
                    case "string" -> value instanceof String;
                    case "number" -> value instanceof Number;
                    case "integer" -> value instanceof Integer || value instanceof Long;
                    case "boolean" -> value instanceof Boolean;
                    case "array" -> value instanceof List;
                    case "object" -> value instanceof Map;
                    default -> true;
                };

        if (!valid) {
            errors.add("Parameter '%s' expected type '%s' but got '%s'"
                    .formatted(paramName, type, value.getClass().getSimpleName()));
        }
    }

    @SuppressWarnings("unchecked")
    private void validateEnum(String paramName, Object value, Map<String, Object> schema, List<String> errors) {
        Object enumValues = schema.get("enum");
        if (enumValues instanceof List<?> allowed) {
            if (!allowed.contains(value)) {
                errors.add(
                        "Parameter '%s' value '%s' is not in allowed values: %s".formatted(paramName, value, allowed));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<ToolCall> castToolCalls(Object raw) {
        if (raw instanceof List<?> list) {
            if (list.isEmpty()) return List.of();
            if (list.get(0) instanceof ToolCall) {
                return (List<ToolCall>) raw;
            }
            if (list.get(0) instanceof Map) {
                return list.stream()
                        .map(item -> ToolCall.fromMap((Map<String, Object>) item))
                        .toList();
            }
        }
        throw new EvaluationException("Expected a List of ToolCall objects for key '%s'".formatted(toolCallsKey));
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
        private String name = "Tool Call Validity";
        private double threshold = 1.0;
        private List<EvalTestCaseParam> evaluationParams = List.of();
        private String toolCallsKey = "toolCalls";
        private String toolsKey = "tools";
        private boolean strictMode = false;

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
         * Sets the key used to retrieve tool definitions from metadata.
         *
         * @param toolsKey the key
         * @return this builder
         */
        public Builder toolsKey(String toolsKey) {
            this.toolsKey = toolsKey;
            return this;
        }

        /**
         * Enables strict mode which fails on any unexpected parameter.
         *
         * @param strictMode true to enable strict mode
         * @return this builder
         */
        public Builder strictMode(boolean strictMode) {
            this.strictMode = strictMode;
            return this;
        }

        /**
         * Builds the evaluator.
         *
         * @return a new evaluator
         */
        public ToolCallValidityEvaluator build() {
            return new ToolCallValidityEvaluator(this);
        }
    }
}
