package dev.dokimos.koog

import dev.dokimos.core.EvalResult
import dev.dokimos.core.Example
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

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
}
