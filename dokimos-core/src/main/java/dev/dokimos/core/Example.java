package dev.dokimos.core;

import java.util.HashMap;
import java.util.Map;

/**
 * A dataset example with inputs, expected outputs, and metadata.
 * <p>
 * {@code datasetItemId} participates in {@code equals}/{@code hashCode} (record-generated), so a
 * server-resolved example and an otherwise-identical file/inline example are not equal. This only
 * matters to callers that dedup examples by equality, which the framework itself does not do.
 *
 * @param inputs          the input values
 * @param expectedOutputs the expected output values
 * @param metadata        additional metadata
 * @param datasetItemId   stable id of the server dataset item this example came from, or null for
 *                        ad-hoc or file/classpath examples; used to pair items across runs
 */
public record Example(
        Map<String, Object> inputs,
        Map<String, Object> expectedOutputs,
        Map<String, Object> metadata,
        String datasetItemId) {
    /**
     * Compact constructor that creates immutable copies of all maps. The {@code datasetItemId} may be
     * null.
     */
    public Example {
        inputs = inputs != null ? Map.copyOf(inputs) : Map.of();
        expectedOutputs = expectedOutputs != null ? Map.copyOf(expectedOutputs) : Map.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Creates an example with no dataset item id. Lets existing positional callers keep compiling.
     *
     * @param inputs          the input values
     * @param expectedOutputs the expected output values
     * @param metadata        additional metadata
     */
    public Example(Map<String, Object> inputs, Map<String, Object> expectedOutputs, Map<String, Object> metadata) {
        this(inputs, expectedOutputs, metadata, null);
    }

    /**
     * Creates an example with a single input and expected output.
     *
     * @param input          the input value
     * @param expectedOutput the expected output value
     * @return a new example
     */
    public static Example of(String input, String expectedOutput) {
        return new Example(Map.of("input", input), Map.of("output", expectedOutput), Map.of(), null);
    }

    /**
     * Creates a new builder for constructing examples.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the primary input value.
     *
     * @return the input value or null if not present
     */
    public String input() {
        Object value = inputs.get("input");
        return value != null ? value.toString() : null;
    }

    /**
     * Returns the primary expected output value.
     *
     * @return the expected output value or null if not present
     */
    public String expectedOutput() {
        Object value = expectedOutputs.get("output");
        return value != null ? value.toString() : null;
    }

    /**
     * Converts this example to a test case with the given actual outputs.
     *
     * @param actualOutputs the actual outputs produced by the system
     * @return a new test case
     */
    public EvalTestCase toTestCase(Map<String, Object> actualOutputs) {
        return new EvalTestCase(inputs, actualOutputs, expectedOutputs, metadata);
    }

    /**
     * Converts this example to a test case with a single actual output.
     *
     * @param actualOutput the actual output value
     * @return a new test case
     */
    public EvalTestCase toTestCase(String actualOutput) {
        return toTestCase(Map.of("output", actualOutput));
    }

    /**
     * Builder for constructing examples with multiple inputs and outputs.
     */
    public static class Builder {
        private final Map<String, Object> inputs = new HashMap<>();
        private final Map<String, Object> expectedOutputs = new HashMap<>();
        private final Map<String, Object> metadata = new HashMap<>();
        private String datasetItemId;

        /**
         * Adds an input with the given key and value.
         *
         * @param key   the input key
         * @param value the input value
         * @return this builder
         */
        public Builder input(String key, Object value) {
            this.inputs.put(key, value);
            return this;
        }

        /**
         * Adds an expected output with the given key and value.
         *
         * @param key   the output key
         * @param value the output value
         * @return this builder
         */
        public Builder expectedOutput(String key, Object value) {
            this.expectedOutputs.put(key, value);
            return this;
        }

        /**
         * Adds metadata with the given key and value.
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
         * Adds all entries from the given inputs map.
         *
         * @param inputs the inputs to add
         * @return this builder
         */
        public Builder inputs(Map<String, Object> inputs) {
            this.inputs.putAll(inputs);
            return this;
        }

        /**
         * Adds all entries from the given expected outputs map.
         *
         * @param expectedOutputs the expected outputs to add
         * @return this builder
         */
        public Builder expectedOutputs(Map<String, Object> expectedOutputs) {
            this.expectedOutputs.putAll(expectedOutputs);
            return this;
        }

        /**
         * Adds all entries from the given metadata map.
         *
         * @param metadata the metadata to add
         * @return this builder
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        /**
         * Sets the stable dataset item id this example originates from.
         *
         * @param datasetItemId the dataset item id, or null if not from a server dataset
         * @return this builder
         */
        public Builder datasetItemId(String datasetItemId) {
            this.datasetItemId = datasetItemId;
            return this;
        }

        public Example build() {
            return new Example(inputs, expectedOutputs, metadata, datasetItemId);
        }
    }
}
