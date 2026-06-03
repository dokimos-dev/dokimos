package dev.dokimos.koog

import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.agents.ToolCall
import dev.dokimos.core.agents.ToolDefinition
import dev.dokimos.core.evaluators.agents.ArgMatchMode
import dev.dokimos.core.evaluators.agents.ArgumentMatcher
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator
import dev.dokimos.core.evaluators.agents.ToolTrajectoryEvaluator
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KoogTraceCollectorTest {

    @Test
    fun `records tool calls in order with name, arguments, and result`() {
        val collector = KoogTraceCollector()
        collector.record(
            "search_flights",
            buildJsonObject { put("origin", "JFK") },
            JsonPrimitive("[{\"id\":\"AF1\"}]"),
        )
        collector.record(
            "book_hotel",
            buildJsonObject { put("city", "Paris") },
            JsonPrimitive("{\"confirmation\":\"X\"}"),
        )

        val trace = collector.toAgentTrace("Booked.")

        assertThat(trace.finalResponse()).isEqualTo("Booked.")
        assertThat(trace.toolCalls()).hasSize(2)
        assertThat(trace.toolCalls()[0].name()).isEqualTo("search_flights")
        assertThat(trace.toolCalls()[0].arguments()).containsEntry("origin", "JFK")
        assertThat(trace.toolCalls()[0].result()).isEqualTo("[{\"id\":\"AF1\"}]")
        assertThat(trace.toolCalls()[1].name()).isEqualTo("book_hotel")
    }

    @Test
    fun `numbers are preserved as numbers and nested objects are converted`() {
        val collector = KoogTraceCollector()
        collector.record(
            "page",
            buildJsonObject {
                put("n", 1)
                put("rate", 1.5)
                put("flag", true)
                put("filter", buildJsonObject { put("area", "EU") })
            },
            JsonPrimitive("ok"),
        )

        val args = collector.toAgentTrace().toolCalls()[0].arguments()

        assertThat(args["n"]).isEqualTo(1L)
        assertThat(args["rate"]).isEqualTo(1.5)
        assertThat(args["flag"]).isEqualTo(true)
        assertThat(args["filter"]).isEqualTo(mapOf("area" to "EU"))
    }

    @Test
    fun `numeric tolerance lets a long argument match an int expectation`() {
        // Koog parses JSON integers as Long; the tolerant matcher must still see 1L as 1.
        val collector = KoogTraceCollector()
        collector.record("page", buildJsonObject { put("n", 1) }, JsonPrimitive("ok"))

        val testCase = EvalTestCase.builder()
            .actualOutput("toolCalls", collector.toAgentTrace().toolCalls())
            .expectedOutput("toolCalls", listOf(ToolCall.of("page", mapOf("n" to 1))))
            .build()

        val score = ToolTrajectoryEvaluator.builder()
            .matchMode(ToolTrajectoryEvaluator.MatchMode.STRICT)
            .argumentMatcher(ArgumentMatcher.tolerant())
            .build()
            .evaluate(testCase)
            .score()

        assertThat(score).isEqualTo(1.0)
    }

    @Test
    fun `a JSON object result is rendered as compact JSON`() {
        val collector = KoogTraceCollector()
        collector.record("t", buildJsonObject {}, buildJsonObject { put("ok", true) })

        assertThat(collector.toAgentTrace().toolCalls()[0].result()).isEqualTo("{\"ok\":true}")
    }

    @Test
    fun `a JSON-null result and a kotlin-null result both become a null result`() {
        val collector = KoogTraceCollector()
        collector.record("a", buildJsonObject {}, JsonNull)
        collector.record("b", buildJsonObject {}, null)

        assertThat(collector.toAgentTrace().toolCalls()[0].result()).isNull()
        assertThat(collector.toAgentTrace().toolCalls()[1].result()).isNull()
    }

    @Test
    fun `an empty arguments object yields empty arguments`() {
        val collector = KoogTraceCollector()
        collector.record("ping", buildJsonObject {}, JsonPrimitive("pong"))

        assertThat(collector.toAgentTrace().toolCalls()[0].arguments()).isEmpty()
    }

    @Test
    fun `a JSON-null argument value is omitted`() {
        val collector = KoogTraceCollector()
        collector.record(
            "t",
            buildJsonObject {
                put("present", "x")
                put("missing", JsonNull)
            },
            JsonPrimitive("r"),
        )

        assertThat(collector.toAgentTrace().toolCalls()[0].arguments()).containsOnlyKeys("present")
    }

    @Test
    fun `no recorded calls yields an empty trace`() {
        val trace = KoogTraceCollector().toAgentTrace()

        assertThat(trace.toolCalls()).isEmpty()
        assertThat(trace.finalResponse()).isNull()
    }

    @Test
    fun `two calls to the same tool with different arguments are both retained`() {
        val collector = KoogTraceCollector()
        collector.record("search", buildJsonObject { put("q", "shoes") }, JsonPrimitive("r1"))
        collector.record("search", buildJsonObject { put("q", "boots") }, JsonPrimitive("r2"))

        assertThat(collector.toAgentTrace().toolCalls()).hasSize(2)
    }

    @Test
    fun `the produced trace satisfies the agent evaluators without throwing`() {
        val collector = KoogTraceCollector()
        collector.record("search_flights", buildJsonObject { put("origin", "JFK") }, JsonPrimitive("[]"))

        val tools = listOf(
            ToolDefinition.of(
                "search_flights",
                "Search flights",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf("origin" to mapOf("type" to "string")),
                ),
            ),
        )
        val testCase = EvalTestCase.builder()
            .input("Fly from JFK")
            .actualOutputs(collector.toAgentTrace("done").toOutputMap())
            .expectedOutput("toolCalls", listOf(ToolCall.of("search_flights", mapOf<String, Any>())))
            .metadata("tools", tools)
            .build()

        assertThat(ToolCallValidityEvaluator.builder().build().evaluate(testCase).score())
            .isEqualTo(1.0)
        assertThat(
            ToolTrajectoryEvaluator.builder()
                .matchMode(ToolTrajectoryEvaluator.MatchMode.ANY_ORDER)
                .argumentMatcher(ArgumentMatcher.of(ArgMatchMode.IGNORE))
                .build()
                .evaluate(testCase)
                .score(),
        ).isEqualTo(1.0)
    }

    @Test
    fun `arguments shaped like Koog's post-0_7 JSON model are read by duck-typing`() {
        val collector = KoogTraceCollector()
        collector.record(
            "search",
            FakeObject(
                linkedMapOf(
                    "origin" to FakeString("JFK"),
                    "n" to FakeLiteral("3"),
                    "rate" to FakeLiteral("1.5"),
                    "flag" to FakeLiteral("true"),
                    "filter" to FakeObject(linkedMapOf("area" to FakeString("EU"))),
                    "tags" to FakeArray(listOf(FakeString("a"), FakeString("b"))),
                    "missing" to FakeNull,
                ),
            ),
            FakeString("[]"),
        )

        val args = collector.toAgentTrace().toolCalls()[0].arguments()
        assertThat(args["origin"]).isEqualTo("JFK")
        assertThat(args["n"]).isEqualTo(3L)
        assertThat(args["rate"]).isEqualTo(1.5)
        assertThat(args["flag"]).isEqualTo(true)
        assertThat(args["filter"]).isEqualTo(mapOf("area" to "EU"))
        assertThat(args["tags"]).isEqualTo(listOf("a", "b"))
        assertThat(args).doesNotContainKey("missing")
    }

    @Test
    fun `a string result shaped like Koog's post-0_7 model is unwrapped, an object is compact JSON`() {
        val collector = KoogTraceCollector()
        collector.record("a", FakeObject(linkedMapOf()), FakeString("hello"))
        collector.record("b", FakeObject(linkedMapOf()), FakeObject(linkedMapOf("ok" to FakeLiteral("true"))))
        collector.record("c", FakeObject(linkedMapOf()), FakeNull)

        val calls = collector.toAgentTrace().toolCalls()
        assertThat(calls[0].result()).isEqualTo("hello")
        assertThat(calls[1].result()).isEqualTo("{\"ok\":true}")
        assertThat(calls[2].result()).isNull()
    }

    /**
     * Doubles that mimic the shape of Koog's `ai.koog.serialization` JSON nodes (0.7.0 onward)
     * without depending on those types, so the collector's reflective walk is exercised against the
     * post-0.7 hierarchy as well as the kotlinx one used elsewhere in this test.
     */
    private class FakeObject(private val entries: Map<String, Any?>) {
        fun getEntries(): Map<String, Any?> = entries
    }

    private class FakeArray(private val elements: List<Any?>) {
        fun getElements(): List<Any?> = elements
    }

    private class FakeString(private val content: String) {
        fun getContent(): String = content

        fun isString(): Boolean = true
    }

    private class FakeLiteral(private val content: String) {
        fun getContent(): String = content

        fun isString(): Boolean = false
    }

    /** Mirrors a JSON-null node: a non-string primitive whose content is the literal "null". */
    private object FakeNull {
        fun getContent(): String = "null"

        fun isString(): Boolean = false
    }
}
