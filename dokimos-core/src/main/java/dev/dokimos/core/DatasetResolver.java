package dev.dokimos.core;

/**
 * Resolves a dataset URI to a {@code Dataset}.
 * Implementations can support different sources, such as files, classpath, or remote servers.
 *
 * <p>Custom resolvers can be registered via Java SPI by adding a file:
 * {@code META-INF/services/dev.dokimos.core.DatasetResolver}
 */
public interface DatasetResolver {

    /**
     * Check if this resolver supports the given URI.
     *
     * @param uri the dataset URI
     * @return true if this resolver can handle the given URI
     */
    boolean supports(String uri);

    /**
     * Resolve the URI to a {@code Dataset}.
     *
     * @param uri the dataset's URI
     * @return the loaded Dataset
     * @throws DatasetResolutionException if loading does fail
     */
    Dataset resolve(String uri);
}
