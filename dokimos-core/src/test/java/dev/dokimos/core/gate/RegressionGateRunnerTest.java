package dev.dokimos.core.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.Example;
import dev.dokimos.core.ExperimentResult;
import dev.dokimos.core.ItemResult;
import dev.dokimos.core.RunResult;
import dev.dokimos.core.internal.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives every lifecycle branch of {@link RegressionGateRunner} through a hand-built {@link
 * RegressionGateRunner.Environment} and {@link TempDir} paths, so no real environment variable is
 * read and nothing is written into the source tree.
 */
class RegressionGateRunnerTest {

    private static EvalResult ev(String name, double score, double threshold) {
        return new EvalResult(name, score, threshold, score >= threshold, "", Map.of());
    }

    private static ItemResult item(int i, EvalResult... evals) {
        return new ItemResult(Example.of("q" + i, "a" + i), Map.of("output", "x"), List.of(evals));
    }

    private static ExperimentResult thirtyItems(int brokenIndex, double score, double brokenScore) {
        List<ItemResult> items = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            double s = i == brokenIndex ? brokenScore : score;
            items.add(item(i, ev("correctness", s, 0.7)));
        }
        return new ExperimentResult("rag-eval", "", Map.of(), List.of(new RunResult(0, items)));
    }

    /** Thirty all-passing items each carrying the named evaluators, so dropping one is a coverage loss. */
    private static ExperimentResult thirtyItemsWithEvaluators(String... evaluatorNames) {
        List<ItemResult> items = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            EvalResult[] evals = new EvalResult[evaluatorNames.length];
            for (int e = 0; e < evaluatorNames.length; e++) {
                evals[e] = ev(evaluatorNames[e], 1.0, 0.7);
            }
            items.add(item(i, evals));
        }
        return new ExperimentResult("rag-eval", "", Map.of(), List.of(new RunResult(0, items)));
    }

    /** A test environment with a collecting log so assertions can inspect the banners. */
    private static final class StubEnv implements RegressionGateRunner.Environment {
        private final boolean ci;
        private final boolean updateRequested;
        private final Path verdictDir;
        final List<String> logs = new ArrayList<>();

        StubEnv(boolean ci, boolean updateRequested, Path verdictDir) {
            this.ci = ci;
            this.updateRequested = updateRequested;
            this.verdictDir = verdictDir;
        }

        @Override
        public boolean ci() {
            return ci;
        }

        @Override
        public boolean updateRequested() {
            return updateRequested;
        }

        @Override
        public Path verdictDir() {
            return verdictDir;
        }

        @Override
        public Consumer<String> log() {
            return logs::add;
        }
    }

    private static GateVerdict readVerdictJson(Path verdictDir) {
        try {
            String json = Files.readString(verdictDir.resolve("gate-verdict.json"));
            return Json.read(json, GateVerdict.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void localBootstrapWritesTheBaselineAndThrowsOnce(@TempDir Path dir) {
        Path baseline = dir.resolve("baselines").resolve("rag.json");
        StubEnv env = new StubEnv(false, false, dir.resolve("verdict"));
        ExperimentResult candidate = thirtyItems(-1, 1.0, 1.0);

        assertThatThrownBy(() -> RegressionGateRunner.run(candidate, baseline, GateConfig.defaults(), env))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("created")
                .hasMessageContaining(baseline.toAbsolutePath().toString());

        assertThat(baseline).exists();
    }

    @Test
    void localBootstrapWritesARoundTrippableBaseline(@TempDir Path dir) throws Exception {
        Path baseline = dir.resolve("rag.json");
        StubEnv env = new StubEnv(false, false, dir.resolve("verdict"));
        ExperimentResult candidate = thirtyItems(-1, 1.0, 1.0);

        assertThatThrownBy(() -> RegressionGateRunner.run(candidate, baseline, GateConfig.defaults(), env))
                .isInstanceOf(AssertionError.class);

        BaselineFile written = BaselineStore.read(baseline);
        assertThat(written.items()).hasSize(30);
        assertThat(written.pairing()).isEqualTo("positional");
    }

    @Test
    void localBootstrapWithBootstrapPassesWritesButDoesNotThrow(@TempDir Path dir) {
        Path baseline = dir.resolve("rag.json");
        StubEnv env = new StubEnv(false, false, dir.resolve("verdict"));
        ExperimentResult candidate = thirtyItems(-1, 1.0, 1.0);
        GateConfig config = GateConfig.builder().bootstrapPasses(true).build();

        GateVerdict verdict = RegressionGateRunner.run(candidate, baseline, config, env);

        assertThat(baseline).exists();
        assertThat(verdict.passed()).isTrue();
        // No comparison ran on bootstrap, so no verdict JSON is written.
        assertThat(env.verdictDir().resolve("gate-verdict.json")).doesNotExist();
    }

    @Test
    void noBaselineInCiDoesNotWriteAndReportsNoBaseline(@TempDir Path dir) {
        Path baseline = dir.resolve("rag.json");
        StubEnv env = new StubEnv(true, false, dir.resolve("verdict"));
        ExperimentResult candidate = thirtyItems(-1, 0.5, 0.5);

        GateVerdict verdict = RegressionGateRunner.run(candidate, baseline, GateConfig.defaults(), env);

        assertThat(baseline).doesNotExist();
        assertThat(verdict.status()).isEqualTo("NO_BASELINE");
        assertThat(verdict.candidatePassRate()).isEqualTo(candidate.passRate());
        assertThat(env.logs).anyMatch(m -> m.contains("The gate measured nothing"));
        // No comparison ran; comparedPass must stay false so CI can tell a no-op bootstrap from a
        // real green comparison.
        assertThat(verdict.comparedPass()).isFalse();

        GateVerdict onDisk = readVerdictJson(env.verdictDir());
        assertThat(onDisk.status()).isEqualTo("NO_BASELINE");
        assertThat(onDisk.candidatePassRate()).isEqualTo(candidate.passRate());
        assertThat(onDisk.comparedPass()).isFalse();
    }

    @Test
    void updateRequestedOverwritesTheBaselineAndPasses(@TempDir Path dir) throws Exception {
        Path baseline = dir.resolve("rag.json");
        // Seed a different baseline (everything passing) so the overwrite is observable.
        BaselineStore.write(baseline, thirtyItems(-1, 1.0, 1.0), GateConfig.defaults());
        String before = Files.readString(baseline);

        StubEnv env = new StubEnv(false, true, dir.resolve("verdict"));
        ExperimentResult candidate = thirtyItems(3, 1.0, 0.0); // one item broken

        GateVerdict verdict = RegressionGateRunner.run(candidate, baseline, GateConfig.defaults(), env);

        assertThat(verdict.passed()).isTrue();
        // The update path ran no comparison; comparedPass must stay false (status NO_BASELINE) so CI
        // can tell a re-baseline from a real green comparison. thirtyItems(3, 1.0, 0.0) is a guard-2
        // FAIL when compared (see RegressionGateTest), so a pass here proves update preempts compare.
        assertThat(verdict.comparedPass()).isFalse();
        assertThat(verdict.status()).isEqualTo("NO_BASELINE");
        assertThat(Files.readString(baseline)).isNotEqualTo(before);
        assertThat(env.logs).anyMatch(m -> m.contains("baseline updated"));
        // Update did not compare, so no verdict JSON.
        assertThat(env.verdictDir().resolve("gate-verdict.json")).doesNotExist();
    }

    @Test
    void updateViaConfigFlagAlsoOverwrites(@TempDir Path dir) throws Exception {
        Path baseline = dir.resolve("rag.json");
        BaselineStore.write(baseline, thirtyItems(-1, 1.0, 1.0), GateConfig.defaults());
        String before = Files.readString(baseline);

        StubEnv env = new StubEnv(false, false, dir.resolve("verdict"));
        GateConfig config = GateConfig.builder().updateBaseline(true).build();
        ExperimentResult candidate = thirtyItems(3, 1.0, 0.0);

        RegressionGateRunner.run(candidate, baseline, config, env);

        assertThat(Files.readString(baseline)).isNotEqualTo(before);
    }

    @Test
    void comparePassWritesTheVerdictJson(@TempDir Path dir) throws Exception {
        Path baseline = dir.resolve("rag.json");
        ExperimentResult run = thirtyItems(-1, 1.0, 1.0);
        BaselineStore.write(baseline, run, GateConfig.defaults());

        StubEnv env = new StubEnv(false, false, dir.resolve("verdict"));
        GateVerdict verdict = RegressionGateRunner.run(run, baseline, GateConfig.defaults(), env);

        assertThat(verdict.status()).isEqualTo("PASS");
        assertThat(verdict.comparedPass()).isTrue();
        assertThat(readVerdictJson(env.verdictDir()).status()).isEqualTo("PASS");
    }

    @Test
    void compareFailLocalThrowsWithTheReBaselineCommandAndPath(@TempDir Path dir) throws Exception {
        Path baseline = dir.resolve("rag.json");
        BaselineStore.write(baseline, thirtyItems(-1, 1.0, 1.0), GateConfig.defaults());

        StubEnv env = new StubEnv(false, false, dir.resolve("verdict"));
        ExperimentResult candidate = thirtyItems(3, 1.0, 0.0); // guard-2 localized break

        assertThatThrownBy(() -> RegressionGateRunner.run(candidate, baseline, GateConfig.defaults(), env))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("DOKIMOS_UPDATE_BASELINE=true")
                .hasMessageContaining("-Ddokimos.updateBaseline")
                .hasMessageContaining(baseline.toString());

        // The verdict JSON is written BEFORE the throw so a CI action can still post a comment.
        assertThat(readVerdictJson(env.verdictDir()).status()).isEqualTo("FAIL");
    }

    @Test
    void compareFailInCiStillThrowsAndWritesFailJson(@TempDir Path dir) throws Exception {
        // Option A: the throw decision is identical in CI and locally.
        Path baseline = dir.resolve("rag.json");
        BaselineStore.write(baseline, thirtyItems(-1, 1.0, 1.0), GateConfig.defaults());

        StubEnv env = new StubEnv(true, false, dir.resolve("verdict"));
        ExperimentResult candidate = thirtyItems(3, 1.0, 0.0);

        assertThatThrownBy(() -> RegressionGateRunner.run(candidate, baseline, GateConfig.defaults(), env))
                .isInstanceOf(AssertionError.class);

        assertThat(readVerdictJson(env.verdictDir()).status()).isEqualTo("FAIL");
    }

    @Test
    void failOnRegressionFalseDoesNotThrowOnARealRegression(@TempDir Path dir) throws Exception {
        // RegressionGate folds failOnRegression(false) into a PASS status; the runner must not throw,
        // proving it gates purely on the verdict status and never re-checks failOnRegression.
        Path baseline = dir.resolve("rag.json");
        BaselineStore.write(
                baseline,
                thirtyItems(-1, 1.0, 1.0),
                GateConfig.builder().failOnRegression(false).build());

        StubEnv env = new StubEnv(false, false, dir.resolve("verdict"));
        GateConfig config = GateConfig.builder().failOnRegression(false).build();
        ExperimentResult candidate = thirtyItems(3, 1.0, 0.0);

        GateVerdict verdict = RegressionGateRunner.run(candidate, baseline, config, env);

        assertThat(verdict.status()).isEqualTo("PASS");
        assertThat(verdict.passed()).isTrue();
    }

    @Test
    void coverageLossFailMessageNamesTheDroppedEvaluator(@TempDir Path dir) throws Exception {
        // Baseline carries two evaluators; the candidate drops one (onRemovedEvaluator defaults to
        // FAIL). No score regressed, so the FAIL has no regressed evaluator or case — the only
        // explanation is the warning, which the console message must surface (not just the JSON).
        Path baseline = dir.resolve("rag.json");
        BaselineStore.write(
                baseline, thirtyItemsWithEvaluators("correctness", "relevance"), GateConfig.defaults());

        StubEnv env = new StubEnv(false, false, dir.resolve("verdict"));
        ExperimentResult candidate = thirtyItemsWithEvaluators("correctness");

        assertThatThrownBy(() -> RegressionGateRunner.run(candidate, baseline, GateConfig.defaults(), env))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("relevance")
                .hasMessageContaining("missing from the candidate");

        assertThat(readVerdictJson(env.verdictDir()).status()).isEqualTo("FAIL");
    }

    @Test
    void verdictJsonWriteFailureDoesNotMaskAFail(@TempDir Path dir) throws Exception {
        Path baseline = dir.resolve("rag.json");
        BaselineStore.write(baseline, thirtyItems(-1, 1.0, 1.0), GateConfig.defaults());

        // Point the verdict dir at an existing regular file so createDirectories cannot make it.
        Path blocker = dir.resolve("blocker");
        Files.writeString(blocker, "x");
        StubEnv env = new StubEnv(false, false, blocker.resolve("sub"));
        ExperimentResult candidate = thirtyItems(3, 1.0, 0.0);

        assertThatThrownBy(() -> RegressionGateRunner.run(candidate, baseline, GateConfig.defaults(), env))
                .isInstanceOf(AssertionError.class);

        assertThat(env.logs).anyMatch(m -> m.contains("could not write"));
    }
}
