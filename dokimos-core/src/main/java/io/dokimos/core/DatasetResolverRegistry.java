package io.dokimos.core;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Singleton registry for dataset resolvers.
 * <p>
 * Manages the discovery and registration of dataset resolvers, supporting both
 * service provider interface (SPI) based auto-discovery and programmatic registration.
 * Resolvers are tried in order until one matches the given URI.
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

    /**
     * Returns the singleton registry instance.
     *
     * @return the registry instance
     */
    public static DatasetResolverRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a custom resolver with the highest priority.
     * <p>
     * The resolver is added at the beginning of the resolver chain and will be
     * consulted before any previously registered resolvers.
     *
     * @param resolver the resolver to register
     */
    public void register(DatasetResolver resolver) {
        resolvers.add(0, resolver);
    }

    /**
     * Resolves a URI to a dataset using the first matching resolver.
     * <p>
     * Iterates through registered resolvers in order until one supports the given URI.
     *
     * @param uri the dataset URI to resolve
     * @return the resolved dataset
     * @throws DatasetResolutionException if no resolver supports the URI
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
