package dev.dokimos.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;

/**
 * Tests that the dataset load path is routed through the hardened internal JSON reader, so malformed
 * structured input (duplicate keys, NaN/Infinity tokens, trailing garbage) is rejected at parse time
 * rather than surfacing confusingly later during evaluation.
 */
class DatasetParserTest {

    @Test
    void shouldLoadValidDatasetUnchanged() {
        String json = """
                {
                  "name": "refund-qa",
                  "description": "Questions about refunds",
                  "examples": [
                    {
                      "inputs": {"question": "What is the refund policy?"},
                      "expectedOutputs": {"answer": "30-day full refund", "confidence": 0.9}
                    }
                  ]
                }
                """;

        Dataset dataset = DatasetParser.parseJson(json);

        assertThat(dataset.name()).isEqualTo("refund-qa");
        assertThat(dataset.description()).isEqualTo("Questions about refunds");
        assertThat(dataset.size()).isEqualTo(1);
        assertThat(dataset.get(0).inputs()).containsEntry("question", "What is the refund policy?");
        assertThat(dataset.get(0).expectedOutputs()).containsEntry("answer", "30-day full refund");
        assertThat(dataset.get(0).expectedOutputs()).containsEntry("confidence", 0.9);
    }

    @Test
    void shouldRejectDuplicateKeysAtParse() {
        // Two "name" keys: a real signal in a dataset, silently collapsed by a default mapper.
        String json = """
                {
                  "name": "first",
                  "name": "second",
                  "examples": [{"input": "q", "expectedOutput": "a"}]
                }
                """;

        assertThatThrownBy(() -> DatasetParser.parseJson(json))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Failed to parse JSON content of dataset")
                .rootCause()
                .hasMessageContaining("Duplicate");
    }

    @Test
    void shouldRejectNaNTokenAtParse() {
        String json = """
                {
                  "name": "broken",
                  "examples": [
                    {"inputs": {"q": "x"}, "expectedOutputs": {"confidence": NaN}}
                  ]
                }
                """;

        assertThatThrownBy(() -> DatasetParser.parseJson(json))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Failed to parse JSON content of dataset");
    }

    @Test
    void shouldRejectTrailingGarbageAtParse() {
        String json = "{\"name\":\"d\",\"examples\":[{\"input\":\"q\",\"expectedOutput\":\"a\"}]} trailing";

        assertThatThrownBy(() -> DatasetParser.parseJson(json))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Failed to parse JSON content of dataset");
    }

    @Test
    void shouldRejectDuplicateKeysInJsonlLine() {
        String jsonl = """
                {"input": "valid", "expectedOutput": "ok"}
                {"input": "dup", "input": "dup2", "expectedOutput": "x"}
                """;

        assertThatThrownBy(() -> DatasetParser.parseJsonl(jsonl, "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line 2");
    }
}
