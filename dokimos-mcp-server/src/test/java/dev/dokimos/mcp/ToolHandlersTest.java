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
import java.util.ArrayList;
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
    void compareRunsDetectsConsistentRegression() throws Exception {
        // Twelve items where the candidate scores strictly below the baseline on every item, a
        // consistent direction the significance test can detect.
        List<RunRecord.ItemDetail> baselineItems = new ArrayList<>();
        List<RunRecord.ItemDetail> candidateItems = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            baselineItems.add(item("q" + i, "a" + i, "a" + i, true, eval("Exact Match", 0.9, true)));
            candidateItems.add(item("q" + i, "a" + i, "wrong" + i, false, eval("Exact Match", 0.2, false)));
        }
        store.save(recordWithItems("run-a", 1.0, Map.of("Exact Match", 0.9), baselineItems));
        store.save(recordWithItems("run-b", 0.0, Map.of("Exact Match", 0.2), candidateItems));

        McpSchema.CallToolResult result = handlers.handleCompareRuns(Map.of("run_id_a", "run-a", "run_id_b", "run-b"));
        Map<String, Object> response = parseResponse(result);

        Map<String, Object> comparison = asMap(response.get("comparison"));
        assertThat(comparison.get("has_regressions")).isEqualTo(true);

        Map<String, Object> passRate = asMap(comparison.get("pass_rate"));
        Map<String, Object> evaluator =
                asMap(asMap(comparison.get("evaluators")).get("Exact Match"));
        boolean passRateSignificant = (boolean) passRate.get("significant");
        boolean evaluatorSignificant = (boolean) evaluator.get("significant");
        assertThat(passRateSignificant || evaluatorSignificant).isTrue();

        List<?> cases = (List<?>) comparison.get("cases");
        assertThat(cases).isNotEmpty();
        assertThat(cases)
                .anySatisfy(entry -> assertThat(asMap(entry).get("status")).isEqualTo("REGRESSED"));
    }

    @Test
    void compareRunsIdenticalRunsHaveNoRegression() throws Exception {
        List<RunRecord.ItemDetail> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            items.add(item("q" + i, "a" + i, "a" + i, true, eval("Exact Match", 1.0, true)));
        }
        store.save(recordWithItems("run-a", 1.0, Map.of("Exact Match", 1.0), copyItems(items)));
        store.save(recordWithItems("run-b", 1.0, Map.of("Exact Match", 1.0), copyItems(items)));

        McpSchema.CallToolResult result = handlers.handleCompareRuns(Map.of("run_id_a", "run-a", "run_id_b", "run-b"));
        Map<String, Object> response = parseResponse(result);

        Map<String, Object> comparison = asMap(response.get("comparison"));
        assertThat(comparison.get("has_regressions")).isEqualTo(false);
        assertThat(asMap(comparison.get("summary_counts")).get("regressed")).isEqualTo(0);
        assertThat((List<?>) comparison.get("cases")).isEmpty();
    }

    @Test
    void compareRunsNoisyDifferenceIsNotFlagged() throws Exception {
        // A single small downward blip among otherwise unchanged items must not trip the gate.
        List<RunRecord.ItemDetail> baselineItems = new ArrayList<>();
        List<RunRecord.ItemDetail> candidateItems = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            baselineItems.add(item("q" + i, "a" + i, "a" + i, true, eval("Exact Match", 0.8, true)));
            double candScore = i == 0 ? 0.78 : 0.8;
            candidateItems.add(item("q" + i, "a" + i, "a" + i, true, eval("Exact Match", candScore, true)));
        }
        store.save(recordWithItems("run-a", 1.0, Map.of("Exact Match", 0.8), baselineItems));
        store.save(recordWithItems("run-b", 1.0, Map.of("Exact Match", 0.798), candidateItems));

        McpSchema.CallToolResult result = handlers.handleCompareRuns(Map.of("run_id_a", "run-a", "run_id_b", "run-b"));
        Map<String, Object> response = parseResponse(result);

        Map<String, Object> comparison = asMap(response.get("comparison"));
        assertThat(comparison.get("has_regressions")).isEqualTo(false);
    }

    @Test
    void compareRunsDifferentItemCountsReflectAddedAndRemoved() throws Exception {
        List<RunRecord.ItemDetail> baselineItems = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            baselineItems.add(item("q" + i, "a" + i, "a" + i, true, eval("Exact Match", 1.0, true)));
        }
        List<RunRecord.ItemDetail> candidateItems = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            candidateItems.add(item("q" + i, "a" + i, "a" + i, true, eval("Exact Match", 1.0, true)));
        }
        store.save(recordWithItems("run-a", 1.0, Map.of("Exact Match", 1.0), baselineItems));
        store.save(recordWithItems("run-b", 1.0, Map.of("Exact Match", 1.0), candidateItems));

        McpSchema.CallToolResult result = handlers.handleCompareRuns(Map.of("run_id_a", "run-a", "run_id_b", "run-b"));
        Map<String, Object> response = parseResponse(result);

        Map<String, Object> comparison = asMap(response.get("comparison"));
        Map<String, Object> counts = asMap(comparison.get("summary_counts"));
        // Baseline has two positional items (index 3, 4) absent from the candidate.
        assertThat(((Number) counts.get("removed")).intValue()).isEqualTo(2);
        assertThat(((Number) counts.get("added")).intValue()).isEqualTo(0);
    }

    @Test
    void compareRunsFlagsPassRateOnlyRegression() throws Exception {
        // Evaluator scores stay essentially flat across both runs, but the stored success flag flips
        // from pass to fail on most items. The engine should pick this up via the pass-rate test even
        // though no evaluator average moved enough to be flagged.
        List<RunRecord.ItemDetail> baselineItems = new ArrayList<>();
        List<RunRecord.ItemDetail> candidateItems = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            baselineItems.add(item("q" + i, "a" + i, "a" + i, true, eval("Exact Match", 0.7, true)));
            // First nine items flip success while keeping the score identical; the rest stay passing.
            boolean candSuccess = i >= 9;
            candidateItems.add(item("q" + i, "a" + i, "a" + i, candSuccess, eval("Exact Match", 0.7, candSuccess)));
        }
        store.save(recordWithItems("run-a", 1.0, Map.of("Exact Match", 0.7), baselineItems));
        store.save(recordWithItems("run-b", 0.25, Map.of("Exact Match", 0.7), candidateItems));

        McpSchema.CallToolResult result = handlers.handleCompareRuns(Map.of("run_id_a", "run-a", "run_id_b", "run-b"));
        Map<String, Object> response = parseResponse(result);

        Map<String, Object> comparison = asMap(response.get("comparison"));
        assertThat(comparison.get("has_regressions")).isEqualTo(true);
        assertThat(comparison.get("pass_rate_regressed")).isEqualTo(true);

        // The evaluator average did not move, so it should not be the source of the regression.
        Map<String, Object> evaluator =
                asMap(asMap(comparison.get("evaluators")).get("Exact Match"));
        assertThat(evaluator.get("significant")).isEqualTo(false);
    }

    @Test
    void compareRunsAddedItemsSetCompositionChanged() throws Exception {
        List<RunRecord.ItemDetail> baselineItems = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            baselineItems.add(item("q" + i, "a" + i, "a" + i, true, eval("Exact Match", 1.0, true)));
        }
        List<RunRecord.ItemDetail> candidateItems = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            candidateItems.add(item("q" + i, "a" + i, "a" + i, true, eval("Exact Match", 1.0, true)));
        }
        store.save(recordWithItems("run-a", 1.0, Map.of("Exact Match", 1.0), baselineItems));
        store.save(recordWithItems("run-b", 1.0, Map.of("Exact Match", 1.0), candidateItems));

        McpSchema.CallToolResult result = handlers.handleCompareRuns(Map.of("run_id_a", "run-a", "run_id_b", "run-b"));
        Map<String, Object> response = parseResponse(result);

        Map<String, Object> comparison = asMap(response.get("comparison"));
        Map<String, Object> counts = asMap(comparison.get("summary_counts"));
        // Candidate carries two positional items (index 3, 4) absent from the baseline.
        assertThat(((Number) counts.get("added")).intValue()).isEqualTo(2);
        assertThat(((Number) counts.get("removed")).intValue()).isEqualTo(0);
        assertThat(comparison.get("composition_changed")).isEqualTo(true);
        assertThat(comparison.get("composition_note")).asString().isNotEmpty();
    }

    @Test
    void compareRunsEqualLengthRunsHaveCompositionChangedFalse() throws Exception {
        List<RunRecord.ItemDetail> items = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            items.add(item("q" + i, "a" + i, "a" + i, true, eval("Exact Match", 1.0, true)));
        }
        store.save(recordWithItems("run-a", 1.0, Map.of("Exact Match", 1.0), copyItems(items)));
        store.save(recordWithItems("run-b", 1.0, Map.of("Exact Match", 1.0), copyItems(items)));

        McpSchema.CallToolResult result = handlers.handleCompareRuns(Map.of("run_id_a", "run-a", "run_id_b", "run-b"));
        Map<String, Object> response = parseResponse(result);

        Map<String, Object> comparison = asMap(response.get("comparison"));
        assertThat(comparison.get("composition_changed")).isEqualTo(false);
        assertThat(comparison).doesNotContainKey("composition_note");
    }

    @Test
    void compareRunsCaseCarriesIndexAndInput() throws Exception {
        List<RunRecord.ItemDetail> baselineItems = new ArrayList<>();
        List<RunRecord.ItemDetail> candidateItems = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            baselineItems.add(item("question-" + i, "a" + i, "a" + i, true, eval("Exact Match", 0.9, true)));
            candidateItems.add(item("question-" + i, "a" + i, "wrong" + i, false, eval("Exact Match", 0.2, false)));
        }
        store.save(recordWithItems("run-a", 1.0, Map.of("Exact Match", 0.9), baselineItems));
        store.save(recordWithItems("run-b", 0.0, Map.of("Exact Match", 0.2), candidateItems));

        McpSchema.CallToolResult result = handlers.handleCompareRuns(Map.of("run_id_a", "run-a", "run_id_b", "run-b"));
        Map<String, Object> response = parseResponse(result);

        Map<String, Object> comparison = asMap(response.get("comparison"));
        List<?> cases = (List<?>) comparison.get("cases");
        assertThat(cases).isNotEmpty();
        Map<String, Object> firstCase = asMap(cases.get(0));
        int index = ((Number) firstCase.get("index")).intValue();
        assertThat(index).isBetween(0, 11);
        assertThat(firstCase.get("input")).isEqualTo("question-" + index);
    }

    @Test
    void compareRunsTruncatesCasesAtFifty() throws Exception {
        List<RunRecord.ItemDetail> baselineItems = new ArrayList<>();
        List<RunRecord.ItemDetail> candidateItems = new ArrayList<>();
        int total = 60;
        for (int i = 0; i < total; i++) {
            baselineItems.add(item("q" + i, "a" + i, "a" + i, true, eval("Exact Match", 0.9, true)));
            candidateItems.add(item("q" + i, "a" + i, "wrong" + i, false, eval("Exact Match", 0.2, false)));
        }
        store.save(recordWithItems("run-a", 1.0, Map.of("Exact Match", 0.9), baselineItems));
        store.save(recordWithItems("run-b", 0.0, Map.of("Exact Match", 0.2), candidateItems));

        McpSchema.CallToolResult result = handlers.handleCompareRuns(Map.of("run_id_a", "run-a", "run_id_b", "run-b"));
        Map<String, Object> response = parseResponse(result);

        Map<String, Object> comparison = asMap(response.get("comparison"));
        List<?> cases = (List<?>) comparison.get("cases");
        assertThat(cases).hasSize(50);
        assertThat(comparison.get("cases_truncated")).isEqualTo(true);
        assertThat(((Number) comparison.get("changed_case_count")).intValue()).isEqualTo(total);
    }

    @Test
    void compareRunsEmptyEvaluationsCountAsFailure() throws Exception {
        // Items carry no evaluator results; pass/fail must follow the stored success flag rather than
        // defaulting to passing for an empty evaluation list.
        List<RunRecord.ItemDetail> baselineItems = new ArrayList<>();
        List<RunRecord.ItemDetail> candidateItems = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            baselineItems.add(new RunRecord.ItemDetail("q" + i, "a" + i, "a" + i, true, List.of()));
            // First nine items fail in the candidate with an empty evaluations list.
            boolean candSuccess = i >= 9;
            candidateItems.add(new RunRecord.ItemDetail("q" + i, "a" + i, "a" + i, candSuccess, List.of()));
        }
        store.save(recordWithItems("run-a", 1.0, Map.of(), baselineItems));
        store.save(recordWithItems("run-b", 0.25, Map.of(), candidateItems));

        McpSchema.CallToolResult result = handlers.handleCompareRuns(Map.of("run_id_a", "run-a", "run_id_b", "run-b"));
        Map<String, Object> response = parseResponse(result);

        Map<String, Object> comparison = asMap(response.get("comparison"));
        assertThat(comparison.get("has_regressions")).isEqualTo(true);
        assertThat(comparison.get("pass_rate_regressed")).isEqualTo(true);
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

    private static RunRecord.EvalDetail eval(String name, double score, boolean success) {
        return new RunRecord.EvalDetail(name, score, success, success ? "ok" : "fail");
    }

    private static RunRecord.ItemDetail item(
            String input, String expected, String actual, boolean success, RunRecord.EvalDetail eval) {
        return new RunRecord.ItemDetail(input, expected, actual, success, List.of(eval));
    }

    private static List<RunRecord.ItemDetail> copyItems(List<RunRecord.ItemDetail> items) {
        return new ArrayList<>(items);
    }

    private static RunRecord recordWithItems(
            String id, double passRate, Map<String, Double> scores, List<RunRecord.ItemDetail> items) {
        int total = items.size();
        int pass = (int) items.stream().filter(RunRecord.ItemDetail::success).count();
        return new RunRecord(
                id,
                Instant.now(),
                "exp",
                "ds",
                "/data/ds.json",
                Map.of("model", "gpt-4o-mini"),
                passRate,
                total,
                pass,
                total - pass,
                scores,
                items);
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
