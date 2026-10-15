package dev.dokimos.kotlin.dsl.gate

import dev.dokimos.core.gate.GateConfig
import dev.dokimos.kotlin.dsl.DokimosDsl

/**
 * Builds a [GateConfig] for the regression gate.
 *
 * Every property starts at the value [GateConfig.defaults] carries, so the defaults stay owned by
 * core rather than being restated here.
 */
fun gateConfig(block: GateConfigDsl.() -> Unit = {}): GateConfig = GateConfigDsl().apply(block).build()

@DokimosDsl
class GateConfigDsl {
    private val defaults: GateConfig = GateConfig.defaults()

    /** Significance level for the permutation test. */
    var alpha: Double = defaults.alpha()

    /** Seed for the permutation and bootstrap resampling. */
    var seed: Long = defaults.seed()
    var permutationIterations: Int = defaults.permutationIterations()
    var bootstrapIterations: Int = defaults.bootstrapIterations()

    /** How large a drop must be, beyond significance, to count as a regression. */
    var severityMargin: Double = defaults.severityMargin()

    /** How baseline and candidate items are paired. */
    var pairing: GateConfig.Pairing = defaults.pairing()
    var failOnRegression: Boolean = defaults.failOnRegression()
    var failOnRemovedItems: Boolean = defaults.failOnRemovedItems()

    /** What to do when an evaluator in the baseline is missing from the candidate. */
    var onRemovedEvaluator: GateConfig.RemovedEvaluatorPolicy = defaults.onRemovedEvaluator()

    /** Whether the first run, which writes the baseline, passes instead of failing once for review. */
    var bootstrapPasses: Boolean = defaults.bootstrapPasses()
    var updateBaseline: Boolean = defaults.updateBaseline()

    fun build(): GateConfig = GateConfig.builder()
        .alpha(alpha)
        .seed(seed)
        .permutationIterations(permutationIterations)
        .bootstrapIterations(bootstrapIterations)
        .severityMargin(severityMargin)
        .pairing(pairing)
        .failOnRegression(failOnRegression)
        .failOnRemovedItems(failOnRemovedItems)
        .onRemovedEvaluator(onRemovedEvaluator)
        .bootstrapPasses(bootstrapPasses)
        .updateBaseline(updateBaseline)
        .build()
}
