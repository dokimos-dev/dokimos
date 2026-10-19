package dev.dokimos.adk.kt.integration

import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.Gemini
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import dev.dokimos.adk.kt.AdkSupport
import dev.dokimos.adk.kt.AdkTraceCollector
import dev.dokimos.adk.kt.toAgentTrace
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.agents.ToolCall
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator
import dev.dokimos.core.evaluators.agents.ToolCorrectnessEvaluator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Live integration test that runs a real Gemini (`gemini-3.8-flash`) [LlmAgent] with a
 * [FunctionTool], captures its trace via [dev.dokimos.adk.kt.AdkTraceCollector] and
 * [AdkSupport.toAgentTrace], and scores tool calls with Dokimos agent evaluators.
 *
 * Requires `GEMINI_API_KEY` in the environment.
 */
@Tag("integration")
class AdkKotlinAgentIT {

    @Test
    @EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
    fun `captures tool calls and metrics from live Gemini 3_8 Flash agent`() {
        val declaration = FunctionDeclaration(
            name = "get_weather",
            description = "Get the current weather for a given city",
            parameters = Schema(
                type = Type.OBJECT,
                properties = mapOf(
                    "city" to Schema(type = Type.STRING, description = "The city name, e.g. Paris"),
                ),
                required = listOf("city"),
            ),
        )
        val weatherTool = object : FunctionTool(
            name = "get_weather",
            description = "Get the current weather for a given city",
        ) {
            override fun declaration(): FunctionDeclaration = declaration

            override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
                mapOf("city" to args["city"], "temperatureC" to 21, "conditions" to "Sunny")
        }

        val agent = LlmAgent(
            name = "weather_agent",
            model = Gemini(name = "gemini-3.8-flash"),
            instruction = Instruction(
                "You are a helpful weather assistant. Always call get_weather when asked about weather.",
            ),
            tools = listOf(weatherTool),
        )

        val collector = AdkTraceCollector()
        val runner = InMemoryRunner(
            agent = agent,
            appName = "dokimos-adk-kotlin-it",
            plugins = listOf(collector),
        )

        val query = "What is the weather in Paris right now?"
        val trace = runner.toAgentTrace(query)

        assertThat(trace.toolNames())
            .describedAs("Live Gemini 3.8 Flash agent should invoke get_weather")
            .contains("get_weather")
        assertThat(trace.finalResponse()).isNotBlank()

        val tools = AdkSupport.toToolDefinitions(collector)
        assertThat(tools).extracting<String> { it.name() }.contains("get_weather")
        assertThat(collector.callMetrics()).isNotNull()

        val testCase = trace.toTestCase(query, tools)
        val validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase)
        assertThat(validity.success()).isTrue()

        val correctness = ToolCorrectnessEvaluator.builder()
            .build()
            .evaluate(
                EvalTestCase.builder()
                    .actualOutputs(testCase.actualOutputs())
                    .metadata(testCase.metadata())
                    .expectedOutput("toolCalls", listOf(ToolCall.of("get_weather", emptyMap())))
                    .build(),
            )
        assertThat(correctness.score()).isEqualTo(1.0)
    }
}
