package io.dokimos.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record EvalTestCase(
        Map<String, Object> inputs,
        Map<String, Object> actualOutputs,
        Map<String, Object> expectedOutputs,
        Map<String, Object> metadata
) {
    public EvalTestCase {
        inputs = inputs != null ? Map.copyOf(inputs) : Map.of();
        actualOutputs = actualOutputs != null ? Map.copyOf(actualOutputs) : Map.of();
        expectedOutputs = expectedOutputs != null ? Map.copyOf(expectedOutputs) : Map.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Simple factory for single input/output case.
     */
    public static EvalTestCase of(String input, String actualOutput) {
        return new EvalTestCase(
                Map.of("input", input),
                Map.of("output", actualOutput),
                Map.of(),
                Map.of()
        );
    }

    /**
     * Factory with expected output.
     */
    public static EvalTestCase of(String input, String actualOutput, String expectedOutput) {
        return new EvalTestCase(
                Map.of("input", input),
                Map.of("output", actualOutput),
                Map.of("output", expectedOutput),
                Map.of()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    // Convenience accessors for common single input/output case
    public String input() {
        Object value = inputs.get("input");
        return value != null ? value.toString() : null;
    }

    public String actualOutput() {
        Object value = actualOutputs.get("output");
        return value != null ? value.toString() : null;
    }

    public String expectedOutput() {
        Object value = expectedOutputs.get("output");
        return value != null ? value.toString() : null;
    }

    public static class Builder {
        private final Map<String, Object> inputs = new HashMap<>();
        private final Map<String, Object> actualOutputs = new HashMap<>();
        private final Map<String, Object> expectedOutputs = new HashMap<>();
        private final Map<String, Object> metadata = new HashMap<>();

        public Builder input(String key, Object value) {
            this.inputs.put(key, value);
            return this;
        }

        public Builder actualOutput(String key, Object value) {
            this.actualOutputs.put(key, value);
            return this;
        }

        public Builder expectedOutput(String key, Object value) {
            this.expectedOutputs.put(key, value);
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder input(String value) {
            return input("input", value);
        }

        public Builder actualOutput(String value) {
            return actualOutput("output", value);
        }

        public Builder expectedOutput(String value) {
            return expectedOutput("output", value);
        }

        // Bulk setters
        public Builder inputs(Map<String, Object> inputs) {
            this.inputs.putAll(inputs);
            return this;
        }

        public Builder actualOutputs(Map<String, Object> actualOutputs) {
            this.actualOutputs.putAll(actualOutputs);
            return this;
        }

        public Builder expectedOutputs(Map<String, Object> expectedOutputs) {
            this.expectedOutputs.putAll(expectedOutputs);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        public EvalTestCase build() {
            return new EvalTestCase(inputs, actualOutputs, expectedOutputs, metadata);
        }
    }
}