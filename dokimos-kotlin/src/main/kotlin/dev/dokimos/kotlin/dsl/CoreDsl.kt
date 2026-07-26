package dev.dokimos.kotlin.dsl

import dev.dokimos.core.*
import dev.dokimos.core.evaluators.*
import dev.dokimos.core.evaluators.agents.*
import dev.dokimos.kotlin.dsl.evaluators.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

@DslMarker
annotation class DokimosDsl

fun evaluators(block: EvaluatorsDsl.() -> Unit): List<Evaluator> = EvaluatorsDsl().apply(block).build()

fun exactMatch(block: ExactMatchEvaluatorDsl.() -> Unit = {}): ExactMatchEvaluator =
    ExactMatchEvaluatorDsl().apply(block).build()

fun regex(block: RegexEvaluatorDsl.() -> Unit): RegexEvaluator = RegexEvaluatorDsl().apply(block).build()

fun llmJudge(judge: JudgeLM, block: LlmJudgeEvaluatorDsl.() -> Unit): LLMJudgeEvaluator =
    LlmJudgeEvaluatorDsl(judge).apply(block).build()

fun hallucination(judge: JudgeLM, block: HallucinationEvaluatorDsl.() -> Unit): HallucinationEvaluator =
    HallucinationEvaluatorDsl(judge).apply(block).build()

fun faithfulness(judge: JudgeLM, block: FaithfulnessEvaluatorDsl.() -> Unit): FaithfulnessEvaluator =
    FaithfulnessEvaluatorDsl(judge).apply(block).build()

fun contextualRelevance(
    judge: JudgeLM,
    block: ContextualRelevanceEvaluatorDsl.() -> Unit,
): ContextualRelevanceEvaluator = ContextualRelevanceEvaluatorDsl(judge).apply(block).build()

fun precision(block: PrecisionEvaluatorDsl.() -> Unit = {}): PrecisionEvaluator =
    PrecisionEvaluatorDsl().apply(block).build()

fun recall(block: RecallEvaluatorDsl.() -> Unit = {}): RecallEvaluator = RecallEvaluatorDsl().apply(block).build()

fun taskCompletion(judge: JudgeLM, block: TaskCompletionEvaluatorDsl.() -> Unit = {}): TaskCompletionEvaluator =
    TaskCompletionEvaluatorDsl(judge).apply(block).build()

fun toolCallValidity(block: ToolCallValidityEvaluatorDsl.() -> Unit = {}): ToolCallValidityEvaluator =
    ToolCallValidityEvaluatorDsl().apply(block).build()

fun toolCorrectness(block: ToolCorrectnessEvaluatorDsl.() -> Unit = {}): ToolCorrectnessEvaluator =
    ToolCorrectnessEvaluatorDsl().apply(block).build()

fun toolArgumentHallucination(
    judge: JudgeLM,
    block: ToolArgumentHallucinationEvaluatorDsl.() -> Unit = {
    },
): ToolArgumentHallucinationEvaluator = ToolArgumentHallucinationEvaluatorDsl(judge).apply(block).build()

fun toolNameReliability(block: ToolNameReliabilityEvaluatorDsl.() -> Unit = {}): ToolNameReliabilityEvaluator =
    ToolNameReliabilityEvaluatorDsl().apply(block).build()

fun toolTrajectory(block: ToolTrajectoryEvaluatorDsl.() -> Unit = {}): ToolTrajectoryEvaluator =
    ToolTrajectoryEvaluatorDsl().apply(block).build()

fun toolError(block: ToolErrorEvaluatorDsl.() -> Unit = {}): ToolErrorEvaluator =
    ToolErrorEvaluatorDsl().apply(block).build()

fun toolEfficiency(block: ToolEfficiencyEvaluatorDsl.() -> Unit = {}): ToolEfficiencyEvaluator =
    ToolEfficiencyEvaluatorDsl().apply(block).build()

fun toolDescriptionReliability(
    block: ToolDescriptionReliabilityEvaluatorDsl.() -> Unit = {
    },
): ToolDescriptionReliabilityEvaluator = ToolDescriptionReliabilityEvaluatorDsl().apply(block).build()

fun planQuality(block: PlanQualityEvaluatorDsl.() -> Unit = {}): PlanQualityEvaluator =
    PlanQualityEvaluatorDsl().apply(block).build()

fun planAdherence(block: PlanAdherenceEvaluatorDsl.() -> Unit = {}): PlanAdherenceEvaluator =
    PlanAdherenceEvaluatorDsl().apply(block).build()

// Experiment, dataset, example, task DSLs remain in root for familiarity
fun experiment(block: ExperimentDsl.() -> Unit): Experiment = ExperimentDsl().apply(block).build()

fun dataset(block: DatasetDsl.() -> Unit): Dataset = DatasetDsl().apply(block).build()

