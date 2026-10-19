package dev.dokimos.adk.kt

import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.events.Event
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.Example
import dev.dokimos.core.agents.ToolCall
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator
import dev.dokimos.core.evaluators.agents.ToolDescriptionReliabilityEvaluator
import dev.dokimos.core.evaluators.agents.ToolErrorEvaluator
import dev.dokimos.core.evaluators.agents.ToolNameReliabilityEvaluator
import dev.dokimos.core.evaluators.agents.ToolTrajectoryEvaluator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class AdkAgentTraceTest {

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `extracts tool calls, reasoning steps, and final response from events`() {
        val userEvent = Event(
            id = "e1",
            author = Role.USER,
            content = Content.fromText(Role.USER, "What's the weather in Paris?"),
        )

        val fc = FunctionCall(
            id = "call-1",
            name = "get_weather",
            args = mapOf("city" to "Paris", "unit" to "celsius"),
        )
        val toolCallEvent = Event(
            id = "e2",
            author = "weather_agent",
            content = Content(role = Role.MODEL, parts = listOf(Part(functionCall = fc))),
        )

        val fr = FunctionResponse(
            id = "call-1",
            name = "get_weather",
            response = mapOf("temp" to 22, "condition" to "sunny"),
        )
        val toolResponseEvent = Event(
            id = "e3",
            author = "weather_agent",
            content = Content(role = Role.USER, parts = listOf(Part(functionResponse = fr))),
        )

        val thoughtPart = Part(
            text = "The user wants the weather in Paris; I have the tool result now.",
            thought = true,
        )
        val answerPart = Part(text = "It is 22°C and sunny in Paris.")
        val finalEvent = Event(
            id = "e4",
            author = "weather_agent",
            content = Content(role = Role.MODEL, parts = listOf(thoughtPart, answerPart)),
        )

        val trace = listOf(userEvent, toolCallEvent, toolResponseEvent, finalEvent).toAgentTrace()

        assertThat(trace.finalResponse()).isEqualTo("It is 22°C and sunny in Paris.")
        assertThat(trace.reasoningSteps())
            .containsExactly("The user wants the weather in Paris; I have the tool result now.")
        assertThat(trace.toolCalls()).hasSize(1)

        val call = trace.toolCalls().first()
        assertThat(call.name()).isEqualTo("get_weather")
        assertThat(call.arguments()).containsEntry("city", "Paris").containsEntry("unit", "celsius")
        val parsedResult = call.resultAs(Map::class.java) as Map<String, Any>
        assertThat(parsedResult)
            .containsEntry("temp", 22)
            .containsEntry("condition", "sunny")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `matches repeated call IDs within their own turn window`() {
        val call1 = Event(
            id = "e1",
            author = "agent",
            content = Content(
                role = Role.MODEL,
                parts = listOf(
                    Part(
                        functionCall = FunctionCall(
                            id = "call_1",
                            name = "search",
                            args = mapOf("q" to "first"),
                        ),
                    ),
                ),
            ),
        )
        val resp1 = Event(
            id = "e2",
            author = "agent",
            content = Content(
                role = Role.USER,
                parts = listOf(
                    Part(
                        functionResponse = FunctionResponse(
                            id = "call_1",
                            name = "search",
                            response = mapOf("result" to "first-result"),
                        ),
                    ),
                ),
            ),
        )
        val call2 = Event(
            id = "e3",
            author = "agent",
            content = Content(
                role = Role.MODEL,
                parts = listOf(
                    Part(
                        functionCall = FunctionCall(
                            id = "call_1",
                            name = "search",
                            args = mapOf("q" to "second"),
                        ),
                    ),
                ),
            ),
        )
        val resp2 = Event(
            id = "e4",
            author = "agent",
            content = Content(
                role = Role.USER,
                parts = listOf(
                    Part(
                        functionResponse = FunctionResponse(
                            id = "call_1",
                            name = "search",
                            response = mapOf("result" to "second-result"),
                        ),
                    ),
                ),
            ),
        )

        val calls = AdkSupport.toToolCalls(listOf(call1, resp1, call2, resp2))

        assertThat(calls).hasSize(2)
        assertThat(calls[0].arguments()).containsEntry("q", "first")
        assertThat(calls[0].resultAs(Map::class.java) as Map<String, Any>).containsEntry("result", "first-result")
        assertThat(calls[1].arguments()).containsEntry("q", "second")
        assertThat(calls[1].resultAs(Map::class.java) as Map<String, Any>).containsEntry("result", "second-result")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `falls back to matching by name when call IDs are absent`() {
        val callEvent = Event(
            id = "e1",
            author = "agent",
            content = Content(
                role = Role.MODEL,
                parts = listOf(
                    Part(functionCall = FunctionCall(name = "lookup_user", args = mapOf("id" to 1))),
                    Part(functionCall = FunctionCall(name = "lookup_user", args = mapOf("id" to 2))),
                ),
            ),
        )
        val respEvent = Event(
            id = "e2",
            author = "agent",
            content = Content(
                role = Role.USER,
                parts = listOf(
                    Part(
                        functionResponse = FunctionResponse(
                            name = "lookup_user",
                            response = mapOf("name" to "Alice"),
                        ),
                    ),
                    Part(
                        functionResponse = FunctionResponse(
                            name = "lookup_user",
                            response = mapOf("name" to "Bob"),
                        ),
                    ),
                ),
            ),
        )

        val calls = AdkSupport.toToolCalls(listOf(callEvent, respEvent))

        assertThat(calls).hasSize(2)
        assertThat(calls[0].arguments()).containsEntry("id", 1)
        assertThat(calls[0].resultAs(Map::class.java) as Map<String, Any>).containsEntry("name", "Alice")
        assertThat(calls[1].arguments()).containsEntry("id", 2)
        assertThat(calls[1].resultAs(Map::class.java) as Map<String, Any>).containsEntry("name", "Bob")
    }

    @Test
    fun `ignores partial streaming events when non-partial events exist`() {
        val partialChunk = Event(
            id = "e1",
            author = "agent",
            partial = true,
            content = Content.fromText(Role.MODEL, "Hel"),
        )
        val fullEvent = Event(
            id = "e2",
            author = "agent",
            partial = false,
            content = Content.fromText(Role.MODEL, "Hello world!"),
        )

        val trace = AdkSupport.toAgentTrace(listOf(partialChunk, fullEvent))
        assertThat(trace.finalResponse()).isEqualTo("Hello world!")
    }

    @Test
    fun `extracts trace from Session using extension function`() {
        val event = Event(
            id = "e1",
            author = "assistant",
            content = Content.fromText(Role.MODEL, "Session reply"),
        )
        val session = Session(
            key = SessionKey(appName = "app", userId = "u1", id = "s1"),
            events = mutableListOf(event),
        )

        val trace = session.toAgentTrace()
        assertThat(trace.finalResponse()).isEqualTo("Session reply")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `runs real LlmAgent end-to-end with FunctionTool and evaluates with Dokimos agent evaluators`() {
        val weatherDeclaration = FunctionDeclaration(
            name = "get_weather",
            description = "Gets the current weather forecast for a given city and temperature unit.",
            parameters = Schema(
                type = Type.OBJECT,
                properties = mapOf(
                    "city" to Schema(type = Type.STRING, description = "The city name, e.g. Paris"),
                    "unit" to Schema(
                        type = Type.STRING,
                        description = "The temperature unit: celsius or fahrenheit",
                    ),
                ),
                required = listOf("city", "unit"),
            ),
        )
        val weatherTool = object : FunctionTool(
            name = "get_weather",
            description = "Gets the current weather forecast for a given city and temperature unit.",
        ) {
            override fun declaration(): FunctionDeclaration = weatherDeclaration

            override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any = mapOf(
                "city" to args["city"],
                "temp" to 22,
                "unit" to args["unit"],
                "condition" to "sunny",
            )
        }

        val step = AtomicInteger(0)
        val fakeModel = object : Model {
            override val name: String = "fake-gemini-3.8-flash"

            override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> {
                if (step.getAndIncrement() == 0) {
                    val call = FunctionCall(
                        id = "call-weather-1",
                        name = "get_weather",
                        args = mapOf("city" to "Paris", "unit" to "celsius"),
                    )
                    return flowOf(
                        LlmResponse(
                            content = Content(role = Role.MODEL, parts = listOf(Part(functionCall = call))),
                        ),
                    )
                }
                return flowOf(
                    LlmResponse(
                        content = Content.fromText(Role.MODEL, "Paris is 22°C and sunny."),
                    ),
                )
            }
        }

        val agent = LlmAgent(
            name = "weather_assistant",
            model = fakeModel,
            description = "Answers weather questions",
            instruction = Instruction("Use the get_weather tool to check the weather."),
            tools = listOf(weatherTool),
        )

        val task = AdkSupport.agentTask(agent)
        val outputs = task.run(Example.of("What is the weather in Paris?", "Paris is 22°C and sunny."))

        assertThat(outputs).containsEntry("output", "Paris is 22°C and sunny.")
        val toolCalls = outputs["toolCalls"] as List<ToolCall>
        assertThat(toolCalls).hasSize(1)
        assertThat(toolCalls[0].name()).isEqualTo("get_weather")
        assertThat(toolCalls[0].arguments()).containsEntry("city", "Paris").containsEntry("unit", "celsius")
        assertThat(toolCalls[0].resultAs(Map::class.java) as Map<String, Any>).containsEntry("temp", 22)

        val toolDefs = agent.toToolDefinitions()
        assertThat(toolDefs).hasSize(1)
        val def = toolDefs[0]
        assertThat(def.name()).isEqualTo("get_weather")
        assertThat(def.description())
            .isEqualTo("Gets the current weather forecast for a given city and temperature unit.")
        assertThat(def.parameterNames()).containsExactlyInAnyOrder("city", "unit")
        assertThat(def.parameterSchema("city")).containsEntry("type", "string")

        val testCase = EvalTestCase.builder()
            .input("What is the weather in Paris?")
            .actualOutputs(outputs)
            .expectedOutput(
                "toolCalls",
                listOf(ToolCall.of("get_weather", mapOf("city" to "Paris", "unit" to "celsius"))),
            )
            .metadata("tools", toolDefs)
            .build()

        val validityResult = ToolCallValidityEvaluator.builder().strictMode(true).build().evaluate(testCase)
        assertThat(validityResult.success()).isTrue()
        assertThat(validityResult.score()).isEqualTo(1.0)

        val trajectoryResult = ToolTrajectoryEvaluator.builder().build().evaluate(testCase)
        assertThat(trajectoryResult.success()).isTrue()
        assertThat(trajectoryResult.score()).isEqualTo(1.0)

        val errorResult = ToolErrorEvaluator.builder().build().evaluate(testCase)
        assertThat(errorResult.success()).isTrue()
        assertThat(errorResult.score()).isEqualTo(1.0)

        val descriptionResult = ToolDescriptionReliabilityEvaluator.builder().build().evaluate(testCase)
        assertThat(descriptionResult.success()).isTrue()
        assertThat(descriptionResult.score()).isEqualTo(1.0)

        val nameResult = ToolNameReliabilityEvaluator.builder().build().evaluate(testCase)
        assertThat(nameResult.success()).isTrue()
        assertThat(nameResult.score()).isEqualTo(1.0)
    }

    @Test
    fun `detects tool error in ADK FunctionResponse with ToolErrorEvaluator`() {
        val fc = FunctionCall(id = "c1", name = "charge_card", args = mapOf("amount" to 100))
        val fr = FunctionResponse(id = "c1", name = "charge_card", response = mapOf("error" to "Card declined"))

        val toolCall = AdkSupport.toToolCall(fc, fr)
        val testCase = EvalTestCase.builder()
            .input("Charge $100")
            .actualOutput("toolCalls", listOf(toolCall))
            .build()

        val result = ToolErrorEvaluator.builder().build().evaluate(testCase)
        assertThat(result.success()).isFalse()
        assertThat(result.score()).isEqualTo(0.0)
    }

    @Test
    fun `converts complex ADK Schema to lowercase JSON Schema map`() {
        val itemSchema = Schema(type = Type.STRING, description = "Tag name")
        val statusSchema = Schema(
            type = Type.STRING,
            description = "Filter by status",
            enum = listOf("OPEN", "CLOSED"),
        )
        val limitSchema = Schema(type = Type.INTEGER, description = "Max items")
        val tagsSchema = Schema(
            type = Type.ARRAY,
            description = "List of tags",
            items = itemSchema,
        )
        val rootSchema = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "status" to statusSchema,
                "limit" to limitSchema,
                "tags" to tagsSchema,
            ),
            required = listOf("status"),
        )

        val declaration = FunctionDeclaration(
            name = "list_issues",
            description = "Lists issues matching the filter",
            parameters = rootSchema,
        )

        val def = declaration.toToolDefinition()

        assertThat(def.name()).isEqualTo("list_issues")
        assertThat(def.description()).isEqualTo("Lists issues matching the filter")
        assertThat(def.requiredParameters()).containsExactly("status")
        assertThat(def.parameterNames()).containsExactlyInAnyOrder("status", "limit", "tags")
        assertThat(def.parameterSchema("status"))
            .containsEntry("type", "string")
            .containsEntry("enum", listOf("OPEN", "CLOSED"))
        assertThat(def.parameterSchema("limit")).containsEntry("type", "integer")
        assertThat(def.parameterSchema("tags")).containsEntry("type", "array")
    }

    @Test
    fun `handles tool without declaration gracefully`() {
        val customTool = object : BaseTool(name = "custom_ping", description = "Pings a host") {
            override fun declaration(): FunctionDeclaration? = null
            override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any = emptyMap<String, Any>()
        }
        val def = customTool.toToolDefinition()

        assertThat(def.name()).isEqualTo("custom_ping")
        assertThat(def.description()).isEqualTo("Pings a host")
        assertThat(def.inputSchema()).containsEntry("type", "object")
    }
}
