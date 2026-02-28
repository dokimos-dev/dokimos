package dev.dokimos.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.testing.feature.withTesting
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLModel
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

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
    fun `asJudge rejects blank responses`() {
        val judge = asJudge { _ -> "" }

        assertThatThrownBy { judge.generate("prompt") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("blank")
    }

    companion object {
        fun mockAgent(modelResponse: String) = AIAgent(
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
