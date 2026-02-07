package dev.dokimos.examples.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import dev.dokimos.core.*
import dev.dokimos.core.evaluators.ExactMatchEvaluator
import dev.dokimos.core.evaluators.LLMJudgeEvaluator
import dev.dokimos.koog.asJudge
import dev.dokimos.koog.runBlocking

/**
 * A simple Koog + Dokimos evaluation example (no RAG).
 * Requires the `OPENAI_API_KEY` environment variable.
 */
fun main() {
    val apiKey = System.getenv("OPENAI_API_KEY")
    if (apiKey.isNullOrBlank()) {
        System.err.println("OPENAI_API_KEY not set; skipping Koog evaluation example")
        return System.exit(-1)
    }

    // 1. Set up Koog single-run agent (idiomatic creation)
    fun agent() = AIAgent(
        promptExecutor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Chat.GPT5Nano,
        maxIterations = 10
    )


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

    // 3. Create task that calls Koog agent
    val task: Task =  { example ->
        val prompt = "Answer the following customer question concisely: " + example.input()
        val response = agent().runBlocking(prompt)
        mapOf("output" to response)
    }

    // 4. Set up evaluators using Koog's agent as judge
    fun judgeAgent() = AIAgent(
        promptExecutor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Chat.GPT5Nano,
        maxIterations = 10
    )
    val judge = asJudge(::judgeAgent)

    val evaluators: List<Evaluator> = listOf(
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
    val result: ExperimentResult = Experiment.builder()
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
