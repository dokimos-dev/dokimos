package dev.dokimos.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskResultTest {

    @Test
    void taskResultOfDefensivelyCopiesAndDefaultsNullMetrics() {
        var outputs = new HashMap<String, Object>();
        outputs.put("k", "v");
        var taskResult = TaskResult.of(outputs);
        outputs.put("k2", "v2");

        assertThat(taskResult.metrics()).isNull();
        assertThat(taskResult.outputs()).containsExactlyEntriesOf(Map.of("k", "v"));

        var nullOutputs = new TaskResult(null, null);
        assertThat(nullOutputs.outputs()).isEmpty();
    }
}
