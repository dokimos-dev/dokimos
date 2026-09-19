package dev.dokimos.kotlin.dsl.agents

import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.agents.AgentTrace
import dev.dokimos.core.agents.ToolCall
import dev.dokimos.core.agents.ToolDefinition
import dev.dokimos.core.evaluators.agents.AgentEvalCase
import dev.dokimos.kotlin.dsl.DokimosDsl

/**
 * Top-level DSL entrypoints for the agent primitives in `dev.dokimos.core.agents`.
 */

/** Builds a single [ToolCall]. */
fun toolCall(name: String, block: ToolCallDsl.() -> Unit = {}): ToolCall = ToolCallDsl(name).apply(block).build()

/** Builds a [ToolCall] from a name and arguments, delegating to [ToolCall.of]. */
fun toolCall(name: String, arguments: Map<String, Any>): ToolCall = ToolCall.of(name, arguments)

/** Builds a list of [ToolCall]s. */
fun toolCalls(block: ToolCallsDsl.() -> Unit): List<ToolCall> = ToolCallsDsl().apply(block).build()

/** Builds a single [ToolDefinition]. */
fun toolDefinition(name: String, block: ToolDefinitionDsl.() -> Unit = {}): ToolDefinition =
    ToolDefinitionDsl(name).apply(block).build()

/** Builds a list of [ToolDefinition]s. */
fun tools(block: ToolDefinitionsDsl.() -> Unit): List<ToolDefinition> = ToolDefinitionsDsl().apply(block).build()

/** Builds an [AgentTrace]. */
fun agentTrace(block: AgentTraceDsl.() -> Unit): AgentTrace = AgentTraceDsl().apply(block).build()

/**
 * Builds an [EvalTestCase] carrying the keys the agent evaluators read.
 *
 * Delegates the shared slots to [AgentEvalCase] and adds the ones it does not cover:
 * the final response, the reasoning steps that `planQuality` and `planAdherence` read,
 * and the `constraints` entry `taskCompletion` reads.
 */
fun agentTestCase(block: AgentTestCaseDsl.() -> Unit): EvalTestCase = AgentTestCaseDsl().apply(block).build()

@DokimosDsl
class ToolCallDsl(var name: String) {
    /** The raw tool result. Use [resultJson] to serialize an object instead. */
    var result: String? = null

    private val arguments: MutableMap<String, Any> = linkedMapOf()
    private val metadata: MutableMap<String, Any> = mutableMapOf()
    private var resultValue: Any? = null

    fun argument(key: String, value: Any) {
        arguments[key] = value
    }

    fun arguments(vararg values: Pair<String, Any>) {
        arguments.putAll(values)
    }

    fun arguments(values: Map<String, Any>) {
        arguments.putAll(values)
    }

    /** Sets the result by serializing [value] to JSON, delegating to `ToolCall.Builder.resultJson`. */
    fun resultJson(value: Any) {
        resultValue = value
    }

    fun metadata(key: String, value: Any) {
        metadata[key] = value
    }

    fun metadata(vararg values: Pair<String, Any>) {
        metadata.putAll(values)
    }

    fun metadata(values: Map<String, Any>) {
        metadata.putAll(values)
    }

    fun build(): ToolCall {
        val builder = ToolCall.builder()
            .name(name)
            .arguments(arguments)
            .metadata(metadata)
        result?.let { builder.result(it) }
        resultValue?.let { builder.resultJson(it) }
        return builder.build()
    }
}

@DokimosDsl
class ToolCallsDsl {
    private val calls: MutableList<ToolCall> = mutableListOf()

    fun call(name: String, block: ToolCallDsl.() -> Unit = {}) {
        calls += ToolCallDsl(name).apply(block).build()
    }

    fun call(name: String, arguments: Map<String, Any>) {
        calls += ToolCall.of(name, arguments)
    }

    fun call(value: ToolCall) {
        calls += value
    }

    fun calls(values: List<ToolCall>) {
        calls += values
    }

    fun build(): List<ToolCall> = calls.toList()
}

@DokimosDsl
class ToolDefinitionDsl(var name: String) {
    var description: String = ""

    private val inputSchema: MutableMap<String, Any> = linkedMapOf()

    /** Sets the raw JSON schema for the tool's arguments. */
    fun inputSchema(values: Map<String, Any>) {
        inputSchema.putAll(values)
    }

    /** Builds the tool's JSON schema from typed parameter declarations. */
    fun parameters(block: JsonSchemaDsl.() -> Unit) {
        inputSchema.putAll(JsonSchemaDsl().apply(block).build())
    }

    fun build(): ToolDefinition = ToolDefinition(name, description, inputSchema)
}

@DokimosDsl
class ToolDefinitionsDsl {
    private val definitions: MutableList<ToolDefinition> = mutableListOf()

