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

class ToolEfficiencyEvaluatorTest {

    private static EvalTestCase of(List<ToolCall> calls) {
        return EvalTestCase.builder().actualOutput("toolCalls", calls).build();
    }

    @Test
    @DisplayName("no duplicates scores 1.0")
    void noDuplicates() {
        var result = ToolEfficiencyEvaluator.builder()
                .build()
                .evaluate(of(List.of(ToolCall.of("a", Map.of()), ToolCall.of("b", Map.of()))));
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("exact duplicate call lowers the score")
    void exactDuplicate() {
        var result = ToolEfficiencyEvaluator.builder()
                .build()
                .evaluate(
                        of(List.of(ToolCall.of("search", Map.of("q", "x")), ToolCall.of("search", Map.of("q", "x")))));
        assertThat(result.score()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("argument-tolerant duplicate is detected")
    void tolerantDuplicate() {
        var result = ToolEfficiencyEvaluator.builder()
                .build()
                .evaluate(of(List.of(ToolCall.of("page", Map.of("n", 1)), ToolCall.of("page", Map.of("n", 1.0)))));
        assertThat(result.score()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("same name different args is not a duplicate")
    void differentArgsNotDuplicate() {
        var result = ToolEfficiencyEvaluator.builder()
                .build()
                .evaluate(
                        of(List.of(ToolCall.of("search", Map.of("q", "x")), ToolCall.of("search", Map.of("q", "y")))));
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("consecutive identical calls are flagged as a loop")
    void consecutiveFlagged() {
        var result = ToolEfficiencyEvaluator.builder()
                .build()
                .evaluate(of(List.of(ToolCall.of("a", Map.of()), ToolCall.of("a", Map.of()))));
        @SuppressWarnings("unchecked")
        List<String> consecutive = (List<String>) result.metadata().get("consecutiveDuplicates");
        assertThat(consecutive).containsExactly("a");
    }

    @Test
    @DisplayName("empty tool calls score 1.0")
    void empty() {
        EvalResult result = ToolEfficiencyEvaluator.builder().build().evaluate(of(List.of()));
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("three identical calls score one third")
    void threeIdentical() {
        var result = ToolEfficiencyEvaluator.builder()
                .build()
                .evaluate(of(
                        List.of(ToolCall.of("a", Map.of()), ToolCall.of("a", Map.of()), ToolCall.of("a", Map.of()))));
        assertThat(result.score()).isEqualTo(1.0 / 3.0);
    }

    @Test
    @DisplayName("a non-consecutive duplicate counts as redundant but not as a loop")
    void nonConsecutiveDuplicate() {
        var result = ToolEfficiencyEvaluator.builder()
                .build()
                .evaluate(of(
                        List.of(ToolCall.of("a", Map.of()), ToolCall.of("b", Map.of()), ToolCall.of("a", Map.of()))));

        assertThat(result.score()).isEqualTo(2.0 / 3.0);
        @SuppressWarnings("unchecked")
        List<String> redundant = (List<String>) result.metadata().get("redundantCalls");
        @SuppressWarnings("unchecked")
        List<String> consecutive = (List<String>) result.metadata().get("consecutiveDuplicates");
        assertThat(redundant).containsExactly("a");
        assertThat(consecutive).isEmpty();
    }

    @Test
    @DisplayName("metadata reports total, distinct, and redundant counts")
    void metadataCounts() {
        var result = ToolEfficiencyEvaluator.builder()
                .build()
                .evaluate(of(
                        List.of(ToolCall.of("a", Map.of()), ToolCall.of("a", Map.of()), ToolCall.of("b", Map.of()))));

        assertThat(result.metadata().get("totalCalls")).isEqualTo(3);
        assertThat(result.metadata().get("distinctCalls")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<String> redundant = (List<String>) result.metadata().get("redundantCalls");
        assertThat(redundant).containsExactly("a");
    }

    @Test
    @DisplayName("an IGNORE matcher treats same-name calls as duplicates regardless of args")
    void ignoreMatcherCollapsesByName() {
        var result = ToolEfficiencyEvaluator.builder()
                .argumentMatcher(ArgumentMatcher.of(ArgMatchMode.IGNORE))
                .build()
                .evaluate(
                        of(List.of(ToolCall.of("search", Map.of("q", "x")), ToolCall.of("search", Map.of("q", "y")))));
        assertThat(result.score()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("tool calls supplied as maps are accepted")
    void mapInput() {
        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(Map.of("name", "a"), Map.of("name", "a")))
                .build();
        var result = ToolEfficiencyEvaluator.builder().build().evaluate(testCase);
        assertThat(result.score()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("missing toolCalls throws EvaluationException")
    void missingKey() {
        var testCase = EvalTestCase.builder().actualOutput("output", "x").build();
        assertThatThrownBy(() -> ToolEfficiencyEvaluator.builder().build().evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("toolCalls");
    }
}
