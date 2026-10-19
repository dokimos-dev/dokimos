package dev.dokimos.adk.kt

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.apps.App
import com.google.adk.kt.events.Event
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.runners.Runner
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.GroundingMetadata
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.UsageMetadata
import dev.dokimos.core.AsyncTask
import dev.dokimos.core.CallMetrics
import dev.dokimos.core.Example
import dev.dokimos.core.JudgeLM
import dev.dokimos.core.MeasuredTask
import dev.dokimos.core.PriceTable
import dev.dokimos.core.Task
import dev.dokimos.core.TaskResult
import dev.dokimos.core.agents.AgentTrace
import dev.dokimos.core.agents.ToolCall
import dev.dokimos.core.agents.ToolDefinition
import dev.dokimos.core.conversation.ConversationTrajectory
import dev.dokimos.core.conversation.ConversationalApplication
import dev.dokimos.core.conversation.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

/**
 * Utilities and adapters for integrating Dokimos with Google Agent Development Kit (ADK) for Kotlin
 * (`com.google.adk.kt`).
 *
 * Bridges ADK Kotlin types ([Model], [BaseAgent], [LlmAgent], [Runner], [Session], [Event],
 * [BaseTool], [FunctionDeclaration], [Schema], [UsageMetadata], and [GroundingMetadata]) into
 * Dokimos evaluation primitives ([JudgeLM], [Task], [AsyncTask], [MeasuredTask], [CallMetrics],
 * [AgentTrace], [ToolCall], [ToolDefinition], [ConversationTrajectory], and
 * [ConversationalApplication]).
 */
object AdkSupport {

    /** Default key used to read the user prompt from an [Example]'s inputs. */
    const val INPUT_KEY: String = "input"

    /** Default key used to store the final text output in an evaluation result map. */
    const val OUTPUT_KEY: String = "output"

    /** Default key used to store retrieved context strings for RAG evaluators. */
    const val CONTEXT_KEY: String = "context"

    /** Default key used to store extracted [ToolCall]s for agent evaluators. */
    const val TOOL_CALLS_KEY: String = "toolCalls"

    /** Default key used to store [ToolDefinition]s in `EvalTestCase.metadata`. */
    const val TOOLS_KEY: String = "tools"

    /** Default key used to store task breakdowns in `EvalTestCase.actualOutputs`. */
    const val TASKS_KEY: String = "tasks"

    private const val DEFAULT_USER_ID: String = "dokimos-eval-user"
    private val MAPPER = ObjectMapper()
    private val ERROR_KEYS = setOf("error", "errorMessage", "error_message", "exception")
    private val CONTEXT_MAP_KEYS = listOf(
        "response",
        "context",
        "contexts",
        "documents",
        "chunks",
        "results",
        "passages",
        "text",
        "content",
        "snippet",
        "output",
        "result",
    )

    // ==================== Judges ====================

    /**
     * Adapts a suspend prompt handler into a Dokimos [JudgeLM].
     *
     * @param judgeCall suspend function accepting a prompt and returning the judge's response text
     * @return a [JudgeLM] backed by the suspend function
     */
    @JvmStatic
    fun asJudge(judgeCall: suspend (String) -> String): JudgeLM = JudgeLM { prompt ->
        runBlocking { judgeCall(prompt) }
    }

    /**
     * Adapts an ADK Kotlin [Model] into a Dokimos [JudgeLM].
     *
     * @param model the ADK [Model] to use as a judge
     * @return a [JudgeLM] backed by the model
     */
    @JvmStatic
    fun asJudge(model: Model): JudgeLM = JudgeLM { prompt ->
        runBlocking { generateTextFromModel(model, prompt).text }
    }

    /**
     * Adapts an ADK Kotlin [Runner] into a Dokimos [JudgeLM].
     *
     * Each invocation creates an isolated session so judge calls are stateless.
     *
     * @param runner the ADK [Runner] to use as a judge
     * @return a [JudgeLM] backed by the runner
     */
    @JvmStatic
    fun asJudge(runner: Runner): JudgeLM = JudgeLM { prompt ->
        runBlocking {
            val events = runRunnerSuspend(runner, prompt)
            extractFinalResponseText(events)
        }
    }

    /**
     * Adapts an ADK Kotlin [BaseAgent] into a Dokimos [JudgeLM] using an [InMemoryRunner].
     *
     * @param agent the ADK [BaseAgent] to use as a judge
     * @return a [JudgeLM] backed by the agent
     */
    @JvmStatic
    fun asJudge(agent: BaseAgent): JudgeLM = asJudge(InMemoryRunner(agent = agent))

    // ==================== Synchronous Tasks ====================

    /**
     * Creates a synchronous [Task] from an ADK [Model].
     *
     * @param model the ADK [Model] under test
     * @param inputKey the example input key (defaults to [INPUT_KEY])
     * @param outputKey the output map key (defaults to [OUTPUT_KEY])
     * @return a [Task] returning `mapOf(outputKey to text)`
     */
    @JvmStatic
    @JvmOverloads
    fun simpleTask(model: Model, inputKey: String = INPUT_KEY, outputKey: String = OUTPUT_KEY): Task = Task { example ->
        val input = extractInput(example, inputKey)
        val result = runBlocking { generateTextFromModel(model, input) }
        mapOf(outputKey to result.text)
    }

    /**
     * Creates a synchronous [Task] from an ADK [Runner] that captures only the final text response.
     *
     * @param runner the ADK [Runner] under test
     * @param inputKey the example input key (defaults to [INPUT_KEY])
     * @param outputKey the output map key (defaults to [OUTPUT_KEY])
     * @return a [Task] returning `mapOf(outputKey to text)`
     */
    @JvmStatic
    @JvmOverloads
    fun simpleTask(runner: Runner, inputKey: String = INPUT_KEY, outputKey: String = OUTPUT_KEY): Task =
        Task { example ->
            val input = extractInput(example, inputKey)
            val events = runBlocking { runRunnerSuspend(runner, input) }
            mapOf(outputKey to extractFinalResponseText(events))
        }

    /**
     * Creates a synchronous [Task] from an ADK [BaseAgent] that captures only the final text
     * response.
     *
     * @param agent the ADK [BaseAgent] under test
     * @param inputKey the example input key (defaults to [INPUT_KEY])
     * @param outputKey the output map key (defaults to [OUTPUT_KEY])
     * @return a [Task] returning `mapOf(outputKey to text)`
     */
    @JvmStatic
    @JvmOverloads
    fun simpleTask(agent: BaseAgent, inputKey: String = INPUT_KEY, outputKey: String = OUTPUT_KEY): Task =
        simpleTask(InMemoryRunner(agent = agent), inputKey, outputKey)

    /**
     * Creates a synchronous [Task] from an ADK [Runner] that captures the full agent execution
     * trace (`output`, `toolCalls`, `steps`, and `metadata`).
     *
     * @param runner the ADK [Runner] under test
     * @param inputKey the example input key (defaults to [INPUT_KEY])
     * @param outputKey the final response output key (defaults to [OUTPUT_KEY])
     * @param toolCallsKey the tool calls output key (defaults to [TOOL_CALLS_KEY])
     * @return a [Task] producing the agent trace map
     */
    @JvmStatic
    @JvmOverloads
    fun agentTask(
        runner: Runner,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        toolCallsKey: String = TOOL_CALLS_KEY,
    ): Task = Task { example ->
        val input = extractInput(example, inputKey)
        val trace = toAgentTrace(runner, input)
        traceToOutputMap(trace, outputKey, toolCallsKey)
    }

