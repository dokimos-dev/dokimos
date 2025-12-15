package io.dokimos.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;


public class ExampleTest {

    @Test
    void canCreateSimpleExample() {
        var example = Example.of("What is 2+2?", "4");

        assertThat(example.input()).isEqualTo("What is 2+2?");
        assertThat(example.expectedOutput()).isEqualTo("4");
    }

    @Test
    void canUseBuilder() {
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
}
