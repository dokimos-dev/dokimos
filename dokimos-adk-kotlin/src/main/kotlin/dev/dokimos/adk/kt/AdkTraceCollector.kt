package dev.dokimos.adk.kt

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.CallbackContext
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.callbacks.CallbackChoice
import com.google.adk.kt.events.Event
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.plugins.Plugin
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.UsageMetadata
import dev.dokimos.core.CallMetrics
import dev.dokimos.core.PriceTable
import dev.dokimos.core.agents.AgentTrace
import dev.dokimos.core.agents.ToolCall
import dev.dokimos.core.agents.ToolDefinition
import dev.dokimos.core.conversation.ConversationTrajectory
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Passive [Plugin] that observes an ADK Kotlin [com.google.adk.kt.runners.Runner] execution and
 * collects tool calls, tool definitions, events, grounding contexts, conversation trajectories, and
 * [CallMetrics] into Dokimos evaluation types.
 *
 * Attach this plugin to an [com.google.adk.kt.runners.InMemoryRunner] or [com.google.adk.kt.apps.App]
 * (either directly via `plugins = listOf(collector)` or via [AdkSupport.attach]), run your agent as
 * usual, and then inspect [trace], [toolDefinitions], [trajectory], [retrievedContexts], or
 * [callMetrics]:
 *
 * ```kotlin
 * val collector = AdkTraceCollector()
 * val runner = InMemoryRunner(agent = agent, plugins = listOf(collector))
 *
 * // Run the agent via your normal Runner flow:
 * runner.runAsync(userId = "user", sessionId = "session", newMessage = Content.fromText(Role.USER, "Weather in Paris?"))
 *     .collect()
 *
 * val trace = collector.trace()
 * val tools = collector.toolDefinitions()
 * val testCase = trace.toTestCase("Weather in Paris?", tools)
 * ```
 *
 * Call [reset] between runs if you reuse the same collector across multiple invocations.
 */
class AdkTraceCollector(override val name: String = DEFAULT_NAME) : Plugin {

    private val events = CopyOnWriteArrayList<Event>()
    private val callbackToolCalls = CopyOnWriteArrayList<ToolCall>()
    private val modelUsages = CopyOnWriteArrayList<UsageMetadata>()
    private val observedTools = ConcurrentHashMap<String, BaseTool>()
    private val observedToolNames = Collections.synchronizedSet(LinkedHashSet<String>())
    private val runStartNanos = AtomicLong(0L)

    @Volatile
    private var lastLatencyMs: Long = 0L

    @Volatile
    private var observedModelId: String? = null

    override suspend fun onUserMessage(invocationContext: InvocationContext, userMessage: Content): Content {
        events.add(
            Event(
                invocationId = invocationContext.invocationId,
                author = Role.USER,
                content = userMessage,
            ),
        )
        return userMessage
    }

    override suspend fun beforeRun(invocationContext: InvocationContext): CallbackChoice<Unit, Content> {
        runStartNanos.set(System.nanoTime())
        recordAgentHierarchy(invocationContext.agent)
        val sessionEvents = invocationContext.session.events
        if (events.isEmpty() && sessionEvents.isNotEmpty()) {
            val last = sessionEvents.last()
            if (Role.USER.equals(last.author, ignoreCase = true) ||
                Role.USER.equals(last.content?.role, ignoreCase = true)
            ) {
                events.add(last)
            }
        }
        return CallbackChoice.Continue(Unit)
    }

    override suspend fun onEvent(invocationContext: InvocationContext, event: Event): Event {
        if (!event.partial) {
            events.add(event)
        }
        return event
    }

    override suspend fun afterModel(context: CallbackContext, response: LlmResponse): LlmResponse {
        if (!response.partial) {
            response.usageMetadata?.let { modelUsages.add(it) }
            if (observedModelId == null && !response.modelVersion.isNullOrBlank()) {
                observedModelId = response.modelVersion
            }
        }
        return response
    }

    override suspend fun beforeTool(
        context: ToolContext,
        tool: BaseTool,
        args: Map<String, Any?>,
    ): CallbackChoice<Map<String, Any?>, Map<String, Any?>> {
        recordTool(tool)
        return CallbackChoice.Continue(args)
    }

