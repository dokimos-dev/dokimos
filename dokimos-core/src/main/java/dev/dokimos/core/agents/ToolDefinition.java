package dev.dokimos.core.agents;

import java.util.*;

/**
 * Describes an available tool's contract including its name, description, and JSON schema.
 * <p>
 * Used by tool reliability evaluators to assess tool naming, description quality,
 * and by tool validity evaluators to validate tool call arguments against schemas.
 *
 * @param name        the tool name
 * @param description the tool description
 * @param inputSchema the JSON Schema for the tool's arguments
 */
public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> inputSchema
) {
    public ToolDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool definition name must not be null or blank");
        }
        inputSchema = inputSchema != null ? Map.copyOf(inputSchema) : Map.of();
    }

    /**
     * Creates a tool definition with a name, description, and input schema.
     *
     * @param name        the tool name
     * @param description the tool description
     * @param inputSchema the JSON Schema for arguments
     * @return a new tool definition
     */
    public static ToolDefinition of(String name, String description, Map<String, Object> inputSchema) {
        return new ToolDefinition(name, description, inputSchema);
    }

    /**
     * Creates a tool definition from a map, typically when deserializing from a JSON dataset.
     * <p>
     * Expected keys: {@code "name"}, {@code "description"}, {@code "inputSchema"}.
     *
     * @param map the map to create from
     * @return a new tool definition
     */
    @SuppressWarnings("unchecked")
    public static ToolDefinition fromMap(Map<String, Object> map) {
        String name = (String) map.get("name");
        String description = map.containsKey("description") ? (String) map.get("description") : "";
        Map<String, Object> inputSchema = map.containsKey("inputSchema")
                ? (Map<String, Object>) map.get("inputSchema")
                : Map.of();
        return new ToolDefinition(name, description, inputSchema);
    }

    /**
     * Extracts required parameter names from the input schema.
     *
     * @return list of required parameter names, or empty list if none defined
     */
    @SuppressWarnings("unchecked")
    public List<String> requiredParameters() {
        Object required = inputSchema.get("required");
        if (required instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    /**
     * Extracts all parameter names from the input schema properties.
     *
     * @return set of parameter names, or empty set if none defined
     */
    @SuppressWarnings("unchecked")
    public Set<String> parameterNames() {
        Object properties = inputSchema.get("properties");
        if (properties instanceof Map<?, ?> map) {
            return new LinkedHashSet<>(((Map<String, Object>) map).keySet());
        }
        return Set.of();
    }

    /**
     * Gets the schema for a specific parameter.
     *
     * @param paramName the parameter name
     * @return the parameter's schema map, or empty map if not found
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parameterSchema(String paramName) {
        Object properties = inputSchema.get("properties");
        if (properties instanceof Map<?, ?> map) {
            Object schema = ((Map<String, Object>) map).get(paramName);
            if (schema instanceof Map<?, ?> schemaMap) {
                return (Map<String, Object>) schemaMap;
            }
        }
        return Map.of();
    }

    /**
     * Creates a new builder for constructing tool definitions.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing tool definitions.
     */
    public static class Builder {
        private String name;
        private String description = "";
        private final Map<String, Object> inputSchema = new HashMap<>();

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
         * Sets the tool description.
         *
         * @param description the tool description
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the input schema.
         *
         * @param inputSchema the JSON Schema map
         * @return this builder
         */
        public Builder inputSchema(Map<String, Object> inputSchema) {
            this.inputSchema.putAll(inputSchema);
            return this;
        }

        /**
         * Builds the tool definition.
         *
         * @return a new tool definition
         */
        public ToolDefinition build() {
            return new ToolDefinition(name, description, inputSchema);
        }
    }
}
