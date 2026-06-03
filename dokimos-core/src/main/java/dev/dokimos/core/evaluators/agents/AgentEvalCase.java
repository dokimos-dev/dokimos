package dev.dokimos.core.evaluators.agents;

import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import java.util.List;

/**
 * Typed entry point for building an {@link EvalTestCase} for the agent evaluators.
 * <p>
 * The agent evaluators in this package read their inputs from fixed string keys:
 * tool calls from {@code "toolCalls"} in actual outputs, available tools from
 * {@code "tools"} in metadata, the task list from {@code "tasks"} in metadata, and
 * expected tool calls from {@code "toolCalls"} in expected outputs. This builder lets
 * callers populate those slots with typed values instead of hand-placing the keys.
 * <p>
 * Only the slots that are set are written, so the resulting test case carries exactly
 * the keys the configured evaluators require.
 *
 * <pre>{@code
 * EvalTestCase testCase = AgentEvalCase.builder()
 *         .input("What's the weather in Paris?")
 *         .toolCalls(actualCalls)
 *         .tools(availableTools)
 *         .build();
 *
 * EvalResult result = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
 * }</pre>
 */
public final class AgentEvalCase {

    private AgentEvalCase() {}

    /**
     * Creates a new builder for constructing an agent {@link EvalTestCase}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing an {@link EvalTestCase} that targets the agent evaluators.
     */
    public static final class Builder {
        private String input;
        private List<ToolCall> toolCalls;
        private List<ToolDefinition> tools;
        private List<ToolCall> expectedToolCalls;
        private List<String> tasks;

        private Builder() {}

        /**
         * Sets the primary input value (the {@code "input"} key).
         *
         * @param input the input value
         * @return this builder
         */
        public Builder input(String input) {
            this.input = input;
            return this;
        }

        /**
         * Sets the actual tool calls the agent made (the {@code "toolCalls"} key in actual outputs).
         *
         * @param toolCalls the tool calls
         * @return this builder
         */
        public Builder toolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = List.copyOf(toolCalls);
            return this;
        }

        /**
         * Sets the tools available to the agent (the {@code "tools"} key in metadata).
         *
         * @param tools the tool definitions
         * @return this builder
         */
        public Builder tools(List<ToolDefinition> tools) {
            this.tools = List.copyOf(tools);
            return this;
        }

        /**
         * Sets the expected tool calls (the {@code "toolCalls"} key in expected outputs).
         *
         * @param expectedToolCalls the expected tool calls
         * @return this builder
         */
        public Builder expectedToolCalls(List<ToolCall> expectedToolCalls) {
            this.expectedToolCalls = List.copyOf(expectedToolCalls);
            return this;
        }

        /**
         * Sets the tasks the agent was asked to complete (the {@code "tasks"} key in metadata).
         *
         * @param tasks the task descriptions
         * @return this builder
         */
        public Builder tasks(List<String> tasks) {
            this.tasks = List.copyOf(tasks);
            return this;
        }

        /**
         * Builds the test case, writing only the slots that were set.
         *
         * @return a new test case populated with the keys the agent evaluators read
         */
        public EvalTestCase build() {
            EvalTestCase.Builder builder = EvalTestCase.builder();
            if (input != null) {
                builder.input(input);
            }
            if (toolCalls != null) {
                builder.actualOutput("toolCalls", toolCalls);
            }
            if (tools != null) {
                builder.metadata("tools", tools);
            }
            if (expectedToolCalls != null) {
                builder.expectedOutput("toolCalls", expectedToolCalls);
            }
            if (tasks != null) {
                builder.metadata("tasks", tasks);
            }
            return builder.build();
        }
    }
}
