package dev.dokimos.koog

import dev.dokimos.core.Example
import dev.dokimos.core.PriceTable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Verifies [measuredTextTask] composes the [dev.dokimos.core.CallMetrics] that feed the run-detail
 * cards: latency is always captured, tokens come from the [KoogResponse], and cost via a [PriceTable].
 */
class KoogMeasuredTaskTest {

    private val prices = PriceTable { model, input, output ->
        if (model != "<test-model>" || input == null || output == null) {
            null
        } else {
            (input * 0.5 + output * 1.5) / 1_000_000.0
        }
    }

    @Test
    fun `measuredTextTask with tokens and price table populates all three card values`() {
        val task = measuredTextTask(model = "<test-model>", prices = prices) { input ->
            KoogResponse(text = "answer for $input", tokensIn = 10, tokensOut = 20)
        }

        val result = task.run(Example.of("q", "a")).get()
        val metrics = result.metrics()

        assertThat(result.outputs()).containsEntry("output", "answer for q")
        assertThat(metrics).isNotNull
        assertThat(metrics.tokensIn()).isEqualTo(10)
        assertThat(metrics.tokensOut()).isEqualTo(20)
        assertThat(metrics.costUsd()).isEqualTo((10 * 0.5 + 20 * 1.5) / 1_000_000.0)
        assertThat(metrics.latencyMs()).isNotNull()
    }

    @Test
    fun `measuredTextTask without price table lights tokens and latency not cost`() {
        val task = measuredTextTask(model = "<test-model>") { _ ->
            KoogResponse(text = "hi", tokensIn = 7, tokensOut = 3)
        }

        val metrics = task.run(Example.of("q", "a")).get().metrics()
        val cost: Double? = metrics.costUsd()
        val latency: Long? = metrics.latencyMs()

        assertThat(metrics.tokensIn()).isEqualTo(7)
        assertThat(metrics.tokensOut()).isEqualTo(3)
        assertThat(cost).isNull()
        assertThat(latency).isNotNull()
    }

    @Test
    fun `measuredTextTask without token counts still captures latency`() {
        val task = measuredTextTask(model = "<test-model>", prices = prices) { _ ->
            KoogResponse(text = "hi")
        }

        val metrics = task.run(Example.of("q", "a")).get().metrics()
        val tokensIn: Int? = metrics.tokensIn()
        val tokensOut: Int? = metrics.tokensOut()
        val cost: Double? = metrics.costUsd()
        val latency: Long? = metrics.latencyMs()

        assertThat(tokensIn).isNull()
        assertThat(tokensOut).isNull()
        assertThat(cost).isNull()
        assertThat(latency).isNotNull()
    }
}