    /**
     * Creates a synchronous [Task] from an ADK [BaseAgent] that captures the full agent execution
     * trace (`output`, `toolCalls`, `steps`, and `metadata`).
     *
     * @param agent the ADK [BaseAgent] under test
     * @param inputKey the example input key (defaults to [INPUT_KEY])
     * @param outputKey the final response output key (defaults to [OUTPUT_KEY])
     * @param toolCallsKey the tool calls output key (defaults to [TOOL_CALLS_KEY])
     * @return a [Task] producing the agent trace map
     */
    @JvmStatic
    @JvmOverloads
    fun agentTask(
        agent: BaseAgent,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        toolCallsKey: String = TOOL_CALLS_KEY,
    ): Task = agentTask(InMemoryRunner(agent = agent), inputKey, outputKey, toolCallsKey)

    // ==================== RAG Tasks ====================

    /**
     * Creates a synchronous RAG [Task] from an ADK [Runner] that captures the agent trace plus
     * retrieved context strings under [contextKey] for Dokimos RAG evaluators.
     *
     * @param runner the ADK [Runner] under test
     * @param inputKey the example input key (defaults to [INPUT_KEY])
     * @param outputKey the final response output key (defaults to [OUTPUT_KEY])
     * @param contextKey the retrieved contexts output key (defaults to [CONTEXT_KEY])
     * @param retrievalToolNames optional names of tools whose responses should be extracted as
     *   retrieved context
     * @return a [Task] producing `output`, `context`, `toolCalls`, `steps`, and `metadata`
     */
    @JvmStatic
    @JvmOverloads
    fun ragTask(
        runner: Runner,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        contextKey: String = CONTEXT_KEY,
        retrievalToolNames: List<String> = emptyList(),
    ): Task = Task { example ->
        val input = extractInput(example, inputKey)
        val events = runBlocking { runRunnerSuspend(runner, input) }
        eventsToRagOutputMap(events, outputKey, contextKey, retrievalToolNames)
    }

    /**
     * Creates a synchronous RAG [Task] from an ADK [BaseAgent] that captures the agent trace plus
     * retrieved context strings under [contextKey] for Dokimos RAG evaluators.
     *
     * @param agent the ADK [BaseAgent] under test
     * @param inputKey the example input key (defaults to [INPUT_KEY])
     * @param outputKey the final response output key (defaults to [OUTPUT_KEY])
     * @param contextKey the retrieved contexts output key (defaults to [CONTEXT_KEY])
     * @param retrievalToolNames optional names of tools whose responses should be extracted as
     *   retrieved context
     * @return a [Task] producing `output`, `context`, `toolCalls`, `steps`, and `metadata`
     */
    @JvmStatic
    @JvmOverloads
    fun ragTask(
        agent: BaseAgent,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        contextKey: String = CONTEXT_KEY,
        retrievalToolNames: List<String> = emptyList(),
    ): Task = ragTask(InMemoryRunner(agent = agent), inputKey, outputKey, contextKey, retrievalToolNames)

    // ==================== Asynchronous Tasks ====================

    /**
     * Creates an [AsyncTask] from an ADK [Model], bridging its `Flow<LlmResponse>` to a
     * [java.util.concurrent.CompletableFuture].
     *
     * @param model the ADK [Model] under test
     * @param inputKey the example input key (defaults to [INPUT_KEY])
     * @param outputKey the output map key (defaults to [OUTPUT_KEY])
     * @param scope the coroutine scope used to launch each invocation (defaults to [GlobalScope])
     * @return an [AsyncTask]
     */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    @JvmOverloads
    fun asyncTask(
        model: Model,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        scope: CoroutineScope = GlobalScope,
    ): AsyncTask = AsyncTask { example ->
        scope.future(Dispatchers.IO) {
            val input = extractInput(example, inputKey)
            val result = generateTextFromModel(model, input)
            TaskResult.of(mapOf(outputKey to result.text))
        }
    }

    /**
     * Creates an [AsyncTask] from an ADK [Model], running on the given [Executor].
     */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    fun asyncTask(
        model: Model,
        executor: Executor,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
    ): AsyncTask = AsyncTask { example ->
        GlobalScope.future(executor.asCoroutineDispatcher()) {
            val input = extractInput(example, inputKey)
            val result = generateTextFromModel(model, input)
            TaskResult.of(mapOf(outputKey to result.text))
        }
    }

    /**
     * Creates an [AsyncTask] from an ADK [Runner] that captures only the final text response.
     *
     * @param runner the ADK [Runner] under test
     * @param inputKey the example input key (defaults to [INPUT_KEY])
     * @param outputKey the output map key (defaults to [OUTPUT_KEY])
     * @param scope the coroutine scope used to launch each invocation (defaults to [GlobalScope])
     * @return an [AsyncTask]
     */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    @JvmOverloads
    fun asyncTask(
        runner: Runner,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        scope: CoroutineScope = GlobalScope,
    ): AsyncTask = AsyncTask { example ->
        scope.future(Dispatchers.IO) {
            val input = extractInput(example, inputKey)
            val events = runRunnerSuspend(runner, input)
            TaskResult.of(mapOf(outputKey to extractFinalResponseText(events)))
        }
    }

    /**
     * Creates an [AsyncTask] from an ADK [Runner] that captures only the final text response,
     * running on the given [Executor].
     */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    fun asyncTask(
        runner: Runner,
        executor: Executor,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
    ): AsyncTask = AsyncTask { example ->
        GlobalScope.future(executor.asCoroutineDispatcher()) {
            val input = extractInput(example, inputKey)
            val events = runRunnerSuspend(runner, input)
            TaskResult.of(mapOf(outputKey to extractFinalResponseText(events)))
        }
    }

    /**
     * Creates an [AsyncTask] from an ADK [BaseAgent] that captures only the final text response.
     */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    @JvmOverloads
    fun asyncTask(
        agent: BaseAgent,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        scope: CoroutineScope = GlobalScope,
    ): AsyncTask = asyncTask(InMemoryRunner(agent = agent), inputKey, outputKey, scope)

    /**
     * Creates an [AsyncTask] from an ADK [BaseAgent] that captures only the final text response,
     * running on the given [Executor].
     */
    @JvmStatic
    fun asyncTask(
        agent: BaseAgent,
        executor: Executor,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
    ): AsyncTask = asyncTask(InMemoryRunner(agent = agent), executor, inputKey, outputKey)

    /**
     * Creates an [AsyncTask] from an ADK [Runner] that captures the full agent execution trace
     * (`output`, `toolCalls`, `steps`, and `metadata`).
     */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    @JvmOverloads
    fun asyncAgentTask(
        runner: Runner,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        toolCallsKey: String = TOOL_CALLS_KEY,
        scope: CoroutineScope = GlobalScope,
    ): AsyncTask = AsyncTask { example ->
        scope.future(Dispatchers.IO) {
            val input = extractInput(example, inputKey)
            val events = runRunnerSuspend(runner, input)
            val trace = toAgentTrace(events)
            TaskResult.of(traceToOutputMap(trace, outputKey, toolCallsKey))
        }
    }

    /**
     * Creates an [AsyncTask] from an ADK [Runner] that captures the full agent execution trace,
     * running on the given [Executor].
     */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    fun asyncAgentTask(
        runner: Runner,
        executor: Executor,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        toolCallsKey: String = TOOL_CALLS_KEY,
    ): AsyncTask = AsyncTask { example ->
        GlobalScope.future(executor.asCoroutineDispatcher()) {
            val input = extractInput(example, inputKey)
            val events = runRunnerSuspend(runner, input)
            val trace = toAgentTrace(events)
            TaskResult.of(traceToOutputMap(trace, outputKey, toolCallsKey))
        }
    }

