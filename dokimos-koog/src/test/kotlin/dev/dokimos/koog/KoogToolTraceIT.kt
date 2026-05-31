package dev.dokimos.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Live integration test that runs a real Koog agent against an OpenAI model with a single tool,
 * proving that Koog fires `onToolCallCompleted` and that [collectAgentTrace] records the call into a
 * [KoogTraceCollector]. This is the end-to-end counterpart to the mocked wiring test in
 * `KoogTraceCollectorTest`.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class KoogToolTraceIT {

    @Serializable
    data class WeatherArgs(val city: String)

    /**
     * A minimal weather tool. Koog serializes the agent's chosen arguments into [WeatherArgs] using
     * the kotlinx-serialization plugin-generated serializer, so the args type must be `@Serializable`.
     */
    private class WeatherTool :
        SimpleTool<WeatherArgs>(
            argsSerializer = serializer(),
            name = "getWeather",
            description = "Returns the current weather for a given city.",
        ) {
        override suspend fun execute(args: WeatherArgs): String = "The weather in ${args.city} is 22C and sunny."
    }

    @Test
    fun `a live agent run records the weather tool call into the collector`() {
        val apiKey = System.getenv("OPENAI_API_KEY")
        val collector = KoogTraceCollector()
        val weatherTool = WeatherTool()

        val agent = AIAgent(
            promptExecutor = simpleOpenAIExecutor(apiKey),
            systemPrompt = "You are a weather assistant. You MUST call the getWeather tool to answer " +
                "any question about the weather. Never guess the weather yourself.",
            llmModel = OpenAIModels.Chat.GPT4oMini,
            toolRegistry = ToolRegistry { tool(weatherTool) },
        ) {
            install(EventHandler) { collectAgentTrace(collector) }
        }

        runBlocking { agent.run("What is the weather in Paris right now?") }

        val toolCalls = collector.toAgentTrace().toolCalls()
        assertThat(toolCalls).isNotEmpty()
        assertThat(toolCalls).anySatisfy { call ->
            assertThat(call.name()).isEqualTo("getWeather")
            assertThat(call.result()).isNotNull()
        }
    }
}
