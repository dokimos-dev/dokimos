package dev.dokimos.core.agents;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single tool invocation made by an AI agent.
 * <p>
 * Contains the tool name, arguments passed, optional result, and metadata
 * such as latency or token usage.
 *
 * @param name      the name of the tool that was called
 * @param arguments the arguments passed to the tool
 * @param result    the tool execution result (may be null)
 * @param metadata  optional metadata (latency, tokens, etc.)
 */
public record ToolCall(
        String name,
        Map<String, Object> arguments,
        String result,
        Map<String, Object> metadata
) {
    public ToolCall {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool call name must not be null or blank");
        }
        arguments = arguments != null ? Collections.unmodifiableMap(new HashMap<>(arguments)) : Map.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Creates a tool call with just a name and arguments.
     *
     * @param name      the tool name
     * @param arguments the arguments passed to the tool
     * @return a new tool call
     */
    public static ToolCall of(String name, Map<String, Object> arguments) {
        return new ToolCall(name, arguments, null, Map.of());
    }

    /**
     * Creates a tool call from a map, typically when deserializing from a JSON dataset.
     * <p>
     * Expected keys: {@code "name"}, {@code "arguments"}, {@code "result"}, {@code "metadata"}.
     *
     * @param map the map to create from
     * @return a new tool call
     */
    @SuppressWarnings("unchecked")
    public static ToolCall fromMap(Map<String, Object> map) {
        String name = (String) map.get("name");
        Map<String, Object> arguments = map.containsKey("arguments")
                ? (Map<String, Object>) map.get("arguments")
                : Map.of();
        Object rawResult = map.get("result");
        String result = rawResult != null ? rawResult.toString() : null;
        Map<String, Object> metadata = map.containsKey("metadata")
                ? (Map<String, Object>) map.get("metadata")
                : Map.of();
        return new ToolCall(name, arguments, result, metadata);
    }

    /**
     * Creates a new builder for constructing tool calls.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing tool calls.
     */
    public static class Builder {
        private String name;
        private final Map<String, Object> arguments = new HashMap<>();
        private String result;
        private final Map<String, Object> metadata = new HashMap<>();

        /**
         * Sets the tool name.
         *
         * @param name the tool name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Adds an argument.
         *
         * @param key   the argument name
         * @param value the argument value
         * @return this builder
         */
        public Builder argument(String key, Object value) {
            this.arguments.put(key, value);
            return this;
        }

        /**
         * Sets all arguments.
         *
         * @param arguments the arguments map
         * @return this builder
         */
        public Builder arguments(Map<String, Object> arguments) {
            this.arguments.putAll(arguments);
            return this;
        }

        /**
         * Sets the tool execution result.
         *
         * @param result the result
         * @return this builder
         */
        public Builder result(String result) {
            this.result = result;
            return this;
        }

        /**
         * Adds a metadata entry.
         *
         * @param key   the metadata key
         * @param value the metadata value
         * @return this builder
         */
        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        /**
         * Sets all metadata.
         *
         * @param metadata the metadata map
         * @return this builder
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        /**
         * Builds the tool call.
         *
         * @return a new tool call
         */
        public ToolCall build() {
            return new ToolCall(name, arguments, result, metadata);
        }
    }
}