    /**
     * Creates an [AsyncTask] from an ADK [BaseAgent] that captures the full agent execution trace.
     */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    @JvmOverloads
    fun asyncAgentTask(
        agent: BaseAgent,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        toolCallsKey: String = TOOL_CALLS_KEY,
        scope: CoroutineScope = GlobalScope,
    ): AsyncTask = asyncAgentTask(InMemoryRunner(agent = agent), inputKey, outputKey, toolCallsKey, scope)

    /**
     * Creates an [AsyncTask] from an ADK [BaseAgent] that captures the full agent execution trace,
     * running on the given [Executor].
     */
    @JvmStatic
    fun asyncAgentTask(
        agent: BaseAgent,
        executor: Executor,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        toolCallsKey: String = TOOL_CALLS_KEY,
    ): AsyncTask = asyncAgentTask(InMemoryRunner(agent = agent), executor, inputKey, outputKey, toolCallsKey)

    /**
     * Creates an [AsyncTask] from an ADK [Runner] that captures the agent trace plus retrieved
     * context strings under [contextKey].
     */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    @JvmOverloads
    fun asyncRagTask(
        runner: Runner,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        contextKey: String = CONTEXT_KEY,
        retrievalToolNames: List<String> = emptyList(),
        scope: CoroutineScope = GlobalScope,
    ): AsyncTask = AsyncTask { example ->
        scope.future(Dispatchers.IO) {
            val input = extractInput(example, inputKey)
            val events = runRunnerSuspend(runner, input)
            TaskResult.of(eventsToRagOutputMap(events, outputKey, contextKey, retrievalToolNames))
        }
    }

    /**
     * Creates an [AsyncTask] from an ADK [BaseAgent] that captures the agent trace plus retrieved
     * context strings under [contextKey].
     */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    @JvmOverloads
    fun asyncRagTask(
        agent: BaseAgent,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        contextKey: String = CONTEXT_KEY,
        retrievalToolNames: List<String> = emptyList(),
        scope: CoroutineScope = GlobalScope,
    ): AsyncTask =
        asyncRagTask(InMemoryRunner(agent = agent), inputKey, outputKey, contextKey, retrievalToolNames, scope)

    // ==================== Measured Tasks & CallMetrics ====================

    /**
     * Converts an ADK [UsageMetadata] and wall-clock latency into a Dokimos [CallMetrics] record.
     *
     * All-zero token counts are treated as unreported (`null`) to match the other Dokimos adapters.
     */
    @JvmStatic
    @JvmOverloads
    fun toCallMetrics(
        usage: UsageMetadata?,
        latencyMs: Long,
        modelId: String? = null,
        priceTable: PriceTable? = null,
    ): CallMetrics {
        if (usage == null) {
            return CallMetrics(null, null, null, latencyMs)
        }
        val rawIn = usage.promptTokenCount
        val rawOut = usage.candidatesTokenCount
        if ((rawIn == null || rawIn == 0) && (rawOut == null || rawOut == 0)) {
            return CallMetrics(null, null, null, latencyMs)
        }
        val tokensIn = if (rawIn != null && rawIn > 0) rawIn else null
        val tokensOut = if (rawOut != null && rawOut > 0) rawOut else null
        val cost = if (priceTable != null && modelId != null && tokensIn != null && tokensOut != null) {
            priceTable.costUsd(modelId, tokensIn, tokensOut)
        } else {
            null
        }
        return CallMetrics(tokensIn, tokensOut, cost, latencyMs)
    }

    /**
     * Aggregates token usage across all [Event]s in an agent run and computes [CallMetrics].
     */
    @JvmStatic
    @JvmOverloads
    fun toCallMetrics(
        events: List<Event>,
        latencyMs: Long,
        modelId: String? = null,
        priceTable: PriceTable? = null,
    ): CallMetrics {
        var totalIn = 0
        var totalOut = 0
        var hasIn = false
        var hasOut = false

        for (event in events) {
            val usage = event.usageMetadata ?: continue
            val p = usage.promptTokenCount
            if (p != null && p > 0) {
                totalIn += p
                hasIn = true
            }
            val c = usage.candidatesTokenCount
            if (c != null && c > 0) {
                totalOut += c
                hasOut = true
            }
        }

        val tokensIn = if (hasIn) totalIn else null
        val tokensOut = if (hasOut) totalOut else null
        val cost = if (priceTable != null && modelId != null && tokensIn != null && tokensOut != null) {
            priceTable.costUsd(modelId, tokensIn, tokensOut)
        } else {
            null
        }
        return CallMetrics(tokensIn, tokensOut, cost, latencyMs)
    }

    /**
     * Creates a [MeasuredTask] from an ADK [Model], capturing token counts, latency, and optional
     * USD cost.
     */
    @JvmStatic
    @JvmOverloads
    fun measuredTask(
        model: Model,
        modelId: String? = model.name,
        priceTable: PriceTable? = null,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
    ): MeasuredTask = MeasuredTask { example ->
        val input = extractInput(example, inputKey)
        val start = System.nanoTime()
        val result = runBlocking { generateTextFromModel(model, input) }
        val latencyMs = maxOf(0L, (System.nanoTime() - start) / 1_000_000L)
        val effectiveModelId = modelId ?: model.name
        val metrics = toCallMetrics(result.usage, latencyMs, effectiveModelId, priceTable)
        TaskResult(mapOf(outputKey to result.text), metrics)
    }

    /**
     * Creates a [MeasuredTask] from an ADK [Runner], capturing the final text response along with
     * aggregated token counts, latency, and optional USD cost.
     */
    @JvmStatic
    @JvmOverloads
    fun measuredTask(
        runner: Runner,
        modelId: String? = resolveModelId(runner.agent),
        priceTable: PriceTable? = null,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
    ): MeasuredTask = MeasuredTask { example ->
        val input = extractInput(example, inputKey)
        val start = System.nanoTime()
        val events = runBlocking { runRunnerSuspend(runner, input) }
        val latencyMs = maxOf(0L, (System.nanoTime() - start) / 1_000_000L)
        val effectiveModelId = modelId ?: resolveModelId(runner.agent)
        val metrics = toCallMetrics(events, latencyMs, effectiveModelId, priceTable)
        TaskResult(mapOf(outputKey to extractFinalResponseText(events)), metrics)
    }

    /**
     * Creates a [MeasuredTask] from an ADK [BaseAgent], capturing the final text response along
     * with aggregated token counts, latency, and optional USD cost.
     */
    @JvmStatic
    @JvmOverloads
    fun measuredTask(
        agent: BaseAgent,
        modelId: String? = resolveModelId(agent),
        priceTable: PriceTable? = null,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
    ): MeasuredTask = measuredTask(InMemoryRunner(agent = agent), modelId, priceTable, inputKey, outputKey)

    /**
     * Creates a [MeasuredTask] from an ADK [Runner] that captures both the full agent trace
     * (`output`, `toolCalls`, `steps`, `metadata`) and [CallMetrics].
     */
    @JvmStatic
    @JvmOverloads
    fun measuredAgentTask(
        runner: Runner,
        modelId: String? = resolveModelId(runner.agent),
        priceTable: PriceTable? = null,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        toolCallsKey: String = TOOL_CALLS_KEY,
    ): MeasuredTask = MeasuredTask { example ->
        val input = extractInput(example, inputKey)
        val start = System.nanoTime()
        val events = runBlocking { runRunnerSuspend(runner, input) }
        val latencyMs = maxOf(0L, (System.nanoTime() - start) / 1_000_000L)
        val trace = toAgentTrace(events)
        val effectiveModelId = modelId ?: resolveModelId(runner.agent)
        val metrics = toCallMetrics(events, latencyMs, effectiveModelId, priceTable)
        TaskResult(traceToOutputMap(trace, outputKey, toolCallsKey), metrics)
    }

