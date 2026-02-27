package dev.dokimos.kotlin.dsl.evaluators

import dev.dokimos.core.EvalTestCaseParam
import dev.dokimos.core.Evaluator
import dev.dokimos.core.JudgeLM
import dev.dokimos.core.MatchingStrategy
import dev.dokimos.core.evaluators.ContextualRelevanceEvaluator
import dev.dokimos.core.evaluators.ExactMatchEvaluator
import dev.dokimos.core.evaluators.FaithfulnessEvaluator
import dev.dokimos.core.evaluators.HallucinationEvaluator
import dev.dokimos.core.evaluators.LLMJudgeEvaluator
import dev.dokimos.core.evaluators.PrecisionEvaluator
import dev.dokimos.core.evaluators.RecallEvaluator
import dev.dokimos.core.evaluators.RegexEvaluator
import dev.dokimos.core.evaluators.agents.TaskCompletionEvaluator
import dev.dokimos.core.evaluators.agents.ToolArgumentHallucinationEvaluator
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator
import dev.dokimos.core.evaluators.agents.ToolCorrectnessEvaluator
import dev.dokimos.core.evaluators.agents.ToolDescriptionReliabilityEvaluator
import dev.dokimos.core.evaluators.agents.ToolNameReliabilityEvaluator
import dev.dokimos.kotlin.dsl.DokimosDsl

@DokimosDsl
class EvaluatorsDsl {
    private val evaluators: MutableList<Evaluator> = mutableListOf()

    fun exactMatch(block: ExactMatchEvaluatorDsl.() -> Unit = {}) {
        evaluators += ExactMatchEvaluatorDsl().apply(block).build()
    }

    fun regex(block: RegexEvaluatorDsl.() -> Unit) {
        evaluators += RegexEvaluatorDsl().apply(block).build()
    }

    fun llmJudge(judge: JudgeLM, block: LlmJudgeEvaluatorDsl.() -> Unit) {
        evaluators += LlmJudgeEvaluatorDsl(judge).apply(block).build()
    }

    fun hallucination(judge: JudgeLM, block: HallucinationEvaluatorDsl.() -> Unit) {
        evaluators += HallucinationEvaluatorDsl(judge).apply(block).build()
    }

    fun faithfulness(judge: JudgeLM, block: FaithfulnessEvaluatorDsl.() -> Unit) {
        evaluators += FaithfulnessEvaluatorDsl(judge).apply(block).build()
    }

    fun contextualRelevance(judge: JudgeLM, block: ContextualRelevanceEvaluatorDsl.() -> Unit) {
        evaluators += ContextualRelevanceEvaluatorDsl(judge).apply(block).build()
    }

    fun precision(block: PrecisionEvaluatorDsl.() -> Unit = {}) {
        evaluators += PrecisionEvaluatorDsl().apply(block).build()
    }

    fun recall(block: RecallEvaluatorDsl.() -> Unit = {}) {
        evaluators += RecallEvaluatorDsl().apply(block).build()
    }

    fun taskCompletion(judge: JudgeLM, block: TaskCompletionEvaluatorDsl.() -> Unit = {}) {
        evaluators += TaskCompletionEvaluatorDsl(judge).apply(block).build()
    }

    fun toolCallValidity(block: ToolCallValidityEvaluatorDsl.() -> Unit = {}) {
        evaluators += ToolCallValidityEvaluatorDsl().apply(block).build()
    }

    fun toolCorrectness(block: ToolCorrectnessEvaluatorDsl.() -> Unit = {}) {
        evaluators += ToolCorrectnessEvaluatorDsl().apply(block).build()
    }

    fun toolArgumentHallucination(judge: JudgeLM, block: ToolArgumentHallucinationEvaluatorDsl.() -> Unit = {}) {
        evaluators += ToolArgumentHallucinationEvaluatorDsl(judge).apply(block).build()
    }

    fun toolNameReliability(block: ToolNameReliabilityEvaluatorDsl.() -> Unit = {}) {
        evaluators += ToolNameReliabilityEvaluatorDsl().apply(block).build()
    }

