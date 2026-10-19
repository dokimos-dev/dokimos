package dev.dokimos.adk.kt

import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.events.Event
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.runners.Runner
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.GroundingChunk
import com.google.adk.kt.types.GroundingChunkRetrievedContext
import com.google.adk.kt.types.GroundingChunkWeb
import com.google.adk.kt.types.GroundingMetadata
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.UsageMetadata
import dev.dokimos.core.Example
import dev.dokimos.core.PriceTable
import dev.dokimos.core.conversation.ConversationTrajectory
import dev.dokimos.core.conversation.Message
import dev.dokimos.kotlin.dsl.experiment
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AdkSupportTest {

    private fun fakeModel(
        replyText: String,
        usage: UsageMetadata? = null,
        grounding: GroundingMetadata? = null,
        modelName: String = "gemini-3.8-flash",
    ): Model = object : Model {
        override val name: String = modelName

        override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flowOf(
            LlmResponse(
                content = Content.fromText(Role.MODEL, replyText),
                usageMetadata = usage,
                groundingMetadata = grounding,
            ),
        )
    }

    @Test
    fun `asJudge delegates to Model, Runner, BaseAgent, and suspend lambda`() {
        val model = fakeModel("""{"score": 1.0}""")
        val judge = model.asJudge()
        assertThat(judge.generate("Evaluate this")).isEqualTo("""{"score": 1.0}""")

        val agent = LlmAgent(
            name = "judge_agent",
            model = model,
            instruction = Instruction("Judge carefully"),
        )
        val agentJudge = agent.asJudge()
        assertThat(agentJudge.generate("Evaluate this")).isEqualTo("""{"score": 1.0}""")

        val suspendJudge = AdkSupport.asJudge { prompt -> "judged:$prompt" }
        assertThat(suspendJudge.generate("hello")).isEqualTo("judged:hello")
    }

    @Test
    fun `simpleTask and asyncTask produce expected outputs and integrate with Kotlin experiment DSL`() {
        val model = fakeModel("Paris")

        val llmTask = AdkSupport.simpleTask(model)
        assertThat(llmTask.run(Example.of("Capital of France?", "Paris"))).containsEntry("output", "Paris")

        val customKeyTask = AdkSupport.simpleTask(model, inputKey = "input", outputKey = "answer")
        assertThat(customKeyTask.run(Example.of("Capital of France?", "Paris"))).containsEntry("answer", "Paris")

        val asyncLlmTask = AdkSupport.asyncTask(model)
        assertThat(asyncLlmTask.run(Example.of("Capital of France?", "Paris")).join().outputs())
            .containsEntry("output", "Paris")

        val agent = LlmAgent(
            name = "qa_agent",
            model = model,
            instruction = Instruction("Answer concisely"),
        )

        val agentSimpleTask = AdkSupport.simpleTask(agent)
        assertThat(agentSimpleTask.run(Example.of("Capital of France?", "Paris")))
            .containsEntry("output", "Paris")

        val agentAsyncTask = AdkSupport.asyncTask(agent)
        assertThat(agentAsyncTask.run(Example.of("Capital of France?", "Paris")).join().outputs())
            .containsEntry("output", "Paris")

        val asyncAgentTraceTask = AdkSupport.asyncAgentTask(agent)
        assertThat(asyncAgentTraceTask.run(Example.of("Capital of France?", "Paris")).join().outputs())
            .containsEntry("output", "Paris")
            .containsKey("toolCalls")
            .containsKey("reasoningSteps")

        val expResult = experiment {
            name = "Kotlin DSL ADK Test"
            dataset {
                example {
                    input = "Capital of France?"
                    expected = "Paris"
                }
            }
            task(AdkSupport.agentTask(agent))
            evaluators {
                exactMatch()
            }
        }.run()

        assertThat(expResult.passRate()).isEqualTo(1.0)
    }

    @Test
    fun `asyncTask runs on provided Executor`() {
        val model = fakeModel("Executed on pool")
        val usedCustomExecutor = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor { r ->
            Thread {
                usedCustomExecutor.set(true)
                r.run()
            }
        }

        try {
            val task = AdkSupport.asyncTask(model, executor)
            val result = task.run(Example.of("hi", "hello")).join()
            assertThat(result.outputs()).containsEntry("output", "Executed on pool")
            assertThat(usedCustomExecutor.get()).isTrue()
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `asyncTask propagates errors cleanly with MockK`() {
        val failingRunner = mockk<Runner>()
        every {
            failingRunner.runAsync(any(), any(), any(), any(), any())
        } returns flow {
            throw IllegalStateException("ADK Kotlin runner failure")
        }

        val asyncTask = AdkSupport.asyncTask(failingRunner)
        assertThatThrownBy { asyncTask.run(Example.of("hi", "hello")).join() }
            .isInstanceOf(CompletionException::class.java)
            .hasCauseInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("ADK Kotlin runner failure")
    }

    @Test
    fun `measuredTasks capture tokens, latency, and cost with automatic modelId inference`() {
        val usage = UsageMetadata(
            promptTokenCount = 1_000,
            candidatesTokenCount = 500,
            totalTokenCount = 1_500,
        )
        val prices = PriceTable { modelId, tokensIn, tokensOut ->
            if (modelId != "gemini-3.8-flash" || tokensIn == null || tokensOut == null) {
                null
            } else {
                (tokensIn * 0.15 + tokensOut * 0.60) / 1_000_000.0
            }
        }

        val model = fakeModel("Measured output", usage = usage, modelName = "gemini-3.8-flash")
        val measuredLlm = AdkSupport.measuredTask(model, priceTable = prices)
        val llmResult = measuredLlm.run(Example.of("prompt", "expected"))

        assertThat(llmResult.outputs()).containsEntry("output", "Measured output")
        val metrics = llmResult.metrics()
        assertThat(metrics.tokensIn()).isEqualTo(1_000)
        assertThat(metrics.tokensOut()).isEqualTo(500)
        assertThat(metrics.latencyMs()).isGreaterThanOrEqualTo(0L)
        assertThat(metrics.costUsd()).isCloseTo(0.00045, within(1e-9))

        val agent = LlmAgent(
            name = "measured_agent",
            model = model,
            instruction = Instruction("Be helpful"),
        )

        // modelId is inferred automatically from agent.model.name ("gemini-3.8-flash")
        val measuredAgent = AdkSupport.measuredAgentTask(agent, priceTable = prices)
        val agentResult = measuredAgent.run(Example.of("prompt", "expected"))
        assertThat(agentResult.outputs()).containsEntry("output", "Measured output")
        assertThat(agentResult.metrics().tokensIn()).isEqualTo(1_000)
        assertThat(agentResult.metrics().tokensOut()).isEqualTo(500)
        assertThat(agentResult.metrics().costUsd()).isCloseTo(0.00045, within(1e-9))

        val measuredAsync = AdkSupport.measuredAsyncAgentTask(InMemoryRunner(agent = agent), priceTable = prices)
        val asyncResult = measuredAsync.run(Example.of("prompt", "expected")).join()
        assertThat(asyncResult.outputs()).containsEntry("output", "Measured output")
        assertThat(asyncResult.metrics().tokensIn()).isEqualTo(1_000)
        assertThat(asyncResult.metrics().tokensOut()).isEqualTo(500)
        assertThat(asyncResult.metrics().costUsd()).isCloseTo(0.00045, within(1e-9))
    }

    @Test
    fun `toCallMetrics handles null and zero usage`() {
        val nullMetrics = AdkSupport.toCallMetrics(null as UsageMetadata?, 42L, "gemini-3.8-flash", null)
        assertThat(nullMetrics.latencyMs()).isEqualTo(42L)
        assertThat(nullMetrics.tokensIn() as Any?).isNull()
        assertThat(nullMetrics.tokensOut() as Any?).isNull()
        assertThat(nullMetrics.costUsd() as Any?).isNull()

        val zeroUsage = UsageMetadata(promptTokenCount = 0, candidatesTokenCount = 0)
        val zeroMetrics = AdkSupport.toCallMetrics(zeroUsage, 15L, "gemini-3.8-flash", null)
        assertThat(zeroMetrics.tokensIn() as Any?).isNull()
        assertThat(zeroMetrics.tokensOut() as Any?).isNull()
    }

    @Test
    fun `converts multi-turn events and Session into ConversationTrajectory`() {
        val u1 = Event(
            id = "1",
            author = Role.USER,
            content = Content.fromText(Role.USER, "Book a flight to Tokyo"),
        )
        val a1Call = Event(
            id = "2",
            author = "travel_agent",
            content = Content(
                role = Role.MODEL,
                parts = listOf(
                    Part(
                        functionCall = FunctionCall(
                            id = "c1",
                            name = "search_flights",
                            args = mapOf("destination" to "Tokyo"),
                        ),
                    ),
                ),
            ),
        )
        val a1Resp = Event(
            id = "3",
            author = "travel_agent",
            content = Content(
                role = Role.USER,
                parts = listOf(
                    Part(
                        functionResponse = FunctionResponse(
                            id = "c1",
                            name = "search_flights",
                            response = mapOf("flights" to listOf("NH101", "JL202")),
                        ),
                    ),
                ),
            ),
        )
        val a1Reply = Event(
            id = "4",
            author = "travel_agent",
            content = Content.fromText(Role.MODEL, "I found NH101 and JL202. Which do you prefer?"),
        )
        val u2 = Event(
            id = "5",
            author = Role.USER,
            content = Content.fromText(Role.USER, "Book NH101 please"),
        )
        val a2Reply = Event(
            id = "6",
            author = "travel_agent",
            content = Content.fromText(Role.MODEL, "NH101 is booked for you!"),
        )

        val session = Session(
            key = SessionKey(appName = "travel", userId = "u1", id = "sess-1"),
            events = mutableListOf(u1, a1Call, a1Resp, a1Reply, u2, a2Reply),
        )

        val trajectory = session.toConversationTrajectory("Flight booking")

        assertThat(trajectory.scenario()).isEqualTo("Flight booking")
        assertThat(trajectory.turnCount()).isEqualTo(2)
        assertThat(trajectory.userMessages())
            .extracting<String> { it.content() }
            .containsExactly("Book a flight to Tokyo", "Book NH101 please")
        assertThat(trajectory.assistantMessages())
            .extracting<String> { it.content() }
            .containsExactly("I found NH101 and JL202. Which do you prefer?", "NH101 is booked for you!")
        assertThat(trajectory.toolCallsByTurn()[0]).hasSize(1)
        assertThat(trajectory.toolCallsByTurn()[0][0].name()).isEqualTo("search_flights")
        assertThat(trajectory.toolCallsByTurn()[1]).isEmpty()
    }

    @Test
    fun `asConversationalApp supports multi-turn conversation simulation`() {
        val turn = AtomicInteger(0)
        val multiTurnModel = object : Model {
            override val name: String = "fake-gemini"

            override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> {
                val current = turn.incrementAndGet()
                return flowOf(
                    LlmResponse(
                        content = Content.fromText(Role.MODEL, "Reply for turn $current"),
                    ),
                )
            }
        }

        val agent = LlmAgent(
            name = "dialog_agent",
            model = multiTurnModel,
            instruction = Instruction("Converse"),
        )

        val app = agent.asConversationalApp()

        val t1 = ConversationTrajectory.builder().userMessage("Hello").build()
        val r1 = app.respond(t1)
        assertThat(r1.content()).isEqualTo("Reply for turn 1")

        val t2 = t1.withMessage(r1).withMessage(Message.user("Follow-up question"))
        val r2 = app.respond(t2)
        assertThat(r2.content()).isEqualTo("Reply for turn 2")
    }

    @Test
    fun `extractTexts reads GroundingMetadata and tool responses`() {
        val grounding = GroundingMetadata(
            groundingChunks = listOf(
                GroundingChunk(
                    retrievedContext = GroundingChunkRetrievedContext(
                        text = "Refund policy: 30-day money-back guarantee.",
                    ),
                ),
                GroundingChunk(
                    web = GroundingChunkWeb(
                        title = "Dokimos Docs",
                        uri = "https://dokimos.dev",
                    ),
                ),
            ),
        )

        val groundedEvent = Event(
            id = "1",
            author = "rag_agent",
            groundingMetadata = grounding,
            content = Content.fromText(Role.MODEL, "We offer a 30-day refund."),
        )

        assertThat(AdkSupport.extractTexts(listOf(groundedEvent)))
            .containsExactly(
                "Refund policy: 30-day money-back guarantee.",
                "Dokimos Docs (https://dokimos.dev)",
            )

        val toolResponseEvent = Event(
            id = "2",
            author = "rag_agent",
            content = Content(
                role = Role.USER,
                parts = listOf(
                    Part(
                        functionResponse = FunctionResponse(
                            name = "search_kb",
                            response = mapOf("response" to listOf("Doc chunk A", "Doc chunk B")),
                        ),
                    ),
                ),
            ),
        )
        val unrelatedToolEvent = Event(
            id = "3",
            author = "rag_agent",
            content = Content(
                role = Role.USER,
                parts = listOf(
                    Part(
                        functionResponse = FunctionResponse(
                            name = "get_time",
                            response = mapOf("now" to "12:00"),
                        ),
                    ),
                ),
            ),
        )

        assertThat(AdkSupport.extractTexts(listOf(toolResponseEvent, unrelatedToolEvent), "search_kb"))
            .containsExactly("Doc chunk A", "Doc chunk B")

        assertThat(AdkSupport.extractTexts(listOf(toolResponseEvent)))
            .containsExactly("Doc chunk A", "Doc chunk B")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `ragTasks populate output, context, and metrics`() {
        val grounding = GroundingMetadata(
            groundingChunks = listOf(
                GroundingChunk(
                    retrievedContext = GroundingChunkRetrievedContext(
                        text = "Full refunds within 30 days of purchase.",
                    ),
                ),
            ),
        )
        val usage = UsageMetadata(promptTokenCount = 50, candidatesTokenCount = 25)
        val ragModel = fakeModel(
            replyText = "You can get a full refund within 30 days.",
            usage = usage,
            grounding = grounding,
        )

        val agent = LlmAgent(
            name = "rag_agent",
            model = ragModel,
            instruction = Instruction("Answer from context"),
        )

        val ragTask = AdkSupport.ragTask(agent)
        val syncOut = ragTask.run(Example.of("What is the refund policy?", "30 days"))
        assertThat(syncOut["output"]).isEqualTo("You can get a full refund within 30 days.")
        assertThat(syncOut["context"] as List<String>)
            .containsExactly("Full refunds within 30 days of purchase.")

        val customKeyTask = AdkSupport.ragTask(
            agent = agent,
            inputKey = "question",
            outputKey = "answer",
            contextKey = "retrievedContext",
        )
        val customOut = customKeyTask.run(
            Example.builder().input("question", "What is the refund policy?").build(),
        )
        assertThat(customOut["answer"]).isEqualTo("You can get a full refund within 30 days.")
        assertThat(customOut["retrievedContext"] as List<String>)
            .containsExactly("Full refunds within 30 days of purchase.")

        val measuredRag = AdkSupport.measuredRagTask(agent)
        val measuredResult = measuredRag.run(Example.of("What is the refund policy?", "30 days"))
        assertThat(measuredResult.outputs()["context"] as List<String>)
            .containsExactly("Full refunds within 30 days of purchase.")
        assertThat(measuredResult.metrics().tokensIn()).isEqualTo(50)
        assertThat(measuredResult.metrics().tokensOut()).isEqualTo(25)

        val asyncRag = AdkSupport.measuredAsyncRagTask(agent)
        val asyncResult = asyncRag.run(Example.of("What is the refund policy?", "30 days")).join()
        assertThat(asyncResult.outputs()["context"] as List<String>)
            .containsExactly("Full refunds within 30 days of purchase.")
        assertThat(asyncResult.metrics().tokensIn()).isEqualTo(50)
    }

    @Test
    fun `runTrace suspend extension runs agent in coroutine context`() = runTest {
        val model = fakeModel("Coroutine reply")
        val agent = LlmAgent(
            name = "coroutine_agent",
            model = model,
            instruction = Instruction("Answer"),
        )

        val trace = agent.runTrace("Hello from coroutine")
        assertThat(trace.finalResponse()).isEqualTo("Coroutine reply")
    }
}
