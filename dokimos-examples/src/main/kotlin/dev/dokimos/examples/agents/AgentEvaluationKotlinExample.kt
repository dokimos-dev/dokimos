package dev.dokimos.examples.agents

import dev.dokimos.core.EvalResult
import dev.dokimos.core.EvalTestCase
import dev.dokimos.kotlin.dsl.agents.agentTestCase
import dev.dokimos.kotlin.dsl.agents.agentTrace
import dev.dokimos.kotlin.dsl.agents.tools
import dev.dokimos.kotlin.dsl.toolCallValidity
import dev.dokimos.kotlin.dsl.toolCorrectness
import dev.dokimos.kotlin.dsl.toolDescriptionReliability
import dev.dokimos.kotlin.dsl.toolEfficiency
import dev.dokimos.kotlin.dsl.toolError
import dev.dokimos.kotlin.dsl.toolNameReliability

/**
 * Kotlin counterpart of [dev.dokimos.examples.basic.AgentEvaluationExample].
 *
 * Shows the Kotlin agent DSL:
 * - declaring tools with a typed JSON schema builder instead of nested maps
 * - building an agent trace with tool calls, results, and reasoning steps
 * - turning that trace into an `EvalTestCase` the agent evaluators understand
 */
object AgentEvaluationKotlinExample {

    private val availableTools = tools {
        tool("search_flights") {
            description = "Search for available flights between airports"
            parameters {
                string("origin", "Origin airport IATA code", required = true)
                string("destination", "Destination airport IATA code", required = true)
                string("date", "Travel date in YYYY-MM-DD format")
            }
        }
        tool("book_hotel") {
            description = "Book a hotel room in a specific city"
            parameters {
                string("city", "City name", required = true)
                string("checkIn", "Check-in date")
                integer("nights", "Number of nights")
            }
        }
        tool("get_weather") {
            description = "Get weather forecast for a city"
            parameters {
                string("city", "City name", required = true)
            }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("=== Dokimos Agent Evaluation Example (Kotlin DSL) ===\n")

        // Simulate an agent trace
        val trace = agentTrace {
            reasoning("User wants to travel to Paris. I need to search for flights and book a hotel.")
            toolCall("search_flights") {
                argument("origin", "NYC")
                argument("destination", "CDG")
                argument("date", "2026-03-15")
                result = "Found 5 flights from NYC to CDG on 2026-03-15"
            }
            reasoning("Found flights. Now booking a hotel.")
            toolCall("book_hotel") {
                argument("city", "Paris")
                argument("checkIn", "2026-03-15")
                argument("nights", 3)
                resultJson(mapOf("hotel" to "Hotel Le Marais", "nights" to 3))
            }
            finalResponse =
                "I've found flights from NYC to Paris and booked Hotel Le Marais for 3 nights starting March 15."
            metadata("totalLatencyMs" to 2500)
        }

        println("Agent trace:")
        println("  Final response: ${trace.finalResponse()}")
        println("  Tool calls: ${trace.toolNames()}")
        println("  Reasoning steps: ${trace.reasoningSteps().size}")
        println()

        // The trace plus the tools and expectations become a test case in one block
        val testCase = agentTestCase {
            input = "Find flights from NYC to Paris and book a hotel for 3 nights"
            trace(trace)
            tools(availableTools)
            expectedToolCalls {
                call("search_flights", mapOf())
                call("book_hotel", mapOf())
            }
            tasks("Search for flights", "Book a hotel")
        }

        println("--- Tool Call Validity ---")
        printResult(toolCallValidity().evaluate(testCase))

        println("--- Tool Correctness ---")
        printResult(toolCorrectness().evaluate(testCase))

        println("--- Tool Error ---")
        printResult(toolError().evaluate(testCase))

        println("--- Tool Efficiency ---")
        printResult(toolEfficiency().evaluate(testCase))

        // Tool reliability only needs the definitions, not a run
        val toolsOnlyTestCase: EvalTestCase = agentTestCase { tools(availableTools) }

        println("--- Tool Name Reliability ---")
        printResult(toolNameReliability().evaluate(toolsOnlyTestCase))

        println("--- Tool Description Reliability ---")
        printResult(toolDescriptionReliability().evaluate(toolsOnlyTestCase))

        println("=== Done ===")
        println("\nNote: taskCompletion and toolArgumentHallucination require a JudgeLM (real LLM)")
        println("and are not shown in this offline example.")
    }

    private fun printResult(result: EvalResult) {
        println(
            "  %s: %s (score: %.2f, threshold: %.2f)".format(
                result.name(),
                if (result.success()) "PASS" else "FAIL",
                result.score(),
                result.threshold() ?: 0.0,
            ),
        )
        println("  Reason: ${result.reason()}")
        println()
    }
}
