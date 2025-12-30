package dev.dokimos.core;

import dev.dokimos.core.evaluators.EvaluationException;
import dev.dokimos.core.evaluators.PrecisionEvaluator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class PrecisionEvaluatorTest {

        @Test
        void shouldReturnPerfectPrecisionWhenAllRetrievedAreRelevant() {
                var evaluator = PrecisionEvaluator.builder()
                                .name("precision")
                                .retrievedKey("retrieved")
                                .expectedKey("relevant")
                                .threshold(0.8)
                                .build();

                var testCase = EvalTestCase.builder()
                                .input("test query")
                                .actualOutput("retrieved", List.of("doc_1", "doc_2", "doc_3"))
                                .expectedOutput("relevant", List.of("doc_1", "doc_2", "doc_3", "doc_4"))
                                .build();

                var result = evaluator.evaluate(testCase);

                assertThat(result.name()).isEqualTo("precision");
                assertThat(result.score()).isEqualTo(1.0);
                assertThat(result.success()).isTrue();
                assertThat(result.reason()).contains("perfect precision");
        }

        @Test
        void shouldReturnZeroPrecisionWhenNoRetrievedAreRelevant() {
                var evaluator = PrecisionEvaluator.builder()
                                .retrievedKey("retrieved")
                                .expectedKey("relevant")
                                .build();

                var testCase = EvalTestCase.builder()
                                .input("test query")
                                .actualOutput("retrieved", List.of("doc_1", "doc_2"))
                                .expectedOutput("relevant", List.of("doc_5", "doc_6"))
                                .build();

                var result = evaluator.evaluate(testCase);

                assertThat(result.score()).isEqualTo(0.0);
                assertThat(result.success()).isFalse();
                assertThat(result.reason()).contains("None");
        }

        @Test
        void shouldCalculatePartialPrecision() {
                var evaluator = PrecisionEvaluator.builder()
                                .retrievedKey("retrieved")
                                .expectedKey("relevant")
                                .threshold(0.5)
                                .build();

                var testCase = EvalTestCase.builder()
                                .input("test query")
                                .actualOutput("retrieved", List.of("doc_1", "doc_2", "doc_3"))
                                .expectedOutput("relevant", List.of("doc_1", "doc_3", "doc_5"))
                                .build();

                var result = evaluator.evaluate(testCase);

                // 2 out of 3 retrieved are relevant
                assertThat(result.score()).isCloseTo(0.667, within(0.001));
                assertThat(result.success()).isTrue();
        }

        @Test
        void shouldReturnOneWhenNothingRetrieved() {
                var evaluator = PrecisionEvaluator.builder()
                                .retrievedKey("retrieved")
                                .expectedKey("relevant")
                                .build();

                var testCase = EvalTestCase.builder()
                                .input("test query")
                                .actualOutput("retrieved", List.of())
                                .expectedOutput("relevant", List.of("doc_1", "doc_2"))
                                .build();

                var result = evaluator.evaluate(testCase);

                assertThat(result.score()).isEqualTo(1.0);
                assertThat(result.reason()).contains("No items were retrieved");
        }

        @Test
        void shouldIncludeMetadata() {
                var evaluator = PrecisionEvaluator.builder()
                                .retrievedKey("retrieved")
                                .expectedKey("relevant")
                                .build();

                var testCase = EvalTestCase.builder()
                                .input("test query")
                                .actualOutput("retrieved", List.of("doc_1", "doc_2", "doc_3"))
                                .expectedOutput("relevant", List.of("doc_1", "doc_5"))
                                .build();

                var result = evaluator.evaluate(testCase);

                assertThat(result.metadata().get("retrieved")).isEqualTo(3);
                assertThat(result.metadata().get("relevant")).isEqualTo(2);
                assertThat(result.metadata().get("truePositives")).isEqualTo(1L);
        }

        @Test
        void shouldThrowExceptionWhenRetrievedKeyMissing() {
                var evaluator = PrecisionEvaluator.builder()
                                .retrievedKey("retrieved")
                                .expectedKey("relevant")
                                .build();

                var testCase = EvalTestCase.builder()
                                .input("test query")
                                .expectedOutput("relevant", List.of("doc_1"))
                                .build();

                assertThatThrownBy(() -> evaluator.evaluate(testCase))
                                .isInstanceOf(EvaluationException.class)
                                .hasMessageContaining("retrieved");
        }

        @Test
        void shouldThrowExceptionWhenExpectedKeyMissing() {
                var evaluator = PrecisionEvaluator.builder()
                                .retrievedKey("retrieved")
                                .expectedKey("relevant")
                                .build();

                var testCase = EvalTestCase.builder()
                                .input("test query")
                                .actualOutput("retrieved", List.of("doc_1"))
                                .build();

                assertThatThrownBy(() -> evaluator.evaluate(testCase))
                                .isInstanceOf(EvaluationException.class)
                                .hasMessageContaining("relevant");
        }

        @Test
        void shouldWorkWithCaseInsensitiveMatching() {
                var evaluator = PrecisionEvaluator.builder()
                                .retrievedKey("retrieved")
                                .expectedKey("relevant")
                                .matchingStrategy(MatchingStrategy.caseInsensitive())
                                .build();

                var testCase = EvalTestCase.builder()
                                .input("test query")
                                .actualOutput("retrieved", List.of("DOC_1", "doc_2"))
                                .expectedOutput("relevant", List.of("doc_1", "DOC_2"))
                                .build();

                var result = evaluator.evaluate(testCase);

                assertThat(result.score()).isEqualTo(1.0);
        }

        @Test
        void shouldWorkWithFieldBasedMatching() {
                var evaluator = PrecisionEvaluator.builder()
                                .retrievedKey("retrieved")
                                .expectedKey("relevant")
                                .matchingStrategy(MatchingStrategy.byField("id"))
                                .build();

                var testCase = EvalTestCase.builder()
                                .input("test query")
                                .actualOutput("retrieved", List.of(
                                                Map.of("id", "doc_1", "score", 0.9),
                                                Map.of("id", "doc_2", "score", 0.8)))
                                .expectedOutput("relevant", List.of(
                                                Map.of("id", "doc_1"),
                                                Map.of("id", "doc_3")))
                                .build();

                var result = evaluator.evaluate(testCase);

                // Only doc_1 matches
                assertThat(result.score()).isEqualTo(0.5);
        }

        @Test
        void shouldWorkWithMultiFieldMatching() {
                var evaluator = PrecisionEvaluator.builder()
                                .retrievedKey("retrieved")
                                .expectedKey("relevant")
                                .matchingStrategy(MatchingStrategy.byFields("subject", "predicate", "object"))
                                .build();

                var testCase = EvalTestCase.builder()
                                .input("Who founded Microsoft?")
                                .actualOutput("retrieved", List.of(
                                                Map.of("subject", "Bill Gates", "predicate", "founded", "object",
                                                                "Microsoft"),
                                                Map.of("subject", "Satya Nadella", "predicate", "leads", "object",
                                                                "Microsoft")))
                                .expectedOutput("relevant", List.of(
                                                Map.of("subject", "Bill Gates", "predicate", "founded", "object",
                                                                "Microsoft"),
                                                Map.of("subject", "Paul Allen", "predicate", "co-founded", "object",
                                                                "Microsoft")))
                                .build();

                var result = evaluator.evaluate(testCase);

                // Only the Bill Gates triple matches
                assertThat(result.score()).isEqualTo(0.5);
        }

        @Test
        void shouldHandleSingleItemAsCollection() {
                var evaluator = PrecisionEvaluator.builder()
                                .retrievedKey("retrieved")
                                .expectedKey("relevant")
                                .build();

                var testCase = EvalTestCase.builder()
                                .input("test query")
                                .actualOutput("retrieved", "doc_1") // Single string, not list
                                .expectedOutput("relevant", List.of("doc_1"))
                                .build();

                var result = evaluator.evaluate(testCase);

                assertThat(result.score()).isEqualTo(1.0);
        }

        @Test
        void shouldRespectCustomThreshold() {
                var evaluator = PrecisionEvaluator.builder()
                                .retrievedKey("retrieved")
                                .expectedKey("relevant")
                                .threshold(0.9)
                                .build();

                var testCase = EvalTestCase.builder()
                                .input("test query")
                                .actualOutput("retrieved", List.of("doc_1", "doc_2", "doc_3"))
                                .expectedOutput("relevant", List.of("doc_1", "doc_2"))
                                .build();

                var result = evaluator.evaluate(testCase);

                // 2/3 = 0.667, below 0.9 threshold
                assertThat(result.score()).isCloseTo(0.667, within(0.001));
                assertThat(result.success()).isFalse();
                assertThat(result.threshold()).isEqualTo(0.9);
        }
}
