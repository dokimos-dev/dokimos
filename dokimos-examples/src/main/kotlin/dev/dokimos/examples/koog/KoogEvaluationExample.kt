package dev.dokimos.examples.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import dev.dokimos.core.EvalTestCaseParam
import dev.dokimos.koog.asJudge
import dev.dokimos.koog.runBlocking
import dev.dokimos.kotlin.dsl.experiment
import dev.dokimos.kotlin.dsl.llmJudge
import kotlin.system.exitProcess

/**
 * A simple Koog + Dokimos evaluation example (no RAG).
 * Requires the `OPENAI_API_KEY` environment variable.
 */
fun main() {
    val apiKey = System.getenv("OPENAI_API_KEY")
    if (apiKey.isNullOrBlank()) {
        System.err.println("OPENAI_API_KEY not set; skipping Koog evaluation example")
        exitProcess(-1)
    }

    // 1. Set up Koog single-run agent (idiomatic creation)
    fun agent() = AIAgent(
        promptExecutor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Chat.GPT5Nano,
        maxIterations = 10
    )

    // 2-5. Build experiment with Kotlin DSL
    fun judgeAgent() = AIAgent(
        promptExecutor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Chat.GPT5Nano,
        maxIterations = 10
    )
    val judge = asJudge(::judgeAgent)

    val result = experiment {
        name = "Koog Customer Support Evaluation"

        dataset {
            name = "customer-support-koog"
            example {
                input = "What is your return policy?"
                expected = "We offer a 30-day money-back guarantee on all purchases."
            }
            example {
                input = "How long does shipping take?"
                expected = "Standard shipping takes 5-7 business days."
            }
            example {
                input = "Do you offer technical support?"
                expected = "Yes, we provide 24/7 technical support via email and chat."
            }
        }

        task { example ->
            val prompt = "Answer the following customer question concisely: ${example.input()}"
            val response = agent().runBlocking(prompt)
            mapOf("output" to response)
        }

        evaluators {
            exactMatch { threshold = 0.5 }

            llmJudge(judge) {
                name = "Answer Quality"
                criteria = "Is the answer helpful, accurate, and professionally worded?"
                params(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT)
                threshold = 0.7
            }

            llmJudge(judge) {
                name = "Conciseness"
                criteria = "Is the answer concise and to the point?"
                params(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT)
                threshold = 0.6
            }
        }
    }.run()

    // 6. Display results
    println("=".repeat(60))
    println("Koog Customer Support Evaluation Results")
    println("=".repeat(60))
    println("Pass rate: ${"%.0f".format(result.passRate() * 100)}%")
    println()

    println("Average Scores:")
    println("  Answer Quality: ${"%.2f".format(result.averageScore("Answer Quality"))}")
    println("  Conciseness: ${"%.2f".format(result.averageScore("Conciseness"))}")
    println()

    println("Detailed Results:")
    println("-".repeat(60))
    result.itemResults().forEach { item ->
        println()
        println("Question: ${item.example().input()}")
        println("Response: ${item.actualOutputs()["output"]}")
        println("Expected: ${item.example().expectedOutput()}")
        println("Status: ${if (item.success()) "✅ PASS" else "❌ FAIL"}")
        println("Scores:")
        item.evalResults().forEach { eval ->
            println("  • ${eval.name()}: ${"%.2f".format(eval.score())}${if (eval.success()) " ❌" else " ✅"}")
        }
    }
    println()
    println("=".repeat(60))
}
