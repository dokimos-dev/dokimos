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
import dev.dokimos.core.Task;
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
            String model = stringOrDefault(arguments, "model", "gpt-4o-mini");
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

    /**
     * Compares two runs side by side, showing metric deltas and regressions.
     */
    public McpSchema.CallToolResult handleCompareRuns(Map<String, Object> arguments) {
        try {
            String idA = requireString(arguments, "run_id_a");
            String idB = requireString(arguments, "run_id_b");

            RunRecord runA = store.get(idA).orElseThrow(() -> new IllegalArgumentException("Run not found: " + idA));
            RunRecord runB = store.get(idB).orElseThrow(() -> new IllegalArgumentException("Run not found: " + idB));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("run_a", summarizeRun(runA));
            response.put("run_b", summarizeRun(runB));

            // Compute metric deltas
            Map<String, Object> comparison = new LinkedHashMap<>();

            double passRateDelta = runB.passRate() - runA.passRate();
            comparison.put(
                    "pass_rate",
                    Map.of(
                            "run_a", runA.passRate(),
                            "run_b", runB.passRate(),
                            "delta", round(passRateDelta),
                            "status", classifyDelta(passRateDelta)));

            // Per evaluator comparisons
            Map<String, Object> evaluatorDeltas = new LinkedHashMap<>();
            List<String> regressions = new ArrayList<>();

            for (String evaluator : allEvaluatorNames(runA, runB)) {
                double scoreA = runA.averageScores().getOrDefault(evaluator, 0.0);
                double scoreB = runB.averageScores().getOrDefault(evaluator, 0.0);
                double delta = scoreB - scoreA;
                String status = classifyDelta(delta);

                evaluatorDeltas.put(
                        evaluator,
                        Map.of(
                                "run_a", scoreA,
                                "run_b", scoreB,
                                "delta", round(delta),
                                "status", status));

                if ("REGRESSION".equals(status)) {
                    regressions.add(evaluator);
                }
            }

            comparison.put("evaluators", evaluatorDeltas);
            comparison.put("regressions", regressions);
            comparison.put("has_regressions", !regressions.isEmpty());

            response.put("comparison", comparison);

            return textResult(response);
        } catch (Exception e) {
            return errorResult("compare_runs failed: " + e.getMessage());
        }
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

    // --- Internal helpers ---

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

    private List<String> allEvaluatorNames(RunRecord a, RunRecord b) {
        List<String> names = new ArrayList<>(a.averageScores().keySet());
        for (String name : b.averageScores().keySet()) {
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private String classifyDelta(double delta) {
        if (delta > 0.001) {
            return "IMPROVED";
        } else if (delta < -0.001) {
            return "REGRESSION";
        }
        return "UNCHANGED";
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
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
