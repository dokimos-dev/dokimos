package io.dokimos.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class DatasetTest {

    @Test
    void shouldBuildSimpleDataset() {
        var dataset = Dataset.builder()
                .name("math-qa")
                .description("Simple math questions dataset")
                .addExample(Example.of("2+2", "4"))
                .addExample(Example.of("3*3", "9"))
                .build();

        assertThat(dataset.name()).isEqualTo("math-qa");
        assertThat(dataset.size()).isEqualTo(2);
        assertThat(dataset.get(0).input()).isEqualTo("2+2");
    }

    @Test
    void shouldBeIterable() {
        var example1 = Example.of("1+1", "2");
        var example2 = Example.of("2+2", "4");

        var dataset = Dataset.builder()
                .addExample(example1)
                .addExample(example2)
                .build();

        assertThat(dataset).hasSize(2);
        assertThat(dataset).containsExactly(example1, example2);
    }

    @Test
    void shouldParseDatasetFromJsonString() {
        String json = """
                {
                  "name": "refund-qa",
                  "description": "Questions about refunds",
                  "examples": [
                    {
                      "input": "What is the refund policy?",
                      "expectedOutput": "30-day full refund"
                    },
                    {
                      "input": "Can I return after 60 days?",
                      "expectedOutput": "No, only within 30 days"
                    }
                  ]
                }
                """;

        var dataset = Dataset.fromJson(json);

        assertThat(dataset.name()).isEqualTo("refund-qa");
        assertThat(dataset.description()).isEqualTo("Questions about refunds");
        assertThat(dataset.size()).isEqualTo(2);
        assertThat(dataset.get(0).input()).isEqualTo("What is the refund policy?");
        assertThat(dataset.get(0).expectedOutput()).isEqualTo("30-day full refund");
    }

    @Test
    void shouldParseJsonWithNestedInputsOutputs() {
        String json = """
                {
                  "name": "complex-qa",
                  "examples": [
                    {
                      "inputs": {
                        "question": "What is AI?",
                        "context": ["AI is artificial intelligence"]
                      },
                      "expectedOutputs": {
                        "answer": "Artificial intelligence",
                        "confidence": 0.9
                      },
                      "metadata": {
                        "source": "wikipedia"
                      }
                    }
                  ]
                }
                """;

        var dataset = Dataset.fromJson(json);

        assertThat(dataset.get(0).inputs()).containsEntry("question", "What is AI?");
        assertThat(dataset.get(0).expectedOutputs()).containsEntry("confidence", 0.9);
        assertThat(dataset.get(0).metadata()).containsEntry("source", "wikipedia");
    }

    @Test
    void shouldParseDatasetFromJsonFile(@TempDir Path tempDir) throws IOException {
        String jsonContent = """
                {
                  "name": "test-file",
                  "examples": [{"input": "hello", "expectedOutput": "world!"}]
                }
                """;

        Path jsonFilePath = tempDir.resolve("test.json");
        Files.writeString(jsonFilePath, jsonContent);

        var dataset = Dataset.fromJson(jsonFilePath);

        assertThat(dataset.name()).isEqualTo("test-file");
        assertThat(dataset.size()).isEqualTo(1);
    }

    @Test
    void shouldParseCsv() {
        String csv = """
                input,expectedOutput,category
                What is 2+2?,4,math
                What is 3*3?,9,math
                """;

        var dataset = Dataset.fromCsv(csv, "math-qa");

        assertThat(dataset.name()).isEqualTo("math-qa");
        assertThat(dataset.size()).isEqualTo(2);
        assertThat(dataset.get(0).input()).isEqualTo("What is 2+2?");
        assertThat(dataset.get(0).expectedOutput()).isEqualTo("4");
        assertThat(dataset.get(0).metadata()).containsEntry("category", "math");
    }

    @Test
    void shouldParseCsvWithDoubleQuotes() {
        String csv = """
                input,expectedOutput
                "Hello, how are you?","I'm fine, thanks"
                """;

        var dataset = Dataset.fromCsv(csv, "greetings");

        assertThat(dataset.get(0).input()).isEqualTo("Hello, how are you?");
        assertThat(dataset.get(0).expectedOutput()).isEqualTo("I'm fine, thanks");
    }

    @Test
    void shouldLoadCsvFromFile(@TempDir Path tempDir) throws IOException {
        String csv = "input,expectedOutput\nhello,world";
        Path file = tempDir.resolve("test.csv");
        Files.writeString(file, csv);

        var dataset = Dataset.fromCsv(file);

        assertThat(dataset.name()).isEqualTo("test");
        assertThat(dataset.size()).isEqualTo(1);
    }

    @Test
    void shouldThrowOnMissingExamples() {
        String jsonContent = """
            {
              "name": "math-qa-dataset",
              "description": "A dataset without examples"
            }
            """;

        assertThatThrownBy(() -> Dataset.fromJson(jsonContent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("examples");
    }

    @Test
    void shouldThrowOnMalformedJson() {
        String jsonContent = """
            {"name": "math-qa-dataset"
            """;

        assertThatThrownBy(() -> Dataset.fromJson(jsonContent))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Failed to parse JSON");
    }

    @Test
    void shouldThrowOnMissingInputColumn() {
        String csv = """
                question,answer
                hello,world
                """;

        assertThatThrownBy(() -> Dataset.fromCsv(csv, "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input");
    }
}