    override suspend fun afterTool(
        context: ToolContext,
        tool: BaseTool,
        args: Map<String, Any?>,
        result: Map<String, Any?>,
    ): Map<String, Any?> {
        recordTool(tool)
        val metadata = LinkedHashMap<String, Any>()
        metadata["error"] = AdkSupport.isErrorMap(result)
        context.functionCallId?.let { metadata["id"] = it }
        callbackToolCalls.add(
            ToolCall.builder()
                .name(tool.name)
                .arguments(AdkSupport.nonNullMap(args))
                .result(AdkSupport.serializeResult(result))
                .metadata(metadata)
                .build(),
        )
        return result
    }

    override suspend fun onToolError(
        context: ToolContext,
        tool: BaseTool,
        args: Map<String, Any?>,
        error: Throwable,
    ): CallbackChoice<Unit, Map<String, Any?>> {
        recordTool(tool)
        val metadata = LinkedHashMap<String, Any>()
        metadata["error"] = true
        metadata["exceptionType"] = error.javaClass.name
        context.functionCallId?.let { metadata["id"] = it }
        callbackToolCalls.add(
            ToolCall.builder()
                .name(tool.name)
                .arguments(AdkSupport.nonNullMap(args))
                .result(error.message ?: error.toString())
                .metadata(metadata)
                .build(),
        )
        return CallbackChoice.Continue(Unit)
    }

    override suspend fun afterRun(invocationContext: InvocationContext) {
        val start = runStartNanos.get()
        if (start > 0L) {
            lastLatencyMs = maxOf(0L, (System.nanoTime() - start) / 1_000_000L)
        }
    }

    /** Clears all recorded events, tool calls, and metrics so this collector can be reused. */
    fun reset() {
        events.clear()
        callbackToolCalls.clear()
        modelUsages.clear()
        observedTools.clear()
        observedToolNames.clear()
        runStartNanos.set(0L)
        lastLatencyMs = 0L
        observedModelId = null
    }

    /** Returns an immutable snapshot of the non-partial [Event]s observed so far. */
    fun events(): List<Event> = events.toList()

    /**
     * Returns the [ToolCall]s observed so far. Prefers calls extracted from [events] (which include
     * model-emitted function calls and responses in turn order), falling back to tool callbacks.
     */
    fun toolCalls(): List<ToolCall> {
        val fromEvents = AdkSupport.toToolCalls(events)
        return if (fromEvents.isNotEmpty()) fromEvents else callbackToolCalls.toList()
    }

    /** Returns the distinct tool names observed during execution in encounter order. */
    fun observedToolNames(): List<String> {
        synchronized(observedToolNames) {
            return observedToolNames.toList()
        }
    }

    /**
     * Returns [ToolDefinition]s for all tools observed during execution, preserving full
     * descriptions and JSON parameter schemas from the [BaseTool] instances.
     */
    fun toolDefinitions(): List<ToolDefinition> {
        val definitions = ArrayList<ToolDefinition>()
        val seen = HashSet<String>()
        for (toolName in observedToolNames()) {
            val tool = observedTools[toolName]
            if (tool != null && seen.add(toolName)) {
                definitions.add(AdkSupport.toToolDefinition(tool))
            }
        }
        for ((key, tool) in observedTools) {
            if (seen.add(key)) {
                definitions.add(AdkSupport.toToolDefinition(tool))
            }
        }
        for (call in toolCalls()) {
            if (seen.add(call.name())) {
                definitions.add(ToolDefinition.of(call.name(), "", mapOf("type" to "object")))
            }
        }
        return definitions.toList()
    }

    /**
     * Extracts retrieved context strings from grounding metadata and retrieval tool responses in
     * the observed [Event]s.
     *
     * @param retrievalToolNames optional names of tools whose responses should be treated as
     *   retrieved context
     * @return the extracted context strings
     */
    fun retrievedContexts(vararg retrievalToolNames: String): List<String> =
        AdkSupport.extractTexts(events, *retrievalToolNames)