    /**
     * Creates a [MeasuredTask] from an ADK [BaseAgent] that captures both the full agent trace and
     * [CallMetrics].
     */
    @JvmStatic
    @JvmOverloads
    fun measuredAgentTask(
        agent: BaseAgent,
        modelId: String? = resolveModelId(agent),
        priceTable: PriceTable? = null,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        toolCallsKey: String = TOOL_CALLS_KEY,
    ): MeasuredTask =
        measuredAgentTask(InMemoryRunner(agent = agent), modelId, priceTable, inputKey, outputKey, toolCallsKey)

    /**
     * Creates a [MeasuredTask] from an ADK [Runner] that captures the agent trace, retrieved RAG
     * contexts under [contextKey], and [CallMetrics].
     */
    @JvmStatic
    @JvmOverloads
    fun measuredRagTask(
        runner: Runner,
        modelId: String? = resolveModelId(runner.agent),
        priceTable: PriceTable? = null,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        contextKey: String = CONTEXT_KEY,
        retrievalToolNames: List<String> = emptyList(),
    ): MeasuredTask = MeasuredTask { example ->
        val input = extractInput(example, inputKey)
        val start = System.nanoTime()
        val events = runBlocking { runRunnerSuspend(runner, input) }
        val latencyMs = maxOf(0L, (System.nanoTime() - start) / 1_000_000L)
        val effectiveModelId = modelId ?: resolveModelId(runner.agent)
        val metrics = toCallMetrics(events, latencyMs, effectiveModelId, priceTable)
        TaskResult(eventsToRagOutputMap(events, outputKey, contextKey, retrievalToolNames), metrics)
    }

    /**
     * Creates a [MeasuredTask] from an ADK [BaseAgent] that captures the agent trace, retrieved RAG
     * contexts under [contextKey], and [CallMetrics].
     */
    @JvmStatic
    @JvmOverloads
    fun measuredRagTask(
        agent: BaseAgent,
        modelId: String? = resolveModelId(agent),
        priceTable: PriceTable? = null,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        contextKey: String = CONTEXT_KEY,
        retrievalToolNames: List<String> = emptyList(),
    ): MeasuredTask = measuredRagTask(
        InMemoryRunner(agent = agent),
        modelId,
        priceTable,
        inputKey,
        outputKey,
        contextKey,
        retrievalToolNames,
    )

    /**
     * Creates a measured [AsyncTask] from an ADK [Model], capturing token counts, latency, and
     * optional USD cost asynchronously.
     */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    @JvmOverloads
    fun measuredAsyncTask(
        model: Model,
        modelId: String? = model.name,
        priceTable: PriceTable? = null,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        scope: CoroutineScope = GlobalScope,
    ): AsyncTask = AsyncTask { example ->
        scope.future(Dispatchers.IO) {
            val input = extractInput(example, inputKey)
            val start = System.nanoTime()
            val result = generateTextFromModel(model, input)
            val latencyMs = maxOf(0L, (System.nanoTime() - start) / 1_000_000L)
            val effectiveModelId = modelId ?: model.name
            val metrics = toCallMetrics(result.usage, latencyMs, effectiveModelId, priceTable)
            TaskResult(mapOf(outputKey to result.text), metrics)
        }
    }

    /**
     * Creates a measured [AsyncTask] from an ADK [Runner], capturing the final text response and
     * [CallMetrics] asynchronously.
     */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    @JvmOverloads
    fun measuredAsyncTask(
        runner: Runner,
        modelId: String? = resolveModelId(runner.agent),
        priceTable: PriceTable? = null,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        scope: CoroutineScope = GlobalScope,
    ): AsyncTask = AsyncTask { example ->
        scope.future(Dispatchers.IO) {
            val input = extractInput(example, inputKey)
            val start = System.nanoTime()
            val events = runRunnerSuspend(runner, input)
            val latencyMs = maxOf(0L, (System.nanoTime() - start) / 1_000_000L)
            val effectiveModelId = modelId ?: resolveModelId(runner.agent)
            val metrics = toCallMetrics(events, latencyMs, effectiveModelId, priceTable)
            TaskResult(mapOf(outputKey to extractFinalResponseText(events)), metrics)
        }
    }

    /**
     * Creates a measured [AsyncTask] from an ADK [BaseAgent], capturing the final text response and
     * [CallMetrics] asynchronously.
     */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    @JvmOverloads
    fun measuredAsyncTask(
        agent: BaseAgent,
        modelId: String? = resolveModelId(agent),
        priceTable: PriceTable? = null,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        scope: CoroutineScope = GlobalScope,
    ): AsyncTask = measuredAsyncTask(InMemoryRunner(agent = agent), modelId, priceTable, inputKey, outputKey, scope)

    /**
     * Creates a measured [AsyncTask] from an ADK [Runner], capturing both the full agent trace and
     * [CallMetrics] asynchronously.
     */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    @JvmOverloads
    fun measuredAsyncAgentTask(
        runner: Runner,
        modelId: String? = resolveModelId(runner.agent),
        priceTable: PriceTable? = null,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        toolCallsKey: String = TOOL_CALLS_KEY,
        scope: CoroutineScope = GlobalScope,
    ): AsyncTask = AsyncTask { example ->
        scope.future(Dispatchers.IO) {
            val input = extractInput(example, inputKey)
            val start = System.nanoTime()
            val events = runRunnerSuspend(runner, input)
            val latencyMs = maxOf(0L, (System.nanoTime() - start) / 1_000_000L)
            val trace = toAgentTrace(events)
            val effectiveModelId = modelId ?: resolveModelId(runner.agent)
            val metrics = toCallMetrics(events, latencyMs, effectiveModelId, priceTable)
            TaskResult(traceToOutputMap(trace, outputKey, toolCallsKey), metrics)
        }
    }

    /**
     * Creates a measured [AsyncTask] from an ADK [BaseAgent], capturing both the full agent trace
     * and [CallMetrics] asynchronously.
     */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    @JvmOverloads
    fun measuredAsyncAgentTask(
        agent: BaseAgent,
        modelId: String? = resolveModelId(agent),
        priceTable: PriceTable? = null,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        toolCallsKey: String = TOOL_CALLS_KEY,
        scope: CoroutineScope = GlobalScope,
    ): AsyncTask = measuredAsyncAgentTask(
        InMemoryRunner(agent = agent),
        modelId,
        priceTable,
        inputKey,
        outputKey,
        toolCallsKey,
        scope,
    )

    /**
     * Creates a measured [AsyncTask] from an ADK [Runner], capturing the agent trace, retrieved RAG
     * contexts under [contextKey], and [CallMetrics] asynchronously.
     */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    @JvmOverloads
    fun measuredAsyncRagTask(
        runner: Runner,
        modelId: String? = resolveModelId(runner.agent),
        priceTable: PriceTable? = null,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        contextKey: String = CONTEXT_KEY,
        retrievalToolNames: List<String> = emptyList(),
        scope: CoroutineScope = GlobalScope,
    ): AsyncTask = AsyncTask { example ->
        scope.future(Dispatchers.IO) {
            val input = extractInput(example, inputKey)
            val start = System.nanoTime()
            val events = runRunnerSuspend(runner, input)
            val latencyMs = maxOf(0L, (System.nanoTime() - start) / 1_000_000L)
            val effectiveModelId = modelId ?: resolveModelId(runner.agent)
            val metrics = toCallMetrics(events, latencyMs, effectiveModelId, priceTable)
            TaskResult(eventsToRagOutputMap(events, outputKey, contextKey, retrievalToolNames), metrics)
        }
    }

