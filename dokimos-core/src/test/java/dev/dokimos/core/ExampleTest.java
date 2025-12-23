package dev.dokimos.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;


class ExampleTest {

    @Test
    void shouldCreateExample() {
        var example = Example.of("What is 2+2?", "4");

        assertThat(example.input()).isEqualTo("What is 2+2?");
        assertThat(example.expectedOutput()).isEqualTo("4");
    }

    @Test
    void shouldSupportBuilder() {
        var example = Example.builder()
                .input("question", "What is the capital of Switzerland?")
                .input("language", "en")
                .expectedOutput("answer", "Bern")
                .metadata("source", "geography")
                .build();

        assertThat(example.inputs()).containsEntry("question", "What is the capital of Switzerland?");
        assertThat(example.inputs()).containsEntry("language", "en");
        assertThat(example.expectedOutputs()).containsEntry("answer", "Bern");
        assertThat(example.metadata()).containsEntry("source", "geography");
    }

    @Test
    void shouldCastToTestCase() {
        var example = Example.of("What is 2+2?", "4");
        var testCase = example.toTestCase("4");

        assertThat(testCase.input()).isEqualTo("What is 2+2?");
        assertThat(testCase.actualOutput()).isEqualTo("4");
        assertThat(testCase.expectedOutput()).isEqualTo("4");
    }

    @Test
    void shouldConvertToTestCaseWithMap() {
        var example = Example.builder()
                .input("input", "Summarize this")
                .expectedOutput("output", "Short summary")
                .build();

        var testCase = example.toTestCase(Map.of("output", "My summary", "confidence", 0.9));

        assertThat(testCase.actualOutput()).isEqualTo("My summary");
    }

    @Test
    void shouldSupportRagUseCase() {
        var example = Example.builder()
                .input("input", "What is the refund policy?")
                .input("context", List.of("30-day refund policy applies"))
                .expectedOutput("output", "You can get a refund within 30 days")
                .build();

        assertThat(example.inputs()).containsKey("context");
        assertThat(example.inputs().get("context")).isEqualTo(List.of("30-day refund policy applies"));
    }
}
