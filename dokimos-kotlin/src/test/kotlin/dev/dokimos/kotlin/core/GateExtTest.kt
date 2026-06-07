package dev.dokimos.kotlin.core

import dev.dokimos.core.EvalResult
import dev.dokimos.core.Example
import dev.dokimos.core.ExperimentResult
import dev.dokimos.core.ItemResult
import dev.dokimos.core.RunResult
import dev.dokimos.core.gate.BaselineStore
import dev.dokimos.core.gate.GateConfig
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

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
}
