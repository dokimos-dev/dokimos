package dev.dokimos.kotlin.dsl

import dev.dokimos.core.Dataset
import dev.dokimos.core.Evaluator
import dev.dokimos.core.Example
import dev.dokimos.core.Experiment
import dev.dokimos.core.JudgeLM
import dev.dokimos.core.Reporter
import dev.dokimos.core.Task
import dev.dokimos.core.NoOpReporter
import dev.dokimos.core.evaluators.ExactMatchEvaluator
import dev.dokimos.core.evaluators.LLMJudgeEvaluator
import dev.dokimos.core.evaluators.RegexEvaluator
import dev.dokimos.core.EvalTestCaseParam

@DslMarker
annotation class DokimosDsl

fun experiment(block: ExperimentDsl.() -> Unit): Experiment =
    ExperimentDsl().apply(block).build()

fun dataset(block: DatasetDsl.() -> Unit): Dataset =
    DatasetDsl().apply(block).build()

fun example(block: ExampleDsl.() -> Unit): Example =
    ExampleDsl().apply(block).build()

fun task(block: (Example) -> Map<String, Any>): Task = Task(block)

fun llmJudge(judge: JudgeLM, block: LlmJudgeEvaluatorDsl.() -> Unit): LLMJudgeEvaluator =
    LlmJudgeEvaluatorDsl(judge).apply(block).build()



@DokimosDsl
class ExperimentDsl {
    var name: String = "unnamed"
    var description: String = ""
    var parallelism: Int = 1
    var runs: Int = 1
    var reporter: Reporter = NoOpReporter.INSTANCE

    private var dataset: Dataset? = null
    private var task: Task? = null
    private val evaluators: MutableList<Evaluator> = mutableListOf()
    private val metadata: MutableMap<String, Any> = mutableMapOf()

    fun dataset(block: DatasetDsl.() -> Unit) {
        dataset = DatasetDsl().apply(block).build()
    }

    fun dataset(value: Dataset) {
        dataset = value
    }

    fun task(block: (Example) -> Map<String, Any>) {
        task = Task(block)
    }

    fun task(value: Task) {
        task = value
    }

    fun evaluators(block: EvaluatorsDsl.() -> Unit) {
        evaluators += EvaluatorsDsl().apply(block).build()
    }

    fun evaluator(evaluator: Evaluator) {
        evaluators += evaluator
    }

    fun metadata(key: String, value: Any) {
        metadata[key] = value
    }

    fun metadata(values: Map<String, Any>) {
        metadata.putAll(values)
    }

    fun build(): Experiment {
        val selectedDataset = dataset ?: error("dataset must be set")
        val selectedTask = task ?: error("task must be set")

        val builder = Experiment.builder()
                .name(name)
                .description(description)
                .dataset(selectedDataset)
                .task(selectedTask)
                .evaluators(evaluators)
                .metadata(metadata)
                .reporter(reporter)
                .parallelism(parallelism)
                .runs(runs)

        return builder.build()
    }
}

@DokimosDsl
class DatasetDsl {
    var name: String = "unnamed"
    var description: String = ""

    private val examples: MutableList<Example> = mutableListOf()

    fun example(block: ExampleDsl.() -> Unit) {
        examples += ExampleDsl().apply(block).build()
    }

    fun example(value: Example) {
        examples += value
    }

    fun examples(values: List<Example>) {
        examples += values
    }

    fun build(): Dataset {
        return Dataset.builder()
                .name(name)
                .description(description)
                .addExamples(examples)
                .build()
    }
}

@DokimosDsl
class ExampleDsl {
    var input: String? = null
    var expected: String? = null

    private val inputs: MutableMap<String, Any> = mutableMapOf()
    private val expectedOutputs: MutableMap<String, Any> = mutableMapOf()
    private val metadata: MutableMap<String, Any> = mutableMapOf()

    fun input(key: String, value: Any) {
        inputs[key] = value
    }

    fun expected(key: String, value: Any) {
        expectedOutputs[key] = value
    }

    fun metadata(key: String, value: Any) {
        metadata[key] = value
    }

    fun metadata(vararg values: Pair<String, Any>) {
        metadata.putAll(values)
    }

    fun build(): Example {
        val builder = Example.builder()
        inputs.forEach { (k, v) -> builder.input(k, v) }
        expectedOutputs.forEach { (k, v) -> builder.expectedOutput(k, v) }
        metadata.forEach { (k, v) -> builder.metadata(k, v) }

        input?.let { builder.input("input", it) }
        expected?.let { builder.expectedOutput("output", it) }

        return builder.build()
    }
}

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
