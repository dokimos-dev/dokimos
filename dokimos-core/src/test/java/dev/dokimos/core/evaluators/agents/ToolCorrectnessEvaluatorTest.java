package dev.dokimos.core.evaluators.agents;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.evaluators.EvaluationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolCorrectnessEvaluatorTest {

    @Test
    void shouldReturnFullScoreWhenToolsMatch() {
        var evaluator = ToolCorrectnessEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput(
                        "toolCalls",
                        List.of(ToolCall.of("search_flights", Map.of()), ToolCall.of("book_hotel", Map.of())))
                .expectedOutput(
                        "toolCalls",
                        List.of(ToolCall.of("search_flights", Map.of()), ToolCall.of("book_hotel", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldDetectRedundantTools() {
        var evaluator = ToolCorrectnessEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput(
                        "toolCalls",
                        List.of(
                                ToolCall.of("search_flights", Map.of()),
                                ToolCall.of("book_hotel", Map.of()),
                                ToolCall.of("send_email", Map.of())))
                .expectedOutput(
                        "toolCalls",
                        List.of(ToolCall.of("search_flights", Map.of()), ToolCall.of("book_hotel", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isLessThan(1.0);
        assertThat(result.score()).isGreaterThan(0.0);
        @SuppressWarnings("unchecked")
        List<String> redundant = (List<String>) result.metadata().get("redundantTools");
        assertThat(redundant).containsExactly("send_email");
    }

    @Test
    void shouldDetectMissingTools() {
        var evaluator = ToolCorrectnessEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(ToolCall.of("search_flights", Map.of())))
                .expectedOutput(
                        "toolCalls",
                        List.of(ToolCall.of("search_flights", Map.of()), ToolCall.of("book_hotel", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isLessThan(1.0);
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) result.metadata().get("missingTools");
        assertThat(missing).containsExactly("book_hotel");
    }

    @Test
    void shouldReturnZeroWhenCompletelyWrong() {
        var evaluator = ToolCorrectnessEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(ToolCall.of("send_email", Map.of())))
                .expectedOutput("toolCalls", List.of(ToolCall.of("search_flights", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.success()).isFalse();
    }

    @Test
    void shouldHandleEmptyBothSets() {
        var evaluator = ToolCorrectnessEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of())
                .expectedOutput("toolCalls", List.of())
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldEvaluateNamesAndOrder() {
        var evaluator = ToolCorrectnessEvaluator.builder()
                .matchMode(ToolCorrectnessEvaluator.MatchMode.NAMES_AND_ORDER)
                .build();

        // Correct order
        var testCase1 = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(ToolCall.of("search", Map.of()), ToolCall.of("book", Map.of())))
                .expectedOutput("toolCalls", List.of(ToolCall.of("search", Map.of()), ToolCall.of("book", Map.of())))
                .build();

        var result1 = evaluator.evaluate(testCase1);
        assertThat(result1.score()).isEqualTo(1.0);

        // Wrong order
        var testCase2 = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(ToolCall.of("book", Map.of()), ToolCall.of("search", Map.of())))
                .expectedOutput("toolCalls", List.of(ToolCall.of("search", Map.of()), ToolCall.of("book", Map.of())))
                .build();

        var result2 = evaluator.evaluate(testCase2);
        assertThat(result2.score()).isLessThan(1.0);
    }

    @Test
    void shouldEvaluateNamesAndArgs() {
        var evaluator = ToolCorrectnessEvaluator.builder()
                .matchMode(ToolCorrectnessEvaluator.MatchMode.NAMES_AND_ARGS)
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput(
                        "toolCalls",
                        List.of(
                                ToolCall.of("search", Map.of("query", "flights")),
                                ToolCall.of("book", Map.of("id", "123"))))
                .expectedOutput(
                        "toolCalls",
                        List.of(
                                ToolCall.of("search", Map.of("query", "flights")),
                                ToolCall.of("book", Map.of("id", "456"))))
                .build();

        var result = evaluator.evaluate(testCase);

        // Only search matches (same args), book doesn't (different id)
        assertThat(result.score()).isLessThan(1.0);
        assertThat(result.score()).isGreaterThan(0.0);
    }

    @Test
    void shouldDeduplicateToolNamesInNamesOnlyMode() {
        var evaluator = ToolCorrectnessEvaluator.builder().build();

        // Agent calls search_flights 3 times, expected once
        var testCase = EvalTestCase.builder()
                .actualOutput(
                        "toolCalls",
                        List.of(
                                ToolCall.of("search_flights", Map.of("origin", "NYC")),
                                ToolCall.of("search_flights", Map.of("origin", "LAX")),
                                ToolCall.of("search_flights", Map.of("origin", "SFO"))))
                .expectedOutput("toolCalls", List.of(ToolCall.of("search_flights", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        // NAMES_ONLY uses sets, so {search_flights} == {search_flights} => F1 = 1.0
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldMatchNestedArgsInNamesAndArgsMode() {
        var evaluator = ToolCorrectnessEvaluator.builder()
                .matchMode(ToolCorrectnessEvaluator.MatchMode.NAMES_AND_ARGS)
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(ToolCall.of("search", Map.of("filter", Map.of("maxPrice", 500)))))
                .expectedOutput("toolCalls", List.of(ToolCall.of("search", Map.of("filter", Map.of("maxPrice", 500)))))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldHandleEmptyActualWithNonEmptyExpectedInOrderMode() {
        var evaluator = ToolCorrectnessEvaluator.builder()
                .matchMode(ToolCorrectnessEvaluator.MatchMode.NAMES_AND_ORDER)
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of())
                .expectedOutput("toolCalls", List.of(ToolCall.of("search", Map.of())))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void shouldThrowWhenActualToolCallsMissing() {
        var evaluator = ToolCorrectnessEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("output", "response")
                .expectedOutput("toolCalls", List.of(ToolCall.of("search", Map.of())))
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("toolCalls");
    }

    @Test
    void shouldThrowWhenExpectedToolCallsMissing() {
        var evaluator = ToolCorrectnessEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(ToolCall.of("search", Map.of())))
                .expectedOutput("other", "value")
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("toolCalls");
    }

    @Test
    void namesAndArgsRegressionNumericallyEqualArgsMatch() {
        // REGRESSION: prior exact Map.equals reported 1 vs 1.0 as a mismatch.
        var evaluator = ToolCorrectnessEvaluator.builder()
                .matchMode(ToolCorrectnessEvaluator.MatchMode.NAMES_AND_ARGS)
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(ToolCall.of("book", Map.of("nights", 1.0))))
                .expectedOutput("toolCalls", List.of(ToolCall.of("book", Map.of("nights", 1))))
                .build();

        assertThat(evaluator.evaluate(testCase).score()).isEqualTo(1.0);
    }

    @Test
    void namesAndArgsExactArgsStillMatch() {
        var evaluator = ToolCorrectnessEvaluator.builder()
                .matchMode(ToolCorrectnessEvaluator.MatchMode.NAMES_AND_ARGS)
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(ToolCall.of("book", Map.of("city", "Paris"))))
                .expectedOutput("toolCalls", List.of(ToolCall.of("book", Map.of("city", "Paris"))))
                .build();

        assertThat(evaluator.evaluate(testCase).score()).isEqualTo(1.0);
    }

    @Test
    void namesAndArgsDifferentArgsStillFail() {
        var evaluator = ToolCorrectnessEvaluator.builder()
                .matchMode(ToolCorrectnessEvaluator.MatchMode.NAMES_AND_ARGS)
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(ToolCall.of("book", Map.of("nights", 2))))
                .expectedOutput("toolCalls", List.of(ToolCall.of("book", Map.of("nights", 3))))
                .build();

        assertThat(evaluator.evaluate(testCase).score()).isEqualTo(0.0);
    }

    @Test
    void namesAndArgsStringCaseStillStrictByDefault() {
        var evaluator = ToolCorrectnessEvaluator.builder()
                .matchMode(ToolCorrectnessEvaluator.MatchMode.NAMES_AND_ARGS)
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(ToolCall.of("book", Map.of("city", "paris"))))
                .expectedOutput("toolCalls", List.of(ToolCall.of("book", Map.of("city", "Paris"))))
                .build();

        assertThat(evaluator.evaluate(testCase).score()).isEqualTo(0.0);
    }

    @Test
    void namesAndArgsNumericToleranceAppliesInsideNestedArgs() {
        var evaluator = ToolCorrectnessEvaluator.builder()
                .matchMode(ToolCorrectnessEvaluator.MatchMode.NAMES_AND_ARGS)
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(ToolCall.of("search", Map.of("filter", Map.of("maxPrice", 500.0)))))
                .expectedOutput("toolCalls", List.of(ToolCall.of("search", Map.of("filter", Map.of("maxPrice", 500)))))
                .build();

        assertThat(evaluator.evaluate(testCase).score()).isEqualTo(1.0);
    }

    @Test
    void namesAndArgsHonorsACustomArgumentMatcher() {
        // A SUBSET matcher lets the actual call carry extra arguments the expected spec omits.
        var evaluator = ToolCorrectnessEvaluator.builder()
                .matchMode(ToolCorrectnessEvaluator.MatchMode.NAMES_AND_ARGS)
                .argumentMatcher(ArgumentMatcher.of(ArgMatchMode.SUBSET))
                .build();

        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(ToolCall.of("book", Map.of("id", "X", "seat", "12A"))))
                .expectedOutput("toolCalls", List.of(ToolCall.of("book", Map.of("id", "X"))))
                .build();

        assertThat(evaluator.evaluate(testCase).score()).isEqualTo(1.0);
    }
}
