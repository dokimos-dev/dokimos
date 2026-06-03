package dev.dokimos.core.agents;

import dev.dokimos.core.OutputType;
import dev.dokimos.core.exceptions.DokimosTypeConversionException;
import dev.dokimos.core.internal.Json;
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
public record ToolCall(String name, Map<String, Object> arguments, String result, Map<String, Object> metadata) {
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
        Map<String, Object> arguments =
                map.containsKey("arguments") ? (Map<String, Object>) map.get("arguments") : Map.of();
        Object rawResult = map.get("result");
        String result = rawResult != null ? rawResult.toString() : null;
        Map<String, Object> metadata =
                map.containsKey("metadata") ? (Map<String, Object>) map.get("metadata") : Map.of();
        return new ToolCall(name, arguments, result, metadata);
    }

    /**
     * Deserializes this tool call's {@code result} string into an instance of {@code type}.
     * <p>
     * This is the read-side counterpart to {@link Builder#resultJson(Object)}: a structured tool
     * result stored as compact JSON is round-tripped back into a typed object. A {@code null} or
     * blank result yields {@code null}, and the JSON literal {@code "null"} also parses to
     * {@code null}.
     * <p>
     * <b>Note:</b> the result must be a JSON string for this to work. {@link Builder#resultJson(Object)}
     * guarantees that. {@link #fromMap(Map)}, however, stores {@code rawResult.toString()}: a
     * structured {@code Map} from a deserialized dataset becomes Java-map syntax
     * (e.g. {@code {a=1}}), which is <b>not</b> valid JSON. So {@code resultAs} after {@code fromMap}
     * only works when the stored result was already a JSON string.
     *
     * @param type the target class
     * @param <T> the target type
     * @return the deserialized result, or {@code null} if the result is {@code null}/blank/JSON null
     * @throws DokimosTypeConversionException if the result cannot be parsed into {@code type}
     */
    public <T> T resultAs(Class<T> type) {
        if (result == null || result.isBlank()) {
            return null;
        }
        try {
            return Json.read(result, type);
        } catch (RuntimeException e) {
            throw new DokimosTypeConversionException("Cannot convert tool result to " + type.getName(), e);
        }
    }

    /**
     * Deserializes this tool call's {@code result} string into a generic target captured by an
     * {@link OutputType} token, for example {@code new OutputType<List<Order>>() {}}.
     * <p>
     * Use this overload when the target type has type arguments that a plain {@code Class<T>} cannot
     * express. The same null/blank/JSON-null and {@code fromMap} caveats described on
     * {@link #resultAs(Class)} apply.
     *
     * @param type the captured generic output type
     * @param <T> the target type
     * @return the deserialized result, or {@code null} if the result is {@code null}/blank/JSON null
     * @throws DokimosTypeConversionException if the result cannot be parsed into {@code type}
     */
    public <T> T resultAs(OutputType<T> type) {
        if (result == null || result.isBlank()) {
            return null;
        }
        try {
            return Json.read(result, Json.resolveType(type.getType()));
        } catch (RuntimeException e) {
            throw new DokimosTypeConversionException("Cannot convert tool result to " + type, e);
        }
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
         * Sets the tool execution result from an arbitrary value by serializing it to compact
         * (single-line) JSON and storing it in the same {@code result} string component used by
         * {@link #result(String)}.
         * <p>
         * Use for a tool that produced a structured value (a record, map, list, or other POJO)
         * rather than a pre-rendered string. A {@code null} value serializes to the JSON literal
         * {@code "null"}.
         *
         * @param value the value to serialize as the result (may be {@code null})
         * @return this builder
         * @throws IllegalArgumentException if the value cannot be serialized to JSON
         */
        public Builder resultJson(Object value) {
            this.result = Json.writeCompact(value);
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