    fun tool(name: String, block: ToolDefinitionDsl.() -> Unit = {}) {
        definitions += ToolDefinitionDsl(name).apply(block).build()
    }

    fun tool(value: ToolDefinition) {
        definitions += value
    }

    fun tools(values: List<ToolDefinition>) {
        definitions += values
    }

    fun build(): List<ToolDefinition> = definitions.toList()
}

/**
 * Builds an object-typed JSON schema (`type`, `properties`, `required`) for a tool's arguments.
 *
 * The declared types are the ones `ToolCallValidityEvaluator` validates against:
 * `string`, `number`, `integer`, `boolean`, `array`, and `object`.
 */
@DokimosDsl
class JsonSchemaDsl {
    /** When set to `false`, arguments outside the declared properties are reported as unexpected. */
    var additionalProperties: Boolean? = null

    private val properties: MutableMap<String, Map<String, Any>> = linkedMapOf()
    private val requiredNames: MutableList<String> = mutableListOf()

    fun string(name: String, description: String = "", required: Boolean = false, enum: List<String> = emptyList()) {
        val schema = typed("string", description).toMutableMap()
        if (enum.isNotEmpty()) schema["enum"] = enum
        property(name, schema, required)
    }

    fun integer(name: String, description: String = "", required: Boolean = false) {
        property(name, typed("integer", description), required)
    }

    fun number(name: String, description: String = "", required: Boolean = false) {
        property(name, typed("number", description), required)
    }

    fun boolean(name: String, description: String = "", required: Boolean = false) {
        property(name, typed("boolean", description), required)
    }

    fun array(name: String, description: String = "", itemType: String = "string", required: Boolean = false) {
        val schema = typed("array", description).toMutableMap()
        schema["items"] = mapOf("type" to itemType)
        property(name, schema, required)
    }

    fun `object`(name: String, description: String = "", required: Boolean = false) {
        property(name, typed("object", description), required)
    }

    /** Declares a property with a raw JSON schema. */
    fun property(name: String, schema: Map<String, Any>, required: Boolean = false) {
        properties[name] = schema.toMap()
        if (required) required(name)
    }

    /** Marks already declared properties as required. */
    fun required(vararg names: String) {
        names.forEach { if (it !in requiredNames) requiredNames += it }
    }

    fun build(): Map<String, Any> = buildMap {
        put("type", "object")
        put("properties", properties.toMap())
        if (requiredNames.isNotEmpty()) put("required", requiredNames.toList())
        additionalProperties?.let { put("additionalProperties", it) }
    }

    private fun typed(type: String, description: String): Map<String, Any> = buildMap {
        put("type", type)
        if (description.isNotEmpty()) put("description", description)
    }
}

@DokimosDsl
class AgentTraceDsl {
    var finalResponse: String? = null

    private val toolCalls: MutableList<ToolCall> = mutableListOf()
    private val reasoningSteps: MutableList<String> = mutableListOf()
    private val metadata: MutableMap<String, Any> = mutableMapOf()

    fun toolCall(name: String, block: ToolCallDsl.() -> Unit = {}) {
        toolCalls += ToolCallDsl(name).apply(block).build()
    }

    fun toolCall(name: String, arguments: Map<String, Any>) {
        toolCalls += ToolCall.of(name, arguments)
    }

    fun toolCall(value: ToolCall) {
        toolCalls += value
    }

    fun toolCalls(values: List<ToolCall>) {
        toolCalls += values
    }

    fun toolCalls(block: ToolCallsDsl.() -> Unit) {
        toolCalls += ToolCallsDsl().apply(block).build()
    }

    fun reasoning(step: String) {
        reasoningSteps += step
    }

    fun reasoning(values: List<String>) {
        reasoningSteps += values
    }

    fun metadata(key: String, value: Any) {
        metadata[key] = value
    }

    fun metadata(vararg values: Pair<String, Any>) {
        metadata.putAll(values)
    }

    fun metadata(values: Map<String, Any>) {
        metadata.putAll(values)
    }

    fun build(): AgentTrace = AgentTrace(finalResponse, toolCalls, reasoningSteps, metadata)
}

/**
 * Builds an [EvalTestCase] using the keys the agent evaluators read: `toolCalls` in actual and
 * expected outputs, `output` and `reasoningSteps` in actual outputs, and `tools`, `tasks` and
 * `constraints` in metadata.
 */
@DokimosDsl
class AgentTestCaseDsl {
    var input: String? = null

    /** The agent's final text response, stored under the `output` key. */
    var output: String? = null

    /** Optional constraints read by `taskCompletion`. */
    var constraints: String? = null

