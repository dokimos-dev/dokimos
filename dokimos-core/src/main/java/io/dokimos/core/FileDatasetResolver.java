package io.dokimos.core;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Resolves datasets from the filesystem.
 * Supports URIs starting with "file:" or simple, plain file paths.
 */
public class FileDatasetResolver implements DatasetResolver {

    private static final String FILE_PREFIX = "file:";

    @Override
    public boolean supports(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }
        return !uri.startsWith("classpath:");
    }

    @Override
    public Dataset resolve(String uri) {
        // Cut off the `FILE_PREFIX` if present
        String pathname = uri.startsWith(FILE_PREFIX) ? uri.substring(FILE_PREFIX.length()) : uri;

        try {
            if (pathname.endsWith(".csv")) {
                return Dataset.fromCsv(Path.of(pathname));
            } else {
                return Dataset.fromJson(Path.of(pathname));
            }
        } catch (IOException e) {
            throw new DatasetResolutionException("Failed to load dataset from file: " + pathname, e);
        }
    }


}
