package dev.dokimos.core;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.evaluators.EvaluationException;
import dev.dokimos.core.evaluators.StructuralMatchEvaluator;
import dev.dokimos.core.evaluators.StructuralMatchMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StructuralMatchEvaluatorTest {

    private static EvalTestCase testCase(Object expected, Object actual) {
        return EvalTestCase.builder()
                .expectedOutput("output", expected)
                .actualOutput("output", actual)
                .build();
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    // ---------- STRICT (default) ----------

    @Test
    void strictExactMatchScoresOne() {
        var evaluator = StructuralMatchEvaluator.builder().build();

        var result = evaluator.evaluate(testCase(map("a", 1, "b", "x"), map("a", 1, "b", "x")));

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void strictExtraFieldIsPenalized() {
        var evaluator = StructuralMatchEvaluator.builder().build();

        // expected has 1 leaf, actual adds an extra -> union denominator 2, matched 1.
        var result = evaluator.evaluate(testCase(map("a", 1), map("a", 1, "b", 2)));

        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.success()).isFalse();
        assertThat(result.reason()).contains("$.b");
    }

    @Test
    void strictReorderedArrayScoresLow() {
        var evaluator = StructuralMatchEvaluator.builder().build();

        var result = evaluator.evaluate(testCase(List.of(1, 2, 3), List.of(3, 2, 1)));

        // positions 0 and 2 differ, position 1 matches -> 1/3.
        assertThat(result.score()).isEqualTo(1.0 / 3.0);
    }

    @Test
    void strictNumberFiveMatchesFivePointZero() {
        var evaluator = StructuralMatchEvaluator.builder().build();

        var result = evaluator.evaluate(testCase(map("n", 5), map("n", 5.0)));

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void strictDroppedFieldScoresLow() {
        var evaluator = StructuralMatchEvaluator.builder().build();

        var result = evaluator.evaluate(testCase(map("a", 1, "b", 2), map("a", 1)));

        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.reason()).contains("$.b");
    }

    @Test
    void strictNestedObjectAndListOfObjects() {
        var evaluator = StructuralMatchEvaluator.builder().build();

        Object expected = map(
                "user", map("name", "Ada", "age", 36),
                "items", List.of(map("id", 1, "qty", 2), map("id", 2, "qty", 5)));
        var result = evaluator.evaluate(testCase(expected, expected));

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void strictNullDistinctFromMissing() {
        var evaluator = StructuralMatchEvaluator.builder().build();

        Map<String, Object> expected = map("a", 1);
        expected.put("b", null);
        var result = evaluator.evaluate(testCase(expected, map("a", 1)));

        // b: null (expected) vs missing (actual) -> distinct in STRICT.
        assertThat(result.score()).isEqualTo(0.5);
    }

    // ---------- LENIENT ----------

    @Test
    void lenientIgnoresExtraField() {
        var evaluator =
                StructuralMatchEvaluator.builder().mode(StructuralMatchMode.LENIENT).build();

        var result = evaluator.evaluate(testCase(map("a", 1), map("a", 1, "b", 2)));

        // denominator = expected leaves only -> extra ignored.
        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void lenientIgnoresArrayOrderAsMultiset() {
        var evaluator =
                StructuralMatchEvaluator.builder().mode(StructuralMatchMode.LENIENT).build();

        assertThat(evaluator.evaluate(testCase(List.of(1, 2, 3), List.of(3, 1, 2))).score())
                .isEqualTo(1.0);
    }

    @Test
    void lenientMultisetRejectsMissingDuplicate() {
        var evaluator =
                StructuralMatchEvaluator.builder().mode(StructuralMatchMode.LENIENT).build();

        // [1,1,2] vs [1,2] -> one of the two expected 1's is unmatched.
        var result = evaluator.evaluate(testCase(List.of(1, 1, 2), List.of(1, 2)));

        assertThat(result.score()).isLessThan(1.0);
    }

    @Test
    void lenientSubsetMatchScoresOne() {
        var evaluator =
                StructuralMatchEvaluator.builder().mode(StructuralMatchMode.LENIENT).build();

        Object expected = map("name", "Ada");
        Object actual = map("name", "Ada", "age", 36, "email", "ada@example.com");
        assertThat(evaluator.evaluate(testCase(expected, actual)).score()).isEqualTo(1.0);
    }

    @Test
    void lenientNullEqualsMissing() {
        var evaluator =
                StructuralMatchEvaluator.builder().mode(StructuralMatchMode.LENIENT).build();

        Map<String, Object> expected = map("a", 1);
        expected.put("b", null);
        var result = evaluator.evaluate(testCase(expected, map("a", 1)));

        assertThat(result.score()).isEqualTo(1.0);
    }

    // ---------- numerics ----------

    @Test
    void bigDecimalScaleInsensitive() {
        var evaluator = StructuralMatchEvaluator.builder().build();

        var result = evaluator.evaluate(testCase(map("p", new java.math.BigDecimal("1.0")),
                map("p", new java.math.BigDecimal("1.00"))));

        assertThat(result.score()).isEqualTo(1.0);
    }

    // ---------- partial score ----------

    @Test
    void partialScoreNineOfTenStrict() {
        var evaluator = StructuralMatchEvaluator.builder().build();

        Map<String, Object> expected = new LinkedHashMap<>();
        Map<String, Object> actual = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) {
            expected.put("k" + i, i);
            actual.put("k" + i, i == 9 ? 999 : i);
        }
        var result = evaluator.evaluate(testCase(expected, actual));

        assertThat(result.score()).isEqualTo(0.9);
    }

    @Test
    void partialScoreNineOfTenLenient() {
        var evaluator =
                StructuralMatchEvaluator.builder().mode(StructuralMatchMode.LENIENT).build();

        Map<String, Object> expected = new LinkedHashMap<>();
        Map<String, Object> actual = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) {
            expected.put("k" + i, i);
            actual.put("k" + i, i == 9 ? 999 : i);
        }
        var result = evaluator.evaluate(testCase(expected, actual));

        assertThat(result.score()).isEqualTo(0.9);
    }

    // ---------- binary ----------

    @Test
    void binaryCollapsesToOneOrZero() {
        var evaluator = StructuralMatchEvaluator.builder().binary().build();

        assertThat(evaluator.evaluate(testCase(map("a", 1), map("a", 1))).score()).isEqualTo(1.0);
        assertThat(evaluator.evaluate(testCase(map("a", 1), map("a", 2))).score()).isEqualTo(0.0);
        // any difference collapses, even a single mismatched leaf among many.
        assertThat(evaluator.evaluate(testCase(map("a", 1, "b", 2), map("a", 1, "b", 3))).score())
                .isEqualTo(0.0);
    }

    // ---------- error / edge cases ----------

    @Test
    void duplicateKeysFailWithReason() {
        var evaluator = StructuralMatchEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .expectedOutput("output", "{\"x\":1}")
                .actualOutput("output", "{\"x\":1,\"x\":2}")
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void nanFailsWithReason() {
        var evaluator = StructuralMatchEvaluator.builder().build();

        var result = testCase(map("n", Double.NaN), map("n", 1.0));
        assertThatThrownBy(() -> evaluator.evaluate(result))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("non-finite");
    }

    @Test
    void infinityFailsWithReason() {
        var evaluator = StructuralMatchEvaluator.builder().build();

        var result = testCase(map("n", 1.0), map("n", Double.POSITIVE_INFINITY));
        assertThatThrownBy(() -> evaluator.evaluate(result))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("non-finite");
    }

    @Test
    void nullExpectedThrows() {
        var evaluator = StructuralMatchEvaluator.builder().build();

        var testCase = EvalTestCase.builder().actualOutput("output", map("a", 1)).build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("expectedOutputs");
    }

    @Test
    void absentActualScoresZero() {
        var evaluator = StructuralMatchEvaluator.builder().build();

        var testCase = EvalTestCase.builder().expectedOutput("output", map("a", 1, "b", 2)).build();

        // actual normalizes to a null node; both expected leaves mismatch.
        var result = evaluator.evaluate(testCase);
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void customOutputKey() {
        var evaluator = StructuralMatchEvaluator.builder().outputKey("answer").build();

        var testCase = EvalTestCase.builder()
                .expectedOutput("answer", map("a", 1))
                .actualOutput("answer", map("a", 1))
                .build();

        assertThat(evaluator.evaluate(testCase).score()).isEqualTo(1.0);
    }

    @Test
    void stringOperandParsedAsJson() {
        var evaluator = StructuralMatchEvaluator.builder().build();

        // expected is a Map, actual is a JSON string -> compare object vs object.
        var testCase = EvalTestCase.builder()
                .expectedOutput("output", map("a", 1, "b", List.of(1, 2)))
                .actualOutput("output", "{\"a\":1,\"b\":[1,2]}")
                .build();

        assertThat(evaluator.evaluate(testCase).score()).isEqualTo(1.0);
    }

    @Test
    void bothStringOperandsParsedAsJson() {
        var evaluator =
                StructuralMatchEvaluator.builder().mode(StructuralMatchMode.LENIENT).build();

        var testCase = EvalTestCase.builder()
                .expectedOutput("output", "{\"a\":1}")
                .actualOutput("output", "{\"a\":1.0,\"extra\":true}")
                .build();

        assertThat(evaluator.evaluate(testCase).score()).isEqualTo(1.0);
    }

    @Test
    void invalidJsonStringFails() {
        var evaluator = StructuralMatchEvaluator.builder().build();

        var testCase = EvalTestCase.builder()
                .expectedOutput("output", map("a", 1))
                .actualOutput("output", "{not valid json")
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase)).isInstanceOf(EvaluationException.class);
    }

    // ---------- regression: comparator correctness ----------

    @Test
    void lenientArrayMatchingPairsSubsetAndSpecificElementsOptimally() {
        // Regression: greedy first-fit let the subset element {a:1} steal the {a:1,b:2} actual,
        // starving the specific expected element. Maximum bipartite matching pairs both -> 1.0.
        var evaluator =
                StructuralMatchEvaluator.builder().mode(StructuralMatchMode.LENIENT).build();

        var testCase = testCase(
                List.of(map("a", 1), map("a", 1, "b", 2)), List.of(map("a", 1, "b", 2), map("a", 1)));

        assertThat(evaluator.evaluate(testCase).score()).isEqualTo(1.0);
    }

    @Test
    void smallDistinctNumbersDoNotCollapse() {
        // No absolute epsilon: genuinely distinct near-zero values must not compare equal.
        var evaluator = StructuralMatchEvaluator.builder().build();

        assertThat(evaluator.evaluate(testCase(0, 5e-10)).score()).isEqualTo(0.0);
        assertThat(evaluator.evaluate(testCase(1e-10, 2e-10)).score()).isEqualTo(0.0);
        // Scale differences still match by value.
        assertThat(evaluator.evaluate(testCase(5, 5.0)).score()).isEqualTo(1.0);
    }

    @Test
    void strictTypeMismatchArrayVsObjectScoresZero() {
        var evaluator = StructuralMatchEvaluator.builder().mode(StructuralMatchMode.STRICT).build();

        var testCase = testCase(map("a", List.of(1)), map("a", map("x", 1)));

        assertThat(evaluator.evaluate(testCase).score()).isEqualTo(0.0);
    }
}
