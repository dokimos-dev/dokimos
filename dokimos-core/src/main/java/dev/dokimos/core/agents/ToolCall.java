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
        String result;
        if (rawResult == null) {
            result = null;
        } else if (rawResult instanceof String s) {
            result = s;
        } else {
            result = Json.writeCompact(rawResult);
        }
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
     * The result must be a JSON string for this to work. Both {@link Builder#resultJson(Object)} and
     * {@link #fromMap(Map)} guarantee that: {@code fromMap} keeps a {@code String} result verbatim and
     * serializes any structured value (a {@code Map}, list, or other object) to compact JSON, so a
     * structured result from a deserialized dataset round-trips here as well.
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
     * express. The same null/blank/JSON-null handling described on {@link #resultAs(Class)} applies.
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
     * Converts this tool call's {@code arguments} map into an instance of {@code type}.
     * <p>
     * The arguments are already an in-memory map, so this converts in place (no textual round-trip).
     * {@link #arguments()} is never {@code null} (it defaults to an empty map); converting an empty map
     * to a record or bean yields an instance with default/empty fields.
     *
     * @param type the target class
     * @param <T> the target type
     * @return the converted arguments
     * @throws DokimosTypeConversionException if the arguments cannot be converted to {@code type}
     */
    public <T> T argumentsAs(Class<T> type) {
        try {
            return Json.convert(arguments, type);
        } catch (RuntimeException e) {
            throw new DokimosTypeConversionException("Cannot convert tool arguments to " + type.getName(), e);
        }
    }

    /**
     * Converts this tool call's {@code arguments} map into a generic target captured by an
     * {@link OutputType} token, for example {@code new OutputType<List<String>>() {}}.
     * <p>
     * Use this overload when the target type has type arguments that a plain {@code Class<T>} cannot
     * express.
     *
     * @param type the captured generic output type
     * @param <T> the target type
     * @return the converted arguments
     * @throws DokimosTypeConversionException if the arguments cannot be converted to {@code type}
     */
    public <T> T argumentsAs(OutputType<T> type) {
        try {
            return Json.convert(arguments, Json.resolveType(type.getType()));
        } catch (RuntimeException e) {
            throw new DokimosTypeConversionException("Cannot convert tool arguments to " + type, e);
        }
    }

    /**
     * Reads the metadata entry stored under {@code key} and converts it into an instance of
     * {@code type}.
     * <p>
     * The value is already in memory, so this converts in place (no textual round-trip). An absent key
     * (or a {@code null} stored value) yields {@code null}.
     *
     * @param key the metadata key
     * @param type the target class
     * @param <T> the target type
     * @return the converted value, or {@code null} if the key is absent or its value is {@code null}
     * @throws DokimosTypeConversionException if the value cannot be converted to {@code type}
     */
    public <T> T metadataAs(String key, Class<T> type) {
        try {
            return Json.convert(metadata.get(key), type);
        } catch (RuntimeException e) {
            throw new DokimosTypeConversionException(
                    "Cannot convert tool metadata '" + key + "' to " + type.getName(), e);
        }
    }

    /**
     * Reads the metadata entry stored under {@code key} and converts it into a generic target captured
     * by an {@link OutputType} token, for example {@code new OutputType<List<String>>() {}}.
     * <p>
     * An absent key (or a {@code null} stored value) yields {@code null}.
     *
     * @param key the metadata key
     * @param type the captured generic output type
     * @param <T> the target type
     * @return the converted value, or {@code null} if the key is absent or its value is {@code null}
     * @throws DokimosTypeConversionException if the value cannot be converted to {@code type}
     */
    public <T> T metadataAs(String key, OutputType<T> type) {
        try {
            return Json.convert(metadata.get(key), Json.resolveType(type.getType()));
        } catch (RuntimeException e) {
            throw new DokimosTypeConversionException("Cannot convert tool metadata '" + key + "' to " + type, e);
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