    fun toolDescriptionReliability(block: ToolDescriptionReliabilityEvaluatorDsl.() -> Unit = {}) {
        evaluators += ToolDescriptionReliabilityEvaluatorDsl().apply(block).build()
    }

    fun evaluator(evaluator: Evaluator) {
        evaluators += evaluator
    }

    fun build(): List<Evaluator> = evaluators.toList()
}

@DokimosDsl
class ExactMatchEvaluatorDsl {
    var name: String = "Exact Match"
    var threshold: Double = 1.0
    var evaluationParams: List<EvalTestCaseParam> = listOf(
            EvalTestCaseParam.ACTUAL_OUTPUT,
            EvalTestCaseParam.EXPECTED_OUTPUT
    )

    fun params(vararg params: EvalTestCaseParam) {
        evaluationParams = params.toList()
    }

    fun build(): ExactMatchEvaluator = ExactMatchEvaluator.builder()
            .name(name)
            .threshold(threshold)
            .evaluationParams(evaluationParams)
            .build()
}

@DokimosDsl
class RegexEvaluatorDsl {
    var name: String = "Regex Match"
    var pattern: String = ""
    var ignoreCase: Boolean = false
    var threshold: Double = 1.0
    var evaluationParams: List<EvalTestCaseParam> = listOf(EvalTestCaseParam.ACTUAL_OUTPUT)

    fun params(vararg params: EvalTestCaseParam) {
        evaluationParams = params.toList()
    }

    fun build(): RegexEvaluator = RegexEvaluator.builder()
            .name(name)
            .pattern(pattern)
            .ignoreCase(ignoreCase)
            .threshold(threshold)
            .evaluationParams(evaluationParams)
            .build()
}

@DokimosDsl
class LlmJudgeEvaluatorDsl(private val judge: JudgeLM) {
    var name: String = "LLM Judge"
    var criteria: String = ""
    var evaluationParams: List<EvalTestCaseParam> = listOf(
            EvalTestCaseParam.INPUT,
            EvalTestCaseParam.ACTUAL_OUTPUT
    )
    var threshold: Double = 0.5
    var minScore: Double = 0.0
    var maxScore: Double = 1.0

    fun params(vararg params: EvalTestCaseParam) {
        evaluationParams = params.toList()
    }

    fun scoreRange(min: Double, max: Double) {
        minScore = min
        maxScore = max
    }

    fun build(): LLMJudgeEvaluator = LLMJudgeEvaluator.builder()
            .name(name)
            .criteria(criteria)
            .evaluationParams(evaluationParams)
            .threshold(threshold)
            .scoreRange(minScore, maxScore)
            .judge(judge)
            .build()
}

@DokimosDsl
class HallucinationEvaluatorDsl(private val judge: JudgeLM) {
    var name: String = "Hallucination"
    var contextKey: String = "context"
    var threshold: Double = 0.5
    var evaluationParams: List<EvalTestCaseParam> = listOf(
            EvalTestCaseParam.INPUT,
            EvalTestCaseParam.ACTUAL_OUTPUT
    )
    var includeReason: Boolean = true

    fun params(vararg params: EvalTestCaseParam) {
        evaluationParams = params.toList()
    }

    fun build(): HallucinationEvaluator = HallucinationEvaluator.builder()
            .name(name)
            .contextKey(contextKey)
            .threshold(threshold)
            .evaluationParams(evaluationParams)
            .judge(judge)
            .includeReason(includeReason)
            .build()
}

@DokimosDsl
class FaithfulnessEvaluatorDsl(private val judge: JudgeLM) {
    var name: String = "Faithfulness"
    var contextKey: String = "context"
    var threshold: Double = 0.8
    var evaluationParams: List<EvalTestCaseParam> = listOf(
            EvalTestCaseParam.INPUT,
            EvalTestCaseParam.ACTUAL_OUTPUT
    )
    var includeReason: Boolean = true

