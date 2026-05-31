package dev.dokimos.core.evaluators.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.evaluators.EvaluationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ToolErrorEvaluatorTest {

    private static EvalTestCase of(List<ToolCall> calls) {
        return EvalTestCase.builder().actualOutput("toolCalls", calls).build();
    }

    private static ToolCall withResult(String name, String result) {
        return ToolCall.builder().name(name).result(result).build();
    }

    @Test
    @DisplayName("all successful results score 1.0")
    void allSucceed() {
        var result = ToolErrorEvaluator.builder()
                .build()
                .evaluate(of(List.of(withResult("a", "{\"ok\":true}"), withResult("b", "done"))));
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("null or blank result counts as error by default")
    void blankIsError() {
        var result = ToolErrorEvaluator.builder()
                .build()
                .evaluate(of(List.of(withResult("a", "done"), ToolCall.of("b", Map.of()))));
        assertThat(result.score()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("blank tolerated when treatBlankAsError is false")
    void blankTolerated() {
        var result = ToolErrorEvaluator.builder()
                .treatBlankAsError(false)
                .build()
                .evaluate(of(List.of(ToolCall.of("b", Map.of()))));
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("JSON top-level error field is detected")
    void jsonErrorField() {
        var result = ToolErrorEvaluator.builder()
                .build()
                .evaluate(of(List.of(withResult("a", "{\"error\":\"not found\"}"), withResult("b", "ok"))));
        assertThat(result.score()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("the word error inside a value is not a false positive")
    void noSubstringFalsePositive() {
        var result = ToolErrorEvaluator.builder()
                .build()
                .evaluate(of(List.of(withResult("a", "{\"message\":\"no error occurred\"}"))));
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("custom error detector fires")
    void customDetector() {
        var result = ToolErrorEvaluator.builder()
                .errorDetector(r -> r.contains("HTTP 500"))
                .build()
                .evaluate(of(List.of(withResult("a", "HTTP 500 internal"), withResult("b", "HTTP 200"))));
        assertThat(result.score()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("empty tool calls score 1.0")
    void empty() {
        EvalResult result = ToolErrorEvaluator.builder().build().evaluate(of(List.of()));
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("a JSON error field set to null is not treated as an error")
    void jsonErrorFieldNull() {
        var result = ToolErrorEvaluator.builder()
                .build()
                .evaluate(of(List.of(withResult("a", "{\"error\":null,\"data\":1}"))));
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("a JSON array result is not treated as an error")
    void jsonArrayResult() {
        var result = ToolErrorEvaluator.builder().build().evaluate(of(List.of(withResult("a", "[{\"error\":\"x\"}]"))));
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("all calls failing scores 0.0")
    void allFail() {
        var result = ToolErrorEvaluator.builder()
                .build()
                .evaluate(of(List.of(withResult("a", "{\"error\":\"boom\"}"), ToolCall.of("b", Map.of()))));
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("JSON error detection can be disabled")
    void jsonDetectionDisabled() {
        var result = ToolErrorEvaluator.builder()
                .detectJsonErrorField(false)
                .build()
                .evaluate(of(List.of(withResult("a", "{\"error\":\"ignored\"}"))));
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("a custom tool calls key is honored")
    void customKey() {
        var testCase = EvalTestCase.builder()
                .actualOutput("calls", List.of(withResult("a", "done"), ToolCall.of("b", Map.of())))
                .build();
        var result = ToolErrorEvaluator.builder().toolCallsKey("calls").build().evaluate(testCase);
        assertThat(result.score()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("per-call metadata records the failing tool and reason")
    void perCallMetadata() {
        var result = ToolErrorEvaluator.builder()
                .build()
                .evaluate(of(List.of(withResult("ok_tool", "fine"), withResult("bad_tool", "{\"error\":\"nope\"}"))));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> perCall =
                (List<Map<String, Object>>) result.metadata().get("results");
        assertThat(perCall).hasSize(2);
        assertThat(perCall.get(0)).containsEntry("toolName", "ok_tool").containsEntry("errored", false);
        assertThat(perCall.get(1)).containsEntry("toolName", "bad_tool").containsEntry("errored", true);
        assertThat(perCall.get(1).get("reason").toString()).isNotBlank();
    }

    @Test
    @DisplayName("tool calls supplied as maps are accepted")
    void mapInput() {
        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(Map.of("name", "a", "result", "done")))
                .build();
        var result = ToolErrorEvaluator.builder().build().evaluate(testCase);
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("missing toolCalls throws EvaluationException")
    void missingKey() {
        var testCase = EvalTestCase.builder().actualOutput("output", "x").build();
        assertThatThrownBy(() -> ToolErrorEvaluator.builder().build().evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("toolCalls");
    }
}
