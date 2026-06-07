package dev.dokimos.kotlin.core

import dev.dokimos.core.Assertions
import dev.dokimos.core.ExperimentResult
import dev.dokimos.core.gate.GateConfig
import java.nio.file.Path

/**
 * Asserts this result has not regressed against the baseline named [baseline] (defaulting to the
 * experiment name), resolved to `src/test/resources/dokimos/baselines/<name>.json`.
 *
 * Throws [IllegalArgumentException] if [baseline] is blank or `"unnamed"`, and [AssertionError] on a
 * regression or a first local baseline write. See [Assertions.assertNoRegression] for the full
 * lifecycle.
 */
fun ExperimentResult.assertNoRegression(baseline: String = name, config: GateConfig = GateConfig.defaults()) =
    Assertions.assertNoRegression(this, baseline, config)

/**
 * Asserts this result has not regressed against the baseline at [baseline]. See
 * [Assertions.assertNoRegression] for the full lifecycle.
 */
fun ExperimentResult.assertNoRegression(baseline: Path, config: GateConfig = GateConfig.defaults()) =
    Assertions.assertNoRegression(this, baseline, config)
