package dev.dokimos.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import dev.dokimos.core.Dataset;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.Evaluator;
import dev.dokimos.core.Example;
import dev.dokimos.core.Experiment;
import dev.dokimos.core.ExperimentResult;
import dev.dokimos.core.ItemResult;
import dev.dokimos.core.RunResult;
import dev.dokimos.core.Task;
import dev.dokimos.core.comparison.ComparisonStatus;
import dev.dokimos.core.comparison.EvaluatorDelta;
import dev.dokimos.core.comparison.ItemComparison;
import dev.dokimos.core.comparison.RunComparison;
import dev.dokimos.core.comparison.RunComparisonResult;
import dev.dokimos.core.comparison.SignificanceResult;
import dev.dokimos.core.evaluators.ExactMatchEvaluator;
import dev.dokimos.core.evaluators.LLMJudgeEvaluator;
import dev.dokimos.mcp.store.ResultStore;
import dev.dokimos.mcp.store.RunRecord;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the four MCP tool handlers for the dokimos evaluation framework.
 */
public class ToolHandlers {

    private static final Logger log = LoggerFactory.getLogger(ToolHandlers.class);

    private final ResultStore store;
    private final ObjectMapper json;

    public ToolHandlers(ResultStore store, ObjectMapper json) {
        this.store = store;
        this.json = json;
    }

    /**
     * Runs an evaluation: loads a dataset, calls an LLM, evaluates results, and persists the run.
     */
    public McpSchema.CallToolResult handleRunEvaluation(Map<String, Object> arguments) {
        try {
            String datasetPath = requireString(arguments, "dataset_path");
            String model = stringOrDefault(arguments, "model", "gpt-5.5");
            double temperature = doubleOrDefault(arguments, "temperature", 0.0);
            String evaluatorType = stringOrDefault(arguments, "evaluator", "exact_match");
            String criteria = stringOrDefault(arguments, "criteria", null);
            double threshold = doubleOrDefault(arguments, "threshold", 0.7);
            String experimentName = stringOrDefault(arguments, "experiment_name", "mcp-evaluation");

            Dataset dataset = loadDataset(datasetPath);
            OpenAIClient openai = buildOpenAIClient();
            Task task = buildTask(openai, model, temperature);
            List<Evaluator> evaluators = buildEvaluators(evaluatorType, criteria, threshold, openai, model);

            ExperimentResult result = Experiment.builder()
                    .name(experimentName)
                    .dataset(dataset)
                    .task(task)
                    .evaluators(evaluators)
                    .build()
                    .run();

            RunRecord record = toRunRecord(result, dataset, datasetPath, model, temperature);
            store.save(record);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("run_id", record.id());
            response.put("experiment_name", experimentName);
            response.put("dataset", dataset.name());
            response.put("model", model);
            response.put("total_examples", result.totalCount());
            response.put("pass_rate", result.passRate());
            response.put("pass_count", (int) result.passCount());
            response.put("fail_count", (int) result.failCount());

            Map<String, Double> scores = new LinkedHashMap<>();
            for (String name : result.evaluatorNames()) {
                scores.put(name, result.averageScore(name));
            }
            response.put("average_scores", scores);

            return textResult(response);
        } catch (Exception e) {
            return errorResult("run_evaluation failed: " + e.getMessage());
        }
    }

    /**
     * Lists past evaluation runs with optional filtering.
     */
    public McpSchema.CallToolResult handleListExperiments(Map<String, Object> arguments) {
        try {
            int limit = intOrDefault(arguments, "limit", 20);
            String datasetName = stringOrDefault(arguments, "dataset_name", null);

            List<RunRecord> runs = store.list(datasetName, limit);

            List<Map<String, Object>> results = new ArrayList<>();
            for (RunRecord run : runs) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("run_id", run.id());
                entry.put("timestamp", run.timestamp().toString());
                entry.put("experiment_name", run.experimentName());
                entry.put("dataset", run.datasetName());
                entry.put("total_examples", run.totalCount());
                entry.put("pass_rate", run.passRate());
                entry.put("average_scores", run.averageScores());
                results.add(entry);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("count", results.size());
            response.put("runs", results);

            return textResult(response);
        } catch (Exception e) {
            return errorResult("list_experiments failed: " + e.getMessage());
        }
    }

