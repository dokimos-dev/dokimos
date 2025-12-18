package io.dokimos.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class FileDatasetResolverTest {

    private final FileDatasetResolver resolver = new FileDatasetResolver();

    @Test
    void shouldSupportFilePaths() {
        assertThat(resolver.supports("path/to/file.json")).isTrue();
        assertThat(resolver.supports("file:path/to/file.json")).isTrue();
        assertThat(resolver.supports("classpath:something.json")).isFalse();
    }

    @Test
    void shouldLoadJsonFrom(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.json");
        Files.writeString(file, """
                {
                  "name": "test-dataset",
                  "examples": [
                    {"input": "Hello", "expectedOutput": "Hi"}
                  ]
                }
                """);

        var dataset = resolver.resolve(file.toString());

        assertThat(dataset.name()).isEqualTo("test-dataset");
        assertThat(dataset.size()).isEqualTo(1);
    }

    @Test
    void shouldLoadFromCsv(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.csv");
        Files.writeString(file, "input,expectedOutput\nHello,Hi\n");

        var dataset = resolver.resolve(file.toString());

        assertThat(dataset.size()).isEqualTo(1);
        assertThat(dataset.get(0).input()).isEqualTo("Hello");
    }
    
    @Test
    void shouldThrowForMissingFile() {
        assertThatThrownBy(() -> resolver.resolve("does-not-exist.json"))
                .isInstanceOf(DatasetResolutionException.class)
                .hasMessageContaining("Failed to load");
    }
}
