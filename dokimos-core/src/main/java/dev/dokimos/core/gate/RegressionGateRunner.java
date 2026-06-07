package dev.dokimos.core.gate;

import dev.dokimos.core.ExperimentResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The run lifecycle around {@link RegressionGate}: resolves whether to update, bootstrap, or compare
 * a baseline, performs the file I/O, and turns a FAIL verdict into an {@link AssertionError}.
 *
 * <p>{@link RegressionGate#evaluate} is the pure verdict layer; this is the side-effecting layer that
 * {@link dev.dokimos.core.Assertions#assertNoRegression} delegates to. Everything that reads the
 * outside world — CI detection, the update-requested flag, the verdict-output directory, the log sink
 * — is injected through {@link Environment}, so the whole lifecycle is unit-testable with a hand-built
 * environment and a temp directory: no real environment variables, no writes into the source tree.
 *
 * <p>The throw decision is identical in CI and locally: a FAIL verdict throws on every runner, so a
 * failing {@code mvn test} (or Gradle, GitLab, Jenkins) is the gate without any CI-specific wiring.
 * {@link Environment#ci()} is consulted in exactly one place — suppressing the bootstrap write when
 * no baseline exists, because a CI checkout is ephemeral and the write would be gitignored and lost.
 */
public final class RegressionGateRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegressionGateRunner.class);
    private static final String VERDICT_SUFFIX = ".json";
    private static final String VERDICT_FALLBACK = "gate-verdict"; // when a baseline stem sanitizes to empty

    private RegressionGateRunner() {}

    /**
     * Reads the outside world the lifecycle depends on. The production implementation comes from
     * {@link #systemEnvironment()}; tests construct their own so no real environment is touched.
     */
    public interface Environment {
        /** @return true on a CI runner (suppresses the no-baseline bootstrap write) */
        boolean ci();

        /** @return true when an out-of-band baseline update was requested (env var or system property) */
        boolean updateRequested();

        /** @return the directory the per-baseline verdict JSON (named for the baseline stem) is written to */
        Path verdictDir();

        /** @return the sink for the one-time bootstrap / no-baseline banners and warnings */
        Consumer<String> log();
    }

    /**
     * Runs the gate lifecycle for one candidate against one baseline path.
     *
     * <p>Branch order (first match wins):
     *
     * <ol>
     *   <li><b>Update</b> — {@code config.updateBaseline()} or {@code env.updateRequested()}: overwrite
     *       the baseline from the candidate and pass. No comparison ran, so no verdict JSON is written.
     *   <li><b>No baseline, CI</b>: do not write a baseline (the checkout is ephemeral); emit a
     *       NO_BASELINE verdict and the loud banner, write the verdict JSON, and pass.
     *   <li><b>No baseline, local</b>: write the baseline from the candidate. Pass when {@code
     *       config.bootstrapPasses()}; otherwise throw once so the new file is reviewed and committed.
     *   <li><b>Compare</b>: read the baseline, evaluate, always write the verdict JSON, then throw on a
     *       FAIL verdict.
     * </ol>
     *
     * @param candidate the candidate experiment result
     * @param baseline the baseline file path (already resolved; this layer applies no path convention)
     * @param config the gate configuration
     * @param env the injected environment
     * @return the verdict on every passing path; the update and bootstrap paths return a non-null
     *     sentinel the void assertion API discards
     * @throws AssertionError on a FAIL verdict, and on a local missing baseline unless {@code
     *     config.bootstrapPasses()}
     * @throws UncheckedIOException if the baseline itself cannot be written or read (a real gate error,
     *     unlike the best-effort verdict-JSON write)
     */
    public static GateVerdict run(ExperimentResult candidate, Path baseline, GateConfig config, Environment env) {
        if (config.updateBaseline() || env.updateRequested()) {
            writeBaseline(baseline, candidate, config);
            env.log().accept("baseline updated at " + baseline.toAbsolutePath());
            // No comparison ran this invocation; the return is an unobserved sentinel (the void
            // assertion API discards it) and comparedPass=false is the honest signal.
            return GateVerdict.noBaseline(candidate.passRate());
        }

        if (Files.notExists(baseline)) {
            if (env.ci()) {
                GateVerdict verdict = GateVerdict.noBaseline(candidate.passRate());
                writeVerdictJson(env, baseline, verdict);
                env.log()
                        .accept("No committed baseline; run locally with DOKIMOS_UPDATE_BASELINE=true and"
                                + " commit the file. The gate measured nothing.");
                return verdict;
            }
            writeBaseline(baseline, candidate, config);
            // Absolute path here is deliberate (unlike the relative commit-path in failMessage): the
            // user needs to locate the file just written, not type it into `git commit`.
            if (config.bootstrapPasses()) {
                env.log().accept("Baseline created at " + baseline.toAbsolutePath());
                return GateVerdict.noBaseline(candidate.passRate());
            }
            throw new AssertionError(
                    "Baseline created at " + baseline.toAbsolutePath() + ". Review and commit it, then re-run.");
        }

        BaselineFile baselineFile = readBaseline(baseline);
        GateVerdict verdict = RegressionGate.evaluate(candidate, baselineFile, config);
        writeVerdictJson(env, baseline, verdict); // always, before any throw, so a CI action can post the comment
        // RegressionGate already folds config.failOnRegression() (and the coverage-loss guards) into
        // the status, so the throw is purely on FAIL — re-checking failOnRegression here would
        // double-gate and suppress coverage-loss failures that fire independently of it.
        if ("FAIL".equals(verdict.status())) {
            throw new AssertionError(failMessage(verdict, baseline));
        }
        return verdict;
    }

    /**
     * Returns the production environment: CI from {@code CI}/{@code GITHUB_ACTIONS}, the update flag
     * from {@link GateConfig#updateBaselineRequested()}, the verdict directory at {@code
     * target/dokimos}, and the log sink routed to this class's logger.
     *
     * @return the system-backed environment
     */
    public static Environment systemEnvironment() {
        boolean ci = truthy(System.getenv("CI")) || truthy(System.getenv("GITHUB_ACTIONS"));
        boolean update = GateConfig.updateBaselineRequested();
        return new Environment() {
            @Override
            public boolean ci() {
                return ci;
            }

            @Override
            public boolean updateRequested() {
                return update;
            }

            @Override
            public Path verdictDir() {
                return Path.of("target", "dokimos");
            }

            @Override
            public Consumer<String> log() {
                return LOGGER::info;
            }
        };
    }

    // --- internals ---

    private static void writeBaseline(Path baseline, ExperimentResult candidate, GateConfig config) {
        try {
            BaselineStore.write(baseline, candidate, config);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write baseline at " + baseline.toAbsolutePath(), e);
        }
    }

    private static BaselineFile readBaseline(Path baseline) {
        try {
            return BaselineStore.read(baseline);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read baseline at " + baseline.toAbsolutePath(), e);
        }
    }

    /**
     * Writes the verdict JSON best-effort, to a per-baseline file named for the baseline stem (so two
     * gates in one {@code verdictDir} do not clobber each other). The write is atomic — a temp file in
     * the same directory then an {@code ATOMIC_MOVE} (with a {@code REPLACE_EXISTING} fallback) — and
     * fully swallowed on failure (logged, never rethrown) so it can never mask the gate result (the
     * throw-on-FAIL or the pass-return proceeds regardless).
     */
    private static void writeVerdictJson(Environment env, Path baseline, GateVerdict verdict) {
        Path dir = env.verdictDir();
        Path target = dir.resolve(verdictFileName(baseline));
        try {
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, ".gate-verdict-", ".tmp");
            try {
                Files.writeString(tmp, verdict.toJson(), StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            env.log().accept("could not write verdict JSON at " + target + ": " + e.getMessage());
        }
    }

    /**
     * Derives the verdict filename from the baseline filename stem (trailing {@code .json} stripped),
     * sanitized to a single safe segment: any character outside {@code [A-Za-z0-9._-]} becomes
     * {@code -}. Falls back to {@code gate-verdict} when the stem is empty or {@code .}/{@code ..}.
     */
    private static String verdictFileName(Path baseline) {
        String name = baseline.getFileName() != null ? baseline.getFileName().toString() : "";
        String stem = name.regionMatches(true, name.length() - VERDICT_SUFFIX.length(), VERDICT_SUFFIX, 0,
                        VERDICT_SUFFIX.length())
                ? name.substring(0, name.length() - VERDICT_SUFFIX.length())
                : name;
        String slug = stem.replaceAll("[^A-Za-z0-9._-]", "-");
        if (slug.isEmpty() || ".".equals(slug) || "..".equals(slug)) {
            slug = VERDICT_FALLBACK;
        }
        return slug + VERDICT_SUFFIX;
    }

    /**
     * Builds the FAIL message: the pass-rate move, the top regressed evaluator(s) or case(s), and the
     * exact command plus path to accept the change. The path is rendered as passed in (relative for a
     * logical-name call) so the printed {@code commit <path>} is what the user actually commits.
     */
    private static String failMessage(GateVerdict verdict, Path baseline) {
        StringBuilder sb = new StringBuilder();
        if (verdict.baselinePassRate() != null && verdict.passRateDelta() != null) {
            sb.append("Eval gate FAILED: pass rate ")
                    .append(pct(verdict.baselinePassRate()))
                    .append(" -> ")
                    .append(pct(verdict.candidatePassRate()))
                    .append(" (")
                    .append(deltaPts(verdict.passRateDelta()))
                    .append(" pts).");
        } else {
            sb.append("Eval gate FAILED.");
        }

        List<GateVerdict.RegressedEvaluator> evaluators = verdict.regressedEvaluators();
        if (!evaluators.isEmpty()) {
            sb.append('\n').append(topEvaluators(evaluators));
        } else if (!verdict.cases().isEmpty()) {
            sb.append('\n').append(topCases(verdict));
        } else {
            // A coverage-loss FAIL (removed evaluator/item) has no regressed evaluator or case; its
            // only explanation is the warning string, which would otherwise live solely in the JSON.
            for (String w : verdict.warnings()) {
                sb.append('\n').append(w);
            }
        }

        sb.append("\nTo accept this change: re-run with DOKIMOS_UPDATE_BASELINE=true"
                + " (or -Ddokimos.updateBaseline) and commit ");
        sb.append(baseline);
        return sb.toString();
    }

    private static String topEvaluators(List<GateVerdict.RegressedEvaluator> evaluators) {
        StringBuilder sb = new StringBuilder("Regressed evaluators: ");
        int shown = Math.min(2, evaluators.size());
        for (int i = 0; i < shown; i++) {
            GateVerdict.RegressedEvaluator e = evaluators.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(e.evaluator())
                    .append(' ')
                    .append(mean(e.baselineMean()))
                    .append("->")
                    .append(mean(e.candidateMean()))
                    .append(" (p=")
                    .append(num(e.pValue()))
                    .append(')');
        }
        return sb.toString();
    }

    private static String topCases(GateVerdict verdict) {
        List<GateVerdict.RegressedCase> cases = verdict.cases();
        StringBuilder sb = new StringBuilder("Regressed cases: ");
        int shown = Math.min(3, cases.size());
        for (int i = 0; i < shown; i++) {
            GateVerdict.RegressedCase c = cases.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(c.index());
            List<GateVerdict.EvaluatorDrop> drops = c.evaluatorDrops();
            if (!drops.isEmpty()) {
                GateVerdict.EvaluatorDrop d = drops.get(0);
                sb.append(' ')
                        .append(d.evaluator())
                        .append(' ')
                        .append(mean(d.baselineMean()))
                        .append("->")
                        .append(mean(d.candidateMean()));
            }
        }
        int more = verdict.totalRegressedCases() - shown;
        if (more > 0) {
            sb.append(" (+").append(more).append(" more)");
        }
        return sb.toString();
    }

    private static String pct(double rate) {
        return Math.round(rate * 100.0) + "%";
    }

    private static String deltaPts(double delta) {
        long pts = Math.round(delta * 100.0);
        return (pts > 0 ? "+" : "") + pts;
    }

    private static String mean(Double value) {
        return value != null ? num(value) : "n/a";
    }

    private static String num(double value) {
        // Mirrors GateVerdict.num (private there); kept local rather than widening GateVerdict's API.
        if (value == 0.0) {
            return "0";
        }
        String s = String.format(java.util.Locale.ROOT, "%.6f", value);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static boolean truthy(String v) {
        // Mirrors GateConfig.truthy (private there); kept local rather than widening GateConfig's API.
        return v != null && (v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes"));
    }
}