    /**
     * Creates a measured [AsyncTask] from an ADK [BaseAgent], capturing the agent trace, retrieved
     * RAG contexts under [contextKey], and [CallMetrics] asynchronously.
     */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    @JvmOverloads
    fun measuredAsyncRagTask(
        agent: BaseAgent,
        modelId: String? = resolveModelId(agent),
        priceTable: PriceTable? = null,
        inputKey: String = INPUT_KEY,
        outputKey: String = OUTPUT_KEY,
        contextKey: String = CONTEXT_KEY,
        retrievalToolNames: List<String> = emptyList(),
        scope: CoroutineScope = GlobalScope,
    ): AsyncTask = measuredAsyncRagTask(
        InMemoryRunner(agent = agent),
        modelId,
        priceTable,
        inputKey,
        outputKey,
        contextKey,
        retrievalToolNames,
        scope,
    )

    // ==================== AgentTrace & Tool Extraction ====================

    /**
     * Runs an ADK [BaseAgent] with the given [input] using an [InMemoryRunner] and captures the
     * resulting [AgentTrace].
     */
    @JvmStatic
    fun toAgentTrace(agent: BaseAgent, input: String): AgentTrace = toAgentTrace(InMemoryRunner(agent = agent), input)

    /**
     * Runs an ADK [Runner] with the given [input] in a fresh session and captures the resulting
     * [AgentTrace].
     */
    @JvmStatic
    fun toAgentTrace(runner: Runner, input: String): AgentTrace = runBlocking {
        val start = System.nanoTime()
        val events = runRunnerSuspend(runner, input)
        val latencyMs = maxOf(0L, (System.nanoTime() - start) / 1_000_000L)
        buildAgentTrace(events, latencyMs)
    }

    /**
     * Extracts a Dokimos [AgentTrace] from an ADK [Session]'s event history.
     */
    @JvmStatic
    fun toAgentTrace(session: Session): AgentTrace = toAgentTrace(session.events)

    /**
     * Extracts a Dokimos [AgentTrace] from a list of ADK [Event]s.
     */
    @JvmStatic
    fun toAgentTrace(events: List<Event>): AgentTrace = buildAgentTrace(events, null)

    /**
     * Extracts Dokimos [ToolCall]s from a list of ADK [Event]s, pairing each [FunctionCall] with
     * its corresponding [FunctionResponse] by call ID or function name.
     */
    @JvmStatic
    fun toToolCalls(events: List<Event>): List<ToolCall> {
        if (events.isEmpty()) return emptyList()

        val cleanEvents = events.filter { !it.partial }
        val result = ArrayList<ToolCall>()

        for (i in cleanEvents.indices) {
            val event = cleanEvents[i]
            val calls = event.functionCalls()
            if (calls.isEmpty()) continue

            val turnResponses = ArrayList<FunctionResponse>()
            turnResponses.addAll(event.functionResponses())
            for (j in i + 1 until cleanEvents.size) {
                val next = cleanEvents[j]
                if (next.functionCalls().isNotEmpty()) {
                    break
                }
                turnResponses.addAll(next.functionResponses())
            }

            val used = BooleanArray(turnResponses.size)
            for (fc in calls) {
                val fcId = fc.id
                var matched: FunctionResponse? = null
                if (!fcId.isNullOrBlank()) {
                    for (r in turnResponses.indices) {
                        if (!used[r] && fcId == turnResponses[r].id) {
                            matched = turnResponses[r]
                            used[r] = true
                            break
                        }
                    }
                }
                if (matched == null && fc.name.isNotBlank()) {
                    for (r in turnResponses.indices) {
                        if (!used[r] && fc.name == turnResponses[r].name) {
                            matched = turnResponses[r]
                            used[r] = true
                            break
                        }
                    }
                }
                val toolCall = toToolCall(fc, matched)
                val meta = LinkedHashMap<String, Any>(toolCall.metadata())
                if (event.author.isNotBlank()) {
                    meta["author"] = event.author
                }
                if (!event.branch.isNullOrBlank()) {
                    meta["branch"] = event.branch!!
                }
                result.add(
                    ToolCall.builder()
                        .name(toolCall.name())
                        .arguments(toolCall.arguments())
                        .result(toolCall.result())
                        .metadata(meta)
                        .build(),
                )
            }
        }
        return result.toList()
    }

    /**
     * Converts an ADK [FunctionCall] and optional [FunctionResponse] into a Dokimos [ToolCall].
     */
    @JvmStatic
    @JvmOverloads
    fun toToolCall(functionCall: FunctionCall, functionResponse: FunctionResponse? = null): ToolCall {
        val name = functionCall.name
        val args = nonNullMap(functionCall.args)
        val metadata = LinkedHashMap<String, Any>()
        if (!functionCall.id.isNullOrBlank()) {
            metadata["id"] = functionCall.id!!
        }

        var resultStr: String? = null
        var isError = false
        if (functionResponse != null) {
            val respMap = functionResponse.response
            isError = isErrorMap(respMap)
            resultStr = serializeResult(respMap)
            if (!metadata.containsKey("id") && !functionResponse.id.isNullOrBlank()) {
                metadata["id"] = functionResponse.id!!
            }
        }
        metadata["error"] = isError

        return ToolCall.builder()
            .name(name)
            .arguments(args)
            .result(resultStr)
            .metadata(metadata)
            .build()
    }

    /**
     * Converts an ADK [FunctionCall] and a serialized result string into a Dokimos [ToolCall].
     */
    @JvmStatic
    fun toToolCall(functionCall: FunctionCall, result: String?): ToolCall {
        val metadata = LinkedHashMap<String, Any>()
        if (!functionCall.id.isNullOrBlank()) {
            metadata["id"] = functionCall.id!!
        }
        return ToolCall.builder()
            .name(functionCall.name)
            .arguments(nonNullMap(functionCall.args))
            .result(result)
            .metadata(metadata)
            .build()
    }

    // ==================== Tool Definitions & Schema Conversion ====================

    /**
     * Extracts [ToolDefinition]s from an ADK [LlmAgent], including tools from its sub-agents.
     */
    @JvmStatic
    fun toToolDefinitions(agent: LlmAgent): List<ToolDefinition> {
        val tools = ArrayList<BaseTool>()
        collectAgentTools(agent, tools)
        return toToolDefinitions(tools)
    }

    /**
     * Converts a list of ADK [BaseTool]s into Dokimos [ToolDefinition]s.
     */
    @JvmStatic
    fun toToolDefinitions(tools: List<BaseTool>): List<ToolDefinition> = tools.map { toToolDefinition(it) }

    /**
     * Extracts [ToolDefinition]s from an [AdkTraceCollector] that observed an agent run.
     */
    @JvmStatic
    fun toToolDefinitions(collector: AdkTraceCollector): List<ToolDefinition> = collector.toolDefinitions()

