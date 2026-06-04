package dev.dokimos.kotlin.core

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.Example
import dev.dokimos.core.agents.ToolCall
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TypedReadsTest {

    // Annotated so the framework's Java-only Jackson mapper (no kotlin module) can construct this
    // data class from a map; the constructor parameter names are otherwise invisible to Jackson.
    data class Whisky
    @JsonCreator
    constructor(
        @JsonProperty("name") val name: String,
        @JsonProperty("age") val age: Int,
    )

    @Test
    fun `reified actualOutputAs reads a record back`() {
        val testCase = EvalTestCase.builder()
            .actualOutput("output", mapOf("name" to "Lagavulin", "age" to 16))
            .build()

        val whisky = testCase.actualOutputAs<Whisky>()

        assertThat(whisky).isEqualTo(Whisky("Lagavulin", 16))
    }

    @Test
    fun `reified actualOutputAs preserves generics for a List of records`() {
        // The reified extension delegates to OutputType, not T class java, so the element type of the
        // list survives erasure and each element materializes as a Whisky rather than a raw Map.
        val testCase = EvalTestCase.builder()
            .actualOutput(
                "output",
                listOf(
                    mapOf("name" to "Lagavulin", "age" to 16),
                    mapOf("name" to "Ardbeg", "age" to 10),
                ),
            )
            .build()

        val whiskies = testCase.actualOutputAs<List<Whisky>>()

        assertThat(whiskies).containsExactly(Whisky("Lagavulin", 16), Whisky("Ardbeg", 10))
        assertThat(whiskies!!.first()).isInstanceOf(Whisky::class.java)
    }

    @Test
    fun `reified keyed actualOutputAs reads under a custom key`() {
        val testCase = EvalTestCase.builder()
            .actualOutput("pick", mapOf("name" to "Oban", "age" to 14))
            .build()

        val whisky = testCase.actualOutputAs<Whisky>("pick")

        assertThat(whisky).isEqualTo(Whisky("Oban", 14))
    }

    @Test
    fun `reified actualOutputAs returns null for an absent output`() {
        val testCase = EvalTestCase.builder().build()

        assertThat(testCase.actualOutputAs<Whisky>()).isNull()
    }

    @Test
    fun `reified expectedOutputAs and inputAs and metadataAs read on EvalTestCase`() {
        val testCase = EvalTestCase.builder()
            .input("input", mapOf("name" to "Talisker", "age" to 10))
            .expectedOutput("output", mapOf("name" to "Talisker", "age" to 10))
            .metadata("featured", mapOf("name" to "Springbank", "age" to 15))
            .build()

        assertThat(testCase.inputAs<Whisky>()).isEqualTo(Whisky("Talisker", 10))
        assertThat(testCase.expectedOutputAs<Whisky>()).isEqualTo(Whisky("Talisker", 10))
        assertThat(testCase.metadataAs<Whisky>("featured")).isEqualTo(Whisky("Springbank", 15))
    }

    @Test
    fun `reified readers work on Example`() {
        val example = Example.builder()
            .input("input", mapOf("name" to "Bowmore", "age" to 12))
            .expectedOutput("output", mapOf("name" to "Bowmore", "age" to 12))
            .metadata("featured", mapOf("name" to "Highland Park", "age" to 18))
            .build()

        assertThat(example.inputAs<Whisky>()).isEqualTo(Whisky("Bowmore", 12))
        assertThat(example.expectedOutputAs<Whisky>()).isEqualTo(Whisky("Bowmore", 12))
        assertThat(example.metadataAs<Whisky>("featured")).isEqualTo(Whisky("Highland Park", 18))
    }

    @Test
    fun `reified resultAs reads a record back from a tool call`() {
        val toolCall = ToolCall.builder()
            .name("lookup")
            .resultJson(Whisky("Macallan", 18))
            .build()

        val whisky = toolCall.resultAs<Whisky>()

        assertThat(whisky).isEqualTo(Whisky("Macallan", 18))
    }

    @Test
    fun `reified resultAs preserves generics for a List of records`() {
        val toolCall = ToolCall.builder()
            .name("search")
            .resultJson(listOf(Whisky("Macallan", 18), Whisky("Glenlivet", 12)))
            .build()

        val whiskies = toolCall.resultAs<List<Whisky>>()

        assertThat(whiskies).containsExactly(Whisky("Macallan", 18), Whisky("Glenlivet", 12))
        assertThat(whiskies!!.first()).isInstanceOf(Whisky::class.java)
    }

    @Test
    fun `reified argumentsAs converts the arguments map`() {
        val toolCall = ToolCall.builder()
            .name("create")
            .argument("name", "Dalmore")
            .argument("age", 12)
            .build()

        val whisky = toolCall.argumentsAs<Whisky>()

        assertThat(whisky).isEqualTo(Whisky("Dalmore", 12))
    }

    @Test
    fun `reified metadataAs reads a tool call metadata entry`() {
        val toolCall = ToolCall.builder()
            .name("create")
            .metadata("featured", mapOf("name" to "Aberlour", "age" to 16))
            .build()

        assertThat(toolCall.metadataAs<Whisky>("featured")).isEqualTo(Whisky("Aberlour", 16))
    }

    @Test
    fun `reified resultAs returns null for a blank result`() {
        val toolCall = ToolCall.builder().name("noop").build()

        assertThat(toolCall.resultAs<Whisky>()).isNull()
    }

    @Test
    fun `reified actualOutputAs wraps a conversion failure`() {
        val testCase = EvalTestCase.builder()
            .actualOutput("output", mapOf("name" to "x", "age" to "not-a-number"))
            .build()

        assertThatThrownBy { testCase.actualOutputAs<Whisky>() }
            .isInstanceOf(dev.dokimos.core.exceptions.DokimosTypeConversionException::class.java)
    }
}
