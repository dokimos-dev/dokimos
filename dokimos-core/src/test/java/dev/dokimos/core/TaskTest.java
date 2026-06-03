package dev.dokimos.core;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskTest {

    private static Example exampleWithInput(String key, Object value) {
        return new Example(Map.of(key, value), Map.of(), Map.of());
    }

    @Test
    void typedShouldWrapValueUnderOutputKey() {
        record Whisky(String name, int age) {}

        Task task = Task.typed(ex -> new Whisky("Lagavulin", 16));

        Map<String, Object> outputs = task.run(exampleWithInput("query", "islay"));

        assertThat(outputs).containsOnlyKeys("output");
        assertThat(outputs.get("output")).isEqualTo(new Whisky("Lagavulin", 16));
    }

    @Test
    void typedShouldNotDoubleNestWhenFunctionReturnsMap() {
        Task task = Task.typed(ex -> Map.of("answer", "42", "confidence", 0.9));

        Map<String, Object> outputs = task.run(exampleWithInput("query", "meaning"));

        assertThat(outputs).containsOnlyKeys("answer", "confidence");
        assertThat(outputs).containsEntry("answer", "42").containsEntry("confidence", 0.9);
        assertThat(outputs).doesNotContainKey("output");
    }

    @Test
    void typedShouldExposeFunctionInputToTheBody() {
        Task task = Task.typed(ex -> ex.inputs().get("query") + "!");

        Map<String, Object> outputs = task.run(exampleWithInput("query", "hello"));

        assertThat(outputs).containsEntry("output", "hello!");
    }

    @Test
    void typedGenericCallShouldCompileWithExplicitTypeWitness() {
        // Compile-time check that the generic factory infers and accepts a typed function.
        Task task = Task.<String>typed(ex -> "value");

        assertThat(task.run(exampleWithInput("k", "v"))).containsEntry("output", "value");
    }
}