    /**
     * Converts a single ADK [BaseTool] into a Dokimos [ToolDefinition].
     */
    @JvmStatic
    fun toToolDefinition(tool: BaseTool): ToolDefinition {
        val declaration = tool.declaration()
        if (declaration != null) {
            return toToolDefinition(declaration)
        }
        return ToolDefinition.of(
            tool.name,
            tool.description,
            mapOf("type" to "object", "properties" to emptyMap<String, Any>()),
        )
    }

    /**
     * Converts an ADK [FunctionDeclaration] into a Dokimos [ToolDefinition].
     */
    @JvmStatic
    fun toToolDefinition(declaration: FunctionDeclaration): ToolDefinition {
        val schemaMap = toSchemaMap(declaration.parameters)
        return ToolDefinition.of(declaration.name, declaration.description, schemaMap)
    }

    /**
     * Recursively converts an ADK [Schema] into a standard JSON Schema `Map<String, Any>` with
     * lowercase type names (`"object"`, `"string"`, `"integer"`, `"number"`, `"boolean"`,
     * `"array"`).
     */
    @JvmStatic
    fun toSchemaMap(schema: Schema?): Map<String, Any> {
        if (schema == null) {
            return mapOf("type" to "object")
        }
        val map = LinkedHashMap<String, Any>()
        val typeName = schema.type?.name
        if (!typeName.isNullOrBlank() && typeName != "TYPE_UNSPECIFIED") {
            map["type"] = typeName.lowercase()
        } else {
            map["type"] = "object"
        }

        if (!schema.description.isNullOrBlank()) {
            map["description"] = schema.description!!
        }
        if (!schema.title.isNullOrBlank()) {
            map["title"] = schema.title!!
        }
        if (!schema.format.isNullOrBlank()) {
            map["format"] = schema.format!!
        }
        if (!schema.pattern.isNullOrBlank()) {
            map["pattern"] = schema.pattern!!
        }
        schema.nullable?.let { map["nullable"] = it }
        schema.default?.let { map["default"] = it }
        schema.minimum?.let { map["minimum"] = it }
        schema.maximum?.let { map["maximum"] = it }
        schema.minItems?.let { map["minItems"] = it }
        schema.maxItems?.let { map["maxItems"] = it }
        schema.minLength?.let { map["minLength"] = it }
        schema.maxLength?.let { map["maxLength"] = it }
        schema.minProperties?.let { map["minProperties"] = it }
        schema.maxProperties?.let { map["maxProperties"] = it }

        if (!schema.enum.isNullOrEmpty()) {
            map["enum"] = schema.enum!!.toList()
        }
        if (!schema.required.isNullOrEmpty()) {
            map["required"] = schema.required!!.toList()
        }
        if (!schema.properties.isNullOrEmpty()) {
            val props = LinkedHashMap<String, Any>()
            for ((k, v) in schema.properties!!) {
                props[k] = toSchemaMap(v)
            }
            map["properties"] = props
        }
        schema.items?.let { map["items"] = toSchemaMap(it) }
        if (!schema.anyOf.isNullOrEmpty()) {
            map["anyOf"] = schema.anyOf!!.map { toSchemaMap(it) }
        }

        return map.toMap()
    }

    // ==================== Plugin Attachment & RAG Context Extraction ====================

    /**
     * Creates and registers an [AdkTraceCollector] plugin on the given [InMemoryRunner.Builder].
     */
    @JvmStatic
    @JvmOverloads
    fun attach(
        builder: InMemoryRunner.Builder,
        collector: AdkTraceCollector = AdkTraceCollector(),
    ): AdkTraceCollector {
        builder.plugins(listOf(collector))
        return collector
    }

    /**
     * Creates and registers an [AdkTraceCollector] plugin on the given [App.Builder].
     */
    @JvmStatic
    @JvmOverloads
    fun attach(builder: App.Builder, collector: AdkTraceCollector = AdkTraceCollector()): AdkTraceCollector {
        builder.plugins(listOf(collector))
        return collector
    }

    /**
     * Extracts retrieved context strings from a list of ADK [Event]s for use with Dokimos RAG
     * evaluators (`FaithfulnessEvaluator`, `ContextualRelevanceEvaluator`, etc.).
     *
     * Collects text from:
     * 1. [Event.groundingMetadata] chunks (`retrievedContext.text`, `web.title`, `web.uri`)
     * 2. [FunctionResponse] payloads of retrieval tools (when [retrievalToolNames] are specified,
     *    only matching tools are inspected; when empty, any tool response carrying standard context
     *    keys such as `"context"`, `"contexts"`, `"documents"`, `"chunks"`, `"results"`, or
     *    `"passages"` is extracted)
     */
    @JvmStatic
    fun extractTexts(events: List<Event>, vararg retrievalToolNames: String): List<String> {
        if (events.isEmpty()) return emptyList()
        val filterNames = retrievalToolNames.filter { it.isNotBlank() }.toSet()
        val contexts = ArrayList<String>()
        for (event in events) {
            if (event.partial) continue
            contexts.addAll(extractTexts(event.groundingMetadata))
            for (fr in event.functionResponses()) {
                val explicitTool = filterNames.isNotEmpty() && fr.name in filterNames
                val implicitRagTool = filterNames.isEmpty() && isImplicitRagResponse(fr.name, fr.response)
                if (explicitTool || implicitRagTool) {
                    extractStringsFromPayload(fr.response, explicitTool, contexts)
                }
            }
        }
        return contexts.toList()
    }

    /**
     * Extracts retrieved context strings from an ADK [GroundingMetadata] instance.
     */
    @JvmStatic
    fun extractTexts(groundingMetadata: GroundingMetadata?): List<String> {
        val chunks = groundingMetadata?.groundingChunks ?: return emptyList()
        val texts = ArrayList<String>()
        for (chunk in chunks) {
            val rc = chunk.retrievedContext
            if (rc != null) {
                if (!rc.text.isNullOrBlank()) {
                    texts.add(rc.text!!)
                    continue
                }
                if (!rc.title.isNullOrBlank()) {
                    texts.add(rc.title!!)
                    continue
                }
            }
            val web = chunk.web
            if (web != null) {
                val title = web.title.orEmpty()
                val uri = web.uri.orEmpty()
                if (title.isNotBlank() && uri.isNotBlank()) {
                    texts.add("$title ($uri)")
                } else if (title.isNotBlank()) {
                    texts.add(title)
                } else if (uri.isNotBlank()) {
                    texts.add(uri)
                }
            }
        }
        return texts.toList()
    }

    // ==================== Multi-Turn Conversations ====================

    /**
     * Converts an ADK [Session] into a Dokimos [ConversationTrajectory] for multi-turn evaluation.
     */
    @JvmStatic
    @JvmOverloads
    fun toConversationTrajectory(session: Session, scenario: String = ""): ConversationTrajectory =
        toConversationTrajectory(session.events, scenario)

    /**
     * Converts a list of ADK [Event]s into a Dokimos [ConversationTrajectory], folding user turns
     * and assistant turns (with their executed [ToolCall]s).
     */
    @JvmStatic
    @JvmOverloads
    fun toConversationTrajectory(events: List<Event>, scenario: String = ""): ConversationTrajectory {
        val builder = ConversationTrajectory.builder().scenario(scenario)
        if (events.isEmpty()) {
            return builder.build()
        }
        val cleanEvents = events.filter { !it.partial }
        val currentAssistantTurnEvents = ArrayList<Event>()

        for (event in cleanEvents) {
            if (isUserTextEvent(event)) {
                if (currentAssistantTurnEvents.isNotEmpty()) {
                    appendAssistantTurnMessage(builder, currentAssistantTurnEvents)
                    currentAssistantTurnEvents.clear()
                }
                builder.userMessage(extractEventText(event))
            } else {
                currentAssistantTurnEvents.add(event)
            }
        }
        if (currentAssistantTurnEvents.isNotEmpty()) {
            appendAssistantTurnMessage(builder, currentAssistantTurnEvents)
        }
        return builder.build()
    }

