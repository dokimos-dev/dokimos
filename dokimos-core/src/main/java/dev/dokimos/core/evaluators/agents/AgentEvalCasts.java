package dev.dokimos.core.evaluators.agents;

import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.EvaluationException;
import java.util.List;
import java.util.Map;

/**
 * Shared coercion helpers for agent evaluators.
 * <p>
 * Agent evaluators read tool calls and tool definitions from an
 * {@link dev.dokimos.core.EvalTestCase}, where a value may already be a typed
 * {@link ToolCall} / {@link ToolDefinition} list (programmatic use) or a list of
 * maps (deserialized from a JSON or CSV dataset). These helpers normalize either
 * form into the typed list every evaluator expects.
 */
final class AgentEvalCasts {

    private AgentEvalCasts() {}

    /**
     * Coerces a raw value into a list of {@link ToolCall}.
     *
     * @param raw the value read from the test case (a list of {@link ToolCall} or of maps)
     * @param key the key the value was read from, used for error messages
     * @return the typed list, or an empty list when the input list is empty
     * @throws EvaluationException if the value is not a list of tool calls or maps
     */
    @SuppressWarnings("unchecked")
    static List<ToolCall> toolCalls(Object raw, String key) {
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
        throw new EvaluationException("Expected a List of ToolCall objects for key '%s'".formatted(key));
    }

    /**
     * Coerces a raw value into a list of {@link ToolDefinition}.
     *
     * @param raw the value read from the test case (a list of {@link ToolDefinition} or of maps)
     * @param key the key the value was read from, used for error messages
     * @return the typed list, or an empty list when the input list is empty
     * @throws EvaluationException if the value is not a list of tool definitions or maps
     */
    @SuppressWarnings("unchecked")
    static List<ToolDefinition> toolDefinitions(Object raw, String key) {
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
        throw new EvaluationException("Expected a List of ToolDefinition objects for key '%s'".formatted(key));
    }
}
