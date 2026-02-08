package dev.dokimos.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class ClasspathDatasetResolver implements DatasetResolver {

    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".json", ".jsonl", ".csv");

    @Override
    public boolean supports(String uri) {
        return uri != null && uri.startsWith(CLASSPATH_PREFIX);
    }

    @Override
    public Dataset resolve(String uri) {
        String resourcePath = uri.substring(CLASSPATH_PREFIX.length());
        String extension = getExtension(resourcePath);

        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new DatasetResolutionException(
                    "Unsupported resource type: " + resourcePath + ". Supported types: .json, .jsonl, .csv");
        }

        try (InputStream is = getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new DatasetResolutionException("Classpath resource not found: " + resourcePath);
            }

            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String name = extractName(resourcePath);

            return switch (extension) {
                case ".json" -> Dataset.fromJson(content);
                case ".jsonl" -> Dataset.fromJsonl(content, name);
                case ".csv" -> Dataset.fromCsv(content, name);
                default -> throw new DatasetResolutionException(
                        "Unsupported resource type: " + resourcePath + ". Supported types: .json, .jsonl, .csv");
            };
        } catch (IOException e) {
            throw new DatasetResolutionException("Failed to load dataset from classpath: " + resourcePath, e);
        }
    }

    private ClassLoader getClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return cl != null ? cl : getClass().getClassLoader();
    }

    private String extractName(String pathname) {
        String fileName = pathname.contains("/") ? pathname.substring(pathname.lastIndexOf("/") + 1) : pathname;
        int dotIdx = fileName.lastIndexOf(".");
        return dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;
    }

    private static String getExtension(String pathname) {
        int dotIdx = pathname.lastIndexOf('.');
        return dotIdx >= 0 ? pathname.substring(dotIdx) : "";
    }
}
