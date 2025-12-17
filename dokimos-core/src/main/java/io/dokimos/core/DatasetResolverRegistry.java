package io.dokimos.core;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Registry of DatasetResolvers.
 * Resolvers are loaded via SPI and can be added programmatically.
 */
public class DatasetResolverRegistry {

    private static final DatasetResolverRegistry INSTANCE = new DatasetResolverRegistry();

    private final List<DatasetResolver> resolvers = new ArrayList<>();

    private DatasetResolverRegistry() {
        // Load the SPI resolvers
        ServiceLoader.load(DatasetResolver.class).forEach(resolvers::add);

        // Add the built-in resolvers
        resolvers.add(new ClasspathDatasetResolver());
        resolvers.add(new FileDatasetResolver());
    }

    public static DatasetResolverRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Register a custom resolver with the highest priority.
     */
    public void register(DatasetResolver resolver) {
        resolvers.add(0, resolver);
    }

    /**
     * Resolve a URI to a Dataset using the first matching resolver.
     */
    public Dataset resolve(String uri) {
        for (DatasetResolver resolver : resolvers) {
            if (resolver.supports(uri)) {
                return resolver.resolve(uri);
            }
        }
        throw new DatasetResolutionException("No resolver found for URI: " + uri);
    }
}
