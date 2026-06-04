package dev.dokimos.core.agents;

import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.OutputType;
import dev.dokimos.core.exceptions.DokimosTypeConversionException;
import dev.dokimos.core.internal.Json;
import java.util.*;

/**
 * Wraps a complete agent execution trace for evaluation.
 * <p>
 * Contains the agent's final text response, all tool calls made during execution,
 * optional reasoning steps, and metadata such as latency and token usage.
 * <p>
 * The {@link #toOutputMap()} method converts the trace to a map suitable for
 * returning from a {@link dev.dokimos.core.Task} implementation.
 *
 * @param finalResponse  the agent's final text response
 * @param toolCalls      all tool calls made during execution
 * @param reasoningSteps optional reasoning/thought steps
 * @param metadata       latency, token usage, and other metadata
 */
public record AgentTrace(
        String finalResponse, List<ToolCall> toolCalls, List<String> reasoningSteps, Map<String, Object> metadata) {
    public AgentTrace {
        toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
        reasoningSteps = reasoningSteps != null ? List.copyOf(reasoningSteps) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Converts this agent trace to a map suitable for use as {@code actualOutputs}
     * in an {@link dev.dokimos.core.EvalTestCase}.
     *
     * @return a map with keys {@code "output"}, {@code "toolCalls"}, and {@code "reasoningSteps"}
     */
    public Map<String, Object> toOutputMap() {
        return Map.of(
                "output", finalResponse != null ? finalResponse : "",
                "toolCalls", toolCalls,
                "reasoningSteps", reasoningSteps);
    }

    /**
     * Builds an {@link EvalTestCase} from this trace for the given user input.
     *
     * @param input the user input the agent responded to
     * @return a test case carrying this trace's outputs
     * @see #toTestCase(String, List, List)
     */
    public EvalTestCase toTestCase(String input) {
        return toTestCase(input, List.of(), List.of());
    }

    /**
     * Builds an {@link EvalTestCase} from this trace, also wiring the available tool
     * definitions into metadata.
     *
     * @param input the user input the agent responded to
     * @param tools the tool definitions available to the agent
     * @return a test case carrying this trace's outputs and tool definitions
     * @see #toTestCase(String, List, List)
     */
    public EvalTestCase toTestCase(String input, List<ToolDefinition> tools) {
        return toTestCase(input, tools, List.of());
    }

    /**
     * Builds an {@link EvalTestCase} from this trace with everything the agent
     * evaluators need in one call.
     * <p>
     * This populates {@code output}, {@code toolCalls} and {@code reasoningSteps} in
     * actual outputs, and {@code tools} and {@code tasks} in metadata. It exists so
     * callers stop hitting {@code EvaluationException} when they forget to add the
     * {@code tools} or {@code tasks} entries that {@link dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator}
     * and {@link dev.dokimos.core.evaluators.agents.TaskCompletionEvaluator} require.
     *
     * @param input the user input the agent responded to (may be null)
     * @param tools the tool definitions available to the agent (may be null)
     * @param tasks the tasks the agent was asked to complete (may be null)
     * @return a fully-populated test case
     */
    public EvalTestCase toTestCase(String input, List<ToolDefinition> tools, List<String> tasks) {
        var builder = EvalTestCase.builder()
                .actualOutputs(toOutputMap())
                .metadata("tools", tools != null ? List.copyOf(tools) : List.of())
                .metadata("tasks", tasks != null ? List.copyOf(tasks) : List.of());
        if (input != null) {
            builder.input(input);
        }
        return builder.build();
    }

    /**
     * Returns the set of tool names used in this trace.
     *
     * @return set of tool names
     */
    public Set<String> toolNames() {
        var names = new LinkedHashSet<String>();
        for (ToolCall call : toolCalls) {
            names.add(call.name());
        }
        return Collections.unmodifiableSet(names);
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
                    "Cannot convert agent trace metadata '" + key + "' to " + type.getName(), e);
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
            throw new DokimosTypeConversionException("Cannot convert agent trace metadata '" + key + "' to " + type, e);
        }
    }

    /**
     * Creates a new builder for constructing agent traces.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing agent traces.
     */
    public static class Builder {
        private String finalResponse;
        private final List<ToolCall> toolCalls = new ArrayList<>();
        private final List<String> reasoningSteps = new ArrayList<>();
        private final Map<String, Object> metadata = new HashMap<>();

        /**
         * Sets the agent's final text response.
         *
         * @param finalResponse the final response
         * @return this builder
         */
        public Builder finalResponse(String finalResponse) {
            this.finalResponse = finalResponse;
            return this;
        }

        /**
         * Adds a tool call to the trace.
         *
         * @param toolCall the tool call
         * @return this builder
         */
        public Builder addToolCall(ToolCall toolCall) {
            this.toolCalls.add(toolCall);
            return this;
        }

        /**
         * Sets all tool calls.
         *
         * @param toolCalls the tool calls
         * @return this builder
         */
        public Builder toolCalls(List<ToolCall> toolCalls) {
            this.toolCalls.clear();
            this.toolCalls.addAll(toolCalls);
            return this;
        }

        /**
         * Adds a reasoning step.
         *
         * @param step the reasoning step
         * @return this builder
         */
        public Builder addReasoningStep(String step) {
            this.reasoningSteps.add(step);
            return this;
        }

        /**
         * Sets all reasoning steps.
         *
         * @param steps the reasoning steps
         * @return this builder
         */
        public Builder reasoningSteps(List<String> steps) {
            this.reasoningSteps.clear();
            this.reasoningSteps.addAll(steps);
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
            this.metadata.clear();
            this.metadata.putAll(metadata);
            return this;
        }

        /**
         * Builds the agent trace.
         *
         * @return a new agent trace
         */
        public AgentTrace build() {
            return new AgentTrace(finalResponse, toolCalls, reasoningSteps, metadata);
        }
    }
}