fun example(block: ExampleDsl.() -> Unit): Example = ExampleDsl().apply(block).build()

fun task(block: (Example) -> Map<String, Any>): Task = Task(block)

/**
 * Builds a [Task] that produces a single typed value and stores it under the conventional
 * `"output"` key, delegating to [Task.typed].
 *
 * Use `typedTask<Whisky> { ... }` to return a record, list, or other POJO directly. If the body
 * itself returns a [Map], that map is used as the output map directly (no double-nesting), matching
 * the Java [Task.typed] guard.
 *
 * @param fn produces the typed output value for an [Example]
 * @param T the produced value type
 * @return a [Task] wrapping the produced value under `"output"`
 */
inline fun <reified T> typedTask(crossinline fn: (Example) -> T): Task = Task.typed { example -> fn(example) }

/**
 * Builds an [AsyncTask] from a `suspend` function returning a [TaskResult], bridging the coroutine
 * to a [CompletableFuture] via the kotlinx-coroutines `future` builder.
 *
 * Produces an [AsyncTask] for the non-blocking experiment execution path. Each invocation launches
 * the suspend body on the given [scope] (the IO dispatcher by default). A suspend exception surfaces
 * as an exceptionally completed future, which the experiment isolates as a failed item.
 *
 * @param scope the coroutine scope used to launch each invocation
 * @param fn the suspend body producing a [TaskResult] for an [Example]
 * @return an [AsyncTask] suitable for [Experiment.Builder.asyncTask]
 */
@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
fun suspendTask(scope: CoroutineScope = GlobalScope, fn: suspend (Example) -> TaskResult): AsyncTask =
    AsyncTask { example ->
        scope.future(Dispatchers.IO) { fn(example) }
    }

/**
 * Builds an [AsyncTask] from a `suspend` function returning an output [Map], wrapping it in a
 * [TaskResult] with no metrics. Convenience overload of [suspendTask].
 *
 * @param scope the coroutine scope used to launch each invocation
 * @param fn the suspend body producing an output map for an [Example]
 * @return an [AsyncTask] suitable for [Experiment.Builder.asyncTask]
 */
@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
fun suspendMapTask(scope: CoroutineScope = GlobalScope, fn: suspend (Example) -> Map<String, Any>): AsyncTask =
    AsyncTask { example ->
        scope.future(Dispatchers.IO) { TaskResult.of(fn(example)) }
    }

@DokimosDsl
class ExperimentDsl {
    var name: String = "unnamed"
    var description: String = ""
    var parallelism: Int = 1
    var runs: Int = 1
    var reporter: Reporter = NoOpReporter.INSTANCE

    private var dataset: Dataset? = null
    private var task: Task? = null
    private var asyncTask: AsyncTask? = null
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

    /**
     * Sets a typed task that returns a single value stored under the `"output"` key. See the
     * top-level [typedTask] for the [Map]-return guard.
     */
    inline fun <reified T> typedTask(crossinline block: (Example) -> T) {
        task(Task.typed { example -> block(example) })
    }

    /**
     * Sets an [AsyncTask] directly, selecting the non-blocking experiment execution path.
     */
    fun asyncTask(value: AsyncTask) {
        asyncTask = value
    }

    /**
     * Sets an async task from a `suspend` function returning a [TaskResult]. See the top-level
     * [suspendTask] for bridging details.
     */
    fun suspendTask(scope: CoroutineScope = GlobalScope, block: suspend (Example) -> TaskResult) {
        asyncTask = dev.dokimos.kotlin.dsl.suspendTask(scope, block)
    }

    fun evaluators(block: EvaluatorsDsl.() -> Unit) {
        evaluators += EvaluatorsDsl().apply(block).build()
    }

    fun evaluator(evaluator: List<Evaluator>) {
        evaluators.addAll(evaluator)
    }

    fun evaluator(vararg evaluator: Evaluator) {
        evaluators.addAll(evaluator.toList())
    }

    fun metadata(key: String, value: Any) {
        metadata[key] = value
    }

    fun metadata(vararg values: Pair<String, Any>) {
        metadata.putAll(values)
    }

    fun metadata(values: Map<String, Any>) {
        metadata.putAll(values)
    }

    fun build(): Experiment {
        val selectedDataset = dataset ?: error("dataset must be set")

        val builder = Experiment.builder()
            .name(name)
            .description(description)
            .dataset(selectedDataset)
            .evaluators(evaluators)
            .metadata(metadata)
            .reporter(reporter)
            .parallelism(parallelism)
            .runs(runs)

        asyncTask?.let { builder.asyncTask(it) }
        task?.let { builder.task(it) }

        if (task == null && asyncTask == null) {
            error("task or asyncTask must be set")
        }

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

    fun build(): Dataset = Dataset.builder()
        .name(name)
        .description(description)
        .addExamples(examples)
        .build()
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

    fun metadata(values: Map<String, Any>) {
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