    /**
     * Builds a Dokimos [AgentTrace] from the events and tool callbacks recorded so far.
     *
     * @return the constructed [AgentTrace]
     */
    fun trace(): AgentTrace {
        if (events.isNotEmpty()) {
            val eventTrace = AdkSupport.toAgentTrace(events)
            if (eventTrace.toolCalls().isNotEmpty() || callbackToolCalls.isEmpty()) {
                return eventTrace
            }
            return AgentTrace.builder()
                .finalResponse(eventTrace.finalResponse())
                .toolCalls(callbackToolCalls.toList())
                .reasoningSteps(eventTrace.reasoningSteps())
                .metadata(
                    buildMap {
                        putAll(eventTrace.metadata())
                        if (lastLatencyMs > 0L) {
                            put("latencyMs", lastLatencyMs)
                        }
                    },
                )
                .build()
        }
        return AgentTrace.builder()
            .finalResponse("")
            .toolCalls(callbackToolCalls.toList())
            .metadata(if (lastLatencyMs > 0L) mapOf("latencyMs" to lastLatencyMs) else emptyMap())
            .build()
    }

    /**
     * Builds a [ConversationTrajectory] from the events recorded so far.
     *
     * @param scenario optional scenario description
     * @return the constructed [ConversationTrajectory]
     */
    @JvmOverloads
    fun trajectory(scenario: String = ""): ConversationTrajectory =
        AdkSupport.toConversationTrajectory(events, scenario)

    /**
     * Computes aggregated [CallMetrics] (tokens, latency, and optional USD cost) across all model
     * turns observed during the run.
     *
     * @param modelId optional model identifier for price lookup (defaults to the agent's model name
     *   when known)
     * @param priceTable optional [PriceTable] for computing cost
     * @return the aggregated [CallMetrics], or `null` if no events or model calls were observed
     */
    @JvmOverloads
    fun callMetrics(modelId: String? = observedModelId, priceTable: PriceTable? = null): CallMetrics? {
        if (events.isEmpty() && modelUsages.isEmpty() && lastLatencyMs == 0L) {
            return null
        }
        val effectiveModelId = modelId ?: observedModelId
        if (events.isNotEmpty()) {
            val fromEvents = AdkSupport.toCallMetrics(events, lastLatencyMs, effectiveModelId, priceTable)
            if (fromEvents.tokensIn() != null || fromEvents.tokensOut() != null || modelUsages.isEmpty()) {
                return fromEvents
            }
        }
        var totalIn = 0
        var totalOut = 0
        var hasIn = false
        var hasOut = false
        for (usage in modelUsages) {
            val promptTokens = usage.promptTokenCount
            if (promptTokens != null && promptTokens > 0) {
                totalIn += promptTokens
                hasIn = true
            }
            val candidateTokens = usage.candidatesTokenCount
            if (candidateTokens != null && candidateTokens > 0) {
                totalOut += candidateTokens
                hasOut = true
            }
        }
        val tokensIn = if (hasIn) totalIn else null
        val tokensOut = if (hasOut) totalOut else null
        val cost = if (priceTable != null && effectiveModelId != null && tokensIn != null && tokensOut != null) {
            priceTable.costUsd(effectiveModelId, tokensIn, tokensOut)
        } else {
            null
        }
        return CallMetrics(tokensIn, tokensOut, cost, lastLatencyMs)
    }

    private fun recordTool(tool: BaseTool) {
        if (tool.name.isNotBlank()) {
            observedTools[tool.name] = tool
            observedToolNames.add(tool.name)
        }
    }

    private fun recordAgentHierarchy(agent: BaseAgent) {
        if (agent is LlmAgent) {
            if (observedModelId == null && agent.model.name.isNotBlank()) {
                observedModelId = agent.model.name
            }
            for (tool in agent.tools) {
                recordTool(tool)
            }
        }
        for (sub in agent.subAgents) {
            recordAgentHierarchy(sub)
        }
    }

    companion object {
        /** Default plugin name registered with ADK's [com.google.adk.kt.plugins.PluginManager]. */
        const val DEFAULT_NAME: String = "dokimos_trace_collector"
    }
}
