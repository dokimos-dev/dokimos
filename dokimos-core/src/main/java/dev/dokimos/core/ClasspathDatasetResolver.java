package dev.dokimos.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ClasspathDatasetResolver implements DatasetResolver {

    private static final String CLASSPATH_PREFIX = "classpath:";

    @Override
    public boolean supports(String uri) {
        return uri != null && uri.startsWith(CLASSPATH_PREFIX);
    }

    @Override
    public Dataset resolve(String uri) {
        String resourcePath = uri.substring(CLASSPATH_PREFIX.length());

        try (InputStream is = getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new DatasetResolutionException("Classpath resource not found: " + resourcePath);
            }

            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            if (resourcePath.endsWith(".csv")) {
                String name = extractName(resourcePath);
                return Dataset.fromCsv(content, name);
            } else {
                return Dataset.fromJson(content);
            }
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
}