    /** Compares two runs via {@link RunComparison}: per-evaluator deltas, pass-rate test, per-item diffs. */
    public McpSchema.CallToolResult handleCompareRuns(Map<String, Object> arguments) {
        try {
            String idA = requireString(arguments, "run_id_a");
            String idB = requireString(arguments, "run_id_b");

            RunRecord runA = store.get(idA).orElseThrow(() -> new IllegalArgumentException("Run not found: " + idA));
            RunRecord runB = store.get(idB).orElseThrow(() -> new IllegalArgumentException("Run not found: " + idB));

            RunResult baseline = toRunResult(runA);
            RunResult candidate = toRunResult(runB);
            RunComparisonResult result = RunComparison.create().compare(List.of(baseline), List.of(candidate));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("run_a", summarizeRun(runA));
            response.put("run_b", summarizeRun(runB));
            response.put("comparison", buildComparison(result, runA, runB));

            return textResult(response);
        } catch (Exception e) {
            return errorResult("compare_runs failed: " + e.getMessage());
        }
    }

    private static final int MAX_CASES = 50;

    // Converts a stored run record into a single-run RunResult for the comparison engine.
    // Items keep stored order (positional pairing). Items with no evaluations get a synthesized
    // "overall" EvalResult so the engine's pass/fail matches the persisted ItemDetail.success flag
    // (an empty eval list would otherwise pass vacuously).
    private RunResult toRunResult(RunRecord run) {
        List<ItemResult> itemResults = new ArrayList<>();
        List<RunRecord.ItemDetail> items = run.items() != null ? run.items() : List.of();
        for (RunRecord.ItemDetail item : items) {
            Example example = Example.of(
                    item.input() != null ? item.input() : "",
                    item.expectedOutput() != null ? item.expectedOutput() : "");

            List<EvalResult> evalResults = new ArrayList<>();
            List<RunRecord.EvalDetail> evaluations = item.evaluations() != null ? item.evaluations() : List.of();
            if (evaluations.isEmpty()) {
                evalResults.add(
                        new EvalResult("overall", item.success() ? 1.0 : 0.0, null, item.success(), "", Map.of()));
            } else {
                for (RunRecord.EvalDetail eval : evaluations) {
                    evalResults.add(new EvalResult(
                            eval.evaluator(), eval.score(), null, eval.success(), eval.reason(), Map.of()));
                }
            }

            Map<String, Object> actualOutputs =
                    Map.of("output", item.actualOutput() != null ? item.actualOutput() : "");
            itemResults.add(new ItemResult(example, actualOutputs, evalResults));
        }
        return new RunResult(0, itemResults);
    }

