package dev.dokimos.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ClasspathDatasetResolverTest {

    private final ClasspathDatasetResolver resolver = new ClasspathDatasetResolver();

    @Test
    void shouldSupportClasspathPrefix() {
        assertThat(resolver.supports("classpath:datasets/sample.json")).isTrue();
        assertThat(resolver.supports("file:something.json")).isFalse();
        assertThat(resolver.supports("something.json")).isFalse();
    }

    @Test
    void shouldLoadJsonFromClasspath() {
        var dataset = resolver.resolve("classpath:datasets/sample.json");

        assertThat(dataset.size()).isGreaterThan(0);
    }

    @Test
    void shouldThrowForMissingResource() {
        assertThatThrownBy(() -> resolver.resolve("classpath:missing.json"))
                .isInstanceOf(DatasetResolutionException.class)
                .hasMessageContaining("not found");
    }
}