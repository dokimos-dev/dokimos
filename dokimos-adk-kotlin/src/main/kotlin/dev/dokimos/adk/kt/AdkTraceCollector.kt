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
    private val observedTools = Collections.synchronizedMap(LinkedHashMap<String, BaseTool>())
    private val observedToolNames = Collections.synchronizedSet(LinkedHashSet<String>())
    private val runStartNanos = AtomicLong(0L)

    @Volatile
    private var totalLatencyMs: Long? = null

    @Volatile
    private var hasObservedActivity: Boolean = false

    @Volatile
    private var observedModelId: String? = null

    override suspend fun onUserMessage(invocationContext: InvocationContext, userMessage: Content): Content {
        hasObservedActivity = true
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
        hasObservedActivity = true
        runStartNanos.set(System.nanoTime())
        recordAgentHierarchy(invocationContext.agent)
        val sessionEvents = invocationContext.session.events
        if (sessionEvents.isNotEmpty()) {
            val last = sessionEvents.last()
            if ((
                    Role.USER.equals(last.author, ignoreCase = true) ||
                        Role.USER.equals(last.content?.role, ignoreCase = true)
                    ) &&
                !hasMatchingTrailingUserEvent(last)
            ) {
                events.add(last)
            }
        }
        return CallbackChoice.Continue(Unit)
    }

    override suspend fun onEvent(invocationContext: InvocationContext, event: Event): Event {
        hasObservedActivity = true
        if (!event.partial) {
            if (!isDuplicateUserEvent(event)) {
                events.add(event)
            }
            if (observedModelId == null && !event.modelVersion.isNullOrBlank()) {
                observedModelId = event.modelVersion
            }
            for (call in event.functionCalls()) {
                if (call.name.isNotBlank()) {
                    observedToolNames.add(call.name)
                }
            }
        }
        return event
    }

    override suspend fun afterModel(context: CallbackContext, response: LlmResponse): LlmResponse {
        hasObservedActivity = true
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
        hasObservedActivity = true
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
        hasObservedActivity = true
        recordTool(tool)
        val errorMsg = error.message ?: error.toString()
        val metadata = LinkedHashMap<String, Any>()
        metadata["error"] = true
        metadata["exceptionType"] = error.javaClass.name
        context.functionCallId?.let { metadata["id"] = it }
        callbackToolCalls.add(
            ToolCall.builder()
                .name(tool.name)
                .arguments(AdkSupport.nonNullMap(args))
                .result(AdkSupport.serializeResult(mapOf("error" to errorMsg)))
                .metadata(metadata)
                .build(),
        )
        return CallbackChoice.Continue(Unit)
    }

    override suspend fun afterRun(invocationContext: InvocationContext) {
        hasObservedActivity = true
        val start = runStartNanos.getAndSet(0L)
        if (start > 0L) {
            val elapsedMs = maxOf(0L, (System.nanoTime() - start) / 1_000_000L)
            totalLatencyMs = (totalLatencyMs ?: 0L) + elapsedMs
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
        totalLatencyMs = null
        hasObservedActivity = false
        observedModelId = null
    }

    /** Returns an immutable snapshot of the non-partial [Event]s observed so far. */
    fun events(): List<Event> = events.toList()

    /**
     * Returns the [ToolCall]s observed so far. Extracts matched calls/responses from [events] in
     * turn order, backfilling any missing results or error metadata from tool callbacks, and
     * falling back to tool callbacks when no tool-call events were emitted.
     */
    fun toolCalls(): List<ToolCall> = mergeCallbackResults(AdkSupport.toToolCalls(events), callbackToolCalls)

    /** Returns the distinct tool names invoked during execution in encounter order. */
    fun observedToolNames(): List<String> {
        synchronized(observedToolNames) {
            return observedToolNames.toList()
        }
    }

    /**
     * Returns [ToolDefinition]s for all tools configured on the agent or observed during execution,
     * preserving full descriptions and JSON parameter schemas from the [BaseTool] instances.
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
        val snapshot = synchronized(observedTools) { LinkedHashMap(observedTools) }
        for ((key, tool) in snapshot) {
            if (seen.add(key)) {
                definitions.add(AdkSupport.toToolDefinition(tool))
            }
        }
        for (call in toolCalls()) {
            if (seen.add(call.name())) {
                definitions.add(
                    ToolDefinition.of(
                        call.name(),
                        "",
                        mapOf(
                            "type" to "object",
                            "properties" to emptyMap<String, Any>(),
                        ),
                    ),
                )
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
        val latency = totalLatencyMs
        if (events.isNotEmpty()) {
            val eventTrace = AdkSupport.toAgentTrace(events)
            val mergedCalls = mergeCallbackResults(eventTrace.toolCalls(), callbackToolCalls)
            if (mergedCalls == eventTrace.toolCalls() && latency == null) {
                return eventTrace
            }
            return AgentTrace.builder()
                .finalResponse(eventTrace.finalResponse())
                .toolCalls(mergedCalls)
                .reasoningSteps(eventTrace.reasoningSteps())
                .metadata(
                    buildMap {
                        putAll(eventTrace.metadata())
                        if (latency != null) {
                            put("latencyMs", latency)
                        }
                    },
                )
                .build()
        }
        return AgentTrace.builder()
            .finalResponse("")
            .toolCalls(callbackToolCalls.toList())
            .metadata(if (latency != null) mapOf("latencyMs" to latency) else emptyMap())
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
        if (!hasObservedActivity && events.isEmpty() && modelUsages.isEmpty() && totalLatencyMs == null) {
            return null
        }
        val effectiveModelId = modelId ?: observedModelId
        val latency = totalLatencyMs ?: 0L
        if (events.isNotEmpty()) {
            val fromEvents = AdkSupport.toCallMetrics(events, latency, effectiveModelId, priceTable)
            if (fromEvents.tokensIn() != null || fromEvents.tokensOut() != null || modelUsages.isEmpty()) {
                if (fromEvents.tokensIn() == null && fromEvents.tokensOut() == null && totalLatencyMs == null) {
                    return null
                }
                return CallMetrics(fromEvents.tokensIn(), fromEvents.tokensOut(), fromEvents.costUsd(), totalLatencyMs)
            }
        }
        var pSum = 0
        var cSum = 0
        for (usage in modelUsages) {
            pSum += usage.promptTokenCount ?: 0
            cSum += usage.candidatesTokenCount ?: 0
        }
        val tokensIn = if (pSum > 0 || cSum > 0) pSum else null
        val tokensOut = if (pSum > 0 || cSum > 0) cSum else null
        if (tokensIn == null && tokensOut == null && totalLatencyMs == null) {
            return null
        }
        val cost = if (priceTable != null && effectiveModelId != null && tokensIn != null && tokensOut != null) {
            priceTable.costUsd(effectiveModelId, tokensIn, tokensOut)
        } else {
            null
        }
        return CallMetrics(tokensIn, tokensOut, cost, totalLatencyMs)
    }

    private fun recordTool(tool: BaseTool) {
        if (tool.name.isNotBlank()) {
            observedTools.putIfAbsent(tool.name, tool)
            observedToolNames.add(tool.name)
        }
    }

    private fun recordAgentHierarchy(agent: BaseAgent) {
        if (agent is LlmAgent) {
            if (observedModelId == null && agent.model.name.isNotBlank()) {
                observedModelId = agent.model.name
            }
            for (tool in agent.tools) {
                if (tool.name.isNotBlank()) {
                    observedTools.putIfAbsent(tool.name, tool)
                }
            }
        }
        for (sub in agent.subAgents) {
            recordAgentHierarchy(sub)
        }
    }

    private fun hasMatchingTrailingUserEvent(candidate: Event): Boolean {
        val last = events.lastOrNull() ?: return false
        val isLastUser = Role.USER.equals(last.author, ignoreCase = true) ||
            Role.USER.equals(last.content?.role, ignoreCase = true)
        if (!isLastUser) return false
        return last.invocationId == candidate.invocationId && last.content == candidate.content
    }

    private fun isDuplicateUserEvent(candidate: Event): Boolean {
        val isCandidateUser = Role.USER.equals(candidate.author, ignoreCase = true) ||
            Role.USER.equals(candidate.content?.role, ignoreCase = true)
        if (!isCandidateUser) return false
        return hasMatchingTrailingUserEvent(candidate)
    }

    private fun mergeCallbackResults(fromEvents: List<ToolCall>, fromCallbacks: List<ToolCall>): List<ToolCall> {
        if (fromEvents.isEmpty()) return fromCallbacks.toList()
        if (fromCallbacks.isEmpty()) return fromEvents
        val callbacks = fromCallbacks.toList()
        val usedCallback = BooleanArray(callbacks.size)
        val merged = ArrayList<ToolCall>(fromEvents.size)
        for (eventCall in fromEvents) {
            if (eventCall.result() != null) {
                merged.add(eventCall)
                continue
            }
            var matchedCb: ToolCall? = null
            val callId = eventCall.metadata()["id"] as? String
            if (!callId.isNullOrBlank()) {
                for (i in callbacks.indices) {
                    if (!usedCallback[i] && callId == callbacks[i].metadata()["id"]) {
                        matchedCb = callbacks[i]
                        usedCallback[i] = true
                        break
                    }
                }
            }
            if (matchedCb == null) {
                for (i in callbacks.indices) {
                    if (!usedCallback[i] && eventCall.name() == callbacks[i].name()) {
                        matchedCb = callbacks[i]
                        usedCallback[i] = true
                        break
                    }
                }
            }
            if (matchedCb != null) {
                val combinedMeta = LinkedHashMap<String, Any>(eventCall.metadata())
                combinedMeta.putAll(matchedCb.metadata())
                merged.add(
                    ToolCall.builder()
                        .name(eventCall.name())
                        .arguments(eventCall.arguments())
                        .result(matchedCb.result())
                        .metadata(combinedMeta)
                        .build(),
                )
            } else {
                merged.add(eventCall)
            }
        }
        return merged.toList()
    }

    companion object {
        /** Default plugin name registered with ADK's [com.google.adk.kt.plugins.PluginManager]. */
        const val DEFAULT_NAME: String = "dokimos_trace_collector"
    }
}
