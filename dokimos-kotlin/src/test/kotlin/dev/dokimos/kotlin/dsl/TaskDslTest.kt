package dev.dokimos.kotlin.dsl

import dev.dokimos.core.CallMetrics
import dev.dokimos.core.Example
import dev.dokimos.core.Task
import dev.dokimos.core.TaskResult
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TaskDslTest {

    data class Whisky(val name: String, val age: Int)

    private fun exampleWith(input: String): Example = Example.builder().input("input", input).build()

    @Test
    fun `typedTask wraps the produced value under the output key`() {
        val task: Task = typedTask { Whisky("Lagavulin", 16) }

        val outputs = task.run(exampleWith("q"))

        assertThat(outputs).containsExactly(
            org.assertj.core.api.Assertions.entry("output", Whisky("Lagavulin", 16)),
        )
    }

    @Test
    fun `typedTask does not double-nest a map value`() {
        val task: Task = typedTask { mapOf("output" to "world", "score" to 1) }

        val outputs = task.run(exampleWith("q"))

        assertThat(outputs)
            .containsEntry("output", "world")
            .containsEntry("score", 1)
            .doesNotContainKey("__nested__")
        assertThat(outputs["output"]).isEqualTo("world")
    }

    @Test
    fun `typedTask rejects a null produced value`() {
        // Task.typed cannot put null into the output map; a null-returning body must throw NPE with the
        // documented message rather than silently dropping the output.
        val task: Task = typedTask<String?> { null }

        assertThatThrownBy { task.run(exampleWith("q")) }
            .isInstanceOf(NullPointerException::class.java)
            .hasMessageContaining("returned null")
    }

    @Test
    fun `experiment DSL rejects configuring both task and asyncTask`() {
        // task and asyncTask are mutually exclusive; the builder fails fast rather than silently
        // ignoring one.
        assertThatThrownBy {
            experiment {
                name = "both-tasks-experiment"
                dataset {
                    name = "ds"
                    example {
                        input = "hello"
                        expected = "ASYNC"
                    }
                }
                task { mapOf("output" to "SYNC") }
                suspendTask { TaskResult(mapOf("output" to "ASYNC"), null) }
                evaluators {
                    exactMatch { threshold = 1.0 }
                }
            }
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("not both")
    }

    @Test
    fun `existing task DSL still compiles and runs without ambiguity`() {
        // Guards the source-compat invariant: a reified overload named `task` would break this.
        val task: Task = task { example -> mapOf("output" to example.input()) }

        val outputs = task.run(exampleWith("hello"))

        assertThat(outputs).containsEntry("output", "hello")
    }

    @Test
    fun `suspendTask bridges a suspend body to an AsyncTask`() {
        val asyncTask = suspendTask { example ->
            TaskResult(mapOf("output" to example.input().uppercase()), null)
        }

        val result = asyncTask.run(exampleWith("hello")).get()

        assertThat(result.outputs()).containsEntry("output", "HELLO")
        assertThat(result.metrics()).isNull()
    }

    @Test
    fun `suspendTask carries metrics through the TaskResult`() {
        val metrics = CallMetrics(3, 5, 0.01, 42L)
        val asyncTask = suspendTask { TaskResult(mapOf("output" to "x"), metrics) }

        val result = asyncTask.run(exampleWith("q")).get()

        assertThat(result.metrics()).isEqualTo(metrics)
    }

    @Test
    fun `suspendMapTask wraps an output map with no metrics`() {
        val asyncTask = suspendMapTask { example -> mapOf("output" to example.input()) }

        val result = asyncTask.run(exampleWith("hi")).get()

        assertThat(result.outputs()).containsEntry("output", "hi")
        assertThat(result.metrics()).isNull()
    }

    @Test
    fun `suspendTask surfaces a suspend exception as a failed future`() {
        val asyncTask = suspendTask {
            throw IllegalStateException("boom")
        }

        val future = asyncTask.run(exampleWith("q"))

        assertThatThrownBy { future.get() }
            .isInstanceOf(java.util.concurrent.ExecutionException::class.java)
            .hasRootCauseInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("boom")
        assertThat(future.isCompletedExceptionally).isTrue()
    }

    @Test
    fun `typedTask runs end to end through the experiment DSL`() {
        val result = experiment {
            name = "typed-task-experiment"
            dataset {
                name = "ds"
                example {
                    input = "hello"
                    expected = "HELLO"
                }
            }
            typedTask { example -> example.input().uppercase() }
            evaluators {
                exactMatch { threshold = 1.0 }
            }
        }.run()

        assertThat(result.passRate()).isEqualTo(1.0)
        assertThat(result.itemResults()).hasSize(1)
        assertThat(result.itemResults().first().actualOutputs()).containsEntry("output", "HELLO")
    }

    @Test
    fun `suspendTask runs end to end through the experiment DSL`() {
        val result = experiment {
            name = "suspend-task-experiment"
            parallelism = 2
            dataset {
                name = "ds"
                example {
                    input = "hello"
                    expected = "HELLO"
                }
            }
            suspendTask { example ->
                TaskResult(mapOf("output" to example.input().uppercase()), null)
            }
            evaluators {
                exactMatch { threshold = 1.0 }
            }
        }.run()

        assertThat(result.passRate()).isEqualTo(1.0)
        assertThat(result.itemResults().first().actualOutputs()).containsEntry("output", "HELLO")
    }

    @Test
    fun `suspendTask in the experiment DSL isolates a suspend failure as a failed item`() {
        val result = experiment {
            name = "suspend-fail-experiment"
            dataset {
                name = "ds"
                example {
                    input = "hello"
                    expected = "HELLO"
                }
            }
            suspendTask {
                throw IllegalStateException("boom")
            }
            evaluators {
                exactMatch { threshold = 1.0 }
            }
        }.run()

        assertThat(result.passRate()).isEqualTo(0.0)
        assertThat(result.itemResults()).hasSize(1)
    }

    @Test
    fun `suspend body can call other suspend functions`() {
        suspend fun fetch(input: String): String {
            kotlinx.coroutines.delay(1)
            return input.reversed()
        }

        val asyncTask = suspendTask { example ->
            TaskResult(mapOf("output" to fetch(example.input())), null)
        }

        val output = runBlocking { asyncTask.run(exampleWith("abc")).await() }

        assertThat(output.outputs()).containsEntry("output", "cba")
    }

    @Test
    fun `measuredTask carries call metrics through to the item result`() {
        val result = experiment {
            name = "measured"
            dataset {
                example {
                    input = "hello"
                    expected = "hello"
                }
            }
            measuredTask { example ->
                TaskResult(mapOf("output" to example.input()), CallMetrics(12, 34, 0.001, 250L))
            }
            evaluators {
                exactMatch { }
            }
        }.run()

        assertThat(result.passRate()).isEqualTo(1.0)
        val metrics = result.itemResults().single().metrics()
        assertThat(metrics.tokensIn()).isEqualTo(12)
        assertThat(metrics.tokensOut()).isEqualTo(34)
        assertThat(metrics.costUsd()).isEqualTo(0.001)
        assertThat(metrics.latencyMs()).isEqualTo(250L)
    }

    @Test
    fun `top-level measuredTask builds a MeasuredTask`() {
        val task = measuredTask { example -> TaskResult(mapOf("output" to example.input()), null) }

        val taskResult = task.run(exampleWith("abc"))

        assertThat(taskResult.outputs()).containsEntry("output", "abc")
        assertThat(taskResult.metrics()).isNull()
    }

    @Test
    fun `experiment DSL rejects setting both task and measuredTask`() {
        assertThatThrownBy {
            experiment {
                name = "conflict"
                dataset { example { input = "hello" } }
                task { mapOf("output" to "hello") }
                measuredTask { TaskResult(mapOf("output" to "hello"), null) }
                evaluators { exactMatch { } }
            }
        }.hasMessageContaining("not both")
    }
}
