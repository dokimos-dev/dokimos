package dev.dokimos.kotlin.core

import dev.dokimos.core.Assertions
import dev.dokimos.core.ExperimentResult
import dev.dokimos.core.gate.GateConfig
import dev.dokimos.kotlin.dsl.gate.GateConfigDsl
import dev.dokimos.kotlin.dsl.gate.gateConfig
import java.nio.file.Path

/**
 * Asserts this result has not regressed against the baseline named [baseline] (defaulting to the
 * experiment name), resolved to `src/test/resources/dokimos/baselines/<name>.json`.
 *
 * Throws [IllegalArgumentException] if [baseline] is blank or `"unnamed"`, and [AssertionError] on a
 * regression (or, when `bootstrapPasses` is false, on the first local baseline write). See
 * [Assertions.assertNoRegression] for the full lifecycle.
 */
fun ExperimentResult.assertNoRegression(baseline: String = name, config: GateConfig = GateConfig.defaults()) =
    Assertions.assertNoRegression(this, baseline, config)

/**
 * Asserts this result has not regressed against the baseline at [baseline]. See
 * [Assertions.assertNoRegression] for the full lifecycle.
 */
fun ExperimentResult.assertNoRegression(baseline: Path, config: GateConfig = GateConfig.defaults()) =
    Assertions.assertNoRegression(this, baseline, config)

/**
 * Asserts this result has not regressed against the baseline named [baseline], configuring the gate
 * inline with the [gateConfig] DSL.
 */
fun ExperimentResult.assertNoRegression(baseline: String = name, config: GateConfigDsl.() -> Unit) =
    Assertions.assertNoRegression(this, baseline, gateConfig(config))

/**
 * Asserts this result has not regressed against the baseline at [baseline], configuring the gate
 * inline with the [gateConfig] DSL.
 */
fun ExperimentResult.assertNoRegression(baseline: Path, config: GateConfigDsl.() -> Unit) =
    Assertions.assertNoRegression(this, baseline, gateConfig(config))
