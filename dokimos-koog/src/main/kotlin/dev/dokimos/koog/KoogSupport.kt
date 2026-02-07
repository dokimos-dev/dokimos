package dev.dokimos.koog

import ai.koog.agents.core.agent.AIAgent
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.JudgeLM
import dev.dokimos.core.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext

/**
 * Utilities for integrating Dokimos with Koog agents.
 */

/** Default key for the model output in evaluation results. */
const val OUTPUT_KEY: String = "output"

/** Default key for additional context in evaluation results. */
const val CONTEXT_KEY: String = "context"

/** Default key for reading input from dataset examples. */
const val INPUT_KEY: String = "input"


/**
 * Creates a [JudgeLM] from any blocking call that accepts a prompt and
 * returns text.
 */
fun asJudge(agentCall: suspend (String) -> String): JudgeLM {
    return JudgeLM { prompt ->
        val content = runBlocking { agentCall(prompt) }
        require(content.isNotBlank()) { "Judge response content was blank" }
        content
    }
}

/**
 * Creates a [JudgeLM] from a suspending Koog agent.
 */
fun asJudge(agent:() ->  AIAgent<String, String>): JudgeLM {
     return asJudge { input -> agent().run(input) }
}


/**
 * Creates a RAG-oriented [Task] using a suspending Koog call that returns
 * both the answer and its retrieved context.
 */
fun ragTask(agentCall: (String) -> RagResult): Task {
    return ragTask(agentCall, INPUT_KEY, OUTPUT_KEY, CONTEXT_KEY)
}

fun <I, O> AIAgent<I, O>.runBlocking(input:I, context: CoroutineContext = Dispatchers.Default):O = runBlocking {
    run(input)
}

/**
 * Creates a RAG-oriented [Task] with configurable keys.
 */
fun ragTask(
    agentCall: (String) -> RagResult,
    inputKey: String,
    outputKey: String,
    contextKey: String
): Task {
    return Task { example ->
        val input = example.inputs()[inputKey] as? String ?: example.input()
        val result = agentCall(input)
        buildMap<String, Any> {
            put(outputKey, result.output)
            if (result.context.isNotEmpty()) {
                put(contextKey, result.context)
            }
            if (result.metadata.isNotEmpty()) {
                putAll(result.metadata)
            }
        }
    }
}

/**
 * Builds an [EvalTestCase] from Koog call inputs/outputs.
 */
fun EvalTestCase(
    input: String,
    output: String,
    context: List<String> = emptyList(),
    metadata: Map<String, Any> = emptyMap()
): EvalTestCase {
    val inputs = mapOf(INPUT_KEY to input)
    val actualOutputs = mutableMapOf<String, Any>(OUTPUT_KEY to output)
    if (context.isNotEmpty()) {
        actualOutputs[CONTEXT_KEY] = context
    }
    return EvalTestCase(inputs, actualOutputs, emptyMap(), metadata)
}

/**
 * RAG result returned from a Koog call.
 */
data class RagResult(
    val output: String,
    val context: List<String> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)