    // Builds the comparison block of a compare_runs response. Stored records resolve per-item keys
    // back to input text by position.
    private Map<String, Object> buildComparison(RunComparisonResult result, RunRecord runA, RunRecord runB) {
        Map<String, Object> comparison = new LinkedHashMap<>();

        SignificanceResult passSig = result.passRateSignificance();
        Map<String, Object> passRate = new LinkedHashMap<>();
        passRate.put("run_a", result.baselinePassRate());
        passRate.put("run_b", result.candidatePassRate());
        passRate.put("delta", result.passRateDelta());
        passRate.put("status", passRateStatus(result));
        passRate.put("significant", passSig != null && passSig.significant());
        passRate.put("p_value", passSig != null ? passSig.pValue() : null);
        passRate.put("method", passSig != null ? passSig.method() : null);
        comparison.put("pass_rate", passRate);

        Map<String, Object> evaluators = new LinkedHashMap<>();
        for (EvaluatorDelta delta : result.evaluatorDeltas()) {
            evaluators.put(delta.evaluatorName(), evaluatorMap(delta));
        }
        comparison.put("evaluators", evaluators);

        List<String> regressions = new ArrayList<>();
        for (EvaluatorDelta delta : result.regressions()) {
            regressions.add(delta.evaluatorName());
        }
        comparison.put("regressions", regressions);
        comparison.put("has_regressions", result.hasRegressions());
        comparison.put("pass_rate_regressed", result.passRateRegressed());

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("improved", result.improvedCount());
        counts.put("regressed", result.regressedCount());
        counts.put("unchanged", result.unchangedCount());
        counts.put("added", result.addedCount());
        counts.put("removed", result.removedCount());
        counts.put("significant_regressed", result.significantRegressedCount());
        counts.put("significant_improved", result.significantImprovedCount());
        comparison.put("summary_counts", counts);

        boolean compositionChanged = (result.addedCount() + result.removedCount()) > 0;
        comparison.put("composition_changed", compositionChanged);
        if (compositionChanged) {
            comparison.put(
                    "composition_note",
                    "Added or removed cases are not significance-tested; the engine only tests"
                            + " significance on items present in both runs. The top-line pass rates"
                            + " above cover each side's full item set, so a pass-rate difference"
                            + " driven only by composition changes will not set has_regressions.");
        }

        addCases(comparison, result, runA, runB);

        return comparison;
    }

    // REGRESSED first, then IMPROVED, then the rest. UNCHANGED filtered out. Capped at MAX_CASES.
    private void addCases(Map<String, Object> comparison, RunComparisonResult result, RunRecord runA, RunRecord runB) {
        List<ItemComparison> changed = new ArrayList<>();
        for (ItemComparison item : result.items()) {
            if (item.status() != ComparisonStatus.UNCHANGED) {
                changed.add(item);
            }
        }
        changed.sort(java.util.Comparator.comparingInt(item -> casePriority(item.status())));

        int changedCount = changed.size();
        boolean truncated = changedCount > MAX_CASES;
        List<ItemComparison> included = truncated ? changed.subList(0, MAX_CASES) : changed;

        List<Map<String, Object>> cases = new ArrayList<>();
        for (ItemComparison item : included) {
            cases.add(caseMap(item, runA, runB));
        }

        comparison.put("cases", cases);
        comparison.put("cases_truncated", truncated);
        comparison.put("changed_case_count", changedCount);
    }

    private Map<String, Object> caseMap(ItemComparison item, RunRecord runA, RunRecord runB) {
        int index = parseIndex(item.key());

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("index", index);
        entry.put("input", inputForIndex(index, runA, runB));
        entry.put("status", item.status().name());
        entry.put("pass_flip", item.passFlip());

        List<Map<String, Object>> evaluatorList = new ArrayList<>();
        for (EvaluatorDelta delta : item.evaluatorDeltas()) {
            Map<String, Object> evalEntry = new LinkedHashMap<>();
            evalEntry.put("name", delta.evaluatorName());
            evalEntry.put("run_a", delta.baselineMean());
            evalEntry.put("run_b", delta.candidateMean());
            evalEntry.put("delta", delta.delta());
            evalEntry.put("status", delta.status().name());
            evalEntry.put(
                    "significant",
                    delta.significance() != null && delta.significance().significant());
            evaluatorList.add(evalEntry);
        }
        entry.put("evaluators", evaluatorList);
        return entry;
    }

    private Map<String, Object> evaluatorMap(EvaluatorDelta delta) {
        SignificanceResult sig = delta.significance();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("run_a", delta.baselineMean());
        entry.put("run_b", delta.candidateMean());
        entry.put("delta", delta.delta());
        entry.put("status", delta.status().name());
        entry.put("significant", sig != null && sig.significant());
        entry.put("p_value", sig != null ? sig.pValue() : null);
        return entry;
    }

