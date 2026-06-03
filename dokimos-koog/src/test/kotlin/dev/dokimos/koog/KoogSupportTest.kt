package dev.dokimos.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.testing.feature.withTesting
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLModel
import dev.dokimos.core.Example
import dev.dokimos.core.TaskResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.ExecutionException

class KoogSupportTest {

    @Test
    fun `asJudge with agent runner delegates to agent`() {
        val mockAgent = mockAgent("Model response")
        val judge = asJudge(mockAgent::run)

        val response = judge.generate("evaluate me")
        assertThat(response).isEqualTo("Model response")
    }

    @Test
    fun `asJudge with lambda delegates prompt and returns text`() {
        var capturedPrompt: String? = null

        val judge = asJudge { prompt ->
            capturedPrompt = prompt
            "judge-output"
        }

        val result = judge.generate("evaluate this")

        assertThat(capturedPrompt).isEqualTo("evaluate this")
        assertThat(result).isEqualTo("judge-output")
    }

    @Test
    fun `asJudge with agent factory invokes the factory on each generate`() {
        // The factory overload's contract is a FRESH agent per invocation: capturing a single instance
        // instead would break statefulness in real multi-example runs. Assert the factory runs once per
        // generate() and both calls return the agent's response.
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val factory: () -> AIAgent<String, String> = {
            calls.incrementAndGet()
            mockAgent("agent response")
        }
        val judge = asJudge(factory)

        val first = judge.generate("a")
        val second = judge.generate("b")

        assertThat(calls.get()).isEqualTo(2)
        assertThat(first).isEqualTo("agent response")
        assertThat(second).isEqualTo("agent response")
    }

    @Test
    fun `asJudge rejects blank responses`() {
        val judge = asJudge { _ -> "" }

        assertThatThrownBy { judge.generate("prompt") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("blank")
    }

    @Test
    fun `asTask produces a future that completes with the TaskResult`() {
        val agentCall = mockk<suspend (Example) -> TaskResult>()
        coEvery { agentCall(any()) } returns TaskResult.of(mapOf("output" to "HELLO"))

        val asyncTask = asTask(agentCall = agentCall)
        val result = asyncTask.run(exampleWith("hello")).get()

        assertThat(result.outputs()).containsEntry("output", "HELLO")
        assertThat(result.metrics()).isNull()
        coVerify(exactly = 1) { agentCall(any()) }
    }

    @Test
    fun `asTask carries metrics through the TaskResult`() {
        val metrics = dev.dokimos.core.CallMetrics(3, 5, 0.01, 42L)
        val agentCall = mockk<suspend (Example) -> TaskResult>()
        coEvery { agentCall(any()) } returns TaskResult(mapOf("output" to "x"), metrics)

        val result = asTask(agentCall = agentCall).run(exampleWith("q")).get()

        assertThat(result.metrics()).isEqualTo(metrics)
    }

    @Test
    fun `asTask surfaces a failing suspend call as an exceptional future`() {
        val agentCall = mockk<suspend (Example) -> TaskResult>()
        coEvery { agentCall(any()) } throws IllegalStateException("boom")

        val future = asTask(agentCall = agentCall).run(exampleWith("q"))

        assertThatThrownBy { future.get() }
            .isInstanceOf(ExecutionException::class.java)
            .hasRootCauseInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("boom")
        assertThat(future.isCompletedExceptionally).isTrue()
    }

    @Test
    fun `asTextTask stores the agent response under the output key`() {
        val agentCall = mockk<suspend (String) -> String>()
        coEvery { agentCall(any()) } returns "Model response"

        val result = asTextTask(agentCall = agentCall).run(exampleWith("evaluate me")).get()

        assertThat(result.outputs()).containsEntry("output", "Model response")
        assertThat(result.metrics()).isNull()
        coVerify(exactly = 1) { agentCall("evaluate me") }
    }

    @Test
    fun `asTextTask rejects a blank response as a failed future`() {
        val agentCall = mockk<suspend (String) -> String>()
        coEvery { agentCall(any()) } returns ""

        val future = asTextTask(agentCall = agentCall).run(exampleWith("q"))

        assertThatThrownBy { future.get() }
            .isInstanceOf(ExecutionException::class.java)
            .hasRootCauseInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("blank")
        assertThat(future.isCompletedExceptionally).isTrue()
    }

    companion object {
        fun exampleWith(input: String): Example = Example.builder().input("input", input).build()

        fun mockAgent(modelResponse: String): AIAgent<String, String> = AIAgent(
            promptExecutor = getMockExecutor {
                mockLLMAnswer(modelResponse).asDefaultResponse
            },
            agentConfig = AIAgentConfig(
                prompt = prompt("test-agent") {},
                model = mockk<LLModel>(relaxed = true),
                maxAgentIterations = 10,
            ),
        ) {
            withTesting()
        }
    }
}
