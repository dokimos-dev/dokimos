package dev.dokimos.koog

import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import dev.dokimos.core.agents.AgentTrace
import dev.dokimos.core.agents.ToolCall
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Accumulates a Koog agent's tool calls into a Dokimos [AgentTrace].
 *
 * A single collector observes one agent run. Install it on the agent's event handler with
 * [collectAgentTrace], run the agent, then read the trace:
 *
 * ```kotlin
 * val collector = KoogTraceCollector()
 * val agent = AIAgent(...) {
 *     install(EventHandler) { collectAgentTrace(collector) }
 * }
 * val response = agent.runBlocking(userInput)
 * val trace = collector.toAgentTrace(response)
 * val testCase = trace.toTestCase(userInput, tools)
 * ```
 *
 * The collector is not thread-safe and is meant to be used for a single agent run.
 */
class KoogTraceCollector {

    private val toolCalls = mutableListOf<ToolCall>()

    /**
     * Records one completed tool call.
     *
     * Arguments are converted from Koog's JSON object into the map the evaluators expect, with
     * integers kept as [Long] and other numbers as [Double] so numeric-tolerant matching applies.
     * The result is converted from its JSON element to a string: a JSON string result is unwrapped
     * to its content, any other element is rendered as compact JSON, and a null or JSON-null result
     * becomes a null result.
     *
     * @param toolName   the name of the tool that was called
     * @param toolArgs   the arguments the agent passed, as a Koog JSON object
     * @param toolResult the tool's result as a JSON element, or null if absent
     */
    fun record(toolName: String, toolArgs: JsonObject, toolResult: JsonElement?) {
        toolCalls.add(
            ToolCall.builder()
                .name(toolName)
                .arguments(toArgumentMap(toolArgs))
                .result(resultToString(toolResult))
                .build(),
        )
    }

    /**
     * Builds an [AgentTrace] from the tool calls recorded so far.
     *
     * @param finalResponse the agent's final text response, or null if not available
     * @return an agent trace containing the recorded tool calls in order
     */
    fun toAgentTrace(finalResponse: String? = null): AgentTrace {
        val builder = AgentTrace.builder().toolCalls(toolCalls.toList())
        if (finalResponse != null) {
            builder.finalResponse(finalResponse)
        }
        return builder.build()
    }

    private fun resultToString(result: JsonElement?): String? = when {
        result == null || result is JsonNull -> null
        result is JsonPrimitive && result.isString -> result.content
        else -> result.toString()
    }

    private fun toArgumentMap(args: JsonObject): Map<String, Any> {
        val map = LinkedHashMap<String, Any>()
        args.forEach { (key, element) ->
            jsonToValue(element)?.let { map[key] = it }
        }
        return map
    }

    private fun jsonToValue(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonObject -> element.mapValues { jsonToValue(it.value) }
        is JsonArray -> element.map { jsonToValue(it) }
        is JsonPrimitive ->
            if (element.isString) {
                element.content
            } else {
                element.booleanOrNull ?: element.longOrNull ?: element.doubleOrNull ?: element.content
            }
    }
}

/**
 * Installs tool-call collection into a Koog event handler configuration, recording each completed
 * tool call into the given [collector].
 *
 * Use inside the `install(EventHandler) { ... }` block when building an agent.
 *
 * @param collector the collector that accumulates the agent's tool calls
 */
fun EventHandlerConfig.collectAgentTrace(collector: KoogTraceCollector) {
    onToolCallCompleted { context -> collector.record(context.toolName, context.toolArgs, context.toolResult) }
}