    fun params(vararg params: EvalTestCaseParam) {
        evaluationParams = params.toList()
    }

    fun build(): FaithfulnessEvaluator = FaithfulnessEvaluator.builder()
            .name(name)
            .contextKey(contextKey)
            .threshold(threshold)
            .evaluationParams(evaluationParams)
            .judge(judge)
            .includeReason(includeReason)
            .build()
}

@DokimosDsl
class ContextualRelevanceEvaluatorDsl(private val judge: JudgeLM) {
    var name: String = "ContextualRelevance"
    var retrievalContextKey: String = "retrievalContext"
    var threshold: Double = 0.5
    var strictMode: Boolean = false
    var evaluationParams: List<EvalTestCaseParam> = listOf(EvalTestCaseParam.INPUT)
    var includeReason: Boolean = true

    fun params(vararg params: EvalTestCaseParam) {
        evaluationParams = params.toList()
    }

    fun build(): ContextualRelevanceEvaluator = ContextualRelevanceEvaluator.builder()
            .name(name)
            .retrievalContextKey(retrievalContextKey)
            .threshold(threshold)
            .strictMode(strictMode)
            .evaluationParams(evaluationParams)
            .judge(judge)
            .includeReason(includeReason)
            .build()
}

@DokimosDsl
class PrecisionEvaluatorDsl {
    var name: String = "Precision"
    var retrievedKey: String = "retrieved"
    var expectedKey: String = "relevant"
    var threshold: Double = 0.5
    var matchingStrategy: MatchingStrategy = MatchingStrategy.byEquality()
    var evaluationParams: List<EvalTestCaseParam> = listOf(EvalTestCaseParam.INPUT)

    fun params(vararg params: EvalTestCaseParam) {
        evaluationParams = params.toList()
    }

    fun build(): PrecisionEvaluator = PrecisionEvaluator.builder()
            .name(name)
            .retrievedKey(retrievedKey)
            .expectedKey(expectedKey)
            .threshold(threshold)
            .matchingStrategy(matchingStrategy)
            .evaluationParams(evaluationParams)
            .build()
}

@DokimosDsl
class RecallEvaluatorDsl {
    var name: String = "Recall"
    var retrievedKey: String = "retrieved"
    var expectedKey: String = "relevant"
    var threshold: Double = 0.5
    var matchingStrategy: MatchingStrategy = MatchingStrategy.byEquality()
    var evaluationParams: List<EvalTestCaseParam> = listOf(EvalTestCaseParam.INPUT)

    fun params(vararg params: EvalTestCaseParam) {
        evaluationParams = params.toList()
    }

    fun build(): RecallEvaluator = RecallEvaluator.builder()
            .name(name)
            .retrievedKey(retrievedKey)
            .expectedKey(expectedKey)
            .threshold(threshold)
            .matchingStrategy(matchingStrategy)
            .evaluationParams(evaluationParams)
            .build()
}

// --- Agent Evaluator DSL classes ---

@DokimosDsl
class TaskCompletionEvaluatorDsl(private val judge: JudgeLM) {
    var name: String = "Task Completion"
    var threshold: Double = 0.5
    var tasksKey: String = "tasks"
    var constraintsKey: String = "constraints"
    var dialogKey: String = "input"
    var evaluationParams: List<EvalTestCaseParam> = listOf(EvalTestCaseParam.INPUT)

    fun params(vararg params: EvalTestCaseParam) {
        evaluationParams = params.toList()
    }

    fun build(): TaskCompletionEvaluator = TaskCompletionEvaluator.builder()
            .name(name)
            .threshold(threshold)
            .tasksKey(tasksKey)
            .constraintsKey(constraintsKey)
            .dialogKey(dialogKey)
            .evaluationParams(evaluationParams)
            .judge(judge)
            .build()
}

