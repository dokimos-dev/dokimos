package dev.dokimos.core.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pipes {@link GateVerdict#toJson()} through the unmodified {@code render-comment.sh} to prove the
 * wire format the shell consumes has not drifted. Skips cleanly where bash, jq, or the script is
 * absent (e.g. a Windows runner). Surefire runs with the module directory as cwd, so the repo root
 * is its parent.
 */
class RenderCommentCompatTest {

    private record Rendered(int exitCode, String stdout) {}

    private static Path scriptPath() {
        Path repoRoot = Path.of("").toAbsolutePath().getParent();
        return repoRoot.resolve(".github/actions/eval-gate/render-comment.sh");
    }

    private static Rendered render(String json) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bash", scriptPath().toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(json.getBytes(StandardCharsets.UTF_8));
        }
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        return new Rendered(exit, stdout);
    }

    private static void assumeToolsAvailable() {
        assumeTrue(onPath("bash"), "bash not on PATH");
        assumeTrue(onPath("jq"), "jq not on PATH");
        assumeTrue(Files.exists(scriptPath()), "render-comment.sh not found");
    }

    private static boolean onPath(String tool) {
        for (String dir : System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator)) {
            if (!dir.isEmpty() && Files.isExecutable(Path.of(dir, tool))) {
                return true;
            }
        }
        return false;
    }

    private static GateVerdict passVerdict() {
        return new GateVerdict(
                "PASS",
                true,
                "positional",
                0.9,
                0.9,
                0.0,
                false,
                0,
                0,
                0,
                30,
                0,
                0,
                List.of(),
                List.of(),
                false,
                true,
                List.of());
    }

    private static GateVerdict failVerdict() {
        return new GateVerdict(
                "FAIL",
                false,
                "positional",
                1.0,
                0.967,
                -0.033,
                false,
                0,
                1,
                1,
                29,
                0,
                0,
                List.of(),
                List.of(new GateVerdict.RegressedCase(
                        null, "item-3", List.of(new GateVerdict.EvaluatorDrop("correctness", 1.0, 0.0, -1.0)))),
                false,
                true,
                List.of());
    }

    private static GateVerdict datasetIdFailVerdict() {
        return new GateVerdict(
                "FAIL",
                false,
                "dataset_item_id",
                1.0,
                0.5,
                -0.5,
                true,
                0,
                60,
                60,
                0,
                0,
                0,
                List.of(),
                List.of(new GateVerdict.RegressedCase(
                        "q-42", "q-42", List.of(new GateVerdict.EvaluatorDrop("correctness", 1.0, 0.0, -1.0)))),
                true,
                true,
                List.of());
    }

    private static GateVerdict failVerdictWithWarnings() {
        return new GateVerdict(
                "FAIL",
                false,
                "positional",
                1.0,
                0.967,
                -0.033,
                false,
                0,
                1,
                1,
                29,
                0,
                0,
                List.of(),
                List.of(new GateVerdict.RegressedCase(
                        null, "item-3", List.of(new GateVerdict.EvaluatorDrop("correctness", 1.0, 0.0, -1.0)))),
                false,
                true,
                List.of("Evaluator 'faithfulness' is in the baseline but missing"));
    }

    @Test
    void scriptRendersPassVerdict() throws Exception {
        assumeToolsAvailable();
        Rendered out = render(passVerdict().toJson());
        assertThat(out.exitCode()).as("stdout was: %s", out.stdout()).isZero();
        assertThat(out.stdout()).contains("Eval gate passed");
        assertThat(out.stdout()).contains("pass rate");
    }

    @Test
    void scriptRendersFailVerdictWithPositionalCase() throws Exception {
        assumeToolsAvailable();
        Rendered out = render(failVerdict().toJson());
        assertThat(out.exitCode()).as("stdout was: %s", out.stdout()).isZero();
        assertThat(out.stdout()).contains("Eval gate failed");
        assertThat(out.stdout()).contains("pass rate");
        // The script does "row " + .index with the full positional key.
        assertThat(out.stdout()).contains("row item-3");
    }

    @Test
    void scriptRendersNoBaselineWithoutCrashingOnCandidatePassRate() throws Exception {
        assumeToolsAvailable();
        Rendered out = render(GateVerdict.noBaseline(0.0).toJson());
        assertThat(out.exitCode()).as("stdout was: %s", out.stdout()).isZero();
        assertThat(out.stdout()).contains("no baseline");
    }

    @Test
    void scriptRendersDatasetIdCaseAndTruncationFooter() throws Exception {
        assumeToolsAvailable();
        Rendered out = render(datasetIdFailVerdict().toJson());
        assertThat(out.exitCode()).as("stdout was: %s", out.stdout()).isZero();
        // datasetItemId != null takes the "item <id>" branch, not "row <index>".
        assertThat(out.stdout()).contains("item q-42");
        assertThat(out.stdout()).doesNotContain("row q-42");
        // casesTruncated true renders the footer; the script's denominator is regressedCount (60).
        assertThat(out.stdout()).contains("Showing 1 of 60 regressed cases");
    }

    @Test
    void scriptIgnoresTheAdditiveWarningsField() throws Exception {
        assumeToolsAvailable();
        // warnings is unknown to the shell; an unmodified script must render and exit 0 regardless.
        Rendered out = render(failVerdictWithWarnings().toJson());
        assertThat(out.exitCode()).as("stdout was: %s", out.stdout()).isZero();
        assertThat(out.stdout()).contains("Eval gate failed");
        assertThat(out.stdout()).contains("row item-3");
    }
}
