package dev.dokimos.kotlin.dsl

import dev.dokimos.core.EvalTestCaseParam
import dev.dokimos.core.Example
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CoreDslTest {

    @Test
    fun `experiment DSL builds and runs`() {
        val separateRegexEvaluator = regex {
            pattern = "wo.*"
        }
        val separateExactMatchEvaluator = exactMatch {
            threshold = 1.0
        }

        val result = experiment {
            name = "dsl-experiment"
            dataset {
                name = "ds"
                example {
                    input = "hello"
                    expected = "world"
                    metadata("traceId" to "abc", "traceEpocMs" to "123456789")
                }
            }

            task { example ->
                mapOf("output" to example.expectedOutput())
            }

            evaluators {
                regex {
                    pattern = "world"
                }
            }
            evaluator(separateRegexEvaluator)
            evaluator(listOf(separateExactMatchEvaluator, separateRegexEvaluator))
        }.run()

        assertThat(result.passRate()).isEqualTo(1.0)
        assertThat(result.itemResults()).hasSize(1)
        val item = result.itemResults().first()
        assertThat(item.example().metadata())
            .containsEntry("traceId", "abc")
            .containsEntry("traceEpocMs", "123456789")
    }

    @Test
    fun `experiment DSL handles nested IO and metadata`() {
        val nested = mapOf(
            "items" to listOf(
                mapOf("id" to 1, "data" to listOf("x", "y")),
                mapOf("id" to 2, "data" to listOf("z")),
            ),
        )

        val result = experiment {
            name = "nested-experiment"
            metadata("exp", mapOf("version" to 2))

            dataset {
                name = "nested-ds"
                example {
                    input("payload", nested)
                    expected("output", nested)
                    expected("payload", nested)
                    metadata("exampleMeta", mapOf("section" to "nested"))
                }
            }

            task { example ->
                val expectedOutputs = example.expectedOutputs()
                mapOf(
                    "output" to expectedOutputs["output"]!!,
                    "payload" to expectedOutputs["payload"]!!,
                )
            }

            evaluators {
                exactMatch {
                    name = "Exact"
                    threshold = 1.0
                    params(EvalTestCaseParam.ACTUAL_OUTPUT, EvalTestCaseParam.EXPECTED_OUTPUT)
                }
            }
        }.run()

        assertThat(result.passRate()).isEqualTo(1.0)
        assertThat(result.metadata()).containsEntry("exp", mapOf("version" to 2))
        assertThat(result.itemResults()).hasSize(1)

        val item = result.itemResults().first()
        assertThat(item.actualOutputs()["payload"]).isEqualTo(nested)
        assertThat(item.example().expectedOutputs()["payload"]).isEqualTo(nested)
        assertThat(item.example().metadata()["exampleMeta"]).isEqualTo(mapOf("section" to "nested"))
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
    fun `example DSL preserves nested structures`() {
        val nestedPayload = mapOf(
            "items" to listOf(
                mapOf("id" to 1, "data" to listOf("x", "y")),
                mapOf("id" to 2, "data" to listOf("z")),
            ),
        )

        val ex: Example = example {
            input("payload", nestedPayload)
            expected("payload", nestedPayload)
            expected("output", nestedPayload)
            metadata("trace", mapOf("path" to listOf("root", "child")))
        }

        assertThat(ex.inputs()["payload"]).isEqualTo(nestedPayload)
        assertThat(ex.expectedOutputs()["payload"]).isEqualTo(nestedPayload)
        assertThat(ex.expectedOutputs()["output"]).isEqualTo(nestedPayload)
        assertThat(ex.metadata()["trace"]).isEqualTo(mapOf("path" to listOf("root", "child")))
    }
}
