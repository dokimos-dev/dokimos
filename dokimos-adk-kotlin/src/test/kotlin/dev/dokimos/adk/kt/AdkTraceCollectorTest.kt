package dev.dokimos.adk.kt

import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.apps.App
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import com.google.adk.kt.types.UsageMetadata
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.PriceTable
import dev.dokimos.core.agents.ToolCall
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator
import dev.dokimos.core.evaluators.agents.ToolCorrectnessEvaluator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class AdkTraceCollectorTest {

    private fun toolCallingFakeModel(): Model {
        val step = AtomicInteger(0)
        return object : Model {
            override val name: String = "gemini-3.8-flash"

            override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> {
                val current = step.incrementAndGet()
                if (current == 1) {
                    val call = FunctionCall(
                        id = "call-weather-1",
                        name = "get_weather",
                        args = mapOf("city" to "Paris"),
                    )
                    return flowOf(
                        LlmResponse(
                            content = Content(role = Role.MODEL, parts = listOf(Part(functionCall = call))),
                            usageMetadata = UsageMetadata(promptTokenCount = 30, candidatesTokenCount = 10),
                        ),
                    )
                }
                return flowOf(
                    LlmResponse(
                        content = Content.fromText(Role.MODEL, "The weather in Paris is 22C and sunny."),
                        usageMetadata = UsageMetadata(promptTokenCount = 45, candidatesTokenCount = 15),
                    ),
                )
            }
        }
    }

    @Test
    fun `fresh collector returns empty trace and null metrics`() {
        val collector = AdkTraceCollector()

        assertThat(collector.name).isEqualTo(AdkTraceCollector.DEFAULT_NAME)
        assertThat(collector.events()).isEmpty()
        assertThat(collector.toolCalls()).isEmpty()
        assertThat(collector.observedToolNames()).isEmpty()
        assertThat(collector.toolDefinitions()).isEmpty()
        assertThat(collector.trace().toolCalls()).isEmpty()
        assertThat(collector.trace().finalResponse()).isEmpty()
        assertThat(collector.callMetrics()).isNull()
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `attach captures tool calls, full tool schemas, trajectory, and metrics from InMemoryRunner`() = runTest {
        val declaration = FunctionDeclaration(
            name = "get_weather",
            description = "Get the current weather for a city",
            parameters = Schema(
                type = Type.OBJECT,
                properties = mapOf(
                    "city" to Schema(type = Type.STRING, description = "The city name"),
                ),
                required = listOf("city"),
            ),
        )
        val weatherTool = object : FunctionTool(
            name = "get_weather",
            description = "Get the current weather for a city",
        ) {
            override fun declaration(): FunctionDeclaration = declaration

            override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
                mapOf("city" to args["city"], "tempC" to 22, "condition" to "Sunny")
        }

        val agent = LlmAgent(
            name = "weather_assistant",
            model = toolCallingFakeModel(),
            instruction = Instruction("Use get_weather to answer weather questions."),
            tools = listOf(weatherTool),
        )

        val collector = AdkTraceCollector()
        val runner = InMemoryRunner(
            agent = agent,
            appName = "weather-app",
            plugins = listOf(collector),
        )

        runner.runAsync(
            userId = "user-1",
            sessionId = "session-1",
            newMessage = Content.fromText(Role.USER, "What is the weather in Paris?"),
        ).collect()

        // 1. AgentTrace & ToolCalls
        val trace = collector.trace()
        assertThat(trace.finalResponse()).isEqualTo("The weather in Paris is 22C and sunny.")
        assertThat(trace.toolCalls()).hasSize(1)
        val call = trace.toolCalls()[0]
        assertThat(call.name()).isEqualTo("get_weather")
        assertThat(call.arguments()).containsEntry("city", "Paris")
        assertThat(call.resultAs(Map::class.java) as Map<String, Any>)
            .containsEntry("city", "Paris")
            .containsEntry("tempC", 22)

        // 2. Full ToolDefinitions captured from agent/tool callbacks
        val tools = AdkSupport.toToolDefinitions(collector)
        assertThat(tools).hasSize(1)
        val def = tools[0]
        assertThat(def.name()).isEqualTo("get_weather")
        assertThat(def.description()).isEqualTo("Get the current weather for a city")
        assertThat(def.parameterNames()).containsExactly("city")

        // 3. Deterministic agent evaluators pass against the captured trace and definitions
        val testCase = trace.toTestCase("What is the weather in Paris?", tools)
        val validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase)
        assertThat(validity.success()).isTrue()
        assertThat(validity.score()).isEqualTo(1.0)

        val correctness = ToolCorrectnessEvaluator.builder()
            .build()
            .evaluate(
                EvalTestCase.builder()
                    .actualOutputs(testCase.actualOutputs())
                    .metadata(testCase.metadata())
                    .expectedOutput("toolCalls", listOf(ToolCall.of("get_weather", mapOf("city" to "Paris"))))
                    .build(),
            )
        assertThat(correctness.success()).isTrue()

        // 4. Multi-turn ConversationTrajectory includes the user message and assistant turn
        val trajectory = collector.trajectory("Weather lookup")
        assertThat(trajectory.scenario()).isEqualTo("Weather lookup")
        assertThat(trajectory.turnCount()).isEqualTo(1)
        assertThat(trajectory.userMessages()[0].content()).isEqualTo("What is the weather in Paris?")
        assertThat(trajectory.assistantMessages()[0].content()).isEqualTo("The weather in Paris is 22C and sunny.")
        assertThat(trajectory.assistantMessages()[0].toolCalls()).hasSize(1)

        // 5. CallMetrics aggregates tokens across both model turns and computes cost (modelId inferred from agent)
        val prices = PriceTable { _, tokensIn, tokensOut -> (tokensIn * 0.001) + (tokensOut * 0.002) }
        val metrics = collector.callMetrics(priceTable = prices)
        assertThat(metrics).isNotNull()
        assertThat(metrics!!.tokensIn()).isEqualTo(75) // 30 + 45
        assertThat(metrics.tokensOut()).isEqualTo(25) // 10 + 15
        assertThat(metrics.costUsd()).isCloseTo(0.125, within(1e-6))
        assertThat(metrics.latencyMs()).isNotNull().isGreaterThanOrEqualTo(0L)

        // 6. reset() clears all state
        collector.reset()
        assertThat(collector.events()).isEmpty()
        assertThat(collector.toolCalls()).isEmpty()
        assertThat(collector.toolDefinitions()).isEmpty()
        assertThat(collector.callMetrics()).isNull()
    }

    @Test
    @Suppress("OPT_IN_USAGE")
    fun `attach works with InMemoryRunner Builder and App Builder`() = runTest {
        val agent = LlmAgent(
            name = "app_agent",
            model = toolCallingFakeModel(),
            instruction = Instruction("Be helpful."),
        )

        val runnerBuilder = InMemoryRunner.builder().agent(agent).appName("runner-app")
        val runnerCollector = AdkSupport.attach(runnerBuilder)
        runnerBuilder.build().runAsync(
            userId = "u1",
            sessionId = "s1",
            newMessage = Content.fromText(Role.USER, "Hello"),
        ).collect()
        assertThat(runnerCollector.events()).isNotEmpty()

        val appBuilder = App.builder().appName("my-app").rootAgent(agent)
        val collector = AdkSupport.attach(appBuilder)
        val runner = InMemoryRunner(app = appBuilder.build())

        runner.runAsync(
            userId = "u1",
            sessionId = "s1",
            newMessage = Content.fromText(Role.USER, "Hello"),
        ).collect()

        assertThat(collector.events()).isNotEmpty()
    }
}
