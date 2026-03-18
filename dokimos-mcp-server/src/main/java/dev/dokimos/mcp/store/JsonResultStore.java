package dev.dokimos.mcp.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores run records as a JSON array in a local file.
 * Default location: ~/.dokimos/mcp-results.json
 */
public class JsonResultStore implements ResultStore {

    private static final Logger log = LoggerFactory.getLogger(JsonResultStore.class);

    private final Path filePath;
    private final ObjectMapper mapper;
    private final Object lock = new Object();

    public JsonResultStore() {
        this(defaultPath());
    }

    public JsonResultStore(Path filePath) {
        this.filePath = filePath;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        ensureParentDir();
    }

    private static Path defaultPath() {
        return Path.of(System.getProperty("user.home"), ".dokimos", "mcp-results.json");
    }

    private void ensureParentDir() {
        try {
            Files.createDirectories(filePath.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create storage directory: " + filePath.getParent(), e);
        }
    }

    @Override
    public void save(RunRecord record) {
        synchronized (lock) {
            List<RunRecord> records = loadAll();
            records.removeIf(r -> r.id().equals(record.id()));
            records.add(record);
            writeAll(records);
        }
    }

    @Override
    public Optional<RunRecord> get(String runId) {
        synchronized (lock) {
            return loadAll().stream().filter(r -> r.id().equals(runId)).findFirst();
        }
    }

    @Override
    public List<RunRecord> list(String datasetName, int limit) {
        synchronized (lock) {
            List<RunRecord> records = loadAll();
            records.sort(Comparator.comparing(RunRecord::timestamp).reversed());

            if (datasetName != null && !datasetName.isBlank()) {
                records = records.stream()
                        .filter(r -> datasetName.equals(r.datasetName()))
                        .toList();
            }

            if (limit > 0 && records.size() > limit) {
                records = records.subList(0, limit);
            }

            return records;
        }
    }

    private List<RunRecord> loadAll() {
        if (!Files.exists(filePath)) {
            return new ArrayList<>();
        }
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            if (bytes.length == 0) {
                return new ArrayList<>();
            }
            return new ArrayList<>(mapper.readValue(bytes, new TypeReference<List<RunRecord>>() {}));
        } catch (IOException e) {
            log.warn("Failed to read results file, starting fresh: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void writeAll(List<RunRecord> records) {
        try {
            mapper.writeValue(filePath.toFile(), records);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write results: " + filePath, e);
        }
    }
}
