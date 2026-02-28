package dev.dokimos.core;

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
        Path path = Path.of(pathname);

        try {
            return switch (getExtension(pathname)) {
                case ".json" -> Dataset.fromJson(path);
                case ".jsonl" -> Dataset.fromJsonl(path);
                case ".csv" -> Dataset.fromCsv(path);
                default ->
                    throw new DatasetResolutionException(
                            "Unsupported file type: " + pathname + ". Supported types: .json, .jsonl, .csv");
            };
        } catch (IOException e) {
            throw new DatasetResolutionException("Failed to load dataset from file: " + pathname, e);
        }
    }

    private static String getExtension(String pathname) {
        int dotIdx = pathname.lastIndexOf('.');
        return dotIdx >= 0 ? pathname.substring(dotIdx) : "";
    }
}