    private val toolCalls: MutableList<ToolCall> = mutableListOf()
    private val expectedToolCalls: MutableList<ToolCall> = mutableListOf()
    private val toolDefinitions: MutableList<ToolDefinition> = mutableListOf()
    private val tasks: MutableList<String> = mutableListOf()
    private val reasoningSteps: MutableList<String> = mutableListOf()
    private val inputs: MutableMap<String, Any> = mutableMapOf()
    private val actualOutputs: MutableMap<String, Any> = mutableMapOf()
    private val expectedOutputs: MutableMap<String, Any> = mutableMapOf()
    private val metadata: MutableMap<String, Any> = mutableMapOf()

    /** Takes the final response, tool calls and reasoning steps from an existing trace. */
    fun trace(value: AgentTrace) {
        value.finalResponse()?.let { output = it }
        toolCalls += value.toolCalls()
        reasoningSteps += value.reasoningSteps()
    }

    /** Builds a trace inline and takes its outputs. */
    fun trace(block: AgentTraceDsl.() -> Unit) {
        trace(AgentTraceDsl().apply(block).build())
    }

    fun toolCall(name: String, block: ToolCallDsl.() -> Unit = {}) {
        toolCalls += ToolCallDsl(name).apply(block).build()
    }

    fun toolCall(name: String, arguments: Map<String, Any>) {
        toolCalls += ToolCall.of(name, arguments)
    }

    fun toolCall(value: ToolCall) {
        toolCalls += value
    }

    fun toolCalls(values: List<ToolCall>) {
        toolCalls += values
    }

    fun toolCalls(block: ToolCallsDsl.() -> Unit) {
        toolCalls += ToolCallsDsl().apply(block).build()
    }

    fun expectedToolCall(name: String, block: ToolCallDsl.() -> Unit = {}) {
        expectedToolCalls += ToolCallDsl(name).apply(block).build()
    }

    fun expectedToolCall(name: String, arguments: Map<String, Any>) {
        expectedToolCalls += ToolCall.of(name, arguments)
    }

    fun expectedToolCall(value: ToolCall) {
        expectedToolCalls += value
    }

    fun expectedToolCalls(values: List<ToolCall>) {
        expectedToolCalls += values
    }

    fun expectedToolCalls(block: ToolCallsDsl.() -> Unit) {
        expectedToolCalls += ToolCallsDsl().apply(block).build()
    }

    fun tool(name: String, block: ToolDefinitionDsl.() -> Unit = {}) {
        toolDefinitions += ToolDefinitionDsl(name).apply(block).build()
    }

    fun tool(value: ToolDefinition) {
        toolDefinitions += value
    }

    fun tools(values: List<ToolDefinition>) {
        toolDefinitions += values
    }

    fun tools(block: ToolDefinitionsDsl.() -> Unit) {
        toolDefinitions += ToolDefinitionsDsl().apply(block).build()
    }

    fun task(value: String) {
        tasks += value
    }

    fun tasks(vararg values: String) {
        tasks += values
    }

    fun tasks(values: List<String>) {
        tasks += values
    }

    fun reasoning(step: String) {
        reasoningSteps += step
    }

    fun reasoning(values: List<String>) {
        reasoningSteps += values
    }

    fun input(key: String, value: Any) {
        inputs[key] = value
    }

    fun actualOutput(key: String, value: Any) {
        actualOutputs[key] = value
    }

    fun expectedOutput(key: String, value: Any) {
        expectedOutputs[key] = value
    }

    fun metadata(key: String, value: Any) {
        metadata[key] = value
    }

    fun metadata(vararg values: Pair<String, Any>) {
        metadata.putAll(values)
    }

    fun metadata(values: Map<String, Any>) {
        metadata.putAll(values)
    }

    fun build(): EvalTestCase {
        // The shared slots come from AgentEvalCase so the key names stay owned by core.
        val agentCase = AgentEvalCase.builder()
        input?.let { agentCase.input(it) }
        if (toolCalls.isNotEmpty()) agentCase.toolCalls(toolCalls)
        if (toolDefinitions.isNotEmpty()) agentCase.tools(toolDefinitions)
        if (expectedToolCalls.isNotEmpty()) agentCase.expectedToolCalls(expectedToolCalls)
        if (tasks.isNotEmpty()) agentCase.tasks(tasks)
        val base = agentCase.build()

        val builder = EvalTestCase.builder()
            .inputs(base.inputs())
            .actualOutputs(base.actualOutputs())
            .expectedOutputs(base.expectedOutputs())
            .metadata(base.metadata())

        output?.let { builder.actualOutput("output", it) }
        if (reasoningSteps.isNotEmpty()) builder.actualOutput("reasoningSteps", reasoningSteps.toList())
        constraints?.let { builder.metadata("constraints", it) }

        inputs.forEach { (k, v) -> builder.input(k, v) }
        actualOutputs.forEach { (k, v) -> builder.actualOutput(k, v) }
        expectedOutputs.forEach { (k, v) -> builder.expectedOutput(k, v) }
        metadata.forEach { (k, v) -> builder.metadata(k, v) }

        return builder.build()
    }
}
