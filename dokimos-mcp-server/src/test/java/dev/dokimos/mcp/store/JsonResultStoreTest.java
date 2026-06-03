package dev.dokimos.mcp.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonResultStoreTest {

    @TempDir
    Path tempDir;

    private Path file;
    private JsonResultStore store;

    @BeforeEach
    void setUp() {
        file = tempDir.resolve("test-results.json");
        store = new JsonResultStore(file);
    }

    @Test
    void saveAndGet() {
        RunRecord record = sampleRecord("run-1", "test-experiment", "dataset-a");

        store.save(record);

        Optional<RunRecord> loaded = store.get("run-1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().experimentName()).isEqualTo("test-experiment");
        assertThat(loaded.get().datasetName()).isEqualTo("dataset-a");
        assertThat(loaded.get().passRate()).isEqualTo(0.75);
    }

    @Test
    void getMissing() {
        assertThat(store.get("nonexistent")).isEmpty();
    }

    @Test
    void saveOverwritesSameId() {
        store.save(sampleRecord("run-1", "exp-v1", "dataset-a"));
        store.save(sampleRecord("run-1", "exp-v2", "dataset-a"));

        List<RunRecord> all = store.list(null, 0);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).experimentName()).isEqualTo("exp-v2");
    }

    @Test
    void listReturnsNewestFirst() {
        store.save(sampleRecord("run-old", "exp", "ds", Instant.parse("2025-01-01T00:00:00Z")));
        store.save(sampleRecord("run-new", "exp", "ds", Instant.parse("2025-06-01T00:00:00Z")));

        List<RunRecord> all = store.list(null, 0);
        assertThat(all).hasSize(2);
        assertThat(all.get(0).id()).isEqualTo("run-new");
        assertThat(all.get(1).id()).isEqualTo("run-old");
    }

    @Test
    void listFiltersByDatasetName() {
        store.save(sampleRecord("run-1", "exp", "alpha"));
        store.save(sampleRecord("run-2", "exp", "beta"));
        store.save(sampleRecord("run-3", "exp", "alpha"));

        List<RunRecord> filtered = store.list("alpha", 0);
        assertThat(filtered).hasSize(2);
        assertThat(filtered).allMatch(r -> r.datasetName().equals("alpha"));
    }

    @Test
    void listRespectsLimit() {
        store.save(sampleRecord("run-1", "exp", "ds", Instant.parse("2025-01-01T00:00:00Z")));
        store.save(sampleRecord("run-2", "exp", "ds", Instant.parse("2025-02-01T00:00:00Z")));
        store.save(sampleRecord("run-3", "exp", "ds", Instant.parse("2025-03-01T00:00:00Z")));

        List<RunRecord> limited = store.list(null, 2);
        assertThat(limited).hasSize(2);
        assertThat(limited.get(0).id()).isEqualTo("run-3");
    }

    @Test
    void persistsAcrossInstances() {
        Path file = tempDir.resolve("persist-test.json");
        JsonResultStore store1 = new JsonResultStore(file);
        store1.save(sampleRecord("run-1", "exp", "ds"));

        JsonResultStore store2 = new JsonResultStore(file);
        assertThat(store2.get("run-1")).isPresent();
    }

    @Test
    void itemDetailsRoundTrip() {
        RunRecord.EvalDetail eval = new RunRecord.EvalDetail("Exact Match", 1.0, true, "Matched");
        RunRecord.ItemDetail item = new RunRecord.ItemDetail("What is 2+2?", "4", "4", true, List.of(eval));

        RunRecord record = new RunRecord(
                "run-items",
                Instant.now(),
                "exp",
                "ds",
                "/path/to/ds.json",
                Map.of("model", "gpt-4o-mini"),
                1.0,
                1,
                1,
                0,
                Map.of("Exact Match", 1.0),
                List.of(item));

        store.save(record);

        RunRecord loaded = store.get("run-items").orElseThrow();
        assertThat(loaded.items()).hasSize(1);
        assertThat(loaded.items().get(0).input()).isEqualTo("What is 2+2?");
        assertThat(loaded.items().get(0).evaluations().get(0).evaluator()).isEqualTo("Exact Match");
    }

    @Test
    void saveThenLoadRoundTrips() {
        store.save(sampleRecord("run-1", "exp-1", "ds-a"));

        RunRecord loaded = store.get("run-1").orElseThrow();
        assertThat(loaded.experimentName()).isEqualTo("exp-1");
        assertThat(loaded.datasetName()).isEqualTo("ds-a");
        assertThat(loaded.passRate()).isEqualTo(0.75);
    }

    @Test
    void overwriteSaveKeepsValidJsonWithPriorRecordsSurviving() throws Exception {
        store.save(sampleRecord("run-1", "exp-1", "ds-a"));
        store.save(sampleRecord("run-2", "exp-2", "ds-b"));

        // The live file must be readable as a well-formed JSON array after the second write.
        byte[] bytes = Files.readAllBytes(file);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        List<RunRecord> parsed = mapper.readValue(bytes, new TypeReference<List<RunRecord>>() {});
        assertThat(parsed).extracting(RunRecord::id).containsExactlyInAnyOrder("run-1", "run-2");

        // And the prior record survives the overwrite when read back through the store.
        assertThat(store.get("run-1")).isPresent();
        assertThat(store.get("run-2")).isPresent();
        assertThat(store.list(null, 0)).hasSize(2);
    }

    @Test
    void overwriteLeavesNoStrayTempFilesBehind() throws Exception {
        store.save(sampleRecord("run-1", "exp-1", "ds-a"));
        store.save(sampleRecord("run-2", "exp-2", "ds-b"));

        try (var entries = Files.list(tempDir)) {
            assertThat(entries).containsExactly(file);
        }
    }

    private static RunRecord sampleRecord(String id, String experiment, String dataset) {
        return sampleRecord(id, experiment, dataset, Instant.now());
    }

    private static RunRecord sampleRecord(String id, String experiment, String dataset, Instant timestamp) {
        return new RunRecord(
                id,
                timestamp,
                experiment,
                dataset,
                "/data/" + dataset + ".json",
                Map.of("model", "gpt-4o-mini", "temperature", 0.0),
                0.75,
                4,
                3,
                1,
                Map.of("Exact Match", 0.75),
                List.of());
    }
}
