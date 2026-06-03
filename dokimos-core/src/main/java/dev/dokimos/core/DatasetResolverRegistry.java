package dev.dokimos.core;

import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Singleton registry for dataset resolvers.
 * <p>
 * Manages the discovery and registration of dataset resolvers, supporting both
 * service provider interface (SPI) based auto-discovery and programmatic registration.
 * Resolvers are tried in order until one matches the given URI.
 */
public class DatasetResolverRegistry {

    private static final DatasetResolverRegistry INSTANCE = new DatasetResolverRegistry();

    private final List<DatasetResolver> resolvers = new CopyOnWriteArrayList<>();

    /**
     * Creates a fresh, independent registry.
     * <p>
     * The new instance is seeded the same way as the global singleton: SPI resolvers
     * are auto-discovered via {@link ServiceLoader} and the built-in resolvers are added.
     * It maintains its own resolver chain, so {@link #register(DatasetResolver)} on this
     * instance does not affect the global singleton returned by {@link #getInstance()}.
     */
    public DatasetResolverRegistry() {
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
