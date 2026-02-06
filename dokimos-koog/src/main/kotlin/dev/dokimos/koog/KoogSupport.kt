package dev.dokimos.koog

import ai.koog.agents.core.agent.AIAgent
import dev.dokimos.core.EvalResult
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.JudgeLM
import dev.dokimos.core.Task

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
    fun asJudge(agent: AIAgent<String, String>, runner: (AIAgent<String, String>, String) -> String): JudgeLM =
        asJudge { prompt -> runner(agent, prompt) }

    /**
     * Creates a [JudgeLM] from any blocking call that accepts a prompt and
     * returns text.
     */
    fun asJudge(agentCall: (String) -> String): JudgeLM {
        return JudgeLM { prompt ->
            requireNotNull(prompt) { "Prompt cannot be null" }
            val content = agentCall(prompt)
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
     * Creates a simple [Task] that runs a Koog agent and maps its string output
     * into Dokimos output keys.
     */
    fun task(agent: AIAgent<String, String>, runner: (AIAgent<String, String>, String) -> String): Task =
        task { input -> runner(agent, input) }

    /**
     * Creates a [Task] from any blocking call that accepts the example input
     * and returns text.
     */
    fun task(agentCall: (String) -> String): Task {
        return Task { example ->
            val input = example.inputs()[INPUT_KEY] as? String ?: example.input()
            val output = agentCall(input)
            mapOf(OUTPUT_KEY to output)
        }
    }

    /**
     * Creates a RAG-oriented [Task] using a suspending Koog call that returns
     * both the answer and its retrieved context.
     */
    fun ragTask(agentCall: (String) -> RagResult): Task {
        return ragTask(agentCall, INPUT_KEY, OUTPUT_KEY, CONTEXT_KEY)
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
}

/**
 * RAG result returned from a Koog call.
 */
data class RagResult(
    val output: String,
    val context: List<String> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)