    private static String passRateStatus(RunComparisonResult result) {
        if (result.passRateRegressed()) {
            return ComparisonStatus.REGRESSED.name();
        }
        if (result.passRateImproved()) {
            return ComparisonStatus.IMPROVED.name();
        }
        return ComparisonStatus.UNCHANGED.name();
    }

    private static int casePriority(ComparisonStatus status) {
        return switch (status) {
            case REGRESSED -> 0;
            case IMPROVED -> 1;
            default -> 2;
        };
    }

    // Parses "item-<index>" engine keys; returns -1 on mismatch.
    private static int parseIndex(String key) {
        if (key != null && key.startsWith("item-")) {
            try {
                return Integer.parseInt(key.substring("item-".length()));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    // Resolves input text for an item index; prefers candidate, falls back to baseline.
    private static String inputForIndex(int index, RunRecord runA, RunRecord runB) {
        if (index >= 0 && index < runB.items().size()) {
            return runB.items().get(index).input();
        }
        if (index >= 0 && index < runA.items().size()) {
            return runA.items().get(index).input();
        }
        return null;
    }

    /**
     * Returns failing queries from a run, filtered by score threshold.
     */
    public McpSchema.CallToolResult handleGetFailingQueries(Map<String, Object> arguments) {
        try {
            String runId = requireString(arguments, "run_id");
            double threshold = doubleOrDefault(arguments, "threshold", 0.5);

            RunRecord run = store.get(runId).orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));

            List<Map<String, Object>> failing = new ArrayList<>();
            for (RunRecord.ItemDetail item : run.items()) {
                boolean belowThreshold = item.evaluations().stream().anyMatch(e -> e.score() < threshold);

                if (belowThreshold || !item.success()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("input", item.input());
                    entry.put("expected_output", item.expectedOutput());
                    entry.put("actual_output", item.actualOutput());
                    entry.put("success", item.success());

                    List<Map<String, Object>> evals = new ArrayList<>();
                    for (RunRecord.EvalDetail eval : item.evaluations()) {
                        Map<String, Object> evalEntry = new LinkedHashMap<>();
                        evalEntry.put("evaluator", eval.evaluator());
                        evalEntry.put("score", eval.score());
                        evalEntry.put("success", eval.success());
                        evalEntry.put("reason", eval.reason());
                        evals.add(evalEntry);
                    }
                    entry.put("evaluations", evals);
                    failing.add(entry);
                }
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("run_id", runId);
            response.put("threshold", threshold);
            response.put("total_examples", run.totalCount());
            response.put("failing_count", failing.size());
            response.put("failing_queries", failing);

            return textResult(response);
        } catch (Exception e) {
            return errorResult("get_failing_queries failed: " + e.getMessage());
        }
    }

    private Dataset loadDataset(String datasetPath) throws IOException {
        Path path = Path.of(datasetPath);
        String fileName = path.getFileName().toString().toLowerCase();

        if (fileName.endsWith(".json")) {
            return Dataset.fromJson(path);
        } else if (fileName.endsWith(".csv")) {
            return Dataset.fromCsv(path);
        } else if (fileName.endsWith(".jsonl")) {
            return Dataset.fromJsonl(path);
        }

        throw new IllegalArgumentException(
                "Unsupported dataset format: " + fileName + " (expected .json, .csv, or .jsonl)");
    }

    private OpenAIClient buildOpenAIClient() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable is required for evaluations");
        }
        return OpenAIOkHttpClient.builder().apiKey(apiKey).build();
    }

    private Task buildTask(OpenAIClient openai, String model, double temperature) {
        return example -> {
            String input = example.input();
            if (input == null || input.isBlank()) {
                return Map.of("output", "");
            }

            ChatCompletion completion = openai.chat()
                    .completions()
                    .create(ChatCompletionCreateParams.builder()
                            .model(ChatModel.of(model))
                            .temperature(temperature)
                            .addUserMessage(input)
                            .build());

            String output = completion.choices().get(0).message().content().orElse("");
            return Map.of("output", output);
        };
    }

