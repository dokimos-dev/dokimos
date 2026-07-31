package dev.dokimos.kotlin.dsl.conversation

import dev.dokimos.core.JudgeLM
import dev.dokimos.core.agents.ToolCall
import dev.dokimos.core.conversation.AggregationStrategy
import dev.dokimos.core.conversation.ConversationTrajectory
import dev.dokimos.core.conversation.ConversationalApplication
import dev.dokimos.core.conversation.EvaluationCriterion
import dev.dokimos.core.conversation.LLMSimulatedUser
import dev.dokimos.core.conversation.Message
import dev.dokimos.core.conversation.SimulatedUser
import dev.dokimos.core.conversation.TrajectoryEvaluator
import dev.dokimos.kotlin.core.EvalTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConversationDslTest {

    @Test
    fun `trajectory DSL builds with messages and metadata`() {
        val trajectory = trajectory {
            scenario = "sample"
            metadata("trace", "abc")
            metadata(mapOf("run" to 2))
            user("hi")
            assistant("hello")
            system("sys")
            messages(listOf(userMessage("another")))
        }

        assertThat(trajectory.scenario()).isEqualTo("sample")
        assertThat(trajectory.metadata()).containsEntry("trace", "abc").containsEntry("run", 2)
        assertThat(trajectory.messages()).hasSize(4)
        assertThat(trajectory.messages().map { it.role() }).containsExactly(
            Message.Role.USER,
            Message.Role.ASSISTANT,
            Message.Role.SYSTEM,
            Message.Role.USER,
        )
    }

    @Test
    fun `conversation helpers construct messages outside DSL`() {
        val user = userMessage("hi", mapOf("k" to 1))
        val assistant = assistantMessage("hey")
        val system = systemMessage("guidance")

        assertThat(user.role()).isEqualTo(Message.Role.USER)
        assertThat(user.metadata()["k"]).isEqualTo(1)
        assertThat(assistant.role()).isEqualTo(Message.Role.ASSISTANT)
        assertThat(system.role()).isEqualTo(Message.Role.SYSTEM)

        val trajectory = conversation(user, assistant, system, scenario = "s", metadata = mapOf("m" to true))
        assertThat(trajectory.messages()).containsExactly(user, assistant, system)
        assertThat(trajectory.scenario()).isEqualTo("s")
        assertThat(trajectory.metadata()["m"]).isEqualTo(true)
    }

    @Test
    fun `assistantMessage carries tool calls and metadata independently`() {
        val toolCall = ToolCall.of("search", mapOf("query" to "weather"))

        val message = assistantMessage("looking it up", listOf(toolCall), mapOf("turn" to 1))

        assertThat(message.role()).isEqualTo(Message.Role.ASSISTANT)
        assertThat(message.content()).isEqualTo("looking it up")
        assertThat(message.toolCalls()).containsExactly(toolCall)
        assertThat(message.metadata()).containsEntry("turn", 1)
        // metadata is independent of the tool calls
        assertThat(message.metadata()).doesNotContainKey("toolCalls")
    }

    @Test
    fun `conversation block exposes assistant tool calls on the trajectory`() {
        val toolCall = ToolCall.of("lookup", mapOf("id" to 42))

        val trajectory = trajectory {
            user("find order 42")
            assistant("here is order 42", listOf(toolCall))
        }

        assertThat(trajectory.toolCalls()).contains(toolCall)
        assertThat(trajectory.toolCallsByTurn()).containsExactly(listOf(toolCall))
    }

    @Test
    fun `simulator DSL wires initial message and stopping condition`() {
        val simulatedUser = object : SimulatedUser {
            override fun generateMessage(trajectory: ConversationTrajectory): Message =
                userMessage("auto-${trajectory.turnCount()}")
        }

        val application = ConversationalApplication { traj ->
            assistantMessage("reply-${traj.turnCount()}")
        }

        val simulator = simulator {
            this.simulatedUser = simulatedUser
            this.application = application
            maxTurns = 5
            scenario = "scenario"
            initialMessage = "start"
            stoppingCondition = { it.userMessages().size >= 1 }
        }

        val result = simulator.simulate()

        assertThat(result.scenario()).isEqualTo("scenario")
        // stopping condition triggers after first user message; assistant never added
        assertThat(result.messages()).hasSize(1)
        assertThat(result.messages().first().content()).isEqualTo("start")
    }

    @Test
    fun `llm user DSL uses fixed then dynamic responses`() {
        val judge = JudgeLM { _ -> " dynamic  " }

        val llmUser: LLMSimulatedUser = llmUser(judge) {
            persona = "customer"
            behaviorGuidelines = "be brief"
            fixedResponses(listOf("one", "two"))
        }

        val emptyTrajectory = ConversationTrajectory.empty()
        val afterOne = emptyTrajectory.withMessage(userMessage("seed"))
        val afterTwo = afterOne.withMessage(assistantMessage("reply"))
        val afterThree = afterTwo.withMessage(userMessage("follow-up"))

        val first = llmUser.generateMessage(emptyTrajectory)
        val second = llmUser.generateMessage(afterOne)
        val third = llmUser.generateMessage(afterThree)

        assertThat(first.content()).isEqualTo("one")
        assertThat(second.content()).isEqualTo("two")
        assertThat(third.content()).isEqualTo("dynamic")
    }

    @Test
    fun `trajectory evaluator DSL builds and evaluates`() {
        val criterion = EvaluationCriterion.of("Quality", "Check quality")
        val judge = JudgeLM { """{"score":0.9,"reason":"good"}""" }

        val evaluator: TrajectoryEvaluator = trajectoryEvaluator(judge) {
            name = "ConvEval"
            threshold = 0.5
            aggregationStrategy = AggregationStrategy.WEIGHTED_MEAN
            trajectoryKey = "trajectory"
            includePerCriterionScores = false
            criterion(criterion)
        }

        val trajectory = conversation(userMessage("hi"), assistantMessage("hello"))
        val testCase = EvalTestCase(
            input = "q",
            actualOutputs = mapOf("trajectory" to trajectory),
        )

        val result = evaluator.evaluate(testCase)

        assertThat(result.name()).isEqualTo("ConvEval")
        assertThat(result.score()).isEqualTo(0.9)
        assertThat(result.success()).isTrue()
    }

    @Test
    fun `golden generator DSL builds a dataset from scripted seeds`() {
        val application = ConversationalApplication { traj ->
            assistantMessage("reply to ${traj.lastUserMessage().content()}")
        }

        val dataset = goldenGenerator {
            this.application = application
            name = "support-goldens"
            seed {
                scenario = "return request"
                userTurns(listOf("I want a refund", "order #123"))
                metadata("suite", "support")
            }
            seed(
                scenarioSeed {
                    scenario = "greeting"
                    userTurn("hello")
                    expectedOutcome = "The agent greets back"
                    expectedOutput("output", "canonical greeting")
                },
            )
        }.generate()

        assertThat(dataset.name()).isEqualTo("support-goldens")
        assertThat(dataset.size()).isEqualTo(2)
        assertThat(dataset.examples()[0].input()).contains("order #123")
        assertThat(dataset.examples()[0].metadata())
            .containsEntry("suite", "support")
            .containsEntry("turnCount", 2)
        assertThat(dataset.examples()[1].expectedOutput()).isEqualTo("canonical greeting")
        assertThat(dataset.examples()[1].metadata()).containsEntry("expectedOutcome", "The agent greets back")
    }

    @Test
    fun `golden generator DSL seeds replaces the seeds added so far`() {
        val application = ConversationalApplication { traj ->
            assistantMessage("reply to ${traj.lastUserMessage().content()}")
        }

        val dataset = goldenGenerator {
            this.application = application
            seed {
                scenario = "discarded"
                userTurn("dropped")
            }
            seeds(
                listOf(
                    scenarioSeed {
                        scenario = "kept"
                        userTurn("hello")
                    },
                ),
            )
        }.generate()

        assertThat(dataset.size()).isEqualTo(1)
        assertThat(dataset.examples()[0].metadata()).containsEntry("scenario", "kept")
    }

    @Test
    fun `scenario seed DSL applies the persona factory with the generator judge`() {
        val judge = JudgeLM { "persona reply" }
        val application = ConversationalApplication { traj ->
            assistantMessage("reply to ${traj.lastUserMessage().content()}")
        }

        val dataset = goldenGenerator {
            this.application = application
            this.judge = judge
            maxTurns = 2
            seed {
                scenario = "curious user"
                initialMessage = "hi there"
                personaFactory = { judgeLM -> SimulatedUser { userMessage(judgeLM.generate("next")) } }
            }
        }.generate()

        assertThat(dataset.size()).isEqualTo(1)
        assertThat(dataset.examples()[0].input()).contains("hi there").contains("persona reply")
        assertThat(dataset.examples()[0].expectedOutputs()).doesNotContainKey("output")
    }
}
