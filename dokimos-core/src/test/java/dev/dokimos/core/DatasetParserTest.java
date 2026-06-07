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

    @Test
    void shouldReadTopLevelJsonIdAsDatasetItemId() {
        String json = """
                {
                  "name": "refund-qa",
                  "examples": [
                    {"id": "item-7", "input": "q", "expectedOutput": "a", "metadata": {"source": "wiki"}}
                  ]
                }
                """;

        Example example = DatasetParser.parseJson(json).get(0);

        assertThat(example.datasetItemId()).isEqualTo("item-7");
        assertThat(example.inputs()).doesNotContainKey("id");
        assertThat(example.expectedOutputs()).doesNotContainKey("id");
        assertThat(example.metadata()).doesNotContainKey("id");
    }

    @Test
    void shouldNotLeakJsonIdIntoNestedInputsOutputsOrMetadata() {
        String json = """
                {
                  "name": "refund-qa",
                  "examples": [
                    {
                      "id": "item-9",
                      "inputs": {"question": "q"},
                      "expectedOutputs": {"answer": "a"},
                      "metadata": {"source": "wiki"}
                    }
                  ]
                }
                """;

        Example example = DatasetParser.parseJson(json).get(0);

        assertThat(example.datasetItemId()).isEqualTo("item-9");
        assertThat(example.inputs()).containsEntry("question", "q").doesNotContainKey("id");
        assertThat(example.expectedOutputs()).containsEntry("answer", "a").doesNotContainKey("id");
        assertThat(example.metadata()).containsEntry("source", "wiki").doesNotContainKey("id");
    }

    @Test
    void shouldStringifyANumericJsonId() {
        String json = """
                {"name": "d", "examples": [{"id": 7, "input": "q", "expectedOutput": "a"}]}
                """;

        Example example = DatasetParser.parseJson(json).get(0);

        assertThat(example.datasetItemId()).isEqualTo("7");
        assertThat(example.inputs()).doesNotContainKey("id");
    }

    @Test
    void shouldLeaveDatasetItemIdNullWhenJsonHasNoId() {
        String json = """
                {
                  "name": "refund-qa",
                  "examples": [{"input": "q", "expectedOutput": "a"}]
                }
                """;

        assertThat(DatasetParser.parseJson(json).get(0).datasetItemId()).isNull();
    }

    @Test
    void shouldReadJsonlIdAsDatasetItemId() {
        String jsonl = """
                {"id": "row-1", "input": "q1", "expectedOutput": "a1"}
                {"input": "q2", "expectedOutput": "a2"}
                """;

        Dataset dataset = DatasetParser.parseJsonl(jsonl, "test");

        assertThat(dataset.get(0).datasetItemId()).isEqualTo("row-1");
        assertThat(dataset.get(1).datasetItemId()).isNull();
    }

    @Test
    void shouldReadCsvIdColumnAsDatasetItemIdAndExcludeItFromMetadata() {
        String csv = """
                id,input,expectedOutput,category
                item-3,What is 2+2?,4,math
                """;

        Example example = DatasetParser.parseCsv(csv, "math-qa").get(0);

        assertThat(example.datasetItemId()).isEqualTo("item-3");
        assertThat(example.metadata()).containsEntry("category", "math").doesNotContainKey("id");
        assertThat(example.inputs()).doesNotContainKey("id");
        assertThat(example.expectedOutputs()).doesNotContainKey("id");
    }

    @Test
    void shouldLeaveDatasetItemIdNullWhenCsvIdCellIsBlank() {
        String csv = """
                id,input,expectedOutput
                ,What is 2+2?,4
                """;

        Example example = DatasetParser.parseCsv(csv, "math-qa").get(0);

        assertThat(example.datasetItemId()).isNull();
        assertThat(example.metadata()).doesNotContainKey("id");
    }

    @Test
    void shouldLeaveDatasetItemIdNullWhenCsvHasNoIdColumn() {
        String csv = """
                input,expectedOutput,category
                What is 2+2?,4,math
                """;

        Example example = DatasetParser.parseCsv(csv, "math-qa").get(0);

        assertThat(example.datasetItemId()).isNull();
        assertThat(example.metadata()).containsEntry("category", "math");
    }
}