    private List<Evaluator> buildEvaluators(
            String type, String criteria, double threshold, OpenAIClient openai, String model) {
        List<Evaluator> evaluators = new ArrayList<>();

        switch (type.toLowerCase()) {
            case "exact_match" -> {
                evaluators.add(
                        ExactMatchEvaluator.builder().threshold(threshold).build());
            }
            case "llm_judge" -> {
                if (criteria == null || criteria.isBlank()) {
                    criteria = "Is the actual output correct and complete compared to the expected output?";
                }
                evaluators.add(LLMJudgeEvaluator.builder()
                        .judge(prompt -> {
                            ChatCompletion c = openai.chat()
                                    .completions()
                                    .create(ChatCompletionCreateParams.builder()
                                            .model(ChatModel.of(model))
                                            .temperature(0.0)
                                            .addUserMessage(prompt)
                                            .build());
                            return c.choices().get(0).message().content().orElse("");
                        })
                        .criteria(criteria)
                        .threshold(threshold)
                        .build());
            }
            default ->
                throw new IllegalArgumentException(
                        "Unknown evaluator: " + type + " (supported: exact_match, llm_judge)");
        }

        return evaluators;
    }

    private RunRecord toRunRecord(
            ExperimentResult result, Dataset dataset, String datasetPath, String model, double temperature) {
        String runId = UUID.randomUUID().toString().substring(0, 8);

        Map<String, Double> averageScores = new LinkedHashMap<>();
        for (String name : result.evaluatorNames()) {
            averageScores.put(name, result.averageScore(name));
        }

        List<RunRecord.ItemDetail> items = new ArrayList<>();
        for (ItemResult item : result.itemResults()) {
            Example ex = item.example();

            List<RunRecord.EvalDetail> evals = new ArrayList<>();
            for (EvalResult eval : item.evalResults()) {
                evals.add(new RunRecord.EvalDetail(eval.name(), eval.score(), eval.success(), eval.reason()));
            }

            items.add(new RunRecord.ItemDetail(
                    ex.input(),
                    ex.expectedOutput(),
                    item.actualOutputs().getOrDefault("output", "").toString(),
                    item.success(),
                    evals));
        }

        Map<String, Object> modelConfig = new LinkedHashMap<>();
        modelConfig.put("model", model);
        modelConfig.put("temperature", temperature);

        return new RunRecord(
                runId,
                Instant.now(),
                result.name(),
                dataset.name(),
                datasetPath,
                modelConfig,
                result.passRate(),
                result.totalCount(),
                (int) result.passCount(),
                (int) result.failCount(),
                averageScores,
                items);
    }

    private Map<String, Object> summarizeRun(RunRecord run) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("run_id", run.id());
        summary.put("timestamp", run.timestamp().toString());
        summary.put("experiment_name", run.experimentName());
        summary.put("dataset", run.datasetName());
        summary.put("model", run.modelConfig().getOrDefault("model", "unknown"));
        summary.put("pass_rate", run.passRate());
        summary.put("total_examples", run.totalCount());
        return summary;
    }

    private McpSchema.CallToolResult textResult(Object content) {
        try {
            String text = json.writerWithDefaultPrettyPrinter().writeValueAsString(content);
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(null, text, null)), false, null, null);
        } catch (JsonProcessingException e) {
            return errorResult("Failed to serialize response: " + e.getMessage());
        }
    }

    private McpSchema.CallToolResult errorResult(String message) {
        log.error(message);
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(null, message, null)), true, null, null);
    }

    private static String requireString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return value.toString();
    }

    private static String stringOrDefault(Map<String, Object> args, String key, String defaultValue) {
        Object value = args.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private static double doubleOrDefault(Map<String, Object> args, String key, double defaultValue) {
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private static int intOrDefault(Map<String, Object> args, String key, int defaultValue) {
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(value.toString());
    }
}
