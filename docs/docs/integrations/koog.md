---
sidebar_position: 4
---

# Koog Integration

Dokimos works with [Koog](https://github.com/koog-ai/koog) so you can evaluate Koog agents and RAG pipelines using the Dokimos Kotlin DSL.

## Why Use This Integration?

**One-line judge conversion**: Turn any Koog `AIAgent` (or suspending call) into a Dokimos `JudgeLM` with `asJudge`.

**Kotlin-first experiments**: Build datasets, tasks, and evaluators with the Dokimos Kotlin DSL—no Java builders needed.

## Setup

Add the Koog integration dependency:

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-koog</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

### Gradle (Groovy DSL)

```groovy
implementation "dev.dokimos:dokimos-koog:${dokimosVersion}"
```

### Gradle (Kotlin DSL)

```kotlin
implementation("dev.dokimos:dokimos-koog:${dokimosVersion}")
```

## Basic Usage (Kotlin DSL)

Evaluate a Koog agent with Dokimos, using the Kotlin DSL throughout:

```kotlin
import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import dev.dokimos.koog.asJudge
import dev.dokimos.koog.runBlocking
import dev.dokimos.kotlin.dsl.experiment
import dev.dokimos.kotlin.dsl.llmJudge

fun main() {
    val apiKey = System.getenv("OPENAI_API_KEY") ?: throw IllegalStateException("OPENAI_API_KEY not set")

    // Generation agent
    fun agent() = AIAgent(
        promptExecutor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Chat.GPT5Nano,
        maxIterations = 10
    )

    // Judge agent -> JudgeLM
    fun judgeAgent() = AIAgent(
        promptExecutor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Chat.GPT5Nano,
        maxIterations = 10
    )
    val judge = asJudge(::judgeAgent)

    val result = experiment {
        name = "Koog Customer Support"

        dataset {
            name = "customer-support-koog"
            example { input = "What is your return policy?"; expected = "30-day money-back guarantee" }
            example { input = "How long does shipping take?"; expected = "5-7 business days" }
        }

        task { example ->
            val prompt = "Answer briefly: ${example.input()}"
            val response = agent().runBlocking(prompt)
            mapOf("output" to response)
        }

        evaluators {
            exactMatch { threshold = 0.5 }

            llmJudge(judge) {
                name = "Answer Quality"
                criteria = "Is the answer helpful and accurate?"
                threshold = 0.7
            }
        }
    }.run()

    println("Pass rate: ${"%.0f".format(result.passRate() * 100)}%")
}
```

## RAG Evaluation with Koog

For RAG pipelines, emit both the generated answer and retrieved context. You can build it manually (as below) or wrap your call with `ragTask` if you already have a function returning `RagResult`.

```kotlin
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

suspend fun main() {
    val apiKey = System.getenv("OPENAI_API_KEY") ?: return

    val baseEmbedder = LLMEmbedder(OpenAILLMClient(apiKey), OpenAIModels.Embeddings.TextEmbeddingAda002)
    val stringEmbedder = object : DocumentEmbedder<String> {
        override suspend fun embed(text: String) = baseEmbedder.embed(text)
        override fun diff(embedding1: Vector, embedding2: Vector) = baseEmbedder.diff(embedding1, embedding2)
    }

    val storage = InMemoryDocumentEmbeddingStorage(embedder = stringEmbedder).apply {
        store("We offer a 30-day money-back guarantee on all purchases.")
        store("Standard shipping takes 5-7 business days.")
        store("All products include a 1-year warranty.")
    }

    fun agent() = AIAgent(
        promptExecutor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Chat.GPT5Nano,
        maxIterations = 10
    )

    fun judgeAgent() = AIAgent(
        promptExecutor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Chat.GPT5Nano,
        maxIterations = 10
    )
    val judge = asJudge(::judgeAgent)

    experiment {
        name = "Koog RAG Evaluation"

        dataset {
            name = "customer-qa-rag-koog"
            example { input = "What is the refund policy?"; expected = "30-day money-back guarantee" }
            example { input = "How long does shipping take?"; expected = "5-7 business days" }
        }

        task { example ->
            val query = example.input()
            val contextDocs = runBlocking { storage.mostRelevantDocuments(query, count = 2).toList() }
            val prompt = """
                Answer using the context below.

                Context:
                ${'$'}{contextDocs.joinToString("\n")}

                Question: $query
                Answer:
            """.trimIndent()

            val answer = agent().runBlocking(prompt)
            mapOf(
                "output" to answer,
                "context" to contextDocs
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
}
```

## Best Practices

- Prefer Kotlin DSL (`experiment { ... }`, `llmJudge`, `faithfulness`) instead of Java builders when working in Kotlin modules.
- Keep judge and generation agents separate—use a stronger or more reliable model for judging when possible.
- Include context in outputs when evaluating RAG so `FaithfulnessEvaluator` can ground its checks.
- Use `runBlocking` from `dev.dokimos.koog` to call Koog agents inside tasks without leaking coroutines.

:::tip
See the Koog examples in `dokimos-examples/src/main/kotlin/dev/dokimos/examples/koog` for runnable Kotlin snippets.
:::
