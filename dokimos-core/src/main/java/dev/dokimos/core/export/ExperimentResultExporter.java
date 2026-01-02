package dev.dokimos.core.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.ExperimentResult;
import dev.dokimos.core.ItemResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for exporting {@link ExperimentResult} to various formats.
 * <p>
 * Supported formats are: JSON, HTML, Markdown, and CSV.
 * Each format can be exported to a file or be returned as string.
 */
public final class ExperimentResultExporter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final int FORMAT_VERSION = 1;

    private ExperimentResultExporter() {
    }

    // ========== JSON Export ==========

    /**
     * Exports the experiment result to a JSON string.
     *
     * @param result the experiment result to export
     * @return JSON string representation
     */
    public static String toJson(ExperimentResult result) {
        try {
            Map<String, Object> export = buildJsonStructure(result);
            return MAPPER.writeValueAsString(export);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize to JSON", e);
        }
    }

    /**
     * Exports the experiment result to a JSON file.
     *
     * @param result the experiment result to export
     * @param path   the file path to write to
     */
    public static void exportJson(ExperimentResult result, Path path) {
        writeToFile(path, toJson(result));
    }

    private static Map<String, Object> buildJsonStructure(ExperimentResult result) {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("version", FORMAT_VERSION);
        export.put("experimentName", result.name());
        export.put("timestamp", TIMESTAMP_FORMATTER.format(Instant.now()));
        export.put("description", result.description());
        export.put("metadata", result.metadata());

        // Config section
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("runs", result.runCount());
        export.put("config", config);

        // Summary section
        export.put("summary", buildSummary(result));

        // Items section from the first run
        export.put("items", buildItemsList(result));

        return export;
    }

    private static Map<String, Object> buildSummary(ExperimentResult result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalExamples", result.totalCount());
        summary.put("passCount", result.passCount());
        summary.put("failCount", result.failCount());
        summary.put("passRate", result.passRate());
        summary.put("runCount", result.runCount());
        summary.put("evaluators", buildEvaluatorSummary(result));
        return summary;
    }

    private static Map<String, Map<String, Object>> buildEvaluatorSummary(ExperimentResult result) {
        Map<String, Map<String, Object>> evaluators = new LinkedHashMap<>();

        for (String name : result.evaluatorNames()) {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("averageScore", result.averageScore(name));
            stats.put("stdDev", result.scoreStdDev(name));
            stats.put("passRate", calculateEvaluatorPassRate(result, name));
            evaluators.put(name, stats);
        }

        return evaluators;
    }

    private static double calculateEvaluatorPassRate(ExperimentResult result, String evaluatorName) {
        List<EvalResult> evals = result.itemResults().stream()
                .flatMap(item -> item.evalResults().stream())
                .filter(eval -> eval.name().equals(evaluatorName))
                .toList();

        if (evals.isEmpty()) {
            return 0.0;
        }

        long successCount = evals.stream().filter(EvalResult::success).count();
        return round((double) successCount / evals.size());
    }

    private static List<Map<String, Object>> buildItemsList(ExperimentResult result) {
        if (result.runCount() == 0) {
            return List.of();
        }

        int itemCount = result.runs().get(0).itemResults().size();
        List<Map<String, Object>> items = new ArrayList<>();

        for (int i = 0; i < itemCount; i++) {
            items.add(buildAggregatedItem(result, i));
        }

        return items;
    }

    private static Map<String, Object> buildAggregatedItem(ExperimentResult result, int itemIndex) {
        Map<String, Object> itemMap = new LinkedHashMap<>();

        // Use the first run for input/expectedOutput
        ItemResult firstRunItem = result.runs().get(0).itemResults().get(itemIndex);
        itemMap.put("input", formatValue(firstRunItem.example().inputs()));
        itemMap.put("expectedOutput", formatValue(firstRunItem.example().expectedOutputs()));

        // For a single run, we use the actual output directly
        if (result.runCount() == 1) {
            itemMap.put("actualOutput", formatValue(firstRunItem.actualOutputs()));
        } else {
            // Use first run's output but this could be different across runs
            itemMap.put("actualOutput", formatValue(firstRunItem.actualOutputs()));
        }

        // Aggregate success across runs
        long successCount = result.runs().stream()
                .map(run -> run.itemResults().get(itemIndex))
                .filter(ItemResult::success)
                .count();
        itemMap.put("success", successCount == result.runCount());

        itemMap.put("evaluations", buildAggregatedEvaluations(result, itemIndex));
        return itemMap;
    }

    private static List<Map<String, Object>> buildAggregatedEvaluations(ExperimentResult result, int itemIndex) {
        // Get all evaluator names from the first item and preserve the order
        List<String> evaluatorNames = result.runs().get(0).itemResults().get(itemIndex)
                .evalResults().stream()
                .map(EvalResult::name)
                .toList();

        List<Map<String, Object>> evaluations = new ArrayList<>();

        for (String evaluatorName : evaluatorNames) {
            Map<String, Object> evalMap = new LinkedHashMap<>();
            evalMap.put("evaluator", evaluatorName);

            // Collect scores across all runs for this evaluator
            List<Double> scores = new ArrayList<>();
            List<Boolean> successes = new ArrayList<>();
            Double threshold = null;
            String reason = null;

            for (var run : result.runs()) {
                ItemResult item = run.itemResults().get(itemIndex);
                for (EvalResult eval : item.evalResults()) {
                    if (eval.name().equals(evaluatorName)) {
                        scores.add(eval.score());
                        successes.add(eval.success());
                        if (threshold == null && eval.threshold() != null) {
                            threshold = eval.threshold();
                        }
                        if (reason == null && eval.reason() != null && !eval.reason().isEmpty()) {
                            reason = eval.reason();
                        }
                        break;
                    }
                }
            }

            // Calculate aggregated stats
            if (!scores.isEmpty()) {
                double avgScore = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                evalMap.put("averageScore", round(avgScore));

                if (scores.size() > 1) {
                    double stdDev = calculateStdDev(scores);
                    evalMap.put("stdDev", round(stdDev));
                    evalMap.put("scores", scores);
                } else {
                    evalMap.put("score", scores.get(0));
                }

                if (threshold != null) {
                    evalMap.put("threshold", threshold);
                }

                // Aggregated success: all runs must pass
                boolean allPassed = successes.stream().allMatch(Boolean::booleanValue);
                evalMap.put("success", allPassed);
                evalMap.put("reason", reason != null ? reason : "");
            }

            evaluations.add(evalMap);
        }

        return evaluations;
    }

    private static double calculateStdDev(List<Double> values) {
        if (values.size() <= 1) {
            return 0.0;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double sumSquaredDiffs = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .sum();
        return Math.sqrt(sumSquaredDiffs / (values.size() - 1));
    }

    // ========== HTML Export ==========

    /**
     * Exports the experiment result to an HTML string.
     *
     * @param result the experiment result to export
     * @return HTML string representation
     */
    public static String toHtml(ExperimentResult result) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>Experiment: ").append(escapeHtml(result.name())).append("</title>\n");
        html.append("<style>\n").append(getEmbeddedCss()).append("\n</style>\n");
        html.append("</head>\n<body>\n");

        // Header section
        html.append("<header>\n");
        html.append("<h1>").append(escapeHtml(result.name())).append("</h1>\n");
        html.append("<p class=\"timestamp\">Generated: ")
                .append(DISPLAY_FORMATTER.format(Instant.now()))
                .append("</p>\n");
        if (result.description() != null && !result.description().isEmpty()) {
            html.append("<p class=\"description\">").append(escapeHtml(result.description())).append("</p>\n");
        }
        html.append("</header>\n");

        // Summary section
        appendSummarySection(html, result);

        // Evaluator statistics table
        appendEvaluatorTable(html, result);

        // Results table
        appendResultsTable(html, result);

        html.append("<script>\n").append(getEmbeddedJs()).append("\n</script>\n");
        html.append("</body>\n</html>");

        return html.toString();
    }

    /**
     * Exports the experiment result to an HTML file.
     *
     * @param result the experiment result to export
     * @param path   the file path to write to
     */
    public static void exportHtml(ExperimentResult result, Path path) {
        writeToFile(path, toHtml(result));
    }

    private static void appendSummarySection(StringBuilder html, ExperimentResult result) {
        html.append("<div class=\"summary\">\n");

        // Pass rate card (prominent)
        int passPercent = (int) Math.round(result.passRate() * 100);
        html.append("<div class=\"stat-card\">\n");
        html.append("<div class=\"stat-value pass\">").append(passPercent).append("%</div>\n");
        html.append("<div class=\"stat-label\">Pass Rate</div>\n");
        html.append("</div>\n");

        // Total examples
        html.append("<div class=\"stat-card\">\n");
        html.append("<div class=\"stat-value\">").append(result.totalCount()).append("</div>\n");
        html.append("<div class=\"stat-label\">Total Examples</div>\n");
        html.append("</div>\n");

        // Pass count
        html.append("<div class=\"stat-card\">\n");
        html.append("<div class=\"stat-value success\">").append(formatNumber(result.passCount())).append("</div>\n");
        html.append("<div class=\"stat-label\">Passed</div>\n");
        html.append("</div>\n");

        // Fail count
        html.append("<div class=\"stat-card\">\n");
        html.append("<div class=\"stat-value failure\">").append(formatNumber(result.failCount())).append("</div>\n");
        html.append("<div class=\"stat-label\">Failed</div>\n");
        html.append("</div>\n");

        // Run count
        html.append("<div class=\"stat-card\">\n");
        html.append("<div class=\"stat-value\">").append(result.runCount()).append("</div>\n");
        html.append("<div class=\"stat-label\">Runs</div>\n");
        html.append("</div>\n");

        html.append("</div>\n");
    }

    private static void appendEvaluatorTable(StringBuilder html, ExperimentResult result) {
        if (result.evaluatorNames().isEmpty()) {
            return;
        }

        html.append("<h2>Evaluator Summary</h2>\n");
        html.append("<table>\n<thead>\n<tr>\n");
        html.append("<th data-sort>Evaluator</th>\n");
        html.append("<th data-sort>Avg Score</th>\n");
        html.append("<th data-sort>Std Dev</th>\n");
        html.append("<th data-sort>Pass Rate</th>\n");
        html.append("</tr>\n</thead>\n<tbody>\n");

        for (String name : result.evaluatorNames()) {
            double avgScore = result.averageScore(name);
            double stdDev = result.scoreStdDev(name);
            double passRate = calculateEvaluatorPassRate(result, name);

            html.append("<tr>\n");
            html.append("<td>").append(escapeHtml(name)).append("</td>\n");
            html.append("<td>").append(String.format("%.2f", avgScore)).append("</td>\n");
            html.append("<td>").append(String.format("%.2f", stdDev)).append("</td>\n");
            html.append("<td>").append(String.format("%.0f%%", passRate * 100)).append("</td>\n");
            html.append("</tr>\n");
        }

        html.append("</tbody>\n</table>\n");
    }

    private static void appendResultsTable(StringBuilder html, ExperimentResult result) {
        List<ItemResult> items = result.runCount() > 0
                ? result.runs().get(0).itemResults()
                : List.of();

        if (items.isEmpty()) {
            return;
        }

        html.append("<h2>Results</h2>\n");
        html.append("<table>\n<thead>\n<tr>\n");
        html.append("<th data-sort>Input</th>\n");
        html.append("<th data-sort>Expected</th>\n");
        html.append("<th data-sort>Actual</th>\n");
        html.append("<th data-sort>Status</th>\n");
        html.append("</tr>\n</thead>\n<tbody>\n");

        for (ItemResult item : items) {
            String statusClass = item.success() ? "success" : "failure";
            String statusText = item.success() ? "PASS" : "FAIL";

            html.append("<tr class=\"expandable\">\n");
            html.append("<td>").append(escapeHtml(truncate(formatValue(item.example().inputs()), 100)))
                    .append("</td>\n");
            html.append("<td>").append(escapeHtml(truncate(formatValue(item.example().expectedOutputs()), 100)))
                    .append("</td>\n");
            html.append("<td>").append(escapeHtml(truncate(formatValue(item.actualOutputs()), 100))).append("</td>\n");
            html.append("<td class=\"").append(statusClass).append("\">").append(statusText).append("</td>\n");
            html.append("</tr>\n");

            // Expandable details row
            html.append("<tr class=\"details\">\n<td colspan=\"4\">\n");
            html.append("<div class=\"detail-content\">\n");

            html.append("<strong>Full Input:</strong><pre>").append(escapeHtml(formatValue(item.example().inputs())))
                    .append("</pre>\n");
            html.append("<strong>Expected Output:</strong><pre>")
                    .append(escapeHtml(formatValue(item.example().expectedOutputs()))).append("</pre>\n");
            html.append("<strong>Actual Output:</strong><pre>").append(escapeHtml(formatValue(item.actualOutputs())))
                    .append("</pre>\n");

            html.append("<strong>Evaluations:</strong>\n<ul>\n");
            for (EvalResult eval : item.evalResults()) {
                String evalClass = eval.success() ? "success" : "failure";
                String evalStatus = eval.success() ? "PASS" : "FAIL";
                html.append("<li class=\"").append(evalClass).append("\">");
                html.append("<strong>").append(escapeHtml(eval.name())).append(":</strong> ");
                html.append(String.format("%.2f", eval.score()));
                if (eval.threshold() != null) {
                    html.append(" (threshold: ").append(String.format("%.2f", eval.threshold())).append(")");
                }
                html.append(" - ").append(evalStatus);
                if (eval.reason() != null && !eval.reason().isEmpty()) {
                    html.append("<br><em>").append(escapeHtml(eval.reason())).append("</em>");
                }
                html.append("</li>\n");
            }
            html.append("</ul>\n");

            html.append("</div>\n</td>\n</tr>\n");
        }

        html.append("</tbody>\n</table>\n");
    }

    private static String getEmbeddedCss() {
        return loadResource("export/report.css");
    }

    private static String getEmbeddedJs() {
        return loadResource("export/report.js");
    }

    private static String loadResource(String resourcePath) {
        try (InputStream is = ExperimentResultExporter.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Resource not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load resource: " + resourcePath, e);
        }
    }

    // ========== Markdown Export ==========

    /**
     * Exports the experiment result to a Markdown string.
     *
     * @param result the experiment result to export
     * @return Markdown string representation
     */
    public static String toMarkdown(ExperimentResult result) {
        StringBuilder md = new StringBuilder();

        // Header
        md.append("# Experiment: ").append(result.name()).append("\n\n");
        md.append("**Date:** ").append(DISPLAY_FORMATTER.format(Instant.now())).append("  \n");

        int passCount = (int) Math.round(result.passCount());
        int totalCount = result.totalCount();
        int passPercent = totalCount > 0 ? (int) Math.round(result.passRate() * 100) : 0;
        md.append("**Pass Rate:** ").append(passPercent).append("% (")
                .append(passCount).append("/").append(totalCount).append(")\n\n");

        if (result.description() != null && !result.description().isEmpty()) {
            md.append(result.description()).append("\n\n");
        }

        // Evaluator Summary Table
        if (!result.evaluatorNames().isEmpty()) {
            md.append("## Evaluator Summary\n\n");
            md.append("| Evaluator | Avg Score | Std Dev | Pass Rate |\n");
            md.append("|-----------|-----------|---------|----------|\n");

            for (String name : result.evaluatorNames()) {
                double avgScore = result.averageScore(name);
                double stdDev = result.scoreStdDev(name);
                double passRate = calculateEvaluatorPassRate(result, name);

                md.append("| ").append(escapeMarkdownTable(name))
                        .append(" | ").append(String.format("%.2f", avgScore))
                        .append(" | ").append(String.format("%.2f", stdDev))
                        .append(" | ").append(String.format("%.0f%%", passRate * 100))
                        .append(" |\n");
            }
            md.append("\n");
        }

        // Failed Examples Section
        List<ItemResult> failedItems = result.itemResults().stream()
                .filter(item -> !item.success())
                .toList();

        if (!failedItems.isEmpty()) {
            md.append("## Failed Examples\n\n");

            for (ItemResult item : failedItems) {
                String input = formatValue(item.example().inputs());
                md.append("### ").append(truncate(input, 80)).append("\n\n");

                md.append("**Expected:** ").append(formatValue(item.example().expectedOutputs())).append("  \n");
                md.append("**Actual:** ").append(formatValue(item.actualOutputs())).append("\n\n");

                for (EvalResult eval : item.evalResults()) {
                    String status = eval.success() ? "PASS" : "FAIL";
                    md.append("**").append(eval.name()).append(":** ")
                            .append(String.format("%.2f", eval.score()))
                            .append(" (").append(status).append(")");
                    if (eval.reason() != null && !eval.reason().isEmpty()) {
                        md.append(": ").append(eval.reason());
                    }
                    md.append("  \n");
                }
                md.append("\n");
            }
        }

        return md.toString();
    }

    /**
     * Exports the experiment result to a Markdown file.
     *
     * @param result the experiment result to export
     * @param path   the file path to write to
     */
    public static void exportMarkdown(ExperimentResult result, Path path) {
        writeToFile(path, toMarkdown(result));
    }

    // ========== CSV Export ==========

    /**
     * Exports the experiment result to a CSV string.
     *
     * @param result the experiment result to export
     * @return CSV string representation
     */
    public static String toCsv(ExperimentResult result) {
        StringBuilder csv = new StringBuilder();

        // Collect all evaluator names for dynamic columns
        List<String> evaluatorNames = new ArrayList<>(result.evaluatorNames());
        Collections.sort(evaluatorNames);

        // Header row
        csv.append("input,expected_output,actual_output,success");
        for (String name : evaluatorNames) {
            String safeName = name.toLowerCase().replace(" ", "_");
            csv.append(",").append(safeName).append("_score");
            csv.append(",").append(safeName).append("_pass");
        }
        csv.append("\n");

        // Data rows (from first run)
        List<ItemResult> items = result.runCount() > 0
                ? result.runs().get(0).itemResults()
                : List.of();

        for (ItemResult item : items) {
            csv.append(escapeCsv(formatValue(item.example().inputs()))).append(",");
            csv.append(escapeCsv(formatValue(item.example().expectedOutputs()))).append(",");
            csv.append(escapeCsv(formatValue(item.actualOutputs()))).append(",");
            csv.append(item.success());

            // Create a map for quick lookup
            Map<String, EvalResult> evalsByName = new LinkedHashMap<>();
            for (EvalResult eval : item.evalResults()) {
                evalsByName.put(eval.name(), eval);
            }

            for (String name : evaluatorNames) {
                EvalResult eval = evalsByName.get(name);
                if (eval != null) {
                    csv.append(",").append(eval.score());
                    csv.append(",").append(eval.success());
                } else {
                    csv.append(",,"); // Empty values for missing evaluator
                }
            }
            csv.append("\n");
        }

        return csv.toString();
    }

    /**
     * Exports the experiment result to a CSV file.
     *
     * @param result the experiment result to export
     * @param path   the file path to write to
     */
    public static void exportCsv(ExperimentResult result, Path path) {
        writeToFile(path, toCsv(result));
    }

    // ========== Helper Methods ==========

    private static void writeToFile(Path path, String content) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write to file: " + path, e);
        }
    }

    private static String formatValue(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        // If map has a single "input" or "output" key, return its value directly
        if (map.size() == 1) {
            Object value = map.values().iterator().next();
            return value != null ? value.toString() : "";
        }
        // Otherwise, return a simple representation
        try {
            return MAPPER.writeValueAsString(map);
        } catch (IOException e) {
            return map.toString();
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String escapeCsv(String text) {
        if (text == null) {
            return "";
        }
        // If contains comma, newline, quote, or starts with special chars, wrap in
        // quotes
        if (text.contains(",") || text.contains("\n") || text.contains("\"") ||
                text.startsWith("=") || text.startsWith("+") || text.startsWith("-") || text.startsWith("@")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String escapeMarkdownTable(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("|", "\\|");
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private static String formatNumber(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((int) value);
        }
        return String.format("%.1f", value);
    }

    private static double round(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}
