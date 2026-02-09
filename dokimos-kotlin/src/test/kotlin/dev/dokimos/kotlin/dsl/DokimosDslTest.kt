package dev.dokimos.kotlin.dsl

import dev.dokimos.core.EvalTestCaseParam
import dev.dokimos.core.Example
import dev.dokimos.core.JudgeLM
import dev.dokimos.core.MatchingStrategy
import dev.dokimos.kotlin.core.EvalTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DokimosDslTest {

    @Test
    fun `experiment DSL builds and runs`() {
        val result = experiment {
            name = "dsl-experiment"
            dataset {
                name = "ds"
                example {
                    input = "hello"
                    expected = "world"
                    metadata("traceId", "abc")
                }
            }

            task { example ->
                mapOf("output" to example.expectedOutput())
            }

            evaluators {
                exactMatch { threshold = 1.0 }
            }
        }.run()

        assertThat(result.passRate()).isEqualTo(1.0)
        assertThat(result.itemResults()).hasSize(1)
        val item = result.itemResults().first()
        assertThat(item.example().metadata()).containsEntry("traceId", "abc")
    }

    @Test
    fun `llm judge DSL wires judge and params`() {
        val judge = JudgeLM { _ -> """{"score":0.8,"reason":"fine"}""" }

        val evaluator = llmJudge(judge) {
            name = "Quality"
            criteria = "Check quality"
            params(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT)
            threshold = 0.7
            scoreRange(min = 0.0, max = 1.0)
        }

        val testCase = EvalTestCase("q", "a")
        val result = evaluator.evaluate(testCase)

        assertThat(result.name()).isEqualTo("Quality")
        assertThat(result.score()).isEqualTo(0.8)
        assertThat(result.success()).isTrue()
        assertThat(result.reason()).contains("fine")
    }

    @Test
    fun `example DSL assignment populates maps`() {
        val ex: Example = example {
            input = "prompt"
            expected = "answer"
            input("lang", "en")
            expected("alt", "alt-answer")
            metadata("source", "dsl")
        }

        assertThat(ex.input()).isEqualTo("prompt")
        assertThat(ex.expectedOutput()).isEqualTo("answer")
        assertThat(ex.inputs()).containsEntry("lang", "en")
        assertThat(ex.expectedOutputs()).containsEntry("alt", "alt-answer")
        assertThat(ex.metadata()).containsEntry("source", "dsl")
    }

    @Test
    fun `hallucination DSL evaluates`() {
        val judge = JudgeLM { _ -> """[{"verdict":"yes","reason":"supported"}]""" }

        val evaluator = hallucination(judge) {
            contextKey = "ctx"
            threshold = 0.5
        }

        val testCase = dev.dokimos.core.EvalTestCase.builder()
            .input("question")
            .actualOutput("output", "answer")
            .actualOutput("ctx", "context text")
            .build()

        val result = evaluator.evaluate(testCase)

        assertThat(result.success()).isTrue()
        assertThat(result.name()).isEqualTo("Hallucination")
    }

    @Test
    fun `faithfulness DSL wires judge and params`() {
        val judge = JudgeLM { prompt ->
            when {
                prompt.contains("Extract the factual truths") -> "[\"Fact A\", \"Fact B\"]"
                prompt.contains("break it down into individual claims", ignoreCase = true) -> "[\"Fact A\"]"
                prompt.contains(
                    "Compare each CLAIM",
                    ignoreCase = true
                ) -> """[{"verdict":"Yes","reasoning":"Matches"}]"""

                prompt.contains("Summarize the faithfulness", ignoreCase = true) -> "All claims supported."
                else -> "{}"
            }
        }

        val evaluator = faithfulness(judge) {
            contextKey = "ctx"
            params(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT)
        }

        val testCase = EvalTestCase(
            input = "question",
            actualOutputs = mapOf("output" to "answer", "ctx" to "context content")
        )

        val result = evaluator.evaluate(testCase)

        assertThat(result.score()).isEqualTo(1.0)
        assertThat(result.success()).isTrue()
        assertThat(result.reason()).contains("All claims")
    }

    @Test
    fun `contextual relevance DSL handles contexts and strict mode flag`() {
        val judge = JudgeLM { prompt ->
            when {
                prompt.contains("first chunk", ignoreCase = true) -> """{"score":0.9,"reason":"high"}"""
                prompt.contains("second chunk", ignoreCase = true) -> """{"score":0.1,"reason":"low"}"""
                prompt.contains("Summarize the contextual relevance", ignoreCase = true) -> "Summary text"
                else -> """{"score":0.5,"reason":"default"}"""
            }
        }

        val evaluator = contextualRelevance(judge) {
            retrievalContextKey = "chunks"
            strictMode = false
        }

        val testCase = EvalTestCase(
            input = "query",
            actualOutputs = mapOf("chunks" to listOf("first chunk", "second chunk"))
        )

        val result = evaluator.evaluate(testCase)

        assertThat(result.score()).isEqualTo(0.5)
        assertThat(result.success()).isTrue()
        assertThat(result.reason()).contains("Summary")
    }

    @Test
    fun `precision and recall DSL evaluate with matching strategy`() {
        val precisionEvaluator = precision {
            retrievedKey = "retr"
            expectedKey = "rel"
            matchingStrategy = MatchingStrategy.caseInsensitive()
        }

        val recallEvaluator = recall {
            retrievedKey = "retr"
            expectedKey = "rel"
            matchingStrategy = MatchingStrategy.caseInsensitive()
        }

        val testCase = EvalTestCase(
            input = "query",
            actualOutputs = mapOf("retr" to listOf("DocA", "DocB")),
            expectedOutput = mapOf("rel" to listOf("doca", "docC"))
        )

        val precisionResult = precisionEvaluator.evaluate(testCase)
        val recallResult = recallEvaluator.evaluate(testCase)

        assertThat(precisionResult.score()).isEqualTo(0.5)
        assertThat(recallResult.score()).isEqualTo(0.5)
        assertThat(precisionResult.success()).isTrue()
        assertThat(recallResult.success()).isTrue()
    }
}
