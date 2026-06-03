package dev.dokimos.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemResultTest {

    @Test
    void shouldBeSuccessWhenAllEvalsPassed() {
        var item = new ItemResult(
                Example.of("a question", "this should be the model's answer"),
                Map.of("output", "the model's answer"),
                List.of(
                        EvalResult.success("eval1", 0.92, "this was good"),
                        EvalResult.success("eval2", 0.8, "it was ok")));

        assertThat(item.success()).isTrue();
    }

    @Test
    void shouldFailWithEvals() {
        var item = new ItemResult(
                Example.of("What is 3*6?", "3*6 is equal to 18."),
                Map.of("output", "12."),
                List.of(
                        EvalResult.success("eval1", 0.9, "the answer is almost correct"),
                        EvalResult.failure("eval2", 0.1, "the answer is incorrect")));

        assertThat(item.success()).isFalse();
    }

    @Test
    void shouldConvertToTestCase() {
        var example = Example.of("question", "expected");
        var actualOutputs = Map.<String, Object>of("output", "actual");

        var item = new ItemResult(example, actualOutputs, List.of());

        var testCase = item.toTestCase();
        assertThat(testCase.expectedOutput()).isEqualTo("expected");
        assertThat(testCase.actualOutput()).isEqualTo("actual");
    }

    @Test
    void shouldDefaultMetricsToNullForThreeArgConstructor() {
        var item = new ItemResult(Example.of("q", "a"), Map.of("output", "a"), List.of());

        assertThat(item.metrics()).isNull();
    }

    @Test
    void shouldCarryCallMetrics() {
        var item = new ItemResult(
                Example.of("q", "a"),
                Map.of("output", "a"),
                List.of(EvalResult.success("eval1", 1.0, "ok")),
                new CallMetrics(120, 40, 0.0023, 512L));

        assertThat(item.metrics().tokensIn()).isEqualTo(120);
        assertThat(item.metrics().tokensOut()).isEqualTo(40);
        assertThat(item.metrics().costUsd()).isEqualTo(0.0023);
        assertThat(item.metrics().latencyMs()).isEqualTo(512L);
        assertThat(item.success()).isTrue();
    }

    @Test
    void shouldTolerateNullValueInActualOutputs() {
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("output", null);

        var item = new ItemResult(Example.of("q", "a"), outputs, List.of(EvalResult.success("eval1", 1.0, "ok")));

        assertThat(item.actualOutputs()).containsKey("output");
        assertThat(item.actualOutputs().get("output")).isNull();
        assertThat(item.success()).isTrue();
    }

    @Test
    void shouldNotBeSuccessWhenEvalResultsEmpty() {
        var item = new ItemResult(Example.of("q", "a"), Map.of("output", "a"), List.of());

        assertThat(item.success()).isFalse();
    }

    @Test
    void shouldNotBeSuccessWhenEvalResultsNull() {
        var item = new ItemResult(Example.of("q", "a"), Map.of("output", "a"), null);

        assertThat(item.success()).isFalse();
    }
}