    /**
     * Adapts an ADK [BaseAgent] as a stateful Dokimos [ConversationalApplication] suitable for use
     * with `ConversationSimulator`.
     */
    @JvmStatic
    fun asConversationalApp(agent: BaseAgent): ConversationalApplication =
        asConversationalApp(InMemoryRunner(agent = agent))

    /**
     * Adapts an ADK [Runner] as a stateful Dokimos [ConversationalApplication] suitable for use
     * with `ConversationSimulator`.
     */
    @JvmStatic
    @JvmOverloads
    fun asConversationalApp(
        runner: Runner,
        userId: String = DEFAULT_USER_ID,
        sessionId: String = UUID.randomUUID().toString(),
    ): ConversationalApplication {
        val processedUserMessages = AtomicInteger(0)
        return ConversationalApplication { trajectory ->
            val userMessages = trajectory?.userMessages().orEmpty()
            if (userMessages.isEmpty()) {
                return@ConversationalApplication Message.assistant("")
            }
            var startIndex = processedUserMessages.get()
            if (startIndex >= userMessages.size) {
                startIndex = userMessages.size - 1
            }
            var lastTurnEvents: List<Event> = emptyList()
            for (i in startIndex until userMessages.size) {
                lastTurnEvents = runBlocking {
                    runRunnerInSessionSuspend(runner, userId, sessionId, userMessages[i].content())
                }
                processedUserMessages.set(i + 1)
            }
            val trace = toAgentTrace(lastTurnEvents)
            Message.assistant(trace.finalResponse(), trace.toolCalls())
        }
    }

    // ==================== Internal Helpers ====================

    internal suspend fun runRunnerSuspend(runner: Runner, input: String): List<Event> {
        val sessionId = UUID.randomUUID().toString()
        val userContent = Content.fromText(Role.USER, input)
        val userEvent = Event(author = Role.USER, content = userContent)
        val emitted = runner.runAsync(
            userId = DEFAULT_USER_ID,
            sessionId = sessionId,
            newMessage = userContent,
        ).toList()
        return prependUserEventIfMissing(userEvent, emitted)
    }

    private suspend fun runRunnerInSessionSuspend(
        runner: Runner,
        userId: String,
        sessionId: String,
        input: String,
    ): List<Event> {
        val userContent = Content.fromText(Role.USER, input)
        return runner.runAsync(
            userId = userId,
            sessionId = sessionId,
            newMessage = userContent,
        ).toList()
    }

    private fun prependUserEventIfMissing(userEvent: Event, emitted: List<Event>): List<Event> {
        val hasUserEvent = emitted.any {
            Role.USER.equals(it.author, ignoreCase = true) ||
                Role.USER.equals(it.content?.role, ignoreCase = true)
        }
        if (hasUserEvent) return emitted
        val all = ArrayList<Event>(emitted.size + 1)
        all.add(userEvent)
        all.addAll(emitted)
        return all
    }

    private data class ModelCallResult(val text: String, val usage: UsageMetadata?)

    private suspend fun generateTextFromModel(model: Model, prompt: String): ModelCallResult {
        val request = LlmRequest(
            model = model,
            contents = listOf(Content.fromText(Role.USER, prompt)),
        )
        val responses: List<LlmResponse> = model.generateContent(request, stream = false).toList()
        val sb = StringBuilder()
        var lastUsage: UsageMetadata? = null
        for (response in responses) {
            response.usageMetadata?.let { lastUsage = it }
            if (response.partial) continue
            val parts = response.content?.parts ?: continue
            for (part in parts) {
                if (part.thought == true) continue
                part.text?.let { sb.append(it) }
            }
        }
        return ModelCallResult(sb.toString().trim(), lastUsage)
    }

    private fun buildAgentTrace(events: List<Event>, latencyMs: Long?): AgentTrace {
        if (events.isEmpty()) {
            return AgentTrace.builder().finalResponse("").build()
        }
        val cleanEvents = events.filter { !it.partial }
        val toolCalls = toToolCalls(cleanEvents)
        val builder = AgentTrace.builder().toolCalls(toolCalls)
        var modelId: String? = null
        var totalIn = 0
        var totalOut = 0
        var hasIn = false
        var hasOut = false

        for (event in cleanEvents) {
            if (modelId == null && !event.modelVersion.isNullOrBlank()) {
                modelId = event.modelVersion
            }
            event.usageMetadata?.let { u ->
                val p = u.promptTokenCount
                if (p != null && p > 0) {
                    totalIn += p
                    hasIn = true
                }
                val c = u.candidatesTokenCount
                if (c != null && c > 0) {
                    totalOut += c
                    hasOut = true
                }
            }

            val parts = event.content?.parts.orEmpty()
            for (part in parts) {
                if (part.thought == true && !part.text.isNullOrBlank()) {
                    builder.addReasoningStep(part.text!!)
                }
            }
        }

        val metadata = LinkedHashMap<String, Any>()
        latencyMs?.let { metadata["latencyMs"] = it }
        modelId?.let { metadata["modelVersion"] = it }
        if (hasIn) metadata["promptTokenCount"] = totalIn
        if (hasOut) metadata["candidatesTokenCount"] = totalOut

        return builder
            .finalResponse(extractFinalResponseText(cleanEvents))
            .metadata(metadata)
            .build()
    }

    private fun isUserTextEvent(event: Event): Boolean {
        if (event.functionCalls().isNotEmpty() || event.functionResponses().isNotEmpty()) {
            return false
        }
        if (event.author.isNotBlank()) {
            return Role.USER.equals(event.author, ignoreCase = true)
        }
        return Role.USER.equals(event.content?.role, ignoreCase = true)
    }

    private fun appendAssistantTurnMessage(builder: ConversationTrajectory.Builder, turnEvents: List<Event>) {
        val turnToolCalls = toToolCalls(turnEvents)
        val turnText = extractFinalResponseText(turnEvents)
        if (turnText.isNotEmpty() || turnToolCalls.isNotEmpty()) {
            builder.assistantMessage(turnText, turnToolCalls)
        }
    }

    private fun extractFinalResponseText(events: List<Event>): String {
        for (i in events.indices.reversed()) {
            val event = events[i]
            if (event.partial) continue
            if (isUserTextEvent(event) || event.functionResponses().isNotEmpty()) {
                continue
            }
            if (event.isFinalResponse) {
                val text = extractEventText(event)
                if (text.isNotBlank()) {
                    return text
                }
            }
        }
        for (i in events.indices.reversed()) {
            val event = events[i]
            if (event.partial) continue
            if (isUserTextEvent(event) || event.functionResponses().isNotEmpty()) {
                continue
            }
            val text = extractEventText(event)
            if (text.isNotBlank()) {
                return text
            }
        }
        return ""
    }

    private fun extractEventText(event: Event): String {
        val parts: List<Part> = event.content?.parts ?: return ""
        val sb = StringBuilder()
        for (part in parts) {
            if (part.thought == true) continue
            val text = part.text
            if (!text.isNullOrBlank()) {
                if (sb.isNotEmpty()) sb.append("\n")
                sb.append(text)
            }
        }
        return sb.toString().trim()
    }

    private fun extractInput(example: Example, inputKey: String): String {
        val raw = example.inputs()[inputKey] ?: example.input()
        require(raw != null) { "Input key '$inputKey' not found in Example inputs: ${example.inputs().keys}" }
        return raw.toString()
    }

