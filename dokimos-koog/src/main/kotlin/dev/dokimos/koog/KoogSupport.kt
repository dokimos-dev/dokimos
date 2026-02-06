package dev.dokimos.koog

import ai.koog.agents.core.agent.AIAgent
import dev.dokimos.core.EvalResult
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.JudgeLM
import dev.dokimos.core.Task
import kotlinx.coroutines.runBlocking

/**
 * Utilities for integrating Dokimos with Koog agents.
 */
object KoogSupport {

    /** Default key for the model output in evaluation results. */
    const val OUTPUT_KEY: String = "output"

    /** Default key for additional context in evaluation results. */
    const val CONTEXT_KEY: String = "context"

    /** Default key for reading input from dataset examples. */
    const val INPUT_KEY: String = "input"

    /**
     * Creates a [JudgeLM] from a Koog [AIAgent].
     *
     * The agent is executed in a blocking coroutine. The agent is expected to
     * take the prompt as input and return a textual response.
     */
    fun asJudge(agent: AIAgent<String, String>): JudgeLM {
        return asJudge(agent) { it }
    }

    /**
     * Creates a [JudgeLM] from a Koog [AIAgent] with a custom mapper from the
     * agent output to text.
     */
    fun <Output> asJudge(agent: AIAgent<String, Output>, toText: (Output) -> String): JudgeLM {
        return asJudge { prompt ->
            val result = runBlocking { agent.run(prompt) }
            toText(result)
        }
    }

    /**
     * Creates a [JudgeLM] from any suspending call that accepts a prompt and
     * returns text.
     */
    fun asJudge(agentCall: suspend (String) -> String): JudgeLM {
        return JudgeLM { prompt ->
            requireNotNull(prompt) { "Prompt cannot be null" }
            val content = runBlocking { agentCall(prompt) }
            require(content.isNotBlank()) { "Judge response content was blank" }
            content
        }
    }

    /**
     * Builds an [EvalTestCase] from Koog call inputs/outputs.
     */
    fun toTestCase(
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
     * Converts a Dokimos [EvalResult] to a simple Koog-friendly response DTO.
     */
    fun toEvaluationResponse(result: EvalResult): KoogEvaluationResponse {
        val metadata = HashMap<String, Any>(result.metadata())
        metadata["score"] = result.score()
        return KoogEvaluationResponse(
            pass = result.success(),
            feedback = result.reason(),
            metadata = metadata
        )
    }

    /**
     * Creates a simple [Task] that runs a Koog agent and maps its string output
     * into Dokimos output keys.
     */
    fun task(agent: AIAgent<String, String>): Task = task(agent) { it }

    /**
     * Creates a [Task] from a Koog agent with a custom mapper from the agent
     * output to text.
     */
    fun <Output> task(agent: AIAgent<String, Output>, toText: (Output) -> String): Task {
        return task { input ->
            val result = runBlocking { agent.run(input) }
            toText(result)
        }
    }

    /**
     * Creates a [Task] from any suspending call that accepts the example input
     * and returns text.
     */
    fun task(agentCall: suspend (String) -> String): Task {
        return Task { example ->
            val input = example.inputs()[INPUT_KEY] as? String ?: example.input()
            val output = runBlocking { agentCall(input) }
            mapOf(OUTPUT_KEY to output)
        }
    }

    /**
     * Creates a RAG-oriented [Task] using a suspending Koog call that returns
     * both the answer and its retrieved context.
     */
    fun ragTask(agentCall: suspend (String) -> RagResult): Task {
        return ragTask(agentCall, INPUT_KEY, OUTPUT_KEY, CONTEXT_KEY)
    }

    /**
     * Creates a RAG-oriented [Task] with configurable keys.
     */
    fun ragTask(
        agentCall: suspend (String) -> RagResult,
        inputKey: String,
        outputKey: String,
        contextKey: String
    ): Task {
        return Task { example ->
            val input = example.inputs()[inputKey] as? String ?: example.input()
            val result = runBlocking { agentCall(input) }
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
}

/**
 * RAG result returned from a Koog call.
 */
data class RagResult(
    val output: String,
    val context: List<String> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Simple response DTO for mapping Dokimos results back into Koog flows.
 */
data class KoogEvaluationResponse(
    val pass: Boolean,
    val feedback: String,
    val metadata: Map<String, Any>
)
