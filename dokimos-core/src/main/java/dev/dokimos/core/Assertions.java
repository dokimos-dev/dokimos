package dev.dokimos.core;

import dev.dokimos.core.gate.GateConfig;
import dev.dokimos.core.gate.RegressionGateRunner;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Assertion utilities for evaluation-based testing.
 *
 * <p>{@link #assertEval} checks a single test case against its evaluators and throws {@link
 * AssertionError} when a score falls below its threshold. {@link #assertNoRegression} compares an
 * experiment result against a committed baseline and throws on a regression on every runner (CI and
 * local alike), so a failing test is the gate without CI-specific wiring.
 */
public class Assertions {

    private Assertions() {}

    /**
     * Asserts that the test case passes all evaluators.
     *
     * @param testCase   the test case to evaluate
     * @param evaluators the evaluators to run
     * @throws AssertionError if any evaluator score falls below its threshold
     */
    public static void assertEval(EvalTestCase testCase, List<Evaluator> evaluators) {
        for (var evaluator : evaluators) {
            var result = evaluator.evaluate(testCase);
            if (!result.success()) {
                throw new AssertionError("Evaluation '%s' failed: score=%.2f (threshold=%.2f), reason=%s"
                        .formatted(result.name(), result.score(), evaluator.threshold(), result.reason()));
            }
        }
    }

    /**
     * Asserts that the test case passes all evaluators.
     *
     * @param testCase   the test case to evaluate
     * @param evaluators the evaluators to run
     * @throws AssertionError if any evaluator score falls below its threshold
     */
    public static void assertEval(EvalTestCase testCase, Evaluator... evaluators) {
        assertEval(testCase, List.of(evaluators));
    }

    /**
     * Asserts that the candidate has not regressed against its committed baseline, resolving the
     * baseline from the experiment name.
     *
     * <p>The baseline lives at {@code src/test/resources/dokimos/baselines/<name>.json} relative to
     * the module directory (the surefire working directory). With no baseline yet, the first local run
     * writes one and fails once so it is reviewed and committed (opt out with {@link
     * GateConfig.Builder#bootstrapPasses(boolean)}); a CI run reports NO_BASELINE without writing
     * (the checkout is ephemeral). Re-run with {@code DOKIMOS_UPDATE_BASELINE=true} (or {@code
     * -Ddokimos.updateBaseline}) to overwrite the baseline and pass.
     *
     * @param candidate the candidate experiment result
     * @throws IllegalArgumentException if the experiment name is blank or the default {@code "unnamed"}
     *     (two unnamed experiments would collide on one baseline); name the experiment or pass a Path
     * @throws AssertionError on a regression, or on a first local baseline write
     */
    public static void assertNoRegression(ExperimentResult candidate) {
        assertNoRegression(candidate, candidate.name());
    }

    /**
     * Asserts that the candidate has not regressed against the baseline named {@code baselineName}.
     *
     * @param candidate the candidate experiment result
     * @param baselineName the logical baseline name (resolved to {@code
     *     src/test/resources/dokimos/baselines/<name>.json})
     * @throws IllegalArgumentException if {@code baselineName} is blank or {@code "unnamed"}
     * @throws AssertionError on a regression, or on a first local baseline write
     */
    public static void assertNoRegression(ExperimentResult candidate, String baselineName) {
        assertNoRegression(candidate, baselinePathFor(baselineName), GateConfig.defaults());
    }

    /**
     * Asserts that the candidate has not regressed against the baseline named {@code baselineName},
     * using the supplied configuration.
     *
     * @param candidate the candidate experiment result
     * @param baselineName the logical baseline name (resolved to {@code
     *     src/test/resources/dokimos/baselines/<name>.json})
     * @param config the gate configuration
     * @throws IllegalArgumentException if {@code baselineName} is blank or {@code "unnamed"}
     * @throws AssertionError on a regression, or on a first local baseline write
     */
    public static void assertNoRegression(ExperimentResult candidate, String baselineName, GateConfig config) {
        assertNoRegression(candidate, baselinePathFor(baselineName), config);
    }

    /**
     * Asserts that the candidate has not regressed against the baseline at the given path.
     *
     * @param candidate the candidate experiment result
     * @param baseline the baseline file path
     * @throws AssertionError on a regression, or on a first local baseline write
     */
    public static void assertNoRegression(ExperimentResult candidate, Path baseline) {
        assertNoRegression(candidate, baseline, GateConfig.defaults());
    }

    /**
     * Asserts that the candidate has not regressed against the baseline at the given path, using the
     * supplied configuration.
     *
     * @param candidate the candidate experiment result
     * @param baseline the baseline file path
     * @param config the gate configuration
     * @throws AssertionError on a regression, or on a first local baseline write
     */
    public static void assertNoRegression(ExperimentResult candidate, Path baseline, GateConfig config) {
        RegressionGateRunner.run(candidate, baseline, config, RegressionGateRunner.systemEnvironment());
    }

    /**
     * Resolves a logical baseline name to its module-relative source path, guarding the names that
     * cannot map to a stable file. A logical name must be a single filename segment so it stays inside
     * {@code src/test/resources/dokimos/baselines/}; a name with a separator, {@code .}/{@code ..}, or
     * an absolute path is rejected. The explicit-Path overloads bypass this guard for nested or
     * absolute locations.
     */
    private static Path baselinePathFor(String name) {
        if (name == null || name.isBlank() || "unnamed".equals(name)) {
            throw new IllegalArgumentException("Cannot resolve a baseline path for a blank or default ('unnamed')"
                    + " experiment name; name the experiment (Experiment.builder().name(...)) or pass an explicit"
                    + " Path to assertNoRegression. Two unnamed experiments would collide on one baseline.");
        }
        if (".".equals(name)
                || "..".equals(name)
                || name.indexOf('/') >= 0
                || name.indexOf('\\') >= 0
                || name.indexOf(File.separatorChar) >= 0
                || new File(name).isAbsolute()) {
            throw new IllegalArgumentException("A baseline name must be a single filename segment (got '" + name
                    + "'); it cannot contain a path separator, be '.'/'..', or be absolute. Pass an explicit Path"
                    + " to assertNoRegression for a nested or absolute baseline location.");
        }
        return Path.of("src", "test", "resources", "dokimos", "baselines", name + ".json");
    }
}
