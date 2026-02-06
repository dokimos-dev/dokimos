package dev.dokimos.examples.koog

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.dsl.user
import ai.koog.prompt.dsl.text
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import dev.dokimos.core.*
import dev.dokimos.core.evaluators.ExactMatchEvaluator
import dev.dokimos.core.evaluators.LLMJudgeEvaluator
import dev.dokimos.koog.KoogSupport

/**
 * A simple Koog + Dokimos evaluation example (no RAG).
 * Requires the `OPENAI_API_KEY` environment variable.
 */
object KoogEvaluationExample {

    @JvmStatic
    fun main(args: Array<String>) {
        val apiKey = System.getenv("OPENAI_API_KEY")
        if (apiKey.isNullOrBlank()) {
            System.err.println("OPENAI_API_KEY not set; skipping Koog evaluation example")
            return
        }

        // 1. Set up Koog LLM client and executor
        val model = LLModel(
            object : LLMProvider("openai", "OpenAI") {},
            "gpt-4o-mini",
            emptyList(),
            128_000,
            null
        )

        val client = OpenAILLMClient(
            apiKey,
            OpenAIClientSettings(),
            null,
            null,
            null
        )

        val executor = SingleLLMPromptExecutor(client)

        fun runKoog(promptText: String): String {
            val builtPrompt = prompt("qa") {
                user { text(promptText) }
            }
            val responses = executor.execute(builtPrompt, model, emptyList())
            val first = responses.firstOrNull()?.firstOrNull()
            val textContent = first?.parts()?.joinToString(separator = "") { part -> part.toString() } ?: ""
            return textContent.ifBlank { "" }
        }

        // 2. Create dataset
        val dataset = Dataset.builder()
            .name("customer-support-koog")
            .addExample(Example.of(
                "What is your return policy?",
                "We offer a 30-day money-back guarantee on all purchases."))
            .addExample(Example.of(
                "How long does shipping take?",
                "Standard shipping takes 5-7 business days."))
            .addExample(Example.of(
                "Do you offer technical support?",
                "Yes, we provide 24/7 technical support via email and chat."))
            .build()

        // 3. Task using Koog executor
        val task = KoogSupport.task { input ->
            runKoog("Answer the following customer question concisely: $input")
        }

        // 4. Judge using same model/executor
        val judge = KoogSupport.asJudge { prompt -> runKoog(prompt) }

        val evaluators = listOf(
            ExactMatchEvaluator.builder()
                .threshold(0.5)
                .build(),
            LLMJudgeEvaluator.builder()
                .name("Answer Quality")
                .judge(judge)
                .criteria("Is the answer helpful, accurate, and professionally worded?")
                .evaluationParams(listOf(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
                .threshold(0.7)
                .build(),
            LLMJudgeEvaluator.builder()
                .name("Conciseness")
                .judge(judge)
                .criteria("Is the answer concise and to the point?")
                .evaluationParams(listOf(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
                .threshold(0.6)
                .build()
        )

        // 5. Run experiment
        val result = Experiment.builder()
            .name("Koog Customer Support Evaluation")
            .dataset(dataset)
            .task(task)
            .evaluators(evaluators)
            .build()
            .run()

        // 6. Display results
        println("=".repeat(60))
        println("Koog Customer Support Evaluation Results")
        println("=".repeat(60))
        println("Pass rate: ${"%0.0f".format(result.passRate() * 100)}%")
        println()

        println("Average Scores:")
        println("  Answer Quality: ${"%0.2f".format(result.averageScore("Answer Quality"))}")
        println("  Conciseness: ${"%0.2f".format(result.averageScore("Conciseness"))}")
        println()

        println("Detailed Results:")
        println("-".repeat(60))
        result.itemResults().forEach { item ->
            println()
            println("Question: ${item.example().input()}")
            println("Response: ${item.actualOutputs()["output"]}")
            println("Expected: ${item.example().expectedOutput()}")
            println("Status: ${if (item.success()) "✓ PASS" else "✗ FAIL"}")
            println("Scores:")
            item.evalResults().forEach { eval ->
                println("  • ${eval.name()}: ${"%0.2f".format(eval.score())}${if (eval.success()) " ✓" else " ✗"}")
            }
        }
        println()
        println("=".repeat(60))
    }
}
