---
sidebar_position: 4
---

# Koog Integration

Evaluate [Koog](https://github.com/koog-ai/koog) agents and RAG pipelines with the Dokimos Kotlin DSL, all in Kotlin.

This page shows you how to turn a Koog agent into a judge, run an experiment over a dataset, score answers, run agent calls without blocking a thread, and evaluate a RAG pipeline.

## What this integration gives you

**One-line judge conversion.** Turn any Koog `AIAgent` (or any suspending call) into a Dokimos `JudgeLM` with `asJudge`.

**Kotlin-first experiments.** Build datasets, tasks, and evaluators with the Dokimos Kotlin DSL. You do not need the Java builders.

:::tip
A `typedTask<T> { ... }` can return a Kotlin data class. Compare it with `StructuralMatchEvaluator` and read it back with the reified `actualOutputAs<T>()`. See the [Structured & Typed Data](../evaluation/structured-typed-data.md) hub.
:::

## Setup

Add the Koog integration dependency.

Maven:

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-koog</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

Gradle (Groovy DSL):

```groovy
implementation "dev.dokimos:dokimos-koog:${dokimosVersion}"
```

Gradle (Kotlin DSL):

```kotlin
implementation("dev.dokimos:dokimos-koog:${dokimosVersion}")
```

## Run your first evaluation

This example evaluates a Koog agent end to end with the Kotlin DSL. Copy it, set `OPENAI_API_KEY`, and run `main`.

It does four things:

1. Builds a generation agent and a separate judge agent.
2. Wraps the judge agent as a `JudgeLM` with `asJudge`.
3. Defines a two-example dataset and a task that calls the agent.
4. Scores answers with `exactMatch` and an LLM judge, then prints the pass rate.

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

    // Generation agent.
    fun agent() = AIAgent(
        promptExecutor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Chat.GPT5Nano,
        maxIterations = 10
    )

    // Judge agent, wrapped as a JudgeLM.
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
            example {
                input = "What is your return policy?"
                expected = "30-day money-back guarantee"
            }
            example {
                input = "How long does shipping take?"
                expected = "5-7 business days"
            }
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

## Run agent calls without blocking a thread

The example above uses `runBlocking`, which holds one thread per example. To keep many agent calls in flight at once, adapt your `suspend` call into a Dokimos `AsyncTask`, wire it with `asyncTask(...)`, and cap concurrency with `parallelism`.

You have two adapters:

- `asTextTask` for the common case. The suspend body receives the example `input()` and returns the model response. Dokimos stores it under the `"output"` key. A blank response throws `IllegalArgumentException`.
- `asTask` for the full output map (for example, RAG context alongside the answer). The suspend body receives the full `Example` and returns a `TaskResult`.

Each invocation launches the suspend body on `Dispatchers.IO` and bridges the coroutine to a `CompletableFuture` with the kotlinx-coroutines `future` builder. A suspend exception becomes an exceptionally completed future, which the experiment isolates as a failed item while the run continues.

Use `asTextTask` when you only need the answer text:

```kotlin
import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import dev.dokimos.koog.asTextTask
import dev.dokimos.kotlin.dsl.experiment

fun agent() = AIAgent(
    promptExecutor = simpleOpenAIExecutor(apiKey),
    llmModel = OpenAIModels.Chat.GPT5Nano,
    maxIterations = 10
)

val task = asTextTask { input -> agent().run("Answer briefly: $input") }

val result = experiment {
    name = "Koog Async"
    dataset(dataset)
    asyncTask(task)
    parallelism = 8
    evaluators {
        llmJudge(judge) {
            name = "Answer Quality"
            criteria = "Is the answer helpful and accurate?"
            threshold = 0.7
        }
    }
}.run()
```

Use `asTask` when you need the full output map. Its suspend body receives the full `Example` and returns a `TaskResult`:

```kotlin
import dev.dokimos.core.TaskResult
import dev.dokimos.koog.asTask

val ragTask = asTask { example ->
    val query = example.input()
    val contextDocs = storage.mostRelevantDocuments(query, count = 2).toList()
    val answer = agent().run(buildPrompt(query, contextDocs))
    TaskResult.of(
        mapOf(
            "output" to answer,
            "context" to contextDocs
        )
    )
}
```

:::note
Both `asTask` and `asTextTask` default the coroutine scope to `GlobalScope`, so the launched coroutine has no parent lifecycle to inherit. To opt into structured concurrency, pass your own scope as the first argument: `asTextTask(scope = myScope) { input -> ... }`.
:::

## Evaluate a RAG pipeline

For RAG, return both the generated answer and the retrieved context. Put the answer under `"output"` and the context under `"context"`. The faithfulness evaluator reads the context key to ground its checks.

This example embeds three documents, retrieves the top matches per query, answers with that context, and scores the answer for quality and faithfulness.

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
    val apiKey = System.getenv("OPENAI_API_KEY") ?: throw IllegalStateException("OPENAI_API_KEY not set")

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
            example {
                input = "What is the refund policy?"
                expected = "30-day money-back guarantee"
            }
            example {
                input = "How long does shipping take?"
                expected = "5-7 business days"
            }
        }

        task { example ->
            val query = example.input()
            val contextDocs = runBlocking { storage.mostRelevantDocuments(query, count = 2).toList() }
            val prompt = """
                Answer using the context below.

                Context:
                ${contextDocs.joinToString("\n")}

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

## Best practices

- In Kotlin modules, use the Kotlin DSL (`experiment { ... }`, `llmJudge`, `faithfulness`) instead of the Java builders.
- Keep the judge agent separate from the generation agent. Use a stronger model for judging when you can.
- For RAG, include the context in the output map so `FaithfulnessEvaluator` can ground its checks.
- Call Koog agents inside tasks with `runBlocking` from `dev.dokimos.koog` so you do not leak coroutines.

:::tip
See the Koog examples in `dokimos-examples/src/main/kotlin/dev/dokimos/examples/koog` for runnable Kotlin snippets.
:::
