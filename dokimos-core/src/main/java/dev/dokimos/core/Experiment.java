package dev.dokimos.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An evaluation experiment that runs a task against a dataset and evaluates the
 * results.
 * <p>
 * Experiments coordinate the execution of a task across dataset examples,
 * apply evaluators to the outputs, and aggregate results.
 */
public class Experiment {

    private static final Logger LOGGER = LoggerFactory.getLogger(Experiment.class);

    private final String name;
    private final String description;
    private final Dataset dataset;
    private final MeasuredTask task;
    private final AsyncTask asyncTask;
    private final List<Evaluator> evaluators;
    private final Map<String, Object> metadata;
    private final Reporter reporter;
    private final int parallelism;
    private final int runs;
    private final boolean autoCloseReporter;

    private Experiment(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.dataset = builder.dataset;
        this.task = builder.task;
        this.asyncTask = builder.asyncTask;
        this.evaluators = List.copyOf(builder.evaluators);
        this.metadata = Map.copyOf(builder.metadata);
        this.reporter = builder.reporter;
        this.parallelism = builder.parallelism;
        this.runs = builder.runs;
        this.autoCloseReporter = builder.autoCloseReporter;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Runs the experiment and returns the aggregated results.
     * <p>
     * If multiple runs are configured, the experiment executes each run
     * sequentially
     * and aggregates results across all runs. If parallelism is greater than 1,
     * examples within each run are processed concurrently.
     *
     * @return the experiment results
     */
    public ExperimentResult run() {
        List<RunResult> runResults = new ArrayList<>();

        try {
            for (int runIndex = 0; runIndex < runs; runIndex++) {
                RunResult runResult = executeSingleRun(runIndex);
                runResults.add(runResult);
            }
        } finally {
            if (autoCloseReporter) {
                reporter.close();
            }
        }

        return new ExperimentResult(name, description, metadata, runResults);
    }

    private RunResult executeSingleRun(int runIndex) {
        List<ItemResult> itemResults;
        RunHandle runHandle = reporter.startRun(name, metadata);
        RunStatus status = RunStatus.FAILED;

        try {
            if (asyncTask != null) {
                itemResults = executeAsync(runHandle);
            } else if (parallelism > 1) {
                itemResults = executeParallel(runHandle);
            } else {
                itemResults = executeSequential(runHandle);
            }
            status = RunStatus.SUCCESS;
        } finally {
            reporter.completeRun(runHandle, status);
            reporter.flush();
        }

        return new RunResult(runIndex, itemResults);
    }

    private List<ItemResult> executeSequential(RunHandle runHandle) {
        List<ItemResult> itemResults = new ArrayList<>();
        for (Example example : dataset) {
            ItemResult itemResult = runSingleExample(example);
            itemResults.add(itemResult);
            reporter.reportItem(runHandle, itemResult);
        }
        return itemResults;
    }

    private List<ItemResult> executeParallel(RunHandle runHandle) {
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        try {
            List<CompletableFuture<ItemResult>> futures = dataset.examples().stream()
                    .map(example -> CompletableFuture.supplyAsync(() -> runSingleExample(example), executor))
                    .toList();

            List<ItemResult> results =
                    futures.stream().map(CompletableFuture::join).toList();

            // Report items after completion to maintain ordering in reports
            results.forEach(itemResult -> reporter.reportItem(runHandle, itemResult));

            executor.shutdown();
            return results;
        } catch (RuntimeException | Error e) {
            // Forcibly terminate the pool on failure so worker threads do not leak.
            executor.shutdownNow();
            throw e;
        }
    }

    private ItemResult runSingleExample(Example example) {
        Map<String, Object> actualOutputs = null;
        try {
            TaskResult taskResult = task.run(example);
            actualOutputs = taskResult.outputs();
            return evaluate(example, actualOutputs, taskResult.metrics());
        } catch (RuntimeException e) {
            // Isolate per-example failures so one bad item does not abort the whole run.
            return failedItemResult(example, actualOutputs, e);
        }
    }

    /**
     * Executes examples via the configured {@link AsyncTask}, bounding the number of in-flight
     * invocations to {@code parallelism} with a {@link Semaphore}.
     * <p>
     * The semaphore is acquired before {@code asyncTask.run(...)} is called (the returned futures
     * are already running, so the cap must gate invocation) and released when each future settles.
     * Per-item failures use the same isolation as the synchronous paths: a failed future becomes a
     * failed {@link ItemResult} with empty eval results and the run continues. Dataset order is
     * preserved in the returned results.
     */
    private List<ItemResult> executeAsync(RunHandle runHandle) {
        Semaphore gate = new Semaphore(parallelism);

        List<CompletableFuture<ItemResult>> futures = dataset.examples().stream()
                .map(example -> {
                    gate.acquireUninterruptibly();
                    CompletableFuture<TaskResult> taskFuture;
                    try {
                        taskFuture = asyncTask.run(example);
                    } catch (RuntimeException e) {
                        // A synchronous throw from run(...) still isolates as a failed item.
                        gate.release();
                        return CompletableFuture.completedFuture(failedItemResult(example, null, e));
                    }
                    return taskFuture
                            .handle((taskResult, error) -> toItemResult(example, taskResult, error))
                            .whenComplete((itemResult, error) -> gate.release());
                })
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        List<ItemResult> results =
                futures.stream().map(CompletableFuture::join).toList();

        // Report items after completion to maintain ordering in reports.
        results.forEach(itemResult -> reporter.reportItem(runHandle, itemResult));

        return results;
    }

    private ItemResult toItemResult(Example example, TaskResult taskResult, Throwable error) {
        if (error != null) {
            Throwable cause = error instanceof CompletionException && error.getCause() != null
                    ? error.getCause()
                    : error;
            return failedItemResult(example, null, cause);
        }
        Map<String, Object> actualOutputs = taskResult.outputs();
        try {
            return evaluate(example, actualOutputs, taskResult.metrics());
        } catch (RuntimeException e) {
            return failedItemResult(example, actualOutputs, e);
        }
    }

    private ItemResult evaluate(Example example, Map<String, Object> actualOutputs, CallMetrics metrics) {
        EvalTestCase testCase = example.toTestCase(actualOutputs);

        List<EvalResult> evalResults = evaluators.stream()
                .map(evaluator -> evaluator.evaluate(testCase))
                .toList();

        return new ItemResult(example, actualOutputs, evalResults, metrics);
    }

    private ItemResult failedItemResult(Example example, Map<String, Object> actualOutputs, Throwable error) {
        LOGGER.warn("Evaluation failed for example, recording it as failed: {}", example.input(), error);
        return new ItemResult(example, actualOutputs, List.of());
    }

    public static class Builder {
        private final List<Evaluator> evaluators = new ArrayList<>();
        private final Map<String, Object> metadata = new HashMap<>();
        private String name = "unnamed";
        private String description = "";
        private Dataset dataset;
        private MeasuredTask task;
        private AsyncTask asyncTask;
        private Reporter reporter = NoOpReporter.INSTANCE;
        private int parallelism = 1;
        private int runs = 1;
        private boolean autoCloseReporter = false;

        /**
         * Sets the experiment name.
         *
         * @param name the experiment name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the description.
         *
         * @param description The experiment's description.
         * @return builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the dataset.
         *
         * @param dataset The dataset to use for the experiment.
         * @return builder
         */
        public Builder dataset(Dataset dataset) {
            this.dataset = dataset;
            return this;
        }

        /**
         * Sets the task.
         *
         * @param task The task to generate outputs from examples.
         * @return builder
         */
        public Builder task(Task task) {
            this.task = task != null ? example -> TaskResult.of(task.run(example)) : null;
            return this;
        }

        /**
         * Sets a measured task that carries {@link CallMetrics} through to each {@link ItemResult}.
         * <p>
         * Named distinctly from {@link #task(Task)} so a lambda passed to {@code task(...)} is never
         * ambiguous between the two functional interfaces.
         *
         * @param measuredTask The task to generate outputs and metrics from examples.
         * @return builder
         */
        public Builder measuredTask(MeasuredTask measuredTask) {
            this.task = measuredTask;
            return this;
        }

        /**
         * Sets an asynchronous task that produces a {@link TaskResult} as a
         * {@link java.util.concurrent.CompletableFuture}.
         * <p>
         * When an async task is set, the experiment runs through a dedicated non-blocking execution
         * path that bounds the number of in-flight invocations to {@link #parallelism(int)} using a
         * semaphore. This path takes precedence over {@link #parallelism(int)}-based parallel and
         * sequential execution. Per-item failures are isolated exactly as in the synchronous paths:
         * a failed future becomes a failed {@link ItemResult} and the run continues.
         * <p>
         * An async task satisfies the task requirement on its own; a synchronous {@link #task(Task)}
         * or {@link #measuredTask(MeasuredTask)} is not also required.
         *
         * @param asyncTask the asynchronous task to generate outputs and metrics from examples
         * @return builder
         */
        public Builder asyncTask(AsyncTask asyncTask) {
            this.asyncTask = asyncTask;
            return this;
        }

        /**
         * Adds a single evaluator to the experiment.
         *
         * @param evaluator The evaluator to add.
         * @return builder
         */
        public Builder evaluator(Evaluator evaluator) {
            this.evaluators.add(evaluator);
            return this;
        }

        /**
         * Adds multiple evaluators to the experiment.
         *
         * @param evaluators The list of evaluators to add.
         * @return builder
         */
        public Builder evaluators(List<Evaluator> evaluators) {
            this.evaluators.addAll(evaluators);
            return this;
        }

        /**
         * Adds a metadata entry to the experiment.
         *
         * @param key   The metadata key.
         * @param value The metadata value.
         * @return builder
         */
        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        /**
         * Adds multiple metadata entries to the experiment.
         *
         * @param metadata The map of metadata entries to add.
         * @return builder
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        /**
         * Sets the reporter for this experiment.
         * <p>
         * The reporter is called during experiment execution to report results
         * to an external system. If not set, a no-op reporter is used.
         *
         * @param reporter the reporter to use
         * @return builder
         */
        public Builder reporter(Reporter reporter) {
            this.reporter = reporter != null ? reporter : NoOpReporter.INSTANCE;
            return this;
        }

        /**
         * Sets the level of parallelism for running the experiment.
         * <p>
         * When parallelism is greater than 1, examples within each run are processed
         * concurrently using a fixed thread pool. Default is 1 for sequential
         * execution.
         * <p>
         * Ensure your task implementation is thread-safe when using parallelism.
         *
         * @param parallelism the number of examples to process in parallel, must be at
         *                    least 1
         * @return builder
         * @throws IllegalArgumentException if parallelism is less than 1
         */
        public Builder parallelism(int parallelism) {
            if (parallelism < 1) {
                throw new IllegalArgumentException("Parallelism must be at least 1, got: " + parallelism);
            }
            this.parallelism = parallelism;
            return this;
        }

        /**
         * Sets the number of times to run the experiment.
         * <p>
         * Running an experiment multiple times helps reduce variance from LLM
         * non-determinism
         * and provides statistical confidence in the results. Results are automatically
         * aggregated across runs.
         * <p>
         * Runs execute sequentially while parallelism applies within each run.
         *
         * @param runs the number of experiment runs, must be at least 1
         * @return builder
         * @throws IllegalArgumentException if runs is less than 1
         */
        public Builder runs(int runs) {
            if (runs < 1) {
                throw new IllegalArgumentException("Runs must be at least 1, got: " + runs);
            }
            this.runs = runs;
            return this;
        }

        /**
         * Controls whether {@link Experiment#run()} closes the reporter after the run completes.
         * <p>
         * When enabled, the reporter is closed (in addition to being flushed) once all runs
         * finish. Defaults to {@code false} so the caller retains ownership of the reporter
         * lifecycle.
         *
         * @param autoCloseReporter whether to close the reporter after the run completes
         * @return builder
         */
        public Builder autoCloseReporter(boolean autoCloseReporter) {
            this.autoCloseReporter = autoCloseReporter;
            return this;
        }

        /**
         * Builds the experiment.
         *
         * @return a new experiment
         * @throws IllegalStateException if dataset or task is not set, the dataset has no
         *                               examples, or no evaluators were added
         */
        public Experiment build() {
            if (dataset == null) {
                throw new IllegalStateException("Dataset is required");
            }
            if (task == null && asyncTask == null) {
                throw new IllegalStateException("Task is required");
            }
            if (dataset.examples().isEmpty()) {
                throw new IllegalStateException("Dataset must contain at least one example");
            }
            if (evaluators.isEmpty()) {
                throw new IllegalStateException("At least one evaluator is required");
            }

            return new Experiment(this);
        }
    }
}
