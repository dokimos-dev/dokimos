package dev.dokimos.kotlin.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull

class ExtTest {


    @Test
    fun `EvalTestCase maps input actualOutput, outputContext and metadata`() {
        val testCase = EvalTestCase(
            input = "What is RAG?",
            actualOutput = "Retrieval-Augmented Generation",
            outputContext = listOf("Doc1", "Doc2"),
            metadata = mapOf("traceId" to mapOf("traceRef" to "abc123", "traceEpocMs" to "102029393832"))
        )

        assertThat(testCase.inputs()).containsEntry("input", "What is RAG?")
        assertThat(testCase.actualOutputs()).containsEntry("output", "Retrieval-Augmented Generation")
        assertThat(testCase.actualOutputs()).containsEntry("context", listOf("Doc1", "Doc2"))
        assertThat(testCase.metadata()).containsEntry(
            "traceId",
            mapOf("traceRef" to "abc123", "traceEpocMs" to "102029393832")
        )
    }

    @Test
    fun `EvalTestCase maps input outputs and metadata`() {
        val testCase = EvalTestCase(
            input = "What is RAG?",
            actualOutputs = mapOf("output" to "Retrieval-Augmented Generation", "context" to listOf("Doc1", "Doc2")),
            metadata = mapOf("traceId" to mapOf("traceRef" to "abc123", "traceEpocMs" to "102029393832"))
        )

        assertThat(testCase.inputs()).containsEntry("input", "What is RAG?")
        assertThat(testCase.actualOutputs()).containsEntry("output", "Retrieval-Augmented Generation")
        assertThat(testCase.actualOutputs()).containsEntry("context", listOf("Doc1", "Doc2"))
        assertThat(testCase.metadata()).containsEntry(
            "traceId",
            mapOf("traceRef" to "abc123", "traceEpocMs" to "102029393832")
        )
    }

    @Test
    fun `EvalResult helper builds core EvalResult`() {
        val result = EvalResult(
            name = "MyEval",
            score = 0.75,
            threshold = 0.7,
            reason = "Looks good"
        )

        assertThat(result.name()).isEqualTo("MyEval")
        assertThat(result.score()).isEqualTo(0.75)
        assertThat(result.threshold()).isEqualTo(0.7)
        assertThat(result.reason()).isEqualTo("Looks good")
        assertThat(result.success()).isTrue()
    }

    @Test
    fun `EvalResult helper builds core EvalResult with success`() {
        val result = EvalResult(
            name = "MyEval",
            score = 0.75,
            success = true,
            reason = "Looks good"
        )

        assertThat(result.name()).isEqualTo("MyEval")
        assertThat(result.score()).isEqualTo(0.75)
        assertNull(result.threshold())
        assertThat(result.reason()).isEqualTo("Looks good")
        assertThat(result.success()).isTrue()
    }

    @Test
    fun `EvalTestCase merges actualOutput into actualOutputs and preserves expectedOutputs and metadata`() {
        val testCase = EvalTestCase(
            input = "What is RAG?",
            actualOutput = "Retrieval-Augmented Generation",
            actualOutputs = mapOf("context" to listOf("Doc1", "Doc2"), "model" to "gpt"),
            expectedOutputs = mapOf("output" to "Retrieval-Augmented Generation"),
            metadata = mapOf("traceId" to "abc123")
        )

        assertThat(testCase.inputs()).containsEntry("input", "What is RAG?")
        assertThat(testCase.actualOutputs()).containsEntry("output", "Retrieval-Augmented Generation")
        assertThat(testCase.actualOutputs()).containsEntry("context", listOf("Doc1", "Doc2"))
        assertThat(testCase.actualOutputs()).containsEntry("model", "gpt")
        assertThat(testCase.expectedOutputs()).containsEntry("output", "Retrieval-Augmented Generation")
        assertThat(testCase.metadata()).containsEntry("traceId", "abc123")
    }

    @Test
    fun `EvalTestCase builder shortcut sets actualOutputs`() {
        val testCase = EvalTestCase(
            actualOutputs = mapOf(
                "output" to "answer",
                "context" to listOf("Doc1")
            )
        )

        assertThat(testCase.actualOutputs()).containsEntry("output", "answer")
        assertThat(testCase.actualOutputs()).containsEntry("context", listOf("Doc1"))
    }

}