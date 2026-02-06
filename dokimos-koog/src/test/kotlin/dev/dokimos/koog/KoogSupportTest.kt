package dev.dokimos.koog

import dev.dokimos.core.EvalResult
import dev.dokimos.core.Example
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.AIAgent.Companion.State.Finished
import ai.koog.agents.core.agent.AIAgent.Companion.State.NotStarted
import ai.koog.agents.testing.client.CapturingLLMClient
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import kotlinx.coroutines.runBlocking

class KoogSupportTest {

    @Test
    fun `asJudge with suspend lambda delegates prompt and returns text`() {
        var capturedPrompt: String? = null

        val judge = KoogSupport.asJudge { prompt ->
            capturedPrompt = prompt
            "judge-output"
        }

        val result = judge.generate("evaluate this")

        assertThat(capturedPrompt).isEqualTo("evaluate this")
        assertThat(result).isEqualTo("judge-output")
    }

    @Test
    fun `asJudge rejects blank responses`() {
        val judge = KoogSupport.asJudge { "" }

        assertThatThrownBy { judge.generate("prompt") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("blank")
    }

    @Test
    fun `toTestCase maps input output and context`() {
        val testCase = KoogSupport.toTestCase(
                input = "What is RAG?",
                output = "Retrieval-Augmented Generation",
                context = listOf("Doc1", "Doc2"),
                metadata = mapOf("traceId" to "abc123")
        )

        assertThat(testCase.inputs()).containsEntry(KoogSupport.INPUT_KEY, "What is RAG?")
        assertThat(testCase.actualOutputs()).containsEntry(KoogSupport.OUTPUT_KEY, "Retrieval-Augmented Generation")
        assertThat(testCase.actualOutputs()).containsEntry(KoogSupport.CONTEXT_KEY, listOf("Doc1", "Doc2"))
        assertThat(testCase.metadata()).containsEntry("traceId", "abc123")
    }

    @Test
    fun `toEvaluationResponse maps score reason and metadata`() {
        val result = EvalResult(
                "faithfulness",
                0.92,
                true,
                "Response grounded in documents",
                mapOf("latencyMs" to 120L)
        )

        assertThat(result.success).isTrue()
        assertThat(result.reason).isEqualTo("Response grounded in documents")
        assertThat(result.score).isEqualTo(0.92)
        assertThat(result.metadata).containsEntry("latencyMs", 120L)
    }

    @Test
    fun `task uses example input and returns output key`() {
        val task = KoogSupport.task { input -> "$input processed" }

        val example = Example.builder()
                .input(KoogSupport.INPUT_KEY, "hello")
                .build()

        val outputs = task.run(example)

        assertThat(outputs).containsEntry(KoogSupport.OUTPUT_KEY, "hello processed")
    }

    @Test
    fun `ragTask returns output context and metadata`() {
        val rag = KoogSupport.ragTask { input ->
            RagResult(
                    output = "$input answer",
                    context = listOf("ctx1", "ctx2"),
                    metadata = mapOf("latencyMs" to 12L)
            )
        }

        val example = Example.builder()
                .input(KoogSupport.INPUT_KEY, "question")
                .build()

        val outputs = rag.run(example)

        assertThat(outputs).containsEntry(KoogSupport.OUTPUT_KEY, "question answer")

        @Suppress("UNCHECKED_CAST")
        val context = outputs[KoogSupport.CONTEXT_KEY] as List<String>
        assertThat(context).containsExactly("ctx1", "ctx2")

        assertThat(outputs).containsEntry("latencyMs", 12L)
    }

    @Test
    fun `asJudge with agent runner delegates to agent`() {
        val client = CapturingLLMClient()
        val model = LLModel(object : LLMProvider("provider", "Provider") {}, "model-id", emptyList(), 0, null)
        val agent = BlockingAgent(model, client) { prompt -> "$prompt -> judged" }

        val judge = KoogSupport.asJudge(agent) { a, prompt ->
            runBlocking { a.run(prompt) }
        }

        val response = judge.generate("evaluate me")

        assertThat(response).isEqualTo("evaluate me -> judged")
        assertThat(agent.lastPrompt).isEqualTo("evaluate me")
        assertThat(client.lastExecutedModel).isEqualTo(model)
        assertThat(client.lastExecutedPrompt).isNotNull
    }

    @Test
    fun `task with agent runner delegates to agent`() {
        val client = CapturingLLMClient()
        val model = LLModel(object : LLMProvider("provider", "Provider") {}, "model-id", emptyList(), 0, null)
        val agent = BlockingAgent(model, client) { input -> "$input -> reply" }

        val task = KoogSupport.task(agent) { a, input ->
            runBlocking { a.run(input) }
        }

        val example = Example.builder()
                .input(KoogSupport.INPUT_KEY, "hello")
                .build()

        val outputs = task.run(example)

        assertThat(outputs).containsEntry(KoogSupport.OUTPUT_KEY, "hello -> reply")
        assertThat(agent.lastPrompt).isEqualTo("hello")
        assertThat(client.lastExecutedModel).isEqualTo(model)
    }
}

private class BlockingAgent(
        override val agentConfig: AIAgentConfigBase,
        private val client: CapturingLLMClient,
        private val handler: (String) -> String
) : AIAgent<String, String> {

    constructor(model: LLModel, client: CapturingLLMClient, handler: (String) -> String) : this(
            AIAgentConfig(
                    prompt = prompt("capture") {},
                    model = model,
                    maxAgentIterations = 1
            ),
            client,
            handler
    )

    override val id: String = "test-agent"
    var lastPrompt: String? = null
    private var lastResult: String? = null

    override suspend fun run(input: String): String {
        lastPrompt = input
        client.lastExecutedPrompt = prompt("capture") {}
        client.lastExecutedModel = agentConfig.model
        return handler(input).also { lastResult = it }
    }

    override suspend fun close() { /* no-op */ }

    override suspend fun getState(): AIAgent.Companion.State<String> =
            lastResult?.let { Finished(it) } ?: NotStarted()
}
