package dev.dokimos.kotlin.dsl.agents

import dev.dokimos.core.agents.ToolCall
import dev.dokimos.core.agents.ToolDefinition
import dev.dokimos.core.evaluators.agents.PlanQualityEvaluator
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator
import dev.dokimos.core.evaluators.agents.ToolCorrectnessEvaluator
import dev.dokimos.core.evaluators.agents.ToolEfficiencyEvaluator
import dev.dokimos.core.evaluators.agents.ToolErrorEvaluator
import dev.dokimos.core.evaluators.agents.ToolNameReliabilityEvaluator
import dev.dokimos.kotlin.dsl.experiment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentDslTest {

    @Test
    fun `toolCall DSL builds arguments, result and metadata`() {
        val call = toolCall("search_flights") {
            argument("origin", "JFK")
            arguments("destination" to "CDG", "date" to "2026-03-15")
            result = "Found 5 flights"
            metadata("latencyMs" to 120)
        }

        assertThat(call.name()).isEqualTo("search_flights")
        assertThat(call.arguments())
            .containsEntry("origin", "JFK")
            .containsEntry("destination", "CDG")
            .containsEntry("date", "2026-03-15")
        assertThat(call.result()).isEqualTo("Found 5 flights")
        assertThat(call.metadata()).containsEntry("latencyMs", 120)
    }

    @Test
    fun `toolCall DSL serializes a result object with resultJson`() {
        val call = toolCall("search_flights") {
            resultJson(mapOf("flights" to 5))
        }

        assertThat(call.result()).isEqualTo("""{"flights":5}""")
    }

    @Test
    fun `toolCall shorthand takes a plain argument map`() {
        val call = toolCall("book_hotel", mapOf("city" to "Paris"))

        assertThat(call.name()).isEqualTo("book_hotel")
        assertThat(call.arguments()).containsEntry("city", "Paris")
        assertThat(call.result()).isNull()
    }

    @Test
    fun `toolDefinition DSL builds a JSON schema from typed parameters`() {
        val definition = toolDefinition("search_flights") {
            description = "Search for available flights between airports"
            parameters {
                string("origin", "Origin airport IATA code", required = true)
                string("destination", "Destination airport IATA code", required = true)
                string("date", "Travel date in YYYY-MM-DD format")
                integer("passengers", "Number of passengers")
                string("cabin", enum = listOf("economy", "business"))
                additionalProperties = false
            }
        }

        assertThat(definition.name()).isEqualTo("search_flights")
        assertThat(definition.description()).isEqualTo("Search for available flights between airports")
        assertThat(definition.requiredParameters()).containsExactly("origin", "destination")
        assertThat(definition.parameterNames())
            .containsExactly("origin", "destination", "date", "passengers", "cabin")
        assertThat(definition.parameterSchema("passengers")).containsEntry("type", "integer")
        assertThat(definition.parameterSchema("cabin")).containsEntry("enum", listOf("economy", "business"))
        assertThat(definition.inputSchema()).containsEntry("additionalProperties", false)
    }

    @Test
    fun `schema DSL drives the validity evaluator`() {
        val testCase = agentTestCase {
            toolCall("search_flights", mapOf("origin" to "JFK", "passengers" to "two"))
            tool("search_flights") {
                parameters {
                    string("origin", required = true)
                    string("destination", required = true)
                    integer("passengers")
                }
            }
        }

        val result = ToolCallValidityEvaluator.builder().build().evaluate(testCase)

        assertThat(result.success()).isFalse()
        // missing required 'destination' and 'passengers' typed as string instead of integer
        assertThat(result.reason()).contains("0/1")
    }

    @Test
    fun `tools DSL builds a list of definitions`() {
        val definitions = tools {
            tool("search_flights") { description = "Search flights" }
            tool("book_hotel") { description = "Book a hotel" }
            tool(ToolDefinition.of("get_weather", "Weather forecast", mapOf()))
        }

        assertThat(definitions).hasSize(3)
        assertThat(definitions.map { it.name() }).containsExactly("search_flights", "book_hotel", "get_weather")
    }

    @Test
    fun `toolCalls DSL builds a list of calls`() {
        val calls = toolCalls {
            call("search_flights") { argument("origin", "JFK") }
            call("book_hotel", mapOf("city" to "Paris"))
            call(ToolCall.of("get_weather", mapOf("city" to "Paris")))
        }

        assertThat(calls.map { it.name() }).containsExactly("search_flights", "book_hotel", "get_weather")
    }

    @Test
    fun `agentTrace DSL builds a trace`() {
        val trace = agentTrace {
            reasoning("User wants to travel to Paris")
            toolCall("search_flights") {
                argument("origin", "JFK")
                argument("destination", "CDG")
            }
            reasoning("Now booking a hotel")
            toolCall("book_hotel", mapOf("city" to "Paris", "nights" to 3))
            finalResponse = "Booked your trip to Paris."
            metadata("totalLatencyMs" to 2500)
        }

        assertThat(trace.finalResponse()).isEqualTo("Booked your trip to Paris.")
        assertThat(trace.toolNames()).containsExactly("search_flights", "book_hotel")
        assertThat(trace.reasoningSteps()).hasSize(2)
        assertThat(trace.metadata()).containsEntry("totalLatencyMs", 2500)
        assertThat(trace.toOutputMap()).containsKeys("output", "toolCalls", "reasoningSteps")
    }

    @Test
    fun `agentTestCase DSL wires the keys the agent evaluators expect`() {
        val testCase = agentTestCase {
            input = "Find flights from NYC to Paris and book a hotel"
            output = "Booked your trip to Paris."
            toolCall("search_flights") {
                argument("origin", "JFK")
                argument("destination", "CDG")
            }
            toolCall("book_hotel", mapOf("city" to "Paris"))
            expectedToolCalls {
                call("search_flights", mapOf())
                call("book_hotel", mapOf())
            }
            tools {
                tool("search_flights") {
                    description = "Search flights"
                    parameters {
                        string("origin", required = true)
                        string("destination", required = true)
                    }
                }
                tool("book_hotel") {
                    description = "Book a hotel"
                    parameters { string("city", required = true) }
                }
            }
            tasks("Search for flights", "Book a hotel")
            constraints = "Budget under 500"
            metadata("traceId" to "abc")
        }

        assertThat(testCase.inputs()).containsEntry("input", "Find flights from NYC to Paris and book a hotel")
        assertThat(testCase.actualOutputs()).containsEntry("output", "Booked your trip to Paris.")
        assertThat(testCase.actualOutputs()["toolCalls"] as List<*>).hasSize(2)
        assertThat(testCase.expectedOutputs()["toolCalls"] as List<*>).hasSize(2)
        assertThat(testCase.metadata()["tools"] as List<*>).hasSize(2)
        assertThat(testCase.metadata()).containsEntry("tasks", listOf("Search for flights", "Book a hotel"))
        assertThat(testCase.metadata()).containsEntry("constraints", "Budget under 500")
        assertThat(testCase.metadata()).containsEntry("traceId", "abc")

        assertThat(ToolCallValidityEvaluator.builder().build().evaluate(testCase).success()).isTrue()
        assertThat(ToolCorrectnessEvaluator.builder().build().evaluate(testCase).success()).isTrue()
        assertThat(ToolEfficiencyEvaluator.builder().build().evaluate(testCase).success()).isTrue()
        assertThat(ToolNameReliabilityEvaluator.builder().build().evaluate(testCase).score()).isGreaterThan(0.0)
    }

    @Test
    fun `agentTestCase takes outputs from a trace`() {
        val testCase = agentTestCase {
            input = "Find flights from NYC to Paris"
            trace {
                reasoning("Search for flights from JFK to CDG")
                toolCall("search_flights") {
                    argument("origin", "JFK")
                    argument("destination", "CDG")
                    result = "Found 5 flights"
                }
                finalResponse = "Found your flights."
            }
            tools {
                tool("search_flights") {
                    description = "Search flights"
                    parameters {
                        string("origin", required = true)
                        string("destination", required = true)
                    }
                }
            }
            expectedToolCall("search_flights", mapOf())
        }

        assertThat(testCase.actualOutputs()).containsEntry("output", "Found your flights.")
        assertThat(testCase.actualOutputs()["reasoningSteps"] as List<*>)
            .containsExactly("Search for flights from JFK to CDG")

        assertThat(ToolCallValidityEvaluator.builder().build().evaluate(testCase).success()).isTrue()
        assertThat(ToolCorrectnessEvaluator.builder().build().evaluate(testCase).success()).isTrue()
        // reasoningSteps is the key planQuality reads — AgentEvalCase alone does not write it
        assertThat(PlanQualityEvaluator.builder().build().evaluate(testCase).score()).isGreaterThan(0.0)
    }

    @Test
    fun `tool results feed the error evaluator`() {
        val testCase = agentTestCase {
            toolCall("search_flights") { result = "Found 5 flights" }
            toolCall("book_hotel") { resultJson(mapOf("error" to "no availability")) }
        }

        val result = ToolErrorEvaluator.builder().build().evaluate(testCase)

        assertThat(result.score()).isEqualTo(0.5)
    }

    @Test
    fun `agent DSL composes with the experiment DSL`() {
        val availableTools = tools {
            tool("search_flights") {
                description = "Search for flights"
                parameters {
                    string("origin", required = true)
                    string("destination", required = true)
                }
            }
        }

        val result = experiment {
            name = "Travel Agent Evaluation"
            dataset {
                name = "Travel Agent"
                example {
                    input = "Find flights to Paris"
                    expected("toolCalls", toolCalls { call("search_flights", mapOf()) })
                    metadata("tools", availableTools)
                    metadata("tasks", listOf("Search flights"))
                }
            }
            task { example ->
                agentTrace {
                    toolCall("search_flights", mapOf("origin" to "JFK", "destination" to "CDG"))
                    finalResponse = "Found flights for ${example.input()}"
                }.toOutputMap()
            }
            evaluators {
                toolCallValidity { }
                toolCorrectness { }
            }
        }.run()

        assertThat(result.passRate()).isEqualTo(1.0)
    }
}
