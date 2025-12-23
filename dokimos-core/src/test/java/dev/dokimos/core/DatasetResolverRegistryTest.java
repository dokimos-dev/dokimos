package dev.dokimos.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DatasetResolverRegistryTest {

    @Test
    void shouldResolveClasspathResource() {
        var registry = DatasetResolverRegistry.getInstance();
        Dataset dataset = registry.resolve("classpath:datasets/sample.json");

        assertThat(dataset).isNotNull();
        assertThat(dataset.size()).isGreaterThan(0);
    }

    @Test
    void shouldThrowForUnknownClasspathResource() {
        var registry = DatasetResolverRegistry.getInstance();

        assertThatThrownBy(() -> registry.resolve("classpath:does-not-exist.json"))
                .isInstanceOf(DatasetResolutionException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void shouldSupportCustomResolver() {
        var registry = DatasetResolverRegistry.getInstance();

        registry.register(new DatasetResolver() {
            @Override
            public boolean supports(String uri) {
                return uri.startsWith("custom:");
            }

            @Override
            public Dataset resolve(String uri) {
                return Dataset.builder()
                        .name("custom-dataset")
                        .build();
            }
        });

        Dataset dataset = registry.resolve("custom:anything");
        assertThat(dataset.name()).isEqualTo("custom-dataset");
    }
}