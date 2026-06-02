package dev.dokimos.core;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DatasetResolverRegistryTest {

    private static final String MARKER = "classpath:__devex_isolation_marker__.json";

    private static DatasetResolver markerResolver(String datasetName) {
        return new DatasetResolver() {
            @Override
            public boolean supports(String uri) {
                return MARKER.equals(uri);
            }

            @Override
            public Dataset resolve(String uri) {
                return Dataset.builder().name(datasetName).build();
            }
        };
    }

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
                return Dataset.builder().name("custom-dataset").build();
            }
        });

        Dataset dataset = registry.resolve("custom:anything");
        assertThat(dataset.name()).isEqualTo("custom-dataset");
    }

    @Test
    void isolatedRegistryShouldResolveBuiltInResolvers() {
        var registry = new DatasetResolverRegistry();

        Dataset dataset = registry.resolve("classpath:datasets/sample.json");

        assertThat(dataset).isNotNull();
        assertThat(dataset.size()).isGreaterThan(0);
    }

    @Test
    void registerOnIsolatedInstanceShouldNotAffectAnother() {
        var a = new DatasetResolverRegistry();
        var b = new DatasetResolverRegistry();

        a.register(markerResolver("from-a"));
        b.register(markerResolver("from-b"));

        // Each isolated registry consults only its own registered resolver (added at highest priority).
        assertThat(a.resolve(MARKER).name()).isEqualTo("from-a");
        assertThat(b.resolve(MARKER).name()).isEqualTo("from-b");
    }

    @Test
    void registerOnIsolatedInstanceShouldNotAffectSingleton() {
        var isolated = new DatasetResolverRegistry();
        isolated.register(markerResolver("isolated-only"));

        assertThat(isolated.resolve(MARKER).name()).isEqualTo("isolated-only");

        // The global singleton never saw the custom resolver, so it falls through to the built-in
        // classpath resolver, which fails to find the (nonexistent) marker resource.
        assertThatThrownBy(() -> DatasetResolverRegistry.getInstance().resolve(MARKER))
                .isInstanceOf(DatasetResolutionException.class)
                .hasMessageContaining("Classpath resource not found");
    }
}
