@file:Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")

package dev.dokimos.examples.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.embeddings.base.Vector
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.rag.base.mostRelevantDocuments
import ai.koog.rag.vector.DocumentEmbedder
import ai.koog.rag.vector.InMemoryDocumentEmbeddingStorage
import dev.dokimos.core.EvalTestCaseParam
import dev.dokimos.koog.asJudge
import dev.dokimos.koog.runBlocking
import dev.dokimos.kotlin.dsl.experiment
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * RAG evaluation with Koog using in-memory embeddings (no files on disk).
 * Mirrors the Spring AI RAG example but uses the Dokimos Kotlin DSL and Koog rag-base.
 * Requires `OPENAI_API_KEY`.
 */
suspend fun main() {
    val apiKey = System.getenv("OPENAI_API_KEY")
    if (apiKey.isNullOrBlank()) {
        System.err.println("OPENAI_API_KEY not set; skipping Koog RAG example")
        exitProcess(-1)
    }

    // Embeddings (all in-memory, string documents)
    val baseEmbedder = LLMEmbedder(OpenAILLMClient(apiKey), OpenAIModels.Embeddings.TextEmbeddingAda002)
    val stringEmbedder = object : DocumentEmbedder<String> {
        override suspend fun embed(text: String) = baseEmbedder.embed(text)
        override fun diff(embedding1: Vector, embedding2: Vector): Double =
            baseEmbedder.diff(embedding1, embedding2)
    }

    val storage = InMemoryDocumentEmbeddingStorage(embedder = stringEmbedder).apply {
        store("We offer a 30-day money-back guarantee on all purchases. No questions asked.")
        store("Standard shipping takes 5-7 business days. Express shipping is available for 2-3 days.")
        store("All products come with a 1-year manufacturer warranty. Extended warranties available for purchase.")
    }

    // Generation agent
    fun agent() = AIAgent(
        promptExecutor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Chat.GPT5Nano,
        maxIterations = 10
    )

    // Judge agent
    fun judgeAgent() = AIAgent(
        promptExecutor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Chat.GPT5Nano,
        maxIterations = 10
    )

    val judge = asJudge(::judgeAgent)

    val result = experiment {
        name = "Koog RAG Evaluation"

        dataset {
            name = "customer-qa-rag-koog"
            example {
                input = "What is the refund policy?"
                expected = "30-day money-back guarantee"
            }
            example {
                input = "How long does shipping take?"
                expected = "5-7 business days"
            }
            example {
                input = "What warranty do you offer?"
                expected = "1-year warranty"
            }
        }

        task { example ->
            // Retrieve top-2 relevant docs
            val query = example.input()
            val ranked: List<String> =  runBlocking {  storage.mostRelevantDocuments(query, count = 2).toList() }

            val contextText = ranked.joinToString("\n")
            val prompt = """
                Answer the following question based on the provided context.

                Context:
                $contextText

                Question: $query

                Answer:
            """.trimIndent()

            val response = agent().runBlocking(prompt)

            mapOf(
                "output" to response,
                "context" to ranked
            )
        }

        evaluators {
            llmJudge(judge) {
                name = "Answer Quality"
                criteria = "Is the answer accurate and helpful?"
                params(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT)
                threshold = 0.7
            }

            faithfulness(judge) {
                name = "Faithfulness"
                contextKey = "context"
                threshold = 0.8
            }
        }
    }.run()

    // Print results
    println("=".repeat(70))
    println("Koog RAG Evaluation Results")
    println("=".repeat(70))
    println("Overall Pass Rate: ${"%.0f".format(result.passRate() * 100)}%")
    println()

    println("Average Scores:")
    println("  Answer Quality:    ${"%.2f".format(result.averageScore("Answer Quality"))}")
    println("  Faithfulness:      ${"%.2f".format(result.averageScore("Faithfulness"))}")
    println()

    println("Detailed Results:")
    println("-".repeat(70))
    result.itemResults().forEach { item ->
        println()
        println("Question: ${item.example().input()}")
        println("Expected: ${item.example().expectedOutput()}")
        println()
        val ctx = item.actualOutputs()["context"] as? List<*> ?: emptyList<Any>()
        println("Retrieved Context:")
        ctx.forEach { c ->
            val text = c?.toString().orEmpty()
            val snippet = if (text.length <= 80) text else text.take(80) + "..."
            println("  • $snippet")
        }
        println()
        println("Generated Answer: ${item.actualOutputs()["output"]}")
        println("Status: ${if (item.success()) "✅ PASS" else "❌ FAIL"}")
        println("Evaluation Scores:")
        item.evalResults().forEach { eval ->
            println("  • ${eval.name()}: ${"%.2f".format(eval.score())}${if (eval.success()) " ✅" else " ❌"}")
        }
    }
    println()
    println("=".repeat(70))
}
