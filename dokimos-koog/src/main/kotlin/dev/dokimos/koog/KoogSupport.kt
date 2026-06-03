package dev.dokimos.koog

import ai.koog.agents.core.agent.AIAgent
import dev.dokimos.core.AsyncTask
import dev.dokimos.core.Example
import dev.dokimos.core.JudgeLM
import dev.dokimos.core.TaskResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
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
fun asJudge(agentCall: suspend (String) -> String): JudgeLM = JudgeLM { prompt ->
    val content = runBlocking { agentCall(prompt) }
    require(content.isNotBlank()) { "Judge response content was blank" }
    content
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
fun asJudge(agent: () -> AIAgent<String, String>): JudgeLM = asJudge { input -> agent().run(input) }

/**
 * Adapts a `suspend` agent call into a Dokimos [AsyncTask], so a Koog agent can drive an experiment
 * through [dev.dokimos.core.Experiment.Builder.asyncTask] without [runBlocking] holding a thread per
 * example.
 *
 * Each invocation launches the suspend body on the given [scope] (default [GlobalScope]) using the
 * [Dispatchers.IO] dispatcher, and bridges the coroutine to a [java.util.concurrent.CompletableFuture]
 * via the kotlinx-coroutines `future` builder. A suspend exception surfaces as an exceptionally
 * completed future, which the experiment isolates as a failed item while the run continues.
 *
 * [GlobalScope] is the default (the launched coroutine has no parent lifecycle to inherit); pass your
 * own [scope] to opt into structured concurrency.
 *
 * The suspend body receives the full [Example] and returns a [TaskResult] (use [TaskResult.of] when
 * there are no call metrics).
 *
 * @param scope the coroutine scope used to launch each invocation. Defaults to [GlobalScope].
 * @param agentCall a suspend function that produces a [TaskResult] for an [Example].
 * @return an [AsyncTask] suitable for the non-blocking experiment execution path.
 * @see asTask for adapting a suspend call that returns the model output text directly
 */
@OptIn(DelicateCoroutinesApi::class)
fun asTask(scope: CoroutineScope = GlobalScope, agentCall: suspend (Example) -> TaskResult): AsyncTask =
    AsyncTask { example ->
        scope.future(Dispatchers.IO) { agentCall(example) }
    }

/**
 * Adapts a `suspend` agent call that returns the model output text into a Dokimos [AsyncTask].
 *
 * Convenience overload of [asTask] for the common case: the suspend body receives the example
 * [input][Example.input] string and returns the model response, which is stored under [OUTPUT_KEY]
 * in a metrics-free [TaskResult]. A blank response throws [IllegalArgumentException], surfacing as an
 * exceptionally completed future that the experiment isolates as a failed item.
 *
 * @param scope the coroutine scope used to launch each invocation. Defaults to [GlobalScope].
 * @param agentCall a suspend function that accepts the example input and returns the model response.
 *                  The response must not be blank.
 * @return an [AsyncTask] suitable for the non-blocking experiment execution path.
 * @throws IllegalArgumentException if the agent response content is blank.
 */
@OptIn(DelicateCoroutinesApi::class)
fun asTextTask(scope: CoroutineScope = GlobalScope, agentCall: suspend (String) -> String): AsyncTask =
    asTask(scope) { example ->
        val content = agentCall(example.input())
        require(content.isNotBlank()) { "Agent response content was blank" }
        TaskResult.of(mapOf(OUTPUT_KEY to content))
    }

/**
 * Executes the `run` method of the `AIAgent` in a blocking coroutine context.
 *
 * @param input The input of type `I` to be processed by the agent.
 * @param context The coroutine context to execute this method in. Default is `Dispatchers.Default`.
 * @return The output of type `O` produced by the agent after processing the input.
 */
fun <I, O> AIAgent<I, O>.runBlocking(input: I, context: CoroutineContext = Dispatchers.Default): O =
    runBlocking(context) {
        run(input)
    }
