package dev.dokimos.kotlin.core

import dev.dokimos.core.EvalResult
import dev.dokimos.core.Example
import dev.dokimos.core.ExperimentResult
import dev.dokimos.core.ItemResult
import dev.dokimos.core.RunResult
import dev.dokimos.core.gate.BaselineStore
import dev.dokimos.core.gate.GateConfig
import dev.dokimos.kotlin.dsl.gate.gateConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

// Tests the env-independent paths only: the COMPARE path (every test pre-writes a baseline so the
// extension never enters the bootstrap branch, which reads the real CI env and diverges between
// runners) and the name-resolution guard, which throws before any filesystem or env access.
class GateExtTest {

    private fun resultWithScore(score: Double): ExperimentResult {
        val eval = EvalResult("correctness", score, 0.7, score >= 0.7, "", emptyMap())
        val item = ItemResult(Example.of("q", "a"), mapOf("output" to "a"), listOf(eval))
        return ExperimentResult("rag-eval", "", emptyMap(), listOf(RunResult(0, listOf(item))))
    }

    @Test
    fun `path overload passes when candidate matches the baseline`(@TempDir dir: Path) {
        val baseline = resultWithScore(1.0)
        val path = dir.resolve("baseline.json")
        BaselineStore.write(path, baseline, GateConfig.defaults())

        assertThatCode { baseline.assertNoRegression(path) }.doesNotThrowAnyException()
    }

    @Test
    fun `path overload throws on the compare path when a candidate score drops`(@TempDir dir: Path) {
        val path = dir.resolve("baseline.json")
        BaselineStore.write(path, resultWithScore(1.0), GateConfig.defaults())

        val regressed = resultWithScore(0.0)

        // Assert the compare-path FAIL message, not just any AssertionError: were
        // DOKIMOS_UPDATE_BASELINE set, the update branch would overwrite the baseline and pass without
        // comparing, and this (unlike a bare assertThrows) would catch that as a real failure.
        val error = assertThrows<AssertionError> { regressed.assertNoRegression(path) }
        assertThat(error).hasMessageContaining("Eval gate FAILED")
    }

    @Test
    fun `name overload default resolves to the experiment name and rejects unnamed`() {
        val unnamed = ExperimentResult("unnamed", "", emptyMap(), emptyList())

        assertThrows<IllegalArgumentException> { unnamed.assertNoRegression() }
    }

    @Test
    fun `gateConfig DSL starts from the core defaults and overrides only what is set`() {
        val defaults = GateConfig.defaults()

        val config = gateConfig {
            alpha = 0.01
            severityMargin = 0.25
            pairing = GateConfig.Pairing.DATASET_ITEM_ID
            onRemovedEvaluator = GateConfig.RemovedEvaluatorPolicy.WARN
            bootstrapPasses = false
        }

        assertThat(config.alpha()).isEqualTo(0.01)
        assertThat(config.severityMargin()).isEqualTo(0.25)
        assertThat(config.pairing()).isEqualTo(GateConfig.Pairing.DATASET_ITEM_ID)
        assertThat(config.onRemovedEvaluator()).isEqualTo(GateConfig.RemovedEvaluatorPolicy.WARN)
        assertThat(config.bootstrapPasses()).isFalse()

        // untouched knobs keep the core defaults
        assertThat(config.seed()).isEqualTo(defaults.seed())
        assertThat(config.permutationIterations()).isEqualTo(defaults.permutationIterations())
        assertThat(config.bootstrapIterations()).isEqualTo(defaults.bootstrapIterations())
        assertThat(config.failOnRegression()).isEqualTo(defaults.failOnRegression())
        assertThat(config.failOnRemovedItems()).isEqualTo(defaults.failOnRemovedItems())
        assertThat(config.updateBaseline()).isEqualTo(defaults.updateBaseline())
    }

    @Test
    fun `empty gateConfig equals the core defaults`() {
        val defaults = GateConfig.defaults()
        val config = gateConfig()

        assertThat(config.alpha()).isEqualTo(defaults.alpha())
        assertThat(config.seed()).isEqualTo(defaults.seed())
        assertThat(config.severityMargin()).isEqualTo(defaults.severityMargin())
        assertThat(config.pairing()).isEqualTo(defaults.pairing())
        assertThat(config.bootstrapPasses()).isEqualTo(defaults.bootstrapPasses())
    }

    @Test
    fun `path overload takes an inline gate config block`(@TempDir dir: Path) {
        val path = dir.resolve("baseline.json")
        BaselineStore.write(path, resultWithScore(1.0), GateConfig.defaults())

        val regressed = resultWithScore(0.0)

        // failOnRegression=false downgrades the compare-path failure to a pass
        assertThatCode {
            regressed.assertNoRegression(path) { failOnRegression = false }
        }.doesNotThrowAnyException()

        val error = assertThrows<AssertionError> {
            regressed.assertNoRegression(path) { failOnRegression = true }
        }
        assertThat(error).hasMessageContaining("Eval gate FAILED")
    }
}
