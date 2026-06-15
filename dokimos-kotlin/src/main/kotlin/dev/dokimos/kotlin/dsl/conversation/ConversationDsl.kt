package dev.dokimos.kotlin.dsl.conversation

import dev.dokimos.core.JudgeLM
import dev.dokimos.core.agents.ToolCall
import dev.dokimos.core.conversation.AggregationStrategy
import dev.dokimos.core.conversation.ConversationSimulator
import dev.dokimos.core.conversation.ConversationTrajectory
import dev.dokimos.core.conversation.ConversationalApplication
import dev.dokimos.core.conversation.EvaluationCriterion
import dev.dokimos.core.conversation.LLMSimulatedUser
import dev.dokimos.core.conversation.Message
import dev.dokimos.core.conversation.SimulatedUser
import dev.dokimos.core.conversation.TrajectoryEvaluator
import dev.dokimos.kotlin.dsl.DokimosDsl
import java.util.function.Predicate

/**
 * Top-level DSL entrypoints for conversation simulation primitives.
 */
fun trajectory(block: ConversationTrajectoryDsl.() -> Unit): ConversationTrajectory =
    ConversationTrajectoryDsl().apply(block).build()

fun simulator(block: ConversationSimulatorDsl.() -> Unit): ConversationSimulator =
    ConversationSimulatorDsl().apply(block).build()

fun llmUser(judge: JudgeLM, block: LlmsimulatedUserDsl.() -> Unit = {}): LLMSimulatedUser =
    LlmsimulatedUserDsl(judge).apply(block).build()

fun trajectoryEvaluator(judge: JudgeLM, block: TrajectoryEvaluatorDsl.() -> Unit): TrajectoryEvaluator =
    TrajectoryEvaluatorDsl(judge).apply(block).build()

/** Convenience builders outside DSL blocks. */
fun message(role: Message.Role, content: String, metadata: Map<String, Any> = emptyMap()): Message =
    Message(role, content, metadata)

fun userMessage(content: String, metadata: Map<String, Any> = emptyMap()): Message =
    message(Message.Role.USER, content, metadata)

fun assistantMessage(content: String, metadata: Map<String, Any> = emptyMap()): Message =
    message(Message.Role.ASSISTANT, content, metadata)

/** Creates an assistant message carrying the tool calls it made on this turn. */
fun assistantMessage(content: String, toolCalls: List<ToolCall>, metadata: Map<String, Any> = emptyMap()): Message =
    Message(Message.Role.ASSISTANT, content, metadata, toolCalls)

fun systemMessage(content: String, metadata: Map<String, Any> = emptyMap()): Message =
    message(Message.Role.SYSTEM, content, metadata)

fun conversation(
    vararg messages: Message,
    scenario: String = "",
    metadata: Map<String, Any> = emptyMap(),
): ConversationTrajectory = ConversationTrajectory.builder()
    .scenario(scenario)
    .messages(messages.toList())
    .metadata(metadata)
    .build()

@DokimosDsl
class ConversationTrajectoryDsl {
    var scenario: String = ""

    private val messages: MutableList<Message> = mutableListOf()
    private val metadata: MutableMap<String, Any> = mutableMapOf()

    fun message(message: Message) {
        messages += message
    }

    fun messages(values: List<Message>) {
        messages += values
    }

    fun user(content: String, metadata: Map<String, Any> = emptyMap()) {
        message(userMessage(content, metadata))
    }

    fun assistant(content: String, metadata: Map<String, Any> = emptyMap()) {
        message(assistantMessage(content, metadata))
    }

    /** Adds an assistant turn carrying the tool calls it made. */
    fun assistant(content: String, toolCalls: List<ToolCall>) {
        message(assistantMessage(content, toolCalls))
    }

    fun system(content: String, metadata: Map<String, Any> = emptyMap()) {
        message(systemMessage(content, metadata))
    }

    fun metadata(key: String, value: Any) {
        metadata[key] = value
    }

    fun metadata(values: Map<String, Any>) {
        metadata.putAll(values)
    }

    fun build(): ConversationTrajectory = ConversationTrajectory.builder()
        .scenario(scenario)
        .messages(messages)
        .metadata(metadata)
        .build()
}

@DokimosDsl
class ConversationSimulatorDsl {
    var simulatedUser: SimulatedUser? = null
    var application: ConversationalApplication? = null
    var maxTurns: Int = 10
    var scenario: String = ""
    var initialMessage: String? = null
    var stoppingCondition: ((ConversationTrajectory) -> Boolean)? = null

    fun build(): ConversationSimulator {
        val selectedUser = simulatedUser ?: error("simulatedUser must be set")
        val selectedApplication = application ?: error("application must be set")

        val builder = ConversationSimulator.builder()
            .simulatedUser(selectedUser)
            .application(selectedApplication)
            .maxTurns(maxTurns)
            .scenario(scenario)

        initialMessage?.let { builder.initialMessage(it) }
        stoppingCondition?.let { predicate ->
            builder.stoppingCondition(Predicate { predicate(it) })
        }

        return builder.build()
    }
}

@DokimosDsl
class LlmsimulatedUserDsl(private val judge: JudgeLM) {
    var systemPrompt: String? = null
    var persona: String? = null
    var behaviorGuidelines: String? = null
    private val fixedResponses: MutableList<String> = mutableListOf()

    fun fixedResponse(response: String) {
        fixedResponses += response
    }

    fun fixedResponses(responses: List<String>) {
        fixedResponses.clear()
        fixedResponses.addAll(responses)
    }

    fun build(): LLMSimulatedUser {
        val builder = LLMSimulatedUser.builder()
            .judge(judge)

        systemPrompt?.let { builder.systemPrompt(it) }
        persona?.let { builder.persona(it) }
        behaviorGuidelines?.let { builder.behaviorGuidelines(it) }
        if (fixedResponses.isNotEmpty()) {
            builder.fixedResponses(fixedResponses)
        }

        return builder.build()
    }
}

@DokimosDsl
class TrajectoryEvaluatorDsl(private val judge: JudgeLM) {
    var name: String = "Trajectory"
    var threshold: Double = 0.7
    var aggregationStrategy: AggregationStrategy = AggregationStrategy.MEAN
    var trajectoryKey: String = "trajectory"
    var includePerCriterionScores: Boolean = true

    private val criteria: MutableList<EvaluationCriterion> = mutableListOf()

    fun criterion(criterion: EvaluationCriterion) {
        criteria += criterion
    }

    fun criteria(values: List<EvaluationCriterion>) {
        criteria += values
    }

    fun build(): TrajectoryEvaluator = TrajectoryEvaluator.builder()
        .name(name)
        .threshold(threshold)
        .judge(judge)
        .criteria(criteria)
        .aggregationStrategy(aggregationStrategy)
        .trajectoryKey(trajectoryKey)
        .includePerCriterionScores(includePerCriterionScores)
        .build()
}
