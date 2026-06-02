package dev.dokimos.koog

import com.fasterxml.jackson.databind.ObjectMapper
import dev.dokimos.core.agents.AgentTrace
import dev.dokimos.core.agents.ToolCall

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
 * The collector imports no Koog or kotlinx-serialization types: it accepts the tool arguments and
 * result as opaque JSON nodes and reads them by duck-typing over method names. Koog moved its JSON
 * model from `kotlinx.serialization.json` to `ai.koog.serialization` at 0.7.0, so a single published
 * artifact must walk either hierarchy without a compiled dependency on either.
 *
 * The collector is not thread-safe and is meant to be used for a single agent run.
 */
class KoogTraceCollector {

    private val toolCalls = mutableListOf<ToolCall>()

    /**
     * Records one completed tool call.
     *
     * Arguments are converted into the map the evaluators expect, with integers kept as [Long] and
     * other numbers as [Double] so numeric-tolerant matching applies, and top-level null values
     * omitted. The result is converted to a string: a JSON string result is unwrapped to its
     * content, any other node is rendered as compact JSON, and a null or JSON-null result becomes a
     * null result.
     *
     * @param toolName   the name of the tool that was called
     * @param toolArgs   the arguments the agent passed, as a JSON object node
     * @param toolResult the tool's result as a JSON node, or null if absent
     */
    fun record(toolName: String, toolArgs: Any?, toolResult: Any?) {
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

    private fun toArgumentMap(args: Any?): Map<String, Any> {
        val entries = objectEntries(args) ?: return emptyMap()
        val map = LinkedHashMap<String, Any>()
        entries.forEach { (key, element) -> jsonToValue(element)?.let { map[key] = it } }
        return map
    }

    private fun resultToString(result: Any?): String? = when {
        result == null || isJsonNull(result) -> null
        isStringPrimitive(result) -> primitiveContent(result)
        else -> MAPPER.writeValueAsString(jsonToValue(result))
    }

    private fun jsonToValue(element: Any?): Any? {
        if (element == null || isJsonNull(element)) return null
        objectEntries(element)?.let { entries ->
            val m = LinkedHashMap<String, Any?>()
            entries.forEach { (k, v) -> m[k] = jsonToValue(v) }
            return m
        }
        arrayElements(element)?.let { list -> return list.map { jsonToValue(it) } }
        val content = primitiveContent(element) ?: return element.toString()
        if (isStringPrimitive(element)) return content
        return content.toBooleanStrictOrNull() ?: content.toLongOrNull() ?: content.toDoubleOrNull() ?: content
    }

    private fun objectEntries(node: Any?): Map<String, Any?>? {
        if (node == null) return null
        (callNoArg(node, "getEntries") as? Map<*, *>)?.let { return it.toStringKeyMap() }
        if (node is Map<*, *>) return node.toStringKeyMap()
        return null
    }

    private fun arrayElements(node: Any?): List<Any?>? {
        if (node == null) return null
        (callNoArg(node, "getElements") as? List<*>)?.let { return it }
        if (node is List<*>) return node
        return null
    }

    private fun isStringPrimitive(node: Any?): Boolean = node != null && callNoArg(node, "isString") == true

    private fun primitiveContent(node: Any?): String? = callNoArg(node, "getContent") as? String

    private fun isJsonNull(node: Any?): Boolean {
        if (node == null) return true
        val name = node.javaClass.simpleName
        if (name == "JsonNull" || name == "JSONNull") return true
        return !isStringPrimitive(node) && primitiveContent(node) == "null"
    }

    private fun callNoArg(target: Any?, method: String): Any? {
        if (target == null) return null
        return try {
            target.javaClass.getMethod(method).invoke(target)
        } catch (e: ReflectiveOperationException) {
            null
        }
    }

    private fun Map<*, *>.toStringKeyMap(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        forEach { (k, v) -> if (k is String) m[k] = v }
        return m
    }

    private companion object {
        private val MAPPER = ObjectMapper()
    }
}

/**
 * Installs tool-call collection into a Koog event handler configuration, recording each completed
 * tool call into the given [collector].
 *
 * Use inside the `install(EventHandler) { ... }` block when building an agent. The callback is
 * registered reflectively because Koog added a second `onToolCallCompleted` overload at 0.7.0 that
 * makes a plain Kotlin lambda ambiguous at compile time, and the completion context's argument and
 * result getters are read reflectively because their return types changed across versions.
 *
 * @param collector the collector that accumulates the agent's tool calls
 */
fun ai.koog.agents.features.eventHandler.feature.EventHandlerConfig.collectAgentTrace(collector: KoogTraceCollector) {
    val handler: (Any?, Any?) -> Any? = { context, _ ->
        collector.record(
            invokeContextGetter(context, "getToolName") as String,
            invokeContextGetter(context, "getToolArgs"),
            invokeContextGetter(context, "getToolResult"),
        )
        Unit
    }
    val register = javaClass.methods.first { m ->
        m.name == "onToolCallCompleted" &&
            m.parameterCount == 1 &&
            kotlin.jvm.functions.Function2::class.java.isAssignableFrom(m.parameterTypes[0])
    }
    register.invoke(this, handler)
}

private fun invokeContextGetter(target: Any?, name: String): Any? =
    target?.let { it.javaClass.getMethod(name).invoke(it) }
