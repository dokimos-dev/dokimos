package dev.dokimos.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * An evaluation experiment that runs a task against a dataset and evaluates the
 * results.
 * <p>
 * Experiments coordinate the execution of a task across dataset examples,
 * apply evaluators to the outputs, and aggregate results.
 */
public class Experiment {

    private final String name;
    private final String description;
    private final Dataset dataset;
    private final Task task;
    private final List<Evaluator> evaluators;
    private final Map<String, Object> metadata;
    private final Reporter reporter;
    private final int parallelism;
    private final int runs;

    private Experiment(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.dataset = builder.dataset;
        this.task = builder.task;
        this.evaluators = List.copyOf(builder.evaluators);
        this.metadata = Map.copyOf(builder.metadata);
        this.reporter = builder.reporter;
        this.parallelism = builder.parallelism;
        this.runs = builder.runs;
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

        for (int runIndex = 0; runIndex < runs; runIndex++) {
            RunResult runResult = executeSingleRun(runIndex);
            runResults.add(runResult);
        }

        return new ExperimentResult(name, description, metadata, runResults);
    }

    private RunResult executeSingleRun(int runIndex) {
        List<ItemResult> itemResults;
        RunHandle runHandle = reporter.startRun(name, metadata);
        RunStatus status = RunStatus.FAILED;

        try {
            if (parallelism > 1) {
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
                    .map(example -> CompletableFuture.supplyAsync(
                            () -> runSingleExample(example), executor))
                    .toList();

            List<ItemResult> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

            // Report items after completion to maintain ordering in reports
            results.forEach(itemResult -> reporter.reportItem(runHandle, itemResult));

            return results;
        } finally {
            executor.shutdown();
        }
    }

    private ItemResult runSingleExample(Example example) {
        Map<String, Object> actualOutputs = task.run(example);
        EvalTestCase testCase = example.toTestCase(actualOutputs);

        List<EvalResult> evalResults = evaluators.stream()
                .map(evaluator -> evaluator.evaluate(testCase))
                .toList();

        return new ItemResult(example, actualOutputs, evalResults);
    }

    public static class Builder {
        private final List<Evaluator> evaluators = new ArrayList<>();
        private final Map<String, Object> metadata = new HashMap<>();
        private String name = "unnamed";
        private String description = "";
        private Dataset dataset;
        private Task task;
        private Reporter reporter = NoOpReporter.INSTANCE;
        private int parallelism = 1;
        private int runs = 1;

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
            this.task = task;
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
         * Builds the experiment.
         *
         * @return a new experiment
         * @throws IllegalStateException if dataset or task is not set
         */
        public Experiment build() {
            if (dataset == null) {
                throw new IllegalStateException("Dataset is required");
            }
            if (task == null) {
                throw new IllegalStateException("Task is required");
            }

            return new Experiment(this);
        }
    }

}
