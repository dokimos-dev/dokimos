package dev.dokimos.core.agents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Coercion helpers for turning a raw value into a typed {@code List<ToolCall>}.
 *
 * <p>A tool-call list reaches an evaluator either already typed (programmatic use) or as a list of
 * maps (deserialized from a JSON or CSV dataset). {@link #coerce(Object)} normalizes both into a
 * typed list, validating every element so a malformed dataset row fails with a clear, indexed error.
 */
public final class ToolCalls {

    private ToolCalls() {}

    /**
     * Coerces a value into a typed {@code List<ToolCall>}.
     *
     * <p>Accepts an already-typed {@code List<ToolCall>}, or a {@code List<Map>} mapped element by
     * element via {@link ToolCall#fromMap(Map)}. Every element is validated; a list mixing tool calls
     * and maps (or containing any other element type) is rejected. {@code null} or an empty list
     * yields an empty list.
     *
     * @param raw the value to coerce
     * @return an unmodifiable list of tool calls, never {@code null}
     * @throws IllegalArgumentException if the value is not a list, mixes element types, or contains a
     *     malformed tool-call map (the message names the offending element index)
     */
    public static List<ToolCall> coerce(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected a List of ToolCall objects or maps, got " + typeOf(raw));
        }
        if (list.isEmpty()) {
            return List.of();
        }
        Object first = list.get(0);
        List<ToolCall> out = new ArrayList<>(list.size());
        if (first instanceof ToolCall) {
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof ToolCall call)) {
                    throw new IllegalArgumentException(
                            "Tool-call list element %d is not a ToolCall (got %s)".formatted(i, typeOf(list.get(i))));
                }
                out.add(call);
            }
            return List.copyOf(out);
        }
        if (first instanceof Map) {
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof Map<?, ?> map)) {
                    throw new IllegalArgumentException(
                            "Tool-call list element %d is not a Map (got %s)".formatted(i, typeOf(list.get(i))));
                }
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) map;
                    out.add(ToolCall.fromMap(typed));
                } catch (RuntimeException e) {
                    throw new IllegalArgumentException(
                            "Invalid tool call at index %d: %s".formatted(i, e.getMessage()), e);
                }
            }
            return List.copyOf(out);
        }
        throw new IllegalArgumentException(
                "Expected a List of ToolCall objects or maps; element 0 is " + typeOf(first));
    }

    private static String typeOf(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }
}
