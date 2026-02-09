package dev.dokimos.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgent.Companion.State.Finished
import ai.koog.agents.core.agent.AIAgent.Companion.State.NotStarted
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.testing.client.CapturingLLMClient
import ai.koog.agents.testing.feature.withTesting
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import dev.dokimos.core.EvalResult
import dev.dokimos.core.Example
import dev.dokimos.kotlin.core.EvalTestCase
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test


class KoogSupportTest {

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
    fun `asJudge rejects blank responses`() {
        val judge = asJudge { _ -> "" }

        assertThatThrownBy { judge.generate("prompt") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("blank")
    }

    @Test
    fun `toTestCase maps input output and context`() {
        val testCase = EvalTestCase(
                input = "What is RAG?",
                actualOutput = "Retrieval-Augmented Generation",
                outputContext = listOf("Doc1", "Doc2"),
                metadata = mapOf("traceId" to "abc123")
        )

        assertThat(testCase.inputs()).containsEntry(INPUT_KEY, "What is RAG?")
        assertThat(testCase.actualOutputs()).containsEntry(OUTPUT_KEY, "Retrieval-Augmented Generation")
        assertThat(testCase.actualOutputs()).containsEntry(CONTEXT_KEY, listOf("Doc1", "Doc2"))
        assertThat(testCase.metadata()).containsEntry("traceId", "abc123")
    }

    @Test
    fun `toEvaluationResponse maps score reason and metadata`() {
        val result = EvalResult(
                "faithfulness",
                0.92,
                true,
                "Response grounded in documents",
                mapOf("latencyMs" to 120L)
        )

        assertThat(result.success).isTrue()
        assertThat(result.reason).isEqualTo("Response grounded in documents")
        assertThat(result.score).isEqualTo(0.92)
        assertThat(result.metadata).containsEntry("latencyMs", 120L)
    }


    @Test
    fun `asJudge with agent runner delegates to agent`() {
        val mockAgent = mockAgent("Model response")
        val judge = asJudge(mockAgent::run)

        val response = judge.generate("evaluate me")
        assertThat(response).isEqualTo("Model response")
    }


    companion object {
        fun mockAgent(modelResponse:String) = AIAgent(
            promptExecutor = getMockExecutor() {
                mockLLMAnswer(modelResponse).asDefaultResponse
            },
            agentConfig = AIAgentConfig(
                prompt = prompt("test-agent"){},
                model = mockk<LLModel>(relaxed = true),
                maxAgentIterations = 10
            )
        ) {
            withTesting()
        }
    }

}
