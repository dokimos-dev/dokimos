package dev.dokimos.kotlin.dsl

import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.EvalTestCaseParam
import dev.dokimos.core.Example
import dev.dokimos.core.JudgeLM
import dev.dokimos.core.evaluators.ExactMatchEvaluator
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
}
