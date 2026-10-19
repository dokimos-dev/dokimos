package dev.dokimos.examples.adk

import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.Gemini
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
import dev.dokimos.adk.kt.AdkSupport
import dev.dokimos.adk.kt.AdkTraceCollector
import dev.dokimos.adk.kt.toToolDefinitions
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.PriceTable
import dev.dokimos.core.agents.ToolCall
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator
import dev.dokimos.core.evaluators.agents.ToolCorrectnessEvaluator
import dev.dokimos.core.evaluators.agents.ToolEfficiencyEvaluator
import dev.dokimos.kotlin.dsl.experiment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runnable Kotlin example demonstrating how to evaluate a Google ADK for Kotlin [LlmAgent]
 * with Dokimos and the `dokimos-kotlin` DSL:
 * - Passive trace and tool-schema collection via [AdkTraceCollector]
 * - Tool-call evaluation with [ToolCallValidityEvaluator], [ToolCorrectnessEvaluator], and
 *   [ToolEfficiencyEvaluator]
 * - Token, latency, and cost tracking via [AdkSupport.measuredAsyncAgentTask] and [PriceTable]
 *   inside the Kotlin `experiment { ... }` DSL
 *
 * Uses live `gemini-3.8-flash` when `GOOGLE_API_KEY` or `GEMINI_API_KEY` is set in the
 * environment, and falls back to an offline simulated [Model] otherwise so the example can run out
 * of the box.
 */
object AdkKotlinAgentEvaluationExample {

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val weatherTool = createWeatherTool()
        val policyTool = createPolicyTool()
        val agent = buildAgent(weatherTool, policyTool)

        // ---------------------------------------------------------------------
        // Part 1: Passive trace collection with AdkTraceCollector plugin
        // ---------------------------------------------------------------------
        val collector = AdkTraceCollector()
        val runner = InMemoryRunner(
            agent = agent,
            appName = "travel-assistant-kt",
            plugins = listOf(collector),
        )

        val userQuery = "What is the weather in Paris?"
        runner.runAsync(
            userId = "user-1",
            sessionId = "session-1",
            newMessage = Content.fromText(Role.USER, userQuery),
        ).collect()

        val trace = collector.trace()
        val tools = collector.toolDefinitions()

        val prices = PriceTable { _, tokensIn, tokensOut ->
            (tokensIn * 0.00015 / 1000.0) + (tokensOut * 0.00060 / 1000.0)
        }
        val metrics = collector.callMetrics(priceTable = prices)

        val testCase = EvalTestCase.builder()
            .input(userQuery)
            .actualOutputs(trace.toOutputMap())
            .expectedOutput("toolCalls", listOf(ToolCall.of("get_weather", mapOf("city" to "Paris"))))
            .metadata("tools", tools)
            .build()

        val validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase)
        val correctness = ToolCorrectnessEvaluator.builder().build().evaluate(testCase)
        val efficiency = ToolEfficiencyEvaluator.builder().build().evaluate(testCase)

        println("=== Part 1: Single-run ADK Kotlin Trace Collector ===")
        println("User query:          $userQuery")
        println("Observed tools:      ${trace.toolNames()}")
        println("Final response:      ${trace.finalResponse()}")
        println("Tool Call Validity:  ${"%.2f".format(validity.score())} (${validity.reason()})")
        println("Tool Correctness:    ${"%.2f".format(correctness.score())} (${correctness.reason()})")
        println("Tool Efficiency:     ${"%.2f".format(efficiency.score())} (${efficiency.reason()})")
        if (metrics != null) {
            println(
                "Call Metrics:        tokensIn=${metrics.tokensIn()}, tokensOut=${metrics.tokensOut()}, " +
                    "latencyMs=${metrics.latencyMs()}, costUsd=${metrics.costUsd()}",
            )
        }

        // ---------------------------------------------------------------------
        // Part 2: Kotlin DSL Experiment with measuredAsyncAgentTask
        // ---------------------------------------------------------------------
        val expResult = experiment {
            name = "ADK Kotlin Weather Agent Evaluation"
            dataset {
                name = "adk-kotlin-weather-eval"
                example {
                    input = "What is the weather in Paris?"
                    expected("toolCalls", listOf(ToolCall.of("get_weather", mapOf("city" to "Paris"))))
                    metadata("tools", agent.toToolDefinitions())
                }
            }
            asyncTask(AdkSupport.measuredAsyncAgentTask(agent, priceTable = prices))
            evaluators {
                toolCallValidity()
                toolCorrectness()
            }
        }.run()

        println("\n=== Part 2: Dokimos Kotlin DSL Experiment ===")
        println("Pass rate:           ${"%.0f".format(expResult.passRate() * 100)}%")
    }

    private fun buildAgent(weatherTool: FunctionTool, policyTool: FunctionTool): LlmAgent {
        val apiKey = System.getenv("GOOGLE_API_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }

        val model: Model = if (apiKey != null) {
            Gemini(name = "gemini-3.8-flash", apiKey = apiKey)
        } else {
            simulatedModel()
        }

        return LlmAgent(
            name = "travel_assistant_kt",
            model = model,
            instruction = Instruction(
                "You are a travel assistant. Use get_weather for weather questions " +
                    "and search_policy for policy questions.",
            ),
            tools = listOf(weatherTool, policyTool),
        )
    }

    private fun createWeatherTool(): FunctionTool {
        val declaration = FunctionDeclaration(
            name = "get_weather",
            description = "Get the current weather for a city",
            parameters = Schema(
                type = Type.OBJECT,
                properties = mapOf(
                    "city" to Schema(type = Type.STRING, description = "City name, e.g. Paris"),
                ),
                required = listOf("city"),
            ),
        )
        return object : FunctionTool(
            name = "get_weather",
            description = "Get the current weather for a city",
        ) {
            override fun declaration(): FunctionDeclaration = declaration

            override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
                mapOf("city" to args["city"], "temperatureC" to 21, "conditions" to "Sunny")
        }
    }

    private fun createPolicyTool(): FunctionTool {
        val declaration = FunctionDeclaration(
            name = "search_policy",
            description = "Search the company travel policy knowledge base",
            parameters = Schema(
                type = Type.OBJECT,
                properties = mapOf(
                    "query" to Schema(type = Type.STRING, description = "Search query"),
                ),
                required = listOf("query"),
            ),
        )
        return object : FunctionTool(
            name = "search_policy",
            description = "Search the company travel policy knowledge base",
        ) {
            override fun declaration(): FunctionDeclaration = declaration

            override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any = mapOf(
                "response" to listOf(
                    "Economy class is required for flights under 6 hours.",
                    "Hotel bookings are reimbursed up to $250 per night.",
                ),
            )
        }
    }

    private fun simulatedModel(): Model {
        val callCount = AtomicInteger(0)
        return object : Model {
            override val name: String = "gemini-3.8-flash"

            override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> {
                val step = callCount.incrementAndGet()
                if (step % 2 == 1) {
                    val call = FunctionCall(
                        id = "call-$step",
                        name = "get_weather",
                        args = mapOf("city" to "Paris"),
                    )
                    return flowOf(
                        LlmResponse(
                            content = Content(role = Role.MODEL, parts = listOf(Part(functionCall = call))),
                            usageMetadata = UsageMetadata(promptTokenCount = 40, candidatesTokenCount = 12),
                        ),
                    )
                }
                return flowOf(
                    LlmResponse(
                        content = Content.fromText(Role.MODEL, "In Paris it is currently 21C and Sunny."),
                        usageMetadata = UsageMetadata(promptTokenCount = 55, candidatesTokenCount = 18),
                    ),
                )
            }
        }
    }
}
