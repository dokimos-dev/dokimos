package io.dokimos.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentResultTest {

    @Test
    void shouldCalculateTotalCount() {
        var result = new ExperimentResult(
                "math-qa",
                "Basic math questions",
                Map.of(),
                List.of(
                        new ItemResult(Example.of("What is 2+2?", "4"), Map.of(), List.of()),
                        new ItemResult(Example.of("What is 3*3?", "9"), Map.of(), List.of())
                )
        );

        assertThat(result.totalCount()).isEqualTo(2);
    }

    @Test
    void shouldCalculatePassAndFailCounts() {
        var result = new ExperimentResult(
                "capital-cities",
                "",
                Map.of(),
                List.of(
                        new ItemResult(Example.of("Capital of France?", "Paris"), Map.of(), List.of(EvalResult.success("correctness", 0.9, "Correct answer"))),
                        new ItemResult(Example.of("Capital of Germany?", "Berlin"), Map.of(), List.of(EvalResult.success("correctness", 0.8, "Correct answer"))),
                        new ItemResult(Example.of("Capital of Italy?", "Rome"), Map.of(), List.of(EvalResult.failure("correctness", 0.3, "Said Milan instead of Rome")))
                )
        );

        assertThat(result.passCount()).isEqualTo(2);
        assertThat(result.failCount()).isEqualTo(1);
    }

    @Test
    void shouldCalculatePassRate() {
        var result = new ExperimentResult(
                "refund-policy-qa",
                "",
                Map.of(),
                List.of(
                        new ItemResult(Example.of("What is the refund policy?", "30 days"), Map.of(), List.of(EvalResult.success("correctness", 0.9, "Accurate"))),
                        new ItemResult(Example.of("Can I return after 60 days?", "No"), Map.of(), List.of(EvalResult.failure("correctness", 0.3, "Incorrect response")))
                )
        );

        assertThat(result.passRate()).isEqualTo(0.5);
    }

    @Test
    void shouldCalculateAverageScorePerEvaluator() {
        var result = new ExperimentResult(
                "customer-support-qa",
                "",
                Map.of(),
                List.of(
                        new ItemResult(Example.of("How do I reset my password?", "Go to settings"), Map.of(), List.of(
                                EvalResult.success("correctness", 0.8, "Accurate"),
                                EvalResult.success("helpfulness", 0.9, "Clear instructions")
                        )),
                        new ItemResult(Example.of("Where is my order?", "Check tracking page"), Map.of(), List.of(
                                EvalResult.success("correctness", 0.6, "Partially correct"),
                                EvalResult.success("helpfulness", 0.7, "Could be more detailed")
                        ))
                )
        );

        assertThat(result.averageScore("correctness")).isEqualTo(0.7);
        assertThat(result.averageScore("helpfulness")).isEqualTo(0.8);
    }

    @Test
    void shouldReturnZeroForEmptyResults() {
        var result = new ExperimentResult("empty-experiment", "", Map.of(), List.of());

        assertThat(result.totalCount()).isEqualTo(0);
        assertThat(result.passRate()).isEqualTo(0.0);
        assertThat(result.averageScore("correctness")).isEqualTo(0.0);
    }
}