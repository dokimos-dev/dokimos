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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ToolTrajectoryEvaluatorTest {

    private static EvalTestCase testCase(List<ToolCall> actual, List<ToolCall> expected) {
        return EvalTestCase.builder()
                .actualOutput("toolCalls", actual)
                .expectedOutput("toolCalls", expected)
                .build();
    }

    private static ToolCall call(String name) {
        return ToolCall.of(name, Map.of());
    }

    @Nested
    @DisplayName("STRICT")
    class Strict {
        @Test
        @DisplayName("identical sequence scores 1.0")
        void exact() {
            var result = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.STRICT)
                    .build()
                    .evaluate(testCase(List.of(call("a"), call("b")), List.of(call("a"), call("b"))));
            assertThat(result.score()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("wrong order fails")
        void wrongOrder() {
            var result = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.STRICT)
                    .build()
                    .evaluate(testCase(List.of(call("b"), call("a")), List.of(call("a"), call("b"))));
            assertThat(result.score()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("IN_ORDER")
    class InOrder {
        @Test
        @DisplayName("ordered subsequence with extras is graded by LCS")
        void graded() {
            var result = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.IN_ORDER)
                    .build()
                    .evaluate(testCase(List.of(call("a"), call("x"), call("b")), List.of(call("a"), call("b"))));
            // LCS=2, maxLen=3
            assertThat(result.score()).isEqualTo(2.0 / 3.0);
        }
    }

    @Nested
    @DisplayName("ANY_ORDER")
    class AnyOrder {
        @Test
        @DisplayName("same set different order scores 1.0")
        void multiset() {
            var result = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.ANY_ORDER)
                    .build()
                    .evaluate(testCase(List.of(call("b"), call("a")), List.of(call("a"), call("b"))));
            assertThat(result.score()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("SUPERSET and SUBSET")
    class SuperSub {
        @Test
        @DisplayName("SUPERSET passes when actual contains all expected")
        void superset() {
            var result = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.SUPERSET)
                    .build()
                    .evaluate(testCase(List.of(call("a"), call("b"), call("c")), List.of(call("a"), call("c"))));
            assertThat(result.score()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("SUBSET fails when actual has a call not in expected")
        void subsetFails() {
            var result = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.SUBSET)
                    .build()
                    .evaluate(testCase(List.of(call("a"), call("z")), List.of(call("a"), call("b"), call("c"))));
            assertThat(result.score()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("PRECISION and RECALL")
    class PrecisionRecall {
        @Test
        @DisplayName("precision is matched over actual count")
        void precision() {
            var result = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.PRECISION)
                    .build()
                    .evaluate(testCase(List.of(call("a"), call("x")), List.of(call("a"))));
            assertThat(result.score()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("recall is matched over expected count")
        void recall() {
            var result = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.RECALL)
                    .build()
                    .evaluate(testCase(List.of(call("a")), List.of(call("a"), call("b"))));
            assertThat(result.score()).isEqualTo(0.5);
        }
    }

    @Nested
    @DisplayName("argument matching")
    class Arguments {
        @Test
        @DisplayName("default compares arguments")
        void comparesArgsByDefault() {
            var actual = List.of(ToolCall.of("search", Map.of("q", "shoes")));
            var expected = List.of(ToolCall.of("search", Map.of("q", "boots")));
            var result = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.STRICT)
                    .build()
                    .evaluate(testCase(actual, expected));
            assertThat(result.score()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("explicit IGNORE matcher compares names only")
        void ignoreMatcherComparesNamesOnly() {
            var actual = List.of(ToolCall.of("search", Map.of("q", "shoes")));
            var expected = List.of(ToolCall.of("search", Map.of("q", "boots")));
            var result = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.STRICT)
                    .argumentMatcher(ArgumentMatcher.of(ArgMatchMode.IGNORE))
                    .build()
                    .evaluate(testCase(actual, expected));
            assertThat(result.score()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("per-tool override enforces arguments for one tool only")
        void perToolOverride() {
            var actual = List.of(ToolCall.of("search", Map.of("q", "shoes")), ToolCall.of("book", Map.of("id", "X")));
            var expected = List.of(ToolCall.of("search", Map.of("q", "boots")), ToolCall.of("book", Map.of("id", "X")));
            var result = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.STRICT)
                    .argumentMatcher("search", ArgumentMatcher.tolerant())
                    .build()
                    .evaluate(testCase(actual, expected));
            // search args differ -> strict fails
            assertThat(result.score()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("default matcher passes under STRICT when arguments match")
        void defaultMatcherPassesWhenArgumentsMatch() {
            var actual = List.of(ToolCall.of("search", Map.of("q", "shoes")));
            var expected = List.of(ToolCall.of("search", Map.of("q", "shoes")));
            var result = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.STRICT)
                    .build()
                    .evaluate(testCase(actual, expected));
            assertThat(result.score()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("duplicate tool names")
    class DuplicateNames {
        @Test
        @DisplayName("ANY_ORDER counts repeated names by multiplicity")
        void repeatedMultiplicity() {
            var oneVsTwo = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.ANY_ORDER)
                    .build()
                    .evaluate(testCase(List.of(call("a"), call("a")), List.of(call("a"))));
            // one expected 'a' matches one of two actual 'a' -> 1/2
            assertThat(oneVsTwo.score()).isEqualTo(0.5);

            var twoVsTwo = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.ANY_ORDER)
                    .build()
                    .evaluate(testCase(List.of(call("a"), call("a")), List.of(call("a"), call("a"))));
            assertThat(twoVsTwo.score()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("SUPERSET requires a distinct actual for each repeated expected")
        void supersetDistinct() {
            var enough = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.SUPERSET)
                    .build()
                    .evaluate(testCase(List.of(call("a"), call("a"), call("b")), List.of(call("a"), call("a"))));
            assertThat(enough.score()).isEqualTo(1.0);

            var notEnough = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.SUPERSET)
                    .build()
                    .evaluate(testCase(List.of(call("a"), call("b")), List.of(call("a"), call("a"))));
            assertThat(notEnough.score()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("maximum matching pairs repeated names where greedy first-fit would undercount")
        void optimalUnderSubsetMatcher() {
            // A per-tool SUBSET matcher makes the two 'page' candidates non-interchangeable.
            // Ordered so greedy first-fit would strand a pair; maximum matching pairs all four.
            var actual = List.of(ToolCall.of("page", Map.of("n", 1, "size", 10)), ToolCall.of("page", Map.of("n", 1)));
            var expected =
                    List.of(ToolCall.of("page", Map.of("n", 1)), ToolCall.of("page", Map.of("n", 1, "size", 10)));
            var result = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.ANY_ORDER)
                    .argumentMatcher("page", ArgumentMatcher.of(ArgMatchMode.SUBSET))
                    .build()
                    .evaluate(testCase(actual, expected));
            assertThat(result.score()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("argument matcher orientation")
    class ArgumentOrientation {
        // SUBSET is asymmetric: matches(expected, actual) holds iff actual's keys cover expected's.
        // These pin the direction so expected and actual are never swapped between modes.
        @Test
        @DisplayName("ANY_ORDER applies the matcher as matches(expected, actual)")
        void anyOrderOrientation() {
            var pass = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.ANY_ORDER)
                    .argumentMatcher("t", ArgumentMatcher.of(ArgMatchMode.SUBSET))
                    .build()
                    .evaluate(testCase(
                            List.of(ToolCall.of("t", Map.of("a", 1, "b", 2))),
                            List.of(ToolCall.of("t", Map.of("a", 1)))));
            // expected {a:1} is covered by actual {a:1,b:2} -> match
            assertThat(pass.score()).isEqualTo(1.0);

            var fail = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.ANY_ORDER)
                    .argumentMatcher("t", ArgumentMatcher.of(ArgMatchMode.SUBSET))
                    .build()
                    .evaluate(testCase(
                            List.of(ToolCall.of("t", Map.of("a", 1))),
                            List.of(ToolCall.of("t", Map.of("a", 1, "b", 2)))));
            // expected {a:1,b:2} is NOT covered by actual {a:1} -> no match
            assertThat(fail.score()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("RECALL keeps the same orientation as ANY_ORDER")
        void recallOrientation() {
            var result = ToolTrajectoryEvaluator.builder()
                    .matchMode(ToolTrajectoryEvaluator.MatchMode.RECALL)
                    .argumentMatcher("t", ArgumentMatcher.of(ArgMatchMode.SUBSET))
                    .build()
                    .evaluate(testCase(
                            List.of(ToolCall.of("t", Map.of("a", 1))),
                            List.of(ToolCall.of("t", Map.of("a", 1, "b", 2)))));
            assertThat(result.score()).isEqualTo(0.0);
        }
    }

    @Test
    @DisplayName("both empty scores 1.0")
    void bothEmpty() {
        var result = ToolTrajectoryEvaluator.builder().build().evaluate(testCase(List.of(), List.of()));
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("missing expected key throws EvaluationException")
    void missingKey() {
        var testCase = EvalTestCase.builder()
                .actualOutput("toolCalls", List.of(call("a")))
                .build();
        assertThatThrownBy(() -> ToolTrajectoryEvaluator.builder().build().evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("expectedOutputs");
    }

    @Test
    @DisplayName("accepts tool calls supplied as maps")
    void mapInput() {
        var actual = List.of(Map.of("name", "a"), Map.of("name", "b"));
        var expected = List.of(Map.of("name", "a"), Map.of("name", "b"));
        EvalResult result = ToolTrajectoryEvaluator.builder()
                .matchMode(ToolTrajectoryEvaluator.MatchMode.STRICT)
                .build()
                .evaluate(EvalTestCase.builder()
                        .actualOutput("toolCalls", actual)
                        .expectedOutput("toolCalls", expected)
                        .build());
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("missing actual key throws EvaluationException")
    void missingActualKey() {
        var testCase = EvalTestCase.builder()
                .expectedOutput("toolCalls", List.of(call("a")))
                .build();
        assertThatThrownBy(() -> ToolTrajectoryEvaluator.builder().build().evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("actualOutputs");
    }

    @Test
    @DisplayName("custom keys are honored")
    void customKeys() {
        var testCase = EvalTestCase.builder()
                .actualOutput("got", List.of(call("a")))
                .expectedOutput("want", List.of(call("a")))
                .build();
        var result = ToolTrajectoryEvaluator.builder()
                .matchMode(ToolTrajectoryEvaluator.MatchMode.STRICT)
                .toolCallsKey("got")
                .expectedToolCallsKey("want")
                .build()
                .evaluate(testCase);
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("metadata carries the per-run diff for downstream rendering")
    void metadataContent() {
        var result = ToolTrajectoryEvaluator.builder()
                .matchMode(ToolTrajectoryEvaluator.MatchMode.ANY_ORDER)
                .build()
                .evaluate(testCase(List.of(call("a"), call("x")), List.of(call("a"), call("b"))));

        assertThat(result.metadata().get("matchMode")).isEqualTo("ANY_ORDER");
        assertThat(result.metadata().get("actualCount")).isEqualTo(2);
        assertThat(result.metadata().get("expectedCount")).isEqualTo(2);
        assertThat(result.metadata().get("matchedCount")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<String> actualTools = (List<String>) result.metadata().get("actualTools");
        assertThat(actualTools).containsExactly("a", "x");
    }

    @Test
    @DisplayName("STRICT fails when actual has extra trailing calls")
    void strictLengthMismatch() {
        var result = ToolTrajectoryEvaluator.builder()
                .matchMode(ToolTrajectoryEvaluator.MatchMode.STRICT)
                .build()
                .evaluate(testCase(List.of(call("a"), call("b"), call("c")), List.of(call("a"), call("b"))));
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("IN_ORDER scores 1.0 when sequences are identical")
    void inOrderIdentical() {
        var result = ToolTrajectoryEvaluator.builder()
                .matchMode(ToolTrajectoryEvaluator.MatchMode.IN_ORDER)
                .build()
                .evaluate(testCase(List.of(call("a"), call("b")), List.of(call("a"), call("b"))));
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("RECALL with empty expected and non-empty actual scores 0.0")
    void recallEmptyExpected() {
        var result = ToolTrajectoryEvaluator.builder()
                .matchMode(ToolTrajectoryEvaluator.MatchMode.RECALL)
                .build()
                .evaluate(testCase(List.of(call("a")), List.of()));
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("PRECISION with empty actual and non-empty expected scores 0.0")
    void precisionEmptyActual() {
        var result = ToolTrajectoryEvaluator.builder()
                .matchMode(ToolTrajectoryEvaluator.MatchMode.PRECISION)
                .build()
                .evaluate(testCase(List.of(), List.of(call("a"))));
        assertThat(result.score()).isEqualTo(0.0);
    }
}
