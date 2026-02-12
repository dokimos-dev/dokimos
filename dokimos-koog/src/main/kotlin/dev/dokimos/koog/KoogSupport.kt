package dev.dokimos.koog

import ai.koog.agents.core.agent.AIAgent
import dev.dokimos.core.JudgeLM
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
 * Creates a [JudgeLM] from a suspend function that accepts a prompt and returns text.
 * 
 * This function wraps any suspend function that processes a string prompt into a Dokimos [JudgeLM] judge.
 * The function is executed in a blocking coroutine context each time the judge is invoked.
 * 
 * @param agentCall A suspend function that accepts a string prompt and returns a string response.
 *                  The response must not be blank.
 * @return A [JudgeLM] that can be used as a judge in Dokimos evaluations.
 * @throws IllegalArgumentException if the judge response content is blank.
 *
 */
fun asJudge(agentCall: suspend (String) -> String): JudgeLM {
    return JudgeLM { prompt ->
        val content = runBlocking { agentCall(prompt) }
        require(content.isNotBlank()) { "Judge response content was blank" }
        content
    }
}

/**
 * Creates a [JudgeLM] from a Koog agent factory.
 * 
 * This function wraps a Koog [AIAgent] factory (that produces agents accepting and returning [String])
 * into a Dokimos [JudgeLM] judge. Each time the judge is invoked, it creates a fresh agent instance
 * via the provided factory function and runs it with the given prompt.
 * 
 * @param agent A factory function that produces an [AIAgent] instance capable of processing
 *              string input and returning string output.
 * @return A [JudgeLM] that can be used as a judge in Dokimos evaluations.
 * 
 * @see asJudge for creating a judge from a direct suspend function
 */
fun asJudge(agent:() ->  AIAgent<String, String>): JudgeLM {
     return asJudge { input -> agent().run(input) }
}


/**
 * Executes the `run` method of the `AIAgent` in a blocking coroutine context.
 *
 * @param input The input of type `I` to be processed by the agent.
 * @param context The coroutine context to execute this method in. Default is `Dispatchers.Default`.
 * @return The output of type `O` produced by the agent after processing the input.
 */
fun <I, O> AIAgent<I, O>.runBlocking(input:I, context: CoroutineContext = Dispatchers.Default):O = runBlocking(context) {
    run(input)
}


