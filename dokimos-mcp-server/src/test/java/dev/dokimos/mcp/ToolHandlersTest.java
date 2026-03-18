package dev.dokimos.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dokimos.mcp.store.JsonResultStore;
import dev.dokimos.mcp.store.ResultStore;
import dev.dokimos.mcp.store.RunRecord;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolHandlersTest {

    @TempDir
    Path tempDir;

    private ToolHandlers handlers;
    private ResultStore store;
    private ObjectMapper json;

    @BeforeEach
    void setUp() {
        json = new ObjectMapper();
        json.registerModule(new JavaTimeModule());
        store = new JsonResultStore(tempDir.resolve("test-results.json"));
        handlers = new ToolHandlers(store, json);
    }

    @Test
    void listExperimentsEmpty() throws Exception {
        McpSchema.CallToolResult result = handlers.handleListExperiments(Map.of());
        Map<String, Object> response = parseResponse(result);

        assertThat(response.get("count")).isEqualTo(0);
        assertThat((List<?>) response.get("runs")).isEmpty();
    }

    @Test
    void listExperimentsWithData() throws Exception {
        store.save(sampleRecord("r1", "exp-1", "ds-alpha"));
        store.save(sampleRecord("r2", "exp-2", "ds-beta"));

        McpSchema.CallToolResult result = handlers.handleListExperiments(Map.of());
        Map<String, Object> response = parseResponse(result);

        assertThat(response.get("count")).isEqualTo(2);
    }

    @Test
    void listExperimentsFilterByDataset() throws Exception {
        store.save(sampleRecord("r1", "exp-1", "alpha"));
        store.save(sampleRecord("r2", "exp-2", "beta"));

        McpSchema.CallToolResult result = handlers.handleListExperiments(Map.of("dataset_name", "alpha"));
        Map<String, Object> response = parseResponse(result);

        assertThat(response.get("count")).isEqualTo(1);
    }

    @Test
    void listExperimentsWithLimit() throws Exception {
        store.save(sampleRecord("r1", "exp-1", "ds"));
        store.save(sampleRecord("r2", "exp-2", "ds"));
        store.save(sampleRecord("r3", "exp-3", "ds"));

        McpSchema.CallToolResult result = handlers.handleListExperiments(Map.of("limit", 2));
        Map<String, Object> response = parseResponse(result);

        assertThat(response.get("count")).isEqualTo(2);
    }

    @Test
    void compareRuns() throws Exception {
        store.save(sampleRecord("run-a", "exp", "ds", 0.6, Map.of("Exact Match", 0.6)));
        store.save(sampleRecord("run-b", "exp", "ds", 0.8, Map.of("Exact Match", 0.8)));

        McpSchema.CallToolResult result = handlers.handleCompareRuns(Map.of("run_id_a", "run-a", "run_id_b", "run-b"));
        Map<String, Object> response = parseResponse(result);

        assertThat(response).containsKey("comparison");
        Map<String, Object> comparison = asMap(response.get("comparison"));
        assertThat(comparison.get("has_regressions")).isEqualTo(false);

        Map<String, Object> passRateComp = asMap(comparison.get("pass_rate"));
        assertThat(passRateComp.get("status")).isEqualTo("IMPROVED");
    }

    @Test
    void compareRunsDetectsRegression() throws Exception {
        store.save(sampleRecord("run-a", "exp", "ds", 0.9, Map.of("Exact Match", 0.9)));
        store.save(sampleRecord("run-b", "exp", "ds", 0.5, Map.of("Exact Match", 0.5)));

        McpSchema.CallToolResult result = handlers.handleCompareRuns(Map.of("run_id_a", "run-a", "run_id_b", "run-b"));
        Map<String, Object> response = parseResponse(result);

        Map<String, Object> comparison = asMap(response.get("comparison"));
        assertThat(comparison.get("has_regressions")).isEqualTo(true);
        assertThat((List<?>) comparison.get("regressions"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .contains("Exact Match");
    }

    @Test
    void compareRunsMissingId() {
        McpSchema.CallToolResult result = handlers.handleCompareRuns(Map.of("run_id_a", "nope", "run_id_b", "nah"));

        assertThat(result.isError()).isTrue();
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(text).contains("Run not found");
    }

    @Test
    void getFailingQueries() throws Exception {
        RunRecord.EvalDetail passing = new RunRecord.EvalDetail("EM", 1.0, true, "matched");
        RunRecord.EvalDetail failing = new RunRecord.EvalDetail("EM", 0.0, false, "no match");

        RunRecord record = new RunRecord(
                "run-fail",
                Instant.now(),
                "exp",
                "ds",
                "/ds.json",
                Map.of(),
                0.5,
                2,
                1,
                1,
                Map.of("EM", 0.5),
                List.of(
                        new RunRecord.ItemDetail("q1", "a1", "a1", true, List.of(passing)),
                        new RunRecord.ItemDetail("q2", "a2", "wrong", false, List.of(failing))));
        store.save(record);

        McpSchema.CallToolResult result = handlers.handleGetFailingQueries(Map.of("run_id", "run-fail"));
        Map<String, Object> response = parseResponse(result);

        assertThat(response.get("failing_count")).isEqualTo(1);
        List<?> queries = (List<?>) response.get("failing_queries");
        assertThat(queries).hasSize(1);

        Map<String, Object> failedItem = asMap(queries.get(0));
        assertThat(failedItem.get("input")).isEqualTo("q2");
        assertThat(failedItem.get("actual_output")).isEqualTo("wrong");
    }

    @Test
    void getFailingQueriesMissingRun() {
        McpSchema.CallToolResult result = handlers.handleGetFailingQueries(Map.of("run_id", "no-such-run"));

        assertThat(result.isError()).isTrue();
    }

    @Test
    void runEvaluationMissingDatasetPath() {
        McpSchema.CallToolResult result = handlers.handleRunEvaluation(Map.of());

        assertThat(result.isError()).isTrue();
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(text).contains("dataset_path");
    }

    @Test
    void runEvaluationBadDatasetFormat() {
        McpSchema.CallToolResult result = handlers.handleRunEvaluation(Map.of("dataset_path", "/some/file.txt"));

        assertThat(result.isError()).isTrue();
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(text).contains("Unsupported dataset format");
    }

    private Map<String, Object> parseResponse(McpSchema.CallToolResult result) throws Exception {
        assertThat(result.isError()).isFalse();
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        return json.readValue(text, new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object obj) {
        return (Map<String, Object>) obj;
    }

    private static RunRecord sampleRecord(String id, String experiment, String dataset) {
        return sampleRecord(id, experiment, dataset, 0.75, Map.of("Exact Match", 0.75));
    }

    private static RunRecord sampleRecord(
            String id, String experiment, String dataset, double passRate, Map<String, Double> scores) {
        int total = 4;
        int pass = (int) (total * passRate);
        return new RunRecord(
                id,
                Instant.now(),
                experiment,
                dataset,
                "/data/" + dataset + ".json",
                Map.of("model", "gpt-4o-mini"),
                passRate,
                total,
                pass,
                total - pass,
                scores,
                List.of());
    }
}
