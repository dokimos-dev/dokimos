package dev.dokimos.core;

import dev.dokimos.core.exceptions.DokimosTypeConversionException;
import dev.dokimos.core.internal.Json;
import java.util.HashMap;
import java.util.Map;

/**
 * A test case for evaluation.
 * <p>
 * Contains inputs provided to the system, actual outputs produced by the
 * system,
 * optional expected outputs for comparison, and additional metadata.
 *
 * @param inputs          the inputs provided to the system under test
 * @param actualOutputs   the outputs produced by the system
 * @param expectedOutputs the expected outputs for comparison
 * @param metadata        additional metadata about the test case
 */
public record EvalTestCase(
        Map<String, Object> inputs,
        Map<String, Object> actualOutputs,
        Map<String, Object> expectedOutputs,
        Map<String, Object> metadata) {
    public EvalTestCase {
        inputs = inputs != null ? Map.copyOf(inputs) : Map.of();
        actualOutputs = actualOutputs != null ? Map.copyOf(actualOutputs) : Map.of();
        expectedOutputs = expectedOutputs != null ? Map.copyOf(expectedOutputs) : Map.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Creates a test case with a single input and actual output.
     *
     * @param input        the input value
     * @param actualOutput the actual output value
     * @return a new test case
     */
    public static EvalTestCase of(String input, String actualOutput) {
        return new EvalTestCase(Map.of("input", input), Map.of("output", actualOutput), Map.of(), Map.of());
    }

    /**
     * Creates a test case with input, actual output, and expected output.
     *
     * @param input          the input value
     * @param actualOutput   the actual output value
     * @param expectedOutput the expected output value
     * @return a new test case
     */
    public static EvalTestCase of(String input, String actualOutput, String expectedOutput) {
        return new EvalTestCase(
                Map.of("input", input), Map.of("output", actualOutput), Map.of("output", expectedOutput), Map.of());
    }

    /**
     * Creates a new builder for constructing test cases.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Gets the primary input value.
     *
     * @return the input value or null if not present
     */
    public String input() {
        Object value = inputs.get("input");
        return value != null ? value.toString() : null;
    }

    /**
     * Gets the primary actual output value.
     *
     * @return the actual output value or null if not present
     */
    public String actualOutput() {
        Object value = actualOutputs.get("output");
        return value != null ? value.toString() : null;
    }

    /**
     * Gets the primary expected output value.
     *
     * @return the expected output value or null if not present
     */
    public String expectedOutput() {
        Object value = expectedOutputs.get("output");
        return value != null ? value.toString() : null;
    }

    /**
     * Reads the primary actual output ({@code "output"}) converted to the given type.
     *
     * @param type the target class
     * @param <T> the target type
     * @return the converted value, or {@code null} if no {@code "output"} entry is present
     * @throws DokimosTypeConversionException if the stored value cannot be converted to {@code type}
     */
    public <T> T actualOutputAs(Class<T> type) {
        return actualOutputAs("output", type);
    }

    /**
     * Reads the primary actual output ({@code "output"}) converted to the given generic type.
     *
     * @param type the target generic type token (for example {@code new OutputType<List<Foo>>() {}})
     * @param <T> the target type
     * @return the converted value, or {@code null} if no {@code "output"} entry is present
     * @throws DokimosTypeConversionException if the stored value cannot be converted to {@code type}
     */
    public <T> T actualOutputAs(OutputType<T> type) {
        return actualOutputAs("output", type);
    }

    /**
     * Reads the actual output under {@code key} converted to the given type.
     *
     * @param key the actual-output key
     * @param type the target class
     * @param <T> the target type
     * @return the converted value, or {@code null} if {@code key} is absent
     * @throws DokimosTypeConversionException if the stored value cannot be converted to {@code type}
     */
    public <T> T actualOutputAs(String key, Class<T> type) {
        return convertFrom(actualOutputs.get(key), type);
    }

    /**
     * Reads the actual output under {@code key} converted to the given generic type.
     *
     * @param key the actual-output key
     * @param type the target generic type token
     * @param <T> the target type
     * @return the converted value, or {@code null} if {@code key} is absent
     * @throws DokimosTypeConversionException if the stored value cannot be converted to {@code type}
     */
    public <T> T actualOutputAs(String key, OutputType<T> type) {
        return convertFrom(actualOutputs.get(key), type);
    }

    /**
     * Reads the primary expected output ({@code "output"}) converted to the given type.
     *
     * @param type the target class
     * @param <T> the target type
     * @return the converted value, or {@code null} if no {@code "output"} entry is present
     * @throws DokimosTypeConversionException if the stored value cannot be converted to {@code type}
     */
    public <T> T expectedOutputAs(Class<T> type) {
        return expectedOutputAs("output", type);
    }

    /**
     * Reads the primary expected output ({@code "output"}) converted to the given generic type.
     *
     * @param type the target generic type token (for example {@code new OutputType<List<Foo>>() {}})
     * @param <T> the target type
     * @return the converted value, or {@code null} if no {@code "output"} entry is present
     * @throws DokimosTypeConversionException if the stored value cannot be converted to {@code type}
     */
    public <T> T expectedOutputAs(OutputType<T> type) {
        return expectedOutputAs("output", type);
    }

    /**
     * Reads the expected output under {@code key} converted to the given type.
     *
     * @param key the expected-output key
     * @param type the target class
     * @param <T> the target type
     * @return the converted value, or {@code null} if {@code key} is absent
     * @throws DokimosTypeConversionException if the stored value cannot be converted to {@code type}
     */
    public <T> T expectedOutputAs(String key, Class<T> type) {
        return convertFrom(expectedOutputs.get(key), type);
    }

    /**
     * Reads the expected output under {@code key} converted to the given generic type.
     *
     * @param key the expected-output key
     * @param type the target generic type token
     * @param <T> the target type
     * @return the converted value, or {@code null} if {@code key} is absent
     * @throws DokimosTypeConversionException if the stored value cannot be converted to {@code type}
     */
    public <T> T expectedOutputAs(String key, OutputType<T> type) {
        return convertFrom(expectedOutputs.get(key), type);
    }

    /**
     * Reads the primary input ({@code "input"}) converted to the given type.
     *
     * @param type the target class
     * @param <T> the target type
     * @return the converted value, or {@code null} if no {@code "input"} entry is present
     * @throws DokimosTypeConversionException if the stored value cannot be converted to {@code type}
     */
    public <T> T inputAs(Class<T> type) {
        return inputAs("input", type);
    }

    /**
     * Reads the primary input ({@code "input"}) converted to the given generic type.
     *
     * @param type the target generic type token (for example {@code new OutputType<List<Foo>>() {}})
     * @param <T> the target type
     * @return the converted value, or {@code null} if no {@code "input"} entry is present
     * @throws DokimosTypeConversionException if the stored value cannot be converted to {@code type}
     */
    public <T> T inputAs(OutputType<T> type) {
        return inputAs("input", type);
    }

    /**
     * Reads the input under {@code key} converted to the given type.
     *
     * @param key the input key
     * @param type the target class
     * @param <T> the target type
     * @return the converted value, or {@code null} if {@code key} is absent
     * @throws DokimosTypeConversionException if the stored value cannot be converted to {@code type}
     */
    public <T> T inputAs(String key, Class<T> type) {
        return convertFrom(inputs.get(key), type);
    }

    /**
     * Reads the input under {@code key} converted to the given generic type.
     *
     * @param key the input key
     * @param type the target generic type token
     * @param <T> the target type
     * @return the converted value, or {@code null} if {@code key} is absent
     * @throws DokimosTypeConversionException if the stored value cannot be converted to {@code type}
     */
    public <T> T inputAs(String key, OutputType<T> type) {
        return convertFrom(inputs.get(key), type);
    }

    /**
     * Reads the metadata value under {@code key} converted to the given type.
     *
     * @param key the metadata key
     * @param type the target class
     * @param <T> the target type
     * @return the converted value, or {@code null} if {@code key} is absent
     * @throws DokimosTypeConversionException if the stored value cannot be converted to {@code type}
     */
    public <T> T metadataAs(String key, Class<T> type) {
        return convertFrom(metadata.get(key), type);
    }

    /**
     * Reads the metadata value under {@code key} converted to the given generic type.
     *
     * @param key the metadata key
     * @param type the target generic type token
     * @param <T> the target type
     * @return the converted value, or {@code null} if {@code key} is absent
     * @throws DokimosTypeConversionException if the stored value cannot be converted to {@code type}
     */
    public <T> T metadataAs(String key, OutputType<T> type) {
        return convertFrom(metadata.get(key), type);
    }

    private static <T> T convertFrom(Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        try {
            return Json.convert(value, type);
        } catch (RuntimeException e) {
            throw new DokimosTypeConversionException("Cannot convert value to " + type.getName(), e);
        }
    }

    private static <T> T convertFrom(Object value, OutputType<T> type) {
        if (value == null) {
            return null;
        }
        try {
            return Json.convert(value, type.toJavaType());
        } catch (RuntimeException e) {
            throw new DokimosTypeConversionException("Cannot convert value to " + type, e);
        }
    }

    /**
     * Builder for constructing test cases with multiple inputs and outputs.
     */
    public static class Builder {
        private final Map<String, Object> inputs = new HashMap<>();
        private final Map<String, Object> actualOutputs = new HashMap<>();
        private final Map<String, Object> expectedOutputs = new HashMap<>();
        private final Map<String, Object> metadata = new HashMap<>();

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
         * Adds an actual output with the given key and value.
         *
         * @param key   the output key
         * @param value the output value
         * @return this builder
         */
        public Builder actualOutput(String key, Object value) {
            this.actualOutputs.put(key, value);
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
         * Sets the primary input value.
         *
         * @param value the input value
         * @return this builder
         */
        public Builder input(String value) {
            return input("input", value);
        }

        /**
         * Sets the primary actual output value.
         *
         * @param value the actual output value
         * @return this builder
         */
        public Builder actualOutput(String value) {
            return actualOutput("output", value);
        }

        /**
         * Sets the primary expected output value.
         *
         * @param value the expected output value
         * @return this builder
         */
        public Builder expectedOutput(String value) {
            return expectedOutput("output", value);
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
         * Adds all entries from the given actual outputs map.
         *
         * @param actualOutputs the actual outputs to add
         * @return this builder
         */
        public Builder actualOutputs(Map<String, Object> actualOutputs) {
            this.actualOutputs.putAll(actualOutputs);
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
         * Builds the test case.
         *
         * @return a new test case
         */
        public EvalTestCase build() {
            return new EvalTestCase(inputs, actualOutputs, expectedOutputs, metadata);
        }
    }
}
