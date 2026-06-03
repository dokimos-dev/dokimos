package dev.dokimos.core;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatasetTest {

    private static final String BOM = "﻿";

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

        var dataset =
                Dataset.builder().addExample(example1).addExample(example2).build();

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

    @Test
    void shouldParseJsonl() {
        String jsonl = """
                {"input": "What is 2+2?", "expectedOutput": "4"}
                {"input": "What is 3*3?", "expectedOutput": "9"}
                """;

        var dataset = Dataset.fromJsonl(jsonl, "math-qa");

        assertThat(dataset.name()).isEqualTo("math-qa");
        assertThat(dataset.size()).isEqualTo(2);
        assertThat(dataset.get(0).input()).isEqualTo("What is 2+2?");
        assertThat(dataset.get(0).expectedOutput()).isEqualTo("4");
        assertThat(dataset.get(1).input()).isEqualTo("What is 3*3?");
    }

    @Test
    void shouldParseJsonlWithNestedStructure() {
        String jsonl = """
                {"inputs": {"question": "What is AI?", "context": "Technology"}, "expectedOutputs": {"answer": "Artificial Intelligence", "confidence": 0.95}, "metadata": {"source": "wiki", "category": "tech"}}
                {"inputs": {"question": "What is ML?"}, "expectedOutputs": {"answer": "Machine Learning"}}
                """;

        var dataset = Dataset.fromJsonl(jsonl, "ai-qa");

        assertThat(dataset.size()).isEqualTo(2);
        assertThat(dataset.get(0).inputs()).containsEntry("question", "What is AI?");
        assertThat(dataset.get(0).inputs()).containsEntry("context", "Technology");
        assertThat(dataset.get(0).expectedOutputs()).containsEntry("answer", "Artificial Intelligence");
        assertThat(dataset.get(0).expectedOutputs()).containsEntry("confidence", 0.95);
        assertThat(dataset.get(0).metadata()).containsEntry("source", "wiki");
        assertThat(dataset.get(0).metadata()).containsEntry("category", "tech");
        assertThat(dataset.get(1).inputs()).containsEntry("question", "What is ML?");
        assertThat(dataset.get(1).expectedOutputs()).containsEntry("answer", "Machine Learning");
    }

    @Test
    void shouldParseJsonlWithNestedInputsAndMetadata() {
        String jsonl = """
                {"inputs": {"query": "Explain photosynthesis", "language": "en", "max_tokens": 150}, "expectedOutputs": {"summary": "Plants convert sunlight to energy", "keywords": ["plants", "sunlight", "energy"]}, "metadata": {"difficulty": "easy", "subject": "biology"}}
                {"inputs": {"query": "Describe quantum entanglement", "language": "en", "max_tokens": 200}, "expectedOutputs": {"summary": "Particles share quantum states", "keywords": ["quantum", "particles"]}, "metadata": {"difficulty": "hard", "subject": "physics"}}
                """;

        var dataset = Dataset.fromJsonl(jsonl, "science-qa");

        assertThat(dataset.size()).isEqualTo(2);
        assertThat(dataset.get(0).inputs()).containsEntry("query", "Explain photosynthesis");
        assertThat(dataset.get(0).inputs()).containsEntry("language", "en");
        assertThat(dataset.get(0).inputs()).containsEntry("max_tokens", 150);
        assertThat(dataset.get(0).expectedOutputs()).containsEntry("summary", "Plants convert sunlight to energy");
        assertThat(dataset.get(0).metadata()).containsEntry("difficulty", "easy");
        assertThat(dataset.get(0).metadata()).containsEntry("subject", "biology");
        assertThat(dataset.get(1).metadata()).containsEntry("difficulty", "hard");
        assertThat(dataset.get(1).metadata()).containsEntry("subject", "physics");
    }

    @Test
    void shouldIgnoreBlankLinesInJsonl() {
        String jsonl = """
                {"input": "First", "expectedOutput": "1"}

                {"input": "Second", "expectedOutput": "2"}

                """;

        var dataset = Dataset.fromJsonl(jsonl, "test");

        assertThat(dataset.size()).isEqualTo(2);
    }

    @Test
    void shouldLoadJsonlFromFile(@TempDir Path tempDir) throws IOException {
        String jsonl = """
                {"input": "hello", "expectedOutput": "world"}
                {"input": "foo", "expectedOutput": "bar"}
                """;
        Path file = tempDir.resolve("test.jsonl");
        Files.writeString(file, jsonl);

        var dataset = Dataset.fromJsonl(file);

        assertThat(dataset.name()).isEqualTo("test");
        assertThat(dataset.size()).isEqualTo(2);
    }

    @Test
    void shouldThrowOnEmptyJsonl() {
        String jsonl = """


                """;

        assertThatThrownBy(() -> Dataset.fromJsonl(jsonl, "empty"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no examples");
    }

    @Test
    void shouldThrowOnMalformedJsonlLine() {
        String jsonl = """
                {"input": "valid", "expectedOutput": "ok"}
                {invalid json here}
                {"input": "another", "expectedOutput": "one"}
                """;

        assertThatThrownBy(() -> Dataset.fromJsonl(jsonl, "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line 2");
    }

    @Test
    void shouldCollapseDoubledQuotesToSingleLiteralQuote() {
        String csv = "input,output\n\"He said \"\"hi\"\"\",greeting\n";

        Dataset dataset = Dataset.fromCsv(csv, "quotes");

        assertThat(dataset.size()).isEqualTo(1);
        assertThat(dataset.get(0).input()).isEqualTo("He said \"hi\"");
        assertThat(dataset.get(0).expectedOutput()).isEqualTo("greeting");
    }

    @Test
    void shouldKeepNewlineInsideQuotedFieldAsOneExample() {
        String csv = "input,output\n\"line one\nline two\",multi\n";

        Dataset dataset = Dataset.fromCsv(csv, "newlines");

        assertThat(dataset.size()).isEqualTo(1);
        assertThat(dataset.get(0).input()).isEqualTo("line one\nline two");
        assertThat(dataset.get(0).expectedOutput()).isEqualTo("multi");
    }

    @Test
    void shouldKeepDelimiterInsideQuotedField() {
        String csv = "input,output\n\"a,b,c\",joined\n";

        Dataset dataset = Dataset.fromCsv(csv, "delimiter");

        assertThat(dataset.size()).isEqualTo(1);
        assertThat(dataset.get(0).input()).isEqualTo("a,b,c");
    }

    @Test
    void shouldPreserveLeadingAndTrailingSpacesInQuotedField() {
        String csv = "input,output\n\"  padded  \",result\n";

        Dataset dataset = Dataset.fromCsv(csv, "padded");

        assertThat(dataset.get(0).input()).isEqualTo("  padded  ");
    }

    @Test
    void shouldTrimUnquotedFields() {
        String csv = "input,output\n  spaced  ,  trimmed  \n";

        Dataset dataset = Dataset.fromCsv(csv, "unquoted");

        assertThat(dataset.get(0).input()).isEqualTo("spaced");
        assertThat(dataset.get(0).expectedOutput()).isEqualTo("trimmed");
    }

    @Test
    void shouldResolveInputColumnWithBomPrefixedHeader() {
        String csv = BOM + "input,output\n2+2,4\n";

        Dataset dataset = Dataset.fromCsv(csv, "bom");

        assertThat(dataset.size()).isEqualTo(1);
        assertThat(dataset.get(0).input()).isEqualTo("2+2");
    }

    @Test
    void shouldStripBomFromJsonContent() {
        String json = BOM + "{\"name\":\"d\",\"examples\":[{\"input\":\"q\",\"expectedOutput\":\"a\"}]}";

        Dataset dataset = Dataset.fromJson(json);

        assertThat(dataset.name()).isEqualTo("d");
        assertThat(dataset.get(0).input()).isEqualTo("q");
    }

    @Test
    void shouldIncludeFoundHeadersWhenInputColumnMissing() {
        String csv = "question,answer\nq,a\n";

        assertThatThrownBy(() -> Dataset.fromCsv(csv, "missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("question")
                .hasMessageContaining("answer");
    }

    @Test
    void shouldRoundTripEscapedCsvOutput() {
        // Mirrors ExperimentResultExporter.escapeCsv: quote-wrap and double inner quotes.
        String raw = "value with \"quotes\", a comma\nand a newline";
        String escaped = "\"" + raw.replace("\"", "\"\"") + "\"";
        String csv = "input,output\n" + escaped + ",ok\n";

        Dataset dataset = Dataset.fromCsv(csv, "roundtrip");

        assertThat(dataset.size()).isEqualTo(1);
        assertThat(dataset.get(0).input()).isEqualTo(raw);
    }

    @Test
    void shouldLoadCsvPath(@TempDir Path tempDir) throws IOException {
        Path csvFile = tempDir.resolve("sample.csv");
        Files.writeString(csvFile, "input,output\n2+2,4\n");

        Dataset dataset = Dataset.load(csvFile.toString());

        assertThat(dataset.size()).isEqualTo(1);
        assertThat(dataset.get(0).input()).isEqualTo("2+2");
    }

    @Test
    void shouldLoadJsonPath(@TempDir Path tempDir) throws IOException {
        Path jsonFile = tempDir.resolve("sample.json");
        Files.writeString(jsonFile, "{\"name\":\"d\",\"examples\":[{\"input\":\"q\",\"expectedOutput\":\"a\"}]}");

        Dataset dataset = Dataset.load(jsonFile.toString());

        assertThat(dataset.size()).isEqualTo(1);
        assertThat(dataset.get(0).input()).isEqualTo("q");
    }
}
