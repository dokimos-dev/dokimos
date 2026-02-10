package dev.dokimos.kotlin.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExtTest {

    @Test
    fun `EvalTestCase maps input actualOutput, outputContext and metadata`() {

        val testCase2 = EvalTestCase(
            input = "Who founded Microsoft?",
            actualOutputs = mapOf("triples" to listOf(
                mapOf("subject" to "Bill Gates", "predicate" to "founded", "object" to "Microsoft")
            )),
            expectedOutputs = mapOf("relevantTriples" to listOf(
                mapOf("subject" to "Bill Gates", "predicate" to "founded", "object" to "Microsoft"),
                mapOf("subject" to "Paul Allen", "predicate" to "co-founded", "object" to "Microsoft")
            )))


        val testCase = EvalTestCase(
            input = "What is RAG?",
            actualOutput = "Retrieval-Augmented Generation",
            outputContext = listOf("Doc1", "Doc2"),
            metadata = mapOf("traceId" to mapOf("traceRef" to "abc123", "traceEpocMs" to "102029393832"))
        )

        assertThat(testCase.inputs()).containsEntry("input", "What is RAG?")
        assertThat(testCase.actualOutputs()).containsEntry("output", "Retrieval-Augmented Generation")
        assertThat(testCase.actualOutputs()).containsEntry("context", listOf("Doc1", "Doc2"))
        assertThat(testCase.metadata()).containsEntry("traceId", mapOf("traceRef" to "abc123", "traceEpocMs" to "102029393832"))
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
        assertThat(testCase.metadata()).containsEntry("traceId", mapOf("traceRef" to "abc123", "traceEpocMs" to "102029393832"))
    }
}