    private fun traceToOutputMap(trace: AgentTrace, outputKey: String, toolCallsKey: String): Map<String, Any> {
        val map = LinkedHashMap<String, Any>(trace.toOutputMap())
        if (outputKey != OUTPUT_KEY) {
            val out = map.remove(OUTPUT_KEY)
            if (out != null) map[outputKey] = out
        }
        if (toolCallsKey != TOOL_CALLS_KEY) {
            val tc = map.remove(TOOL_CALLS_KEY)
            if (tc != null) map[toolCallsKey] = tc
        }
        return map.toMap()
    }

    private fun eventsToRagOutputMap(
        events: List<Event>,
        outputKey: String,
        contextKey: String,
        retrievalToolNames: List<String>,
    ): Map<String, Any> {
        val trace = toAgentTrace(events)
        val map = LinkedHashMap<String, Any>(traceToOutputMap(trace, outputKey, TOOL_CALLS_KEY))
        val contexts = extractTexts(events, *retrievalToolNames.toTypedArray())
        map[contextKey] = contexts
        return map.toMap()
    }

    private fun isImplicitRagResponse(toolName: String, response: Map<String, Any?>): Boolean {
        val lowerName = toolName.lowercase()
        if (lowerName.contains("rag") ||
            lowerName.contains("retriev") ||
            lowerName.contains("search") ||
            lowerName.contains("knowledge") ||
            lowerName.contains("doc")
        ) {
            return true
        }
        for (key in listOf("context", "contexts", "documents", "chunks", "passages")) {
            if (response.containsKey(key)) return true
        }
        return false
    }

    private fun extractStringsFromPayload(payload: Any?, includeGenericStrings: Boolean, sink: MutableList<String>) {
        if (payload == null) return
        when (payload) {
            is String -> if (payload.isNotBlank()) sink.add(payload)
            is Collection<*> -> payload.forEach { extractStringsFromPayload(it, true, sink) }
            is Map<*, *> -> {
                var matchedKey = false
                for (key in CONTEXT_MAP_KEYS) {
                    if (payload.containsKey(key)) {
                        matchedKey = true
                        extractStringsFromPayload(payload[key], true, sink)
                    }
                }
                if (!matchedKey && includeGenericStrings && payload.isNotEmpty()) {
                    sink.add(serializeResult(payload))
                }
            }
        }
    }

    private fun collectAgentTools(agent: BaseAgent, sink: MutableList<BaseTool>) {
        if (agent is LlmAgent) {
            sink.addAll(agent.tools)
        }
        for (sub in agent.subAgents) {
            collectAgentTools(sub, sink)
        }
    }

    internal fun resolveModelId(agent: BaseAgent): String? {
        if (agent is LlmAgent && agent.model.name.isNotBlank()) {
            return agent.model.name
        }
        for (sub in agent.subAgents) {
            resolveModelId(sub)?.let { return it }
        }
        return null
    }

    internal fun nonNullMap(map: Map<String, Any?>?): Map<String, Any> {
        if (map.isNullOrEmpty()) return emptyMap()
        val copy = LinkedHashMap<String, Any>()
        for ((k, v) in map) {
            if (v != null) {
                copy[k] = v
            }
        }
        return copy.toMap()
    }

    internal fun isErrorMap(map: Map<String, Any?>?): Boolean {
        if (map.isNullOrEmpty()) return false
        for (key in ERROR_KEYS) {
            val v = map[key]
            if (v != null && v != false && v.toString().isNotBlank()) {
                return true
            }
        }
        val status = map["status"]
        return status is String &&
            (status.equals("error", ignoreCase = true) || status.equals("failed", ignoreCase = true))
    }

    internal fun serializeResult(value: Any?): String {
        if (value == null) return ""
        if (value is String) return value
        return try {
            MAPPER.writeValueAsString(value)
        } catch (_: JsonProcessingException) {
            value.toString()
        }
    }
}

// ==================== Idiomatic Kotlin Extension Functions ====================

/** Adapts this ADK [Model] into a Dokimos [JudgeLM]. */
fun Model.asJudge(): JudgeLM = AdkSupport.asJudge(this)

/** Adapts this ADK [Runner] into a Dokimos [JudgeLM]. */
fun Runner.asJudge(): JudgeLM = AdkSupport.asJudge(this)

/** Adapts this ADK [BaseAgent] into a Dokimos [JudgeLM]. */
fun BaseAgent.asJudge(): JudgeLM = AdkSupport.asJudge(this)

/** Runs this ADK [BaseAgent] on [input] and returns the captured [AgentTrace]. */
fun BaseAgent.toAgentTrace(input: String): AgentTrace = AdkSupport.toAgentTrace(this, input)

/** Suspending variant that runs this [BaseAgent] on [input] and returns the [AgentTrace]. */
suspend fun BaseAgent.runTrace(input: String): AgentTrace = InMemoryRunner(agent = this).runTrace(input)

/** Runs this ADK [Runner] on [input] and returns the captured [AgentTrace]. */
fun Runner.toAgentTrace(input: String): AgentTrace = AdkSupport.toAgentTrace(this, input)

/** Suspending variant that runs this [Runner] on [input] and returns the [AgentTrace]. */
suspend fun Runner.runTrace(input: String): AgentTrace {
    val events = AdkSupport.runRunnerSuspend(this, input)
    return AdkSupport.toAgentTrace(events)
}

/** Converts this ADK [Session]'s event history into a Dokimos [AgentTrace]. */
fun Session.toAgentTrace(): AgentTrace = AdkSupport.toAgentTrace(this)

/** Converts this list of ADK [Event]s into a Dokimos [AgentTrace]. */
fun List<Event>.toAgentTrace(): AgentTrace = AdkSupport.toAgentTrace(this)

/** Extracts [ToolDefinition]s from this [LlmAgent] and its sub-agents. */
fun LlmAgent.toToolDefinitions(): List<ToolDefinition> = AdkSupport.toToolDefinitions(this)

/** Converts this ADK [BaseTool] into a Dokimos [ToolDefinition]. */
fun BaseTool.toToolDefinition(): ToolDefinition = AdkSupport.toToolDefinition(this)

/** Converts this ADK [FunctionDeclaration] into a Dokimos [ToolDefinition]. */
fun FunctionDeclaration.toToolDefinition(): ToolDefinition = AdkSupport.toToolDefinition(this)

/** Converts this ADK [Schema] into a JSON Schema `Map<String, Any>`. */
fun Schema.toSchemaMap(): Map<String, Any> = AdkSupport.toSchemaMap(this)

/** Converts this ADK [Session] into a Dokimos [ConversationTrajectory]. */
fun Session.toConversationTrajectory(scenario: String = ""): ConversationTrajectory =
    AdkSupport.toConversationTrajectory(this, scenario)

/** Converts this list of ADK [Event]s into a Dokimos [ConversationTrajectory]. */
fun List<Event>.toConversationTrajectory(scenario: String = ""): ConversationTrajectory =
    AdkSupport.toConversationTrajectory(this, scenario)

/** Wraps this ADK [BaseAgent] as a stateful Dokimos [ConversationalApplication]. */
fun BaseAgent.asConversationalApp(): ConversationalApplication = AdkSupport.asConversationalApp(this)

/** Wraps this ADK [Runner] as a stateful Dokimos [ConversationalApplication]. */
fun Runner.asConversationalApp(): ConversationalApplication = AdkSupport.asConversationalApp(this)
