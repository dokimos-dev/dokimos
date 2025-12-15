package io.dokimos.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DatasetTest {

    @Test
    void shouldBuildSimpleDataset() {
        var dataset = Dataset.builder()
                .name("math-qa")
                .description("Simple math questions dataset")
                .addExample(Example.of("2+2", "4"))
                .addExample(Example.of("3*3", "9"))
                .build();

        assertThat(dataset.name()).isEqualTo("math-qa");
        assertThat(dataset.size()).isEqualTo(2);
        assertThat(dataset.get(0).input()).isEqualTo("2+2");
    }
}
