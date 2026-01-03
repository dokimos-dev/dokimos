package dev.dokimos.core.export;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.Example;
import dev.dokimos.core.ExperimentResult;
import dev.dokimos.core.ItemResult;
import dev.dokimos.core.RunResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ExperimentResultExporterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    // ========== JSON Tests ==========

    @Test
    void shouldExportValidJsonStructure() throws IOException {
        ExperimentResult result = createTestResult();
        String json = ExperimentResultExporter.toJson(result);

        JsonNode root = MAPPER.readTree(json);

        assertThat(root.get("version").asInt()).isEqualTo(1);
        assertThat(root.get("experimentName").asText()).isEqualTo("test-experiment");
        assertThat(root.has("timestamp")).isTrue();
        assertThat(root.has("description")).isTrue();
        assertThat(root.has("metadata")).isTrue();
        assertThat(root.has("config")).isTrue();
        assertThat(root.has("summary")).isTrue();
        assertThat(root.has("items")).isTrue();
    }

    @Test
    void shouldIncludeConfigWithRunCount() throws IOException {
        ExperimentResult result = createMultiRunResult();
        String json = ExperimentResultExporter.toJson(result);

        JsonNode root = MAPPER.readTree(json);
        JsonNode config = root.get("config");

        assertThat(config.get("runs").asInt()).isEqualTo(3);
    }

    @Test
    void shouldIncludeSummaryWithEvaluatorStats() throws IOException {
        ExperimentResult result = createTestResult();
        String json = ExperimentResultExporter.toJson(result);

        JsonNode root = MAPPER.readTree(json);
        JsonNode summary = root.get("summary");

        assertThat(summary.get("totalExamples").asInt()).isEqualTo(2);
        assertThat(summary.get("runCount").asInt()).isEqualTo(1);
        assertThat(summary.has("evaluators")).isTrue();

        JsonNode evaluators = summary.get("evaluators");
        assertThat(evaluators.has("correctness")).isTrue();
        assertThat(evaluators.get("correctness").has("averageScore")).isTrue();
        assertThat(evaluators.get("correctness").has("stdDev")).isTrue();
        assertThat(evaluators.get("correctness").has("passRate")).isTrue();
    }

    @Test
    void shouldIncludeItemsWithEvaluations() throws IOException {
        ExperimentResult result = createTestResult();
        String json = ExperimentResultExporter.toJson(result);

        JsonNode root = MAPPER.readTree(json);
        JsonNode items = root.get("items");

        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isEqualTo(2);

        JsonNode firstItem = items.get(0);
        assertThat(firstItem.has("input")).isTrue();
        assertThat(firstItem.has("expectedOutput")).isTrue();
        assertThat(firstItem.has("actualOutput")).isTrue();
        assertThat(firstItem.has("success")).isTrue();
        assertThat(firstItem.has("evaluations")).isTrue();
    }

    @Test
    void shouldExportJsonToFile() throws IOException {
        ExperimentResult result = createTestResult();
        Path jsonFile = tempDir.resolve("result.json");

        ExperimentResultExporter.exportJson(result, jsonFile);

        assertThat(jsonFile).exists();
        String content = Files.readString(jsonFile);
        assertThat(content).startsWith("{");
        assertThat(content).contains("\"experimentName\"");
    }

    @Test
    void shouldCreateParentDirectoriesForJsonExport() throws IOException {
        ExperimentResult result = createTestResult();
        Path jsonFile = tempDir.resolve("nested/dirs/result.json");

        ExperimentResultExporter.exportJson(result, jsonFile);

        assertThat(jsonFile).exists();
    }

    @Test
    void shouldRoundTripJson() throws IOException {
        ExperimentResult result = createTestResult();
        String json = ExperimentResultExporter.toJson(result);

        // Parse back to verify structure
        Map<String, Object> parsed = MAPPER.readValue(json, new TypeReference<>() {
        });

        assertThat(parsed.get("experimentName")).isEqualTo("test-experiment");
        assertThat(parsed.get("version")).isEqualTo(1);
    }

    // ========== HTML Tests ==========

    @Test
    void shouldGenerateValidHtml() {
        ExperimentResult result = createTestResult();
        String html = ExperimentResultExporter.toHtml(result);

        assertThat(html).startsWith("<!DOCTYPE html>");
        assertThat(html).contains("<html lang=\"en\">");
        assertThat(html).contains("<title>Experiment: test-experiment</title>");
        assertThat(html).contains("</html>");
    }

    @Test
    void shouldIncludeEmbeddedCssAndJs() {
        ExperimentResult result = createTestResult();
        String html = ExperimentResultExporter.toHtml(result);

        assertThat(html).contains("<style>");
        assertThat(html).contains("</style>");
        assertThat(html).contains("<script>");
        assertThat(html).contains("</script>");
    }

    @Test
    void shouldEscapeHtmlSpecialCharacters() {
        ExperimentResult result = createResultWithSpecialChars("<script>alert('xss')</script>");
        String html = ExperimentResultExporter.toHtml(result);

        assertThat(html).doesNotContain("<script>alert");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void shouldIncludePassRateProminently() {
        ExperimentResult result = createTestResult();
        String html = ExperimentResultExporter.toHtml(result);

        assertThat(html).contains("Pass Rate");
        assertThat(html).contains("stat-card");
    }

    @Test
    void shouldColorCodeSuccessAndFailure() {
        ExperimentResult result = createTestResult();
        String html = ExperimentResultExporter.toHtml(result);

        assertThat(html).contains("class=\"success\"");
        assertThat(html).contains("class=\"failure\"");
    }

    @Test
    void shouldExportHtmlToFile() throws IOException {
        ExperimentResult result = createTestResult();
        Path htmlFile = tempDir.resolve("result.html");

        ExperimentResultExporter.exportHtml(result, htmlFile);

        assertThat(htmlFile).exists();
        String content = Files.readString(htmlFile);
        assertThat(content).startsWith("<!DOCTYPE html>");
    }

    // ========== Markdown Tests ==========

    @Test
    void shouldGenerateValidMarkdown() {
        ExperimentResult result = createTestResult();
        String md = ExperimentResultExporter.toMarkdown(result);

        assertThat(md).startsWith("# Experiment: test-experiment");
        assertThat(md).contains("**Date:**");
        assertThat(md).contains("**Pass Rate:**");
    }

    @Test
    void shouldIncludeEvaluatorSummaryTable() {
        ExperimentResult result = createTestResult();
        String md = ExperimentResultExporter.toMarkdown(result);

        assertThat(md).contains("## Evaluator Summary");
        assertThat(md).contains("| Evaluator | Avg Score | Std Dev | Pass Rate |");
        assertThat(md).contains("|-----------|-----------|---------|----------|");
        assertThat(md).contains("correctness");
    }

    @Test
    void shouldOnlyShowFailedExamplesInDetail() {
        ExperimentResult result = createResultWithMixedOutcomes();
        String md = ExperimentResultExporter.toMarkdown(result);

        assertThat(md).contains("## Failed Examples");
        assertThat(md).contains("Capital of Italy?"); // Failed example
        // The passed examples shouldn't be in the "Failed Examples" section
        assertThat(md).doesNotContain("Capital of France?");
        assertThat(md).doesNotContain("Capital of Germany?");
    }

    @Test
    void shouldNotDuplicateFailedExamplesAcrossRuns() {
        // Multi-run experiment where the same example fails in all runs
        ExperimentResult result = new ExperimentResult(
                "multi-run-failures",
                "",
                Map.of(),
                List.of(
                        new RunResult(0, List.of(
                                new ItemResult(Example.of("Q1", "A1"), Map.of("output", "Wrong"),
                                        List.of(EvalResult.failure("accuracy", 0.3, "Failed"))))),
                        new RunResult(1, List.of(
                                new ItemResult(Example.of("Q1", "A1"), Map.of("output", "Wrong"),
                                        List.of(EvalResult.failure("accuracy", 0.2, "Failed"))))),
                        new RunResult(2, List.of(
                                new ItemResult(Example.of("Q1", "A1"), Map.of("output", "Wrong"),
                                        List.of(EvalResult.failure("accuracy", 0.4, "Failed")))))));

        String md = ExperimentResultExporter.toMarkdown(result);

        // Count occurrences of the failed example header - should appear exactly once
        int occurrences = md.split("### Q1").length - 1;
        assertThat(occurrences).isEqualTo(1);
    }

    @Test
    void shouldShowFailedExamplesFromLaterRunsUsingFirstFailingRunData() {
        // Item passes in run 0, but fails in runs 1 and 2
        // Should appear in Failed Examples using data from run 1
        ExperimentResult result = new ExperimentResult(
                "flaky-test",
                "",
                Map.of(),
                List.of(
                        new RunResult(0, List.of(
                                new ItemResult(Example.of("Flaky question", "Expected"),
                                        Map.of("output", "Correct in run 0"),
                                        List.of(EvalResult.success("accuracy", 0.9, "Passed"))))),
                        new RunResult(1, List.of(
                                new ItemResult(Example.of("Flaky question", "Expected"),
                                        Map.of("output", "Wrong in run 1"),
                                        List.of(EvalResult.failure("accuracy", 0.3, "Failed in run 1"))))),
                        new RunResult(2, List.of(
                                new ItemResult(Example.of("Flaky question", "Expected"),
                                        Map.of("output", "Wrong in run 2"),
                                        List.of(EvalResult.failure("accuracy", 0.4, "Failed in run 2")))))));

        String md = ExperimentResultExporter.toMarkdown(result);

        assertThat(md).contains("## Failed Examples");
        assertThat(md).contains("Flaky question");

        // Should show data from run 1, not run 0
        assertThat(md).contains("Wrong in run 1");
        assertThat(md).contains("Failed in run 1");
        assertThat(md).doesNotContain("Correct in run 0");
        assertThat(md).doesNotContain("Wrong in run 2");
    }

    @Test
    void shouldExportMarkdownToFile() throws IOException {
        ExperimentResult result = createTestResult();
        Path mdFile = tempDir.resolve("result.md");

        ExperimentResultExporter.exportMarkdown(result, mdFile);

        assertThat(mdFile).exists();
        String content = Files.readString(mdFile);
        assertThat(content).startsWith("# Experiment:");
    }

    // ========== CSV Tests ==========

    @Test
    void shouldGenerateCsvWithHeader() {
        ExperimentResult result = createTestResult();
        String csv = ExperimentResultExporter.toCsv(result);

        String[] lines = csv.split("\n");
        assertThat(lines[0]).contains("input");
        assertThat(lines[0]).contains("expected_output");
        assertThat(lines[0]).contains("actual_output");
        assertThat(lines[0]).contains("success");
    }

    @Test
    void shouldIncludeDynamicEvaluatorColumns() {
        ExperimentResult result = createTestResult();
        String csv = ExperimentResultExporter.toCsv(result);

        String header = csv.split("\n")[0];
        assertThat(header).contains("correctness_score");
        assertThat(header).contains("correctness_pass");
    }

    @Test
    void shouldEscapeCsvSpecialCharacters() {
        ExperimentResult result = createResultWithSpecialChars("Hello, \"World\"");
        String csv = ExperimentResultExporter.toCsv(result);

        // Quoted string with escaped quotes
        assertThat(csv).contains("\"Hello, \"\"World\"\"\"");
    }

    @Test
    void shouldHandleMultipleEvaluators() {
        ExperimentResult result = createResultWithMultipleEvaluators();
        String csv = ExperimentResultExporter.toCsv(result);

        String header = csv.split("\n")[0];
        assertThat(header).contains("accuracy_score");
        assertThat(header).contains("helpfulness_score");
    }

    @Test
    void shouldExportCsvToFile() throws IOException {
        ExperimentResult result = createTestResult();
        Path csvFile = tempDir.resolve("result.csv");

        ExperimentResultExporter.exportCsv(result, csvFile);

        assertThat(csvFile).exists();
        String content = Files.readString(csvFile);
        assertThat(content).contains("input,expected_output");
    }

    @Test
    void shouldHandleEmptyExperimentResult() {
        ExperimentResult empty = new ExperimentResult("empty", "", Map.of(), List.of());

        assertThatCode(() -> ExperimentResultExporter.toJson(empty)).doesNotThrowAnyException();
        assertThatCode(() -> ExperimentResultExporter.toHtml(empty)).doesNotThrowAnyException();
        assertThatCode(() -> ExperimentResultExporter.toMarkdown(empty)).doesNotThrowAnyException();
        assertThatCode(() -> ExperimentResultExporter.toCsv(empty)).doesNotThrowAnyException();
    }

    @Test
    void shouldHandleSingleRunExperiment() {
        ExperimentResult result = createTestResult();

        String json = ExperimentResultExporter.toJson(result);
        assertThat(json).contains("\"runCount\" : 1");
    }

    @Test
    void shouldHandleMultiRunExperiment() {
        ExperimentResult result = createMultiRunResult();

        String json = ExperimentResultExporter.toJson(result);
        assertThat(json).contains("\"runCount\" : 3");
    }

    @Test
    void shouldAggregateEvaluationsAcrossRuns() throws IOException {
        ExperimentResult result = createMultiRunResult();
        String json = ExperimentResultExporter.toJson(result);

        JsonNode root = MAPPER.readTree(json);
        JsonNode items = root.get("items");
        assertThat(items.size()).isEqualTo(1);

        JsonNode evaluation = items.get(0).get("evaluations").get(0);

        // Should have averageScore for multi-run
        assertThat(evaluation.has("averageScore")).isTrue();
        assertThat(evaluation.get("averageScore").asDouble()).isCloseTo(0.8,
                org.assertj.core.api.Assertions.within(0.01));

        // Should have stdDev for multi-run
        assertThat(evaluation.has("stdDev")).isTrue();

        // Should have scores array for multi-run
        assertThat(evaluation.has("scores")).isTrue();
        assertThat(evaluation.get("scores").isArray()).isTrue();
        assertThat(evaluation.get("scores").size()).isEqualTo(3);
    }

    @Test
    void shouldUseSingleScoreForSingleRun() throws IOException {
        ExperimentResult result = createTestResult();
        String json = ExperimentResultExporter.toJson(result);

        JsonNode root = MAPPER.readTree(json);
        JsonNode evaluation = root.get("items").get(0).get("evaluations").get(0);

        assertThat(evaluation.has("score")).isTrue();
        assertThat(evaluation.has("scores")).isFalse();
        assertThat(evaluation.has("stdDev")).isFalse();
    }

    @Test
    void shouldHandleNoEvaluators() {
        ExperimentResult result = new ExperimentResult(
                "no-evaluators",
                "",
                Map.of(),
                List.of(new RunResult(0, List.of(
                        new ItemResult(Example.of("Q1", "A1"), Map.of(), List.of())))));

        assertThatCode(() -> ExperimentResultExporter.toJson(result)).doesNotThrowAnyException();
        assertThatCode(() -> ExperimentResultExporter.toCsv(result)).doesNotThrowAnyException();
    }

    @Test
    void shouldHandleNullReason() {
        ExperimentResult result = new ExperimentResult(
                "null-reason",
                "",
                Map.of(),
                List.of(new RunResult(0, List.of(
                        new ItemResult(Example.of("Q1", "A1"), Map.of(),
                                List.of(EvalResult.of("test", 0.8, 0.5, null)))))));

        String json = ExperimentResultExporter.toJson(result);
        assertThat(json).contains("\"reason\" : \"\"");
    }

    @Test
    void shouldHandleNullThreshold() throws IOException {
        ExperimentResult result = new ExperimentResult(
                "null-threshold",
                "",
                Map.of(),
                List.of(new RunResult(0, List.of(
                        new ItemResult(Example.of("Q1", "A1"), Map.of(),
                                List.of(EvalResult.success("test", 0.8, "OK")))))));

        String json = ExperimentResultExporter.toJson(result);
        JsonNode root = MAPPER.readTree(json);
        JsonNode evaluations = root.get("items").get(0).get("evaluations").get(0);

        // Threshold should not be in the output when it's null
        assertThat(evaluations.has("threshold")).isFalse();
    }

    @Test
    void shouldHandleUnicodeCharacters() throws IOException {
        ExperimentResult result = createResultWithSpecialChars("Test: éñü 中文 🎉");
        Path jsonFile = tempDir.resolve("unicode.json");

        ExperimentResultExporter.exportJson(result, jsonFile);

        String content = Files.readString(jsonFile, StandardCharsets.UTF_8);
        assertThat(content).contains("éñü");
        assertThat(content).contains("中文");
    }

    @Test
    void shouldHandleNewlinesInContent() {
        ExperimentResult result = createResultWithSpecialChars("Line 1\nLine 2\nLine 3");
        String csv = ExperimentResultExporter.toCsv(result);

        // Should be quoted due to newlines
        assertThat(csv).contains("\"Line 1\nLine 2\nLine 3\"");
    }

    @Test
    void shouldExtractOutputKeyFromMultiKeyActualOutputMap() throws IOException {
        // When actualOutputs has multiple keys including "output", only the "output"
        // value should be used
        ExperimentResult result = new ExperimentResult(
                "multi-key-output",
                "",
                Map.of(),
                List.of(new RunResult(0, List.of(
                        new ItemResult(
                                Example.of("question", "expected answer"),
                                Map.of(
                                        "output", "The actual response text",
                                        "retrievedContext", List.of("context 1", "context 2"),
                                        "metadata", Map.of("latency", 100)),
                                List.of(EvalResult.success("accuracy", 0.9, "Good")))))));

        // Check JSON
        String json = ExperimentResultExporter.toJson(result);
        JsonNode root = MAPPER.readTree(json);
        String actualOutput = root.get("items").get(0).get("actualOutput").asText();
        assertThat(actualOutput).isEqualTo("The actual response text");
        assertThat(actualOutput).doesNotContain("retrievedContext");

        // Check HTML
        String html = ExperimentResultExporter.toHtml(result);
        assertThat(html).contains("The actual response text");
        assertThat(html).doesNotContain("retrievedContext");

        // Check CSV
        String csv = ExperimentResultExporter.toCsv(result);
        assertThat(csv).contains("The actual response text");
        assertThat(csv).doesNotContain("retrievedContext");
    }

    // ========== Tests with ExperimentResult ==========

    @Test
    void shouldExportViaExperimentResultMethods() {
        ExperimentResult result = createTestResult();

        assertThat(result.toJson()).isNotEmpty();
        assertThat(result.toHtml()).isNotEmpty();
        assertThat(result.toMarkdown()).isNotEmpty();
        assertThat(result.toCsv()).isNotEmpty();
    }

    @Test
    void shouldExportFilesViaExperimentResultMethods() throws IOException {
        ExperimentResult result = createTestResult();

        result.exportJson(tempDir.resolve("test.json"));
        result.exportHtml(tempDir.resolve("test.html"));
        result.exportMarkdown(tempDir.resolve("test.md"));
        result.exportCsv(tempDir.resolve("test.csv"));

        assertThat(tempDir.resolve("test.json")).exists();
        assertThat(tempDir.resolve("test.html")).exists();
        assertThat(tempDir.resolve("test.md")).exists();
        assertThat(tempDir.resolve("test.csv")).exists();
    }

    // ========== Helper Methods ==========

    private ExperimentResult createTestResult() {
        return new ExperimentResult(
                "test-experiment",
                "A test experiment",
                Map.of("version", "1.0"),
                List.of(new RunResult(0, List.of(
                        new ItemResult(
                                Example.of("What is 2+2?", "4"),
                                Map.of("output", "4"),
                                List.of(EvalResult.success("correctness", 0.9, "Correct"))),
                        new ItemResult(
                                Example.of("What is 3*3?", "9"),
                                Map.of("output", "10"),
                                List.of(EvalResult.failure("correctness", 0.3, "Wrong answer")))))));
    }

    private ExperimentResult createMultiRunResult() {
        return new ExperimentResult(
                "multi-run-test",
                "",
                Map.of(),
                List.of(
                        new RunResult(0, List.of(
                                new ItemResult(Example.of("Q1", "A1"), Map.of("output", "A1"),
                                        List.of(EvalResult.success("accuracy", 0.8, "Good"))))),
                        new RunResult(1, List.of(
                                new ItemResult(Example.of("Q1", "A1"), Map.of("output", "A1"),
                                        List.of(EvalResult.success("accuracy", 0.7, "OK"))))),
                        new RunResult(2, List.of(
                                new ItemResult(Example.of("Q1", "A1"), Map.of("output", "A1"),
                                        List.of(EvalResult.success("accuracy", 0.9, "Great")))))));
    }

    private ExperimentResult createResultWithMixedOutcomes() {
        return new ExperimentResult(
                "mixed-outcomes",
                "",
                Map.of(),
                List.of(new RunResult(0, List.of(
                        new ItemResult(
                                Example.of("Capital of France?", "Paris"),
                                Map.of("output", "Paris"),
                                List.of(EvalResult.success("correctness", 0.9, "Correct"))),
                        new ItemResult(
                                Example.of("Capital of Germany?", "Berlin"),
                                Map.of("output", "Berlin"),
                                List.of(EvalResult.success("correctness", 0.8, "Correct"))),
                        new ItemResult(
                                Example.of("Capital of Italy?", "Rome"),
                                Map.of("output", "Milan"),
                                List.of(EvalResult.failure("correctness", 0.3, "Said Milan")))))));
    }

    private ExperimentResult createResultWithSpecialChars(String input) {
        return new ExperimentResult(
                "special-chars",
                "",
                Map.of(),
                List.of(new RunResult(0, List.of(
                        new ItemResult(
                                Example.of(input, "expected"),
                                Map.of("output", "actual"),
                                List.of(EvalResult.success("test", 0.9, "OK")))))));
    }

    private ExperimentResult createResultWithMultipleEvaluators() {
        return new ExperimentResult(
                "multi-evaluator",
                "",
                Map.of(),
                List.of(new RunResult(0, List.of(
                        new ItemResult(
                                Example.of("How to reset password?", "Go to settings"),
                                Map.of("output", "Click settings"),
                                List.of(
                                        EvalResult.success("accuracy", 0.8, "Accurate"),
                                        EvalResult.success("helpfulness", 0.9, "Helpful")))))));
    }
}
