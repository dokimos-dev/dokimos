package io.dokimos.core;

import java.util.HashMap;
import java.util.Map;

public record Example(
        Map<String, Object> inputs,
        Map<String, Object> expectedOutputs,
        Map<String, Object> metadata
) {
    public Example {
        inputs = inputs != null ? Map.copyOf(inputs) : Map.of();
        expectedOutputs = expectedOutputs != null ? Map.copyOf(expectedOutputs) : Map.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Simple factory for basic input/output pairs.
     */
    public static Example of(String input, String expectedOutput) {
        return new Example(
                Map.of("input", input),
                Map.of("output", expectedOutput),
                Map.of()
        );
    }

    /**
     * Convenience accessor for getting the input value.
     */
    public String input() {
        Object value = inputs.get("input");
        return value != null ? value.toString() : null;
    }

    /**
     * Convenience accessor for getting output value.
     */
    public String expectedOutput() {
        Object value = expectedOutputs.get("output");
        return value != null ? value.toString() : null;
    }

    /**
     * Convert to a {@link EvalTestCase} by given outputs.
     */
    public EvalTestCase toTestCase(Map<String, Object> actualOutputs) {
        return EvalTestCase.builder()
                .input(input())
                .actualOutput(actualOutputs.getOrDefault("output", "").toString())
                .expectedOutput(expectedOutput())
                .metadata(mergeMaps(metadata, Map.of(
                        "_inputs", inputs,
                        "_expectedOutputs", expectedOutputs,
                        "_actualOutputs", actualOutputs
                )))
                .build();
    }

    /**
     * Convenience method for simple single-output cases.
     */
    public EvalTestCase toTestCase(String actualOutput) {
        return toTestCase(Map.of("output", actualOutput));
    }

    private Map<String, Object> mergeMaps(Map<String, Object> a, Map<String, Object> b) {
        var merged = new HashMap<>(a);
        merged.putAll(b);
        return merged;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, Object> inputs = new HashMap<>();
        private final Map<String, Object> expectedOutputs = new HashMap<>();
        private final Map<String, Object> metadata = new HashMap<>();

        public Builder input(String key, Object value) {
            this.inputs.put(key, value);
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

        public Builder inputs(Map<String, Object> inputs) {
            this.inputs.putAll(inputs);
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

        public Example build() {
            return new Example(inputs, expectedOutputs, metadata);
        }
    }
}
