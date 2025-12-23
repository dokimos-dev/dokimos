package dev.dokimos.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class EvalTestCaseTest {

    @Test
    void shouldCreateWithStaticFactory() {
        var testCase = EvalTestCase.of("What is 2+2?", "4");

        assertThat(testCase.input()).isEqualTo("What is 2+2?");
        assertThat(testCase.actualOutput()).isEqualTo("4");
        assertThat(testCase.expectedOutput()).isNull();
    }

    @Test
    void shouldSupportExpectedOutput() {
        var testCase = EvalTestCase.of("What is 2+2?", "4", "4");

        assertThat(testCase.input()).isEqualTo("What is 2+2?");
        assertThat(testCase.actualOutput()).isEqualTo("4");
        assertThat(testCase.expectedOutput()).isEqualTo("4");
    }

    @Test
    void shouldBuildWithConvenienceMethods() {
        var testCase = EvalTestCase.builder()
                .input("What is the capital of Switzerland?")
                .actualOutput("Bern")
                .expectedOutput("Bern")
                .build();

        assertThat(testCase.input()).isEqualTo("What is the capital of Switzerland?");
        assertThat(testCase.actualOutput()).isEqualTo("Bern");
        assertThat(testCase.expectedOutput()).isEqualTo("Bern");
    }

    @Test
    void shouldBuildWithGenericMaps() {
        var testCase = EvalTestCase.builder()
                .input("question", "What is AI?")
                .input("language", "en")
                .actualOutput("answer", "Artificial Intelligence")
                .actualOutput("confidence", 0.95)
                .expectedOutput("answer", "Artificial Intelligence")
                .metadata("source", "test")
                .build();

        assertThat(testCase.inputs()).containsEntry("question", "What is AI?");
        assertThat(testCase.inputs()).containsEntry("language", "en");
        assertThat(testCase.actualOutputs()).containsEntry("confidence", 0.95);
        assertThat(testCase.metadata()).containsEntry("source", "test");
    }

    @Test
    void shouldMakeDefensiveCopies() {
        var inputs = new HashMap<String, Object>();
        inputs.put("input", "test");

        var testCase = EvalTestCase.builder()
                .inputs(inputs)
                .actualOutput("output")
                .build();

        inputs.put("modified", "value");

        assertThat(testCase.inputs()).doesNotContainKey("modified");
    }
}