package dev.dokimos.core;

import dev.dokimos.core.evaluators.EvaluationException;
import dev.dokimos.core.evaluators.RecallEvaluator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class RecallEvaluatorTest {

        @Test
        void shouldReturnPerfectRecallWhenAllRelevantAreRetrieved() {
                var evaluator = RecallEvaluator.builder()
                                .name("recall")
                                .retrievedKey("retrieved")
                                .expectedKey("relevant")
                                .threshold(0.8)
                                .build();

                var testCase = EvalTestCase.builder()
                                .input("test query")
                                .actualOutput("retrieved", List.of("doc_1", "doc_2", "doc_3", "doc_4"))
                                .expectedOutput("relevant", List.of("doc_1", "doc_2", "doc_3"))
                                .build();

                var result = evaluator.evaluate(testCase);

                assertThat(result.name()).isEqualTo("recall");
                assertThat(result.score()).isEqualTo(1.0);
                assertThat(result.success()).isTrue();
                assertThat(result.reason()).contains("perfect recall");
        }

        @Test
        void shouldReturnZeroRecallWhenNoRelevantAreRetrieved() {
                var evaluator = RecallEvaluator.builder()
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
        void shouldCalculatePartialRecall() {
                var evaluator = RecallEvaluator.builder()
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

                // 2 out of 3 relevant are retrieved
                assertThat(result.score()).isCloseTo(0.667, within(0.001));
                assertThat(result.success()).isTrue();
        }

        @Test
        void shouldReturnOneWhenNoRelevantItems() {
                var evaluator = RecallEvaluator.builder()
                                .retrievedKey("retrieved")
                                .expectedKey("relevant")
                                .build();

                var testCase = EvalTestCase.builder()
                                .input("test query")
                                .actualOutput("retrieved", List.of("doc_1", "doc_2"))
                                .expectedOutput("relevant", List.of())
                                .build();

                var result = evaluator.evaluate(testCase);

                assertThat(result.score()).isEqualTo(1.0);
                assertThat(result.reason()).contains("No relevant items");
        }

        @Test
        void shouldIncludeMetadata() {
                var evaluator = RecallEvaluator.builder()
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
                var evaluator = RecallEvaluator.builder()
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
                var evaluator = RecallEvaluator.builder()
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
                var evaluator = RecallEvaluator.builder()
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
                var evaluator = RecallEvaluator.builder()
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

                // Only doc_1 is found out of 2 relevant
                assertThat(result.score()).isEqualTo(0.5);
        }

        @Test
        void shouldWorkWithMultiFieldMatching() {
                var evaluator = RecallEvaluator.builder()
                                .retrievedKey("retrieved")
                                .expectedKey("relevant")
                                .matchingStrategy(MatchingStrategy.byFields("subject", "predicate", "object"))
                                .build();

                var testCase = EvalTestCase.builder()
                                .input("Who founded Microsoft?")
                                .actualOutput("retrieved", List.of(
                                                Map.of("subject", "Bill Gates", "predicate", "founded", "object",
                                                                "Microsoft")))
                                .expectedOutput("relevant", List.of(
                                                Map.of("subject", "Bill Gates", "predicate", "founded", "object",
                                                                "Microsoft"),
                                                Map.of("subject", "Paul Allen", "predicate", "co-founded", "object",
                                                                "Microsoft")))
                                .build();

                var result = evaluator.evaluate(testCase);

                // Found 1 of 2 relevant triples
                assertThat(result.score()).isEqualTo(0.5);
        }

        @Test
        void shouldHandleSingleItemAsCollection() {
                var evaluator = RecallEvaluator.builder()
                                .retrievedKey("retrieved")
                                .expectedKey("relevant")
                                .build();

                var testCase = EvalTestCase.builder()
                                .input("test query")
                                .actualOutput("retrieved", List.of("doc_1"))
                                .expectedOutput("relevant", "doc_1") // Single string, not list
                                .build();

                var result = evaluator.evaluate(testCase);

                assertThat(result.score()).isEqualTo(1.0);
        }

        @Test
        void shouldRespectCustomThreshold() {
                var evaluator = RecallEvaluator.builder()
                                .retrievedKey("retrieved")
                                .expectedKey("relevant")
                                .threshold(0.9)
                                .build();

                var testCase = EvalTestCase.builder()
                                .input("test query")
                                .actualOutput("retrieved", List.of("doc_1", "doc_2"))
                                .expectedOutput("relevant", List.of("doc_1", "doc_2", "doc_3"))
                                .build();

                var result = evaluator.evaluate(testCase);

                // 2/3 = 0.667, below 0.9 threshold
                assertThat(result.score()).isCloseTo(0.667, within(0.001));
                assertThat(result.success()).isFalse();
                assertThat(result.threshold()).isEqualTo(0.9);
        }

        @Test
        void shouldShowMissedItemsInReason() {
                var evaluator = RecallEvaluator.builder()
                                .retrievedKey("retrieved")
                                .expectedKey("relevant")
                                .build();

                var testCase = EvalTestCase.builder()
                                .input("test query")
                                .actualOutput("retrieved", List.of("doc_1"))
                                .expectedOutput("relevant", List.of("doc_1", "doc_2", "doc_3"))
                                .build();

                var result = evaluator.evaluate(testCase);

                assertThat(result.reason()).contains("1 of 3");
                assertThat(result.reason()).contains("2 missed");
        }
}
