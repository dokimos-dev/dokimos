package dev.dokimos.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


class ItemResultTest {

    @Test
    void shouldBeSuccessWhenAllEvalsPassed() {
        var item = new ItemResult(
                Example.of("a question", "this should be the model's answer"),
                Map.of("output", "the model's answer"),
                List.of(
                        EvalResult.success("eval1", 0.92, "this was good"),
                        EvalResult.success("eval2", 0.8, "it was ok")
                )
        );

        assertThat(item.success()).isTrue();
    }

    @Test
    void shouldFailWithEvals() {
        var item = new ItemResult(
                Example.of("What is 3*6?", "3*6 is equal to 18."),
                Map.of("output", "12."),
                List.of(
                        EvalResult.success("eval1", 0.9, "the answer is almost correct"),
                        EvalResult.failure("eval2", 0.1, "the answer is incorrect")
                )
        );

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
}