@DokimosDsl
class ToolCallValidityEvaluatorDsl {
    var name: String = "Tool Call Validity"
    var threshold: Double = 1.0
    var toolCallsKey: String = "toolCalls"
    var toolsKey: String = "tools"
    var strictMode: Boolean = false
    var evaluationParams: List<EvalTestCaseParam> = listOf()

    fun params(vararg params: EvalTestCaseParam) {
        evaluationParams = params.toList()
    }

    fun build(): ToolCallValidityEvaluator = ToolCallValidityEvaluator.builder()
            .name(name)
            .threshold(threshold)
            .toolCallsKey(toolCallsKey)
            .toolsKey(toolsKey)
            .strictMode(strictMode)
            .evaluationParams(evaluationParams)
            .build()
}

@DokimosDsl
class ToolCorrectnessEvaluatorDsl {
    var name: String = "Tool Correctness"
    var threshold: Double = 1.0
    var toolCallsKey: String = "toolCalls"
    var expectedToolCallsKey: String = "toolCalls"
    var matchMode: ToolCorrectnessEvaluator.MatchMode = ToolCorrectnessEvaluator.MatchMode.NAMES_ONLY
    var evaluationParams: List<EvalTestCaseParam> = listOf()

    fun params(vararg params: EvalTestCaseParam) {
        evaluationParams = params.toList()
    }

    fun build(): ToolCorrectnessEvaluator = ToolCorrectnessEvaluator.builder()
            .name(name)
            .threshold(threshold)
            .toolCallsKey(toolCallsKey)
            .expectedToolCallsKey(expectedToolCallsKey)
            .matchMode(matchMode)
            .evaluationParams(evaluationParams)
            .build()
}

@DokimosDsl
class ToolArgumentHallucinationEvaluatorDsl(private val judge: JudgeLM) {
    var name: String = "Tool Argument Hallucination"
    var threshold: Double = 0.8
    var toolCallsKey: String = "toolCalls"
    var evaluationParams: List<EvalTestCaseParam> = listOf(EvalTestCaseParam.INPUT)

    fun params(vararg params: EvalTestCaseParam) {
        evaluationParams = params.toList()
    }

    fun build(): ToolArgumentHallucinationEvaluator = ToolArgumentHallucinationEvaluator.builder()
            .name(name)
            .threshold(threshold)
            .toolCallsKey(toolCallsKey)
            .evaluationParams(evaluationParams)
            .judge(judge)
            .build()
}

@DokimosDsl
class ToolNameReliabilityEvaluatorDsl {
    var name: String = "Tool Name Reliability"
    var threshold: Double = 0.8
    var toolsKey: String = "tools"
    var judge: JudgeLM? = null
    var evaluationParams: List<EvalTestCaseParam> = listOf()

    fun params(vararg params: EvalTestCaseParam) {
        evaluationParams = params.toList()
    }

    fun build(): ToolNameReliabilityEvaluator {
        val builder = ToolNameReliabilityEvaluator.builder()
                .name(name)
                .threshold(threshold)
                .toolsKey(toolsKey)
                .evaluationParams(evaluationParams)
        judge?.let { builder.judge(it) }
        return builder.build()
    }
}

@DokimosDsl
class ToolDescriptionReliabilityEvaluatorDsl {
    var name: String = "Tool Description Reliability"
    var threshold: Double = 0.8
    var toolsKey: String = "tools"
    var judge: JudgeLM? = null
    var maxOptionalArgs: Int = 3
    var maxInputArgs: Int = 5
    var evaluationParams: List<EvalTestCaseParam> = listOf()

    fun params(vararg params: EvalTestCaseParam) {
        evaluationParams = params.toList()
    }

    fun build(): ToolDescriptionReliabilityEvaluator {
        val builder = ToolDescriptionReliabilityEvaluator.builder()
                .name(name)
                .threshold(threshold)
                .toolsKey(toolsKey)
                .maxOptionalArgs(maxOptionalArgs)
                .maxInputArgs(maxInputArgs)
                .evaluationParams(evaluationParams)
        judge?.let { builder.judge(it) }
        return builder.build()
    }
}
