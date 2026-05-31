package dev.dokimos.core.evaluators.agents;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TolerantArgumentMatcherTest {

    @Nested
    @DisplayName("EXACT mode")
    class Exact {

        private final ArgumentMatcher matcher = ArgumentMatcher.tolerant();

        @Test
        @DisplayName("equal maps match")
        void equalMatch() {
            assertThat(matcher.matches(Map.of("a", "x", "n", 1), Map.of("a", "x", "n", 1)))
                    .isTrue();
        }

        @Test
        @DisplayName("numerically equal values match across int, long and double")
        void numericTolerance() {
            assertThat(matcher.matches(Map.of("n", 1), Map.of("n", 1.0))).isTrue();
            assertThat(matcher.matches(Map.of("n", 1L), Map.of("n", 1))).isTrue();
            assertThat(matcher.matches(Map.of("n", 1.50), Map.of("n", 1.5))).isTrue();
        }

        @Test
        @DisplayName("different numeric values do not match")
        void numericMismatch() {
            assertThat(matcher.matches(Map.of("n", 1), Map.of("n", 2))).isFalse();
        }

        @Test
        @DisplayName("extra or missing keys do not match")
        void keySetMismatch() {
            assertThat(matcher.matches(Map.of("a", "x"), Map.of("a", "x", "b", "y")))
                    .isFalse();
            assertThat(matcher.matches(Map.of("a", "x", "b", "y"), Map.of("a", "x")))
                    .isFalse();
        }

        @Test
        @DisplayName("strings are case-sensitive and whitespace-sensitive by default")
        void strictStringsByDefault() {
            assertThat(matcher.matches(Map.of("a", "X"), Map.of("a", "x"))).isFalse();
            assertThat(matcher.matches(Map.of("a", " x "), Map.of("a", "x"))).isFalse();
        }

        @Test
        @DisplayName("nested maps and lists compare recursively with numeric tolerance")
        void nestedStructures() {
            Map<String, Object> expected = Map.of("o", Map.of("n", 1), "l", List.of(1, 2));
            Map<String, Object> actual = Map.of("o", Map.of("n", 1.0), "l", List.of(1.0, 2.0));
            assertThat(matcher.matches(expected, actual)).isTrue();
        }
    }

    @Nested
    @DisplayName("opt-in string tolerances")
    class StringTolerances {

        @Test
        @DisplayName("trim and case-insensitive match when enabled")
        void trimAndCase() {
            ArgumentMatcher matcher = TolerantArgumentMatcher.builder()
                    .trimStrings(true)
                    .caseInsensitive(true)
                    .build();
            assertThat(matcher.matches(Map.of("a", "Hello"), Map.of("a", " hello ")))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("key-set modes")
    class KeySetModes {

        @Test
        @DisplayName("SUBSET requires expected keys present, allows extras")
        void subset() {
            ArgumentMatcher matcher = ArgumentMatcher.of(ArgMatchMode.SUBSET);
            assertThat(matcher.matches(Map.of("a", "x"), Map.of("a", "x", "b", "y")))
                    .isTrue();
            assertThat(matcher.matches(Map.of("a", "x", "c", "z"), Map.of("a", "x")))
                    .isFalse();
        }

        @Test
        @DisplayName("SUPERSET allows omissions, rejects unexpected keys")
        void superset() {
            ArgumentMatcher matcher = ArgumentMatcher.of(ArgMatchMode.SUPERSET);
            assertThat(matcher.matches(Map.of("a", "x", "b", "y"), Map.of("a", "x")))
                    .isTrue();
            assertThat(matcher.matches(Map.of("a", "x"), Map.of("a", "x", "b", "y")))
                    .isFalse();
        }

        @Test
        @DisplayName("IGNORE always matches")
        void ignore() {
            ArgumentMatcher matcher = ArgumentMatcher.of(ArgMatchMode.IGNORE);
            assertThat(matcher.matches(Map.of("a", "x"), Map.of("totally", "different")))
                    .isTrue();
        }
    }

    @Test
    @DisplayName("null argument maps are treated as empty")
    void nullMaps() {
        assertThat(ArgumentMatcher.tolerant().matches(null, null)).isTrue();
        assertThat(ArgumentMatcher.tolerant().matches(null, Map.of("a", "x"))).isFalse();
    }

    @Test
    @DisplayName("non-finite doubles compare without throwing")
    void nonFiniteNumbers() {
        var matcher = ArgumentMatcher.tolerant();
        assertThat(matcher.matches(Map.of("n", Double.NaN), Map.of("n", Double.NaN)))
                .isTrue();
        assertThat(matcher.matches(Map.of("n", Double.POSITIVE_INFINITY), Map.of("n", Double.POSITIVE_INFINITY)))
                .isTrue();
        assertThat(matcher.matches(Map.of("n", Double.POSITIVE_INFINITY), Map.of("n", 1.0)))
                .isFalse();
    }

    @Nested
    @DisplayName("numeric edge cases")
    class NumericEdges {

        private final ArgumentMatcher matcher = ArgumentMatcher.tolerant();

        @Test
        @DisplayName("BigDecimal and BigInteger compare by value with the boxed primitives")
        void bigNumbers() {
            assertThat(matcher.matches(Map.of("n", new BigDecimal("1.0")), Map.of("n", 1)))
                    .isTrue();
            assertThat(matcher.matches(Map.of("n", new BigInteger("42")), Map.of("n", 42L)))
                    .isTrue();
            assertThat(matcher.matches(Map.of("n", new BigDecimal("1.5")), Map.of("n", 2)))
                    .isFalse();
        }

        @Test
        @DisplayName("very large doubles in scientific notation compare correctly")
        void scientificNotation() {
            assertThat(matcher.matches(Map.of("n", 1.0e10), Map.of("n", 10_000_000_000L)))
                    .isTrue();
        }

        @Test
        @DisplayName("a number and its string form do not match")
        void numberVsStringDoesNotMatch() {
            assertThat(matcher.matches(Map.of("n", 1), Map.of("n", "1"))).isFalse();
        }

        @Test
        @DisplayName("NaN does not match a finite number")
        void nanVsFinite() {
            assertThat(matcher.matches(Map.of("n", Double.NaN), Map.of("n", 1.0)))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("structural edge cases")
    class Structural {

        private final ArgumentMatcher matcher = ArgumentMatcher.tolerant();

        @Test
        @DisplayName("empty maps match")
        void emptyMaps() {
            assertThat(matcher.matches(Map.of(), Map.of())).isTrue();
        }

        @Test
        @DisplayName("lists of different sizes do not match")
        void listSizeMismatch() {
            assertThat(matcher.matches(Map.of("l", List.of(1, 2)), Map.of("l", List.of(1, 2, 3))))
                    .isFalse();
        }

        @Test
        @DisplayName("list element order is significant")
        void listOrderMatters() {
            assertThat(matcher.matches(Map.of("l", List.of(1, 2)), Map.of("l", List.of(2, 1))))
                    .isFalse();
        }

        @Test
        @DisplayName("a map value and a scalar value of the same key do not match")
        void typeMismatch() {
            assertThat(matcher.matches(Map.of("a", Map.of("x", 1)), Map.of("a", "scalar")))
                    .isFalse();
        }

        @Test
        @DisplayName("deeply nested mismatch is detected")
        void deepNestedMismatch() {
            Map<String, Object> expected = Map.of("o", Map.of("p", List.of(Map.of("n", 1))));
            Map<String, Object> actual = Map.of("o", Map.of("p", List.of(Map.of("n", 2))));
            assertThat(matcher.matches(expected, actual)).isFalse();
        }

        @Test
        @DisplayName("a null value matches only another null value")
        void nullValues() {
            Map<String, Object> expectedNull = new HashMap<>();
            expectedNull.put("a", null);
            Map<String, Object> actualNull = new HashMap<>();
            actualNull.put("a", null);
            assertThat(matcher.matches(expectedNull, actualNull)).isTrue();

            Map<String, Object> actualValue = new HashMap<>();
            actualValue.put("a", "x");
            assertThat(matcher.matches(expectedNull, actualValue)).isFalse();
        }
    }

    @Nested
    @DisplayName("string tolerance combinations")
    class StringToleranceCombos {

        @Test
        @DisplayName("trim only does not relax case")
        void trimOnly() {
            ArgumentMatcher matcher =
                    TolerantArgumentMatcher.builder().trimStrings(true).build();
            assertThat(matcher.matches(Map.of("a", "Hello"), Map.of("a", " Hello ")))
                    .isTrue();
            assertThat(matcher.matches(Map.of("a", "Hello"), Map.of("a", " hello ")))
                    .isFalse();
        }

        @Test
        @DisplayName("case-insensitive only does not trim")
        void caseOnly() {
            ArgumentMatcher matcher =
                    TolerantArgumentMatcher.builder().caseInsensitive(true).build();
            assertThat(matcher.matches(Map.of("a", "Hello"), Map.of("a", "hello")))
                    .isTrue();
            assertThat(matcher.matches(Map.of("a", "Hello"), Map.of("a", " hello")))
                    .isFalse();
        }
    }

    @Test
    @DisplayName("key-set modes still compare values on shared keys")
    void keySetModesCompareValues() {
        ArgumentMatcher subset = ArgumentMatcher.of(ArgMatchMode.SUBSET);
        // expected key present in actual but with a different value -> no match
        assertThat(subset.matches(Map.of("a", "x"), Map.of("a", "y", "b", "z"))).isFalse();

        ArgumentMatcher superset = ArgumentMatcher.of(ArgMatchMode.SUPERSET);
        assertThat(superset.matches(Map.of("a", "x", "b", "z"), Map.of("a", "y")))
                .isFalse();
    }

    @Test
    @DisplayName("a custom matcher lambda is honored")
    void customLambda() {
        ArgumentMatcher anySize = (expected, actual) -> expected.size() == actual.size();
        assertThat(anySize.matches(Map.of("a", 1), Map.of("z", 99))).isTrue();
        assertThat(anySize.matches(Map.of("a", 1), Map.of("a", 1, "b", 2))).isFalse();
    }

    @Test
    @DisplayName("nested array contents compare with numeric tolerance via java arrays as lists")
    void listsAreCheckedElementwise() {
        var matcher = ArgumentMatcher.tolerant();
        assertThat(matcher.matches(Map.of("l", Arrays.asList(1, 2, 3)), Map.of("l", Arrays.asList(1.0, 2.0, 3.0))))
                .isTrue();
    }
}
