package dev.dokimos.kotlin.dsl.evaluators

import dev.dokimos.core.EvalResult
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.EvalTestCaseParam
import dev.dokimos.core.Evaluator
import dev.dokimos.core.JudgeLM
import dev.dokimos.core.MatchingStrategy
import dev.dokimos.kotlin.core.EvalTestCase
import dev.dokimos.kotlin.dsl.contextualRelevance
import dev.dokimos.kotlin.dsl.evaluators
import dev.dokimos.kotlin.dsl.faithfulness
import dev.dokimos.kotlin.dsl.hallucination
import dev.dokimos.kotlin.dsl.llmJudge
import dev.dokimos.kotlin.dsl.precision
import dev.dokimos.kotlin.dsl.recall
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EvaluatorDslTest {

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
    fun `hallucination DSL evaluates`() {
        val judge = JudgeLM { _ -> """[{"verdict":"yes","reason":"supported"}]""" }

        val evaluator = hallucination(judge) {
            contextKey = "ctx"
            threshold = 0.5
        }

        val testCase = EvalTestCase(
            input = "question",
            actualOutputs = mapOf("output" to "answer", "ctx" to "context text")
        )

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
                prompt.contains("Compare each CLAIM", ignoreCase = true) -> """[{"verdict":"Yes","reasoning":"Matches"}]"""
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
    fun `recall DSL evaluate with matching strategy`() {
        val recallEvaluator = recall {
            retrievedKey = "retr"
            expectedKey = "rel"
            matchingStrategy = MatchingStrategy.caseInsensitive()
        }

        val testCase = EvalTestCase(
            input = "query",
            actualOutputs = mapOf("retr" to listOf("DocA", "DocB")),
            expectedOutputs = mapOf("rel" to listOf("doca", "docC"))
        )

        val recallResult = recallEvaluator.evaluate(testCase)

        assertThat(recallResult.score()).isEqualTo(0.5)
        assertThat(recallResult.success()).isTrue()
    }

    @Test
    fun `precision DSL evaluate with matching strategy`() {
        val precisionEvaluator = precision {
            retrievedKey = "retr"
            expectedKey = "rel"
            matchingStrategy = MatchingStrategy.caseInsensitive()
        }

        val testCase = EvalTestCase(
            input = "query",
            actualOutputs = mapOf("retr" to listOf("DocA", "DocB")),
            expectedOutputs = mapOf("rel" to listOf("doca", "docC"))
        )

        val precisionResult = precisionEvaluator.evaluate(testCase)

        assertThat(precisionResult.score()).isEqualTo(0.5)
        assertThat(precisionResult.success()).isTrue()
    }

    @Test
    fun `evaluators DSL builds every evaluator method`() {
        val judge = JudgeLM { "{}" }

        val customEvaluator = object : Evaluator {
            override fun evaluate(testCase: EvalTestCase): EvalResult =
                EvalResult.builder()
                    .name(name())
                    .score(1.0)
                    .reason("custom")
                    .build()

            override fun name(): String = "Custom Eval"

            override fun threshold(): Double = 0.0
        }

        val evaluators = evaluators {
            exactMatch {
                name = "Exact"
                threshold = 0.9
            }
            regex {
                name = "Regex"
                pattern = "foo"
                ignoreCase = true
                threshold = 0.1
            }
            llmJudge(judge) {
                name = "LLM"
                criteria = "crit"
                threshold = 0.2
            }
            hallucination(judge) {
                name = "Halluc"
                contextKey = "ctx"
                threshold = 0.3
            }
            faithfulness(judge) {
                name = "Faith"
                contextKey = "ctx"
                threshold = 0.4
            }
            contextualRelevance(judge) {
                name = "Context"
                retrievalContextKey = "ctxList"
                strictMode = false
                threshold = 0.5
            }
            precision {
                name = "Precision"
                retrievedKey = "retr"
                expectedKey = "rel"
                threshold = 0.6
            }
            recall {
                name = "Recall"
                retrievedKey = "retr"
                expectedKey = "rel"
                threshold = 0.7
            }
            evaluator(customEvaluator)
        }

        assertThat(evaluators).hasSize(9)
        assertThat(evaluators.map { it.name() }).containsExactly(
            "Exact",
            "Regex",
            "LLM",
            "Halluc",
            "Faith",
            "Context",
            "Precision",
            "Recall",
            "Custom Eval"
        )
        assertThat(evaluators.map { it.threshold() }).containsSequence(
            0.9, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.0
        )
    }
}
