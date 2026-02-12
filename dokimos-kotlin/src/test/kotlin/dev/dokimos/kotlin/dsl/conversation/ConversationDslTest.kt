package dev.dokimos.kotlin.dsl.conversation

import dev.dokimos.core.JudgeLM
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
            Message.Role.USER
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
            actualOutputs = mapOf("trajectory" to trajectory)
        )

        val result = evaluator.evaluate(testCase)

        assertThat(result.name()).isEqualTo("ConvEval")
        assertThat(result.score()).isEqualTo(0.9)
        assertThat(result.success()).isTrue()
    }
}
