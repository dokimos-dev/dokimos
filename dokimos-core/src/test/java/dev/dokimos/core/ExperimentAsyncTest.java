package dev.dokimos.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExperimentAsyncTest {

    private static Evaluator passingEvaluator() {
        return new Evaluator() {
            @Override
            public EvalResult evaluate(EvalTestCase testCase) {
                return EvalResult.success("noop", 1.0, "ok");
            }

            @Override
            public String name() {
                return "noop";
            }

            @Override
            public double threshold() {
                return 0.5;
            }
        };
    }

    @Test
    void asyncTaskHappyPathProducesResultsForEachExample() {
        var dataset = Dataset.builder()
                .addExample(Example.of("What is 2+2?", "4"))
                .addExample(Example.of("What is 3*3?", "9"))
                .build();

        AsyncTask task =
                example -> CompletableFuture.completedFuture(TaskResult.of(Map.of("output", example.expectedOutput())));

        var result = Experiment.builder()
                .name("async-happy")
                .dataset(dataset)
                .asyncTask(task)
                .evaluator(passingEvaluator())
                .build()
                .run();

        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.itemResults().get(0).actualOutputs()).containsEntry("output", "4");
        assertThat(result.itemResults().get(1).actualOutputs()).containsEntry("output", "9");
        assertThat(result.itemResults()).allMatch(ItemResult::success);
    }

    @Test
    void failedFutureBecomesFailedItemResultAndRunContinues() {
        var dataset = Dataset.builder()
                .addExample(Example.of("ok1", "a1"))
                .addExample(Example.of("boom", "a2"))
                .addExample(Example.of("ok3", "a3"))
                .build();

        AsyncTask task = example -> {
            if ("boom".equals(example.input())) {
                return CompletableFuture.failedFuture(new RuntimeException("async kaboom"));
            }
            return CompletableFuture.completedFuture(TaskResult.of(Map.of("output", example.expectedOutput())));
        };

        var result = Experiment.builder()
                .name("async-failure")
                .dataset(dataset)
                .asyncTask(task)
                .evaluator(passingEvaluator())
                .build()
                .run();

        assertThat(result.itemResults()).hasSize(3);
        assertThat(result.itemResults().get(0).success()).isTrue();
        assertThat(result.itemResults().get(1).success()).isFalse();
        assertThat(result.itemResults().get(1).evalResults()).isEmpty();
        assertThat(result.itemResults().get(2).success()).isTrue();
    }

    @Test
    void synchronousThrowFromAsyncTaskIsIsolated() {
        var dataset = Dataset.builder()
                .addExample(Example.of("ok", "a1"))
                .addExample(Example.of("boom", "a2"))
                .build();

        AsyncTask task = example -> {
            if ("boom".equals(example.input())) {
                throw new RuntimeException("sync throw from run");
            }
            return CompletableFuture.completedFuture(TaskResult.of(Map.of("output", example.expectedOutput())));
        };

        var result = Experiment.builder()
                .name("async-sync-throw")
                .dataset(dataset)
                .asyncTask(task)
                .evaluator(passingEvaluator())
                .build()
                .run();

        assertThat(result.itemResults()).hasSize(2);
        assertThat(result.itemResults().get(0).success()).isTrue();
        assertThat(result.itemResults().get(1).success()).isFalse();
        assertThat(result.itemResults().get(1).evalResults()).isEmpty();
    }

    @Test
    void measuredMetricsFlowFromTaskResultToItemResult() {
        var dataset = Dataset.builder().addExample(Example.of("q", "a")).build();
        var metrics = new CallMetrics(10, 20, 0.5, 123L);

        AsyncTask task = example -> CompletableFuture.completedFuture(new TaskResult(Map.of("output", "a"), metrics));

        var result = Experiment.builder()
                .name("async-metrics")
                .dataset(dataset)
                .asyncTask(task)
                .evaluator(passingEvaluator())
                .build()
                .run();

        assertThat(result.itemResults()).hasSize(1);
        assertThat(result.itemResults().get(0).metrics()).isEqualTo(metrics);
        assertThat(result.itemResults().get(0).actualOutputs()).containsEntry("output", "a");
    }

    @Test
    void executeAsyncCapsConcurrencyAtParallelism() {
        int parallelism = 3;
        int exampleCount = 20;

        var datasetBuilder = Dataset.builder();
        for (int i = 0; i < exampleCount; i++) {
            datasetBuilder.addExample(Example.of("q" + i, "a" + i));
        }
        var dataset = datasetBuilder.build();

        // Drive the futures off a separate pool so they actually overlap in time.
        ExecutorService pool = Executors.newFixedThreadPool(parallelism * 4);
        AtomicInteger inFlight = new AtomicInteger(0);
        AtomicInteger maxObserved = new AtomicInteger(0);

        try {
            AsyncTask task = example -> CompletableFuture.supplyAsync(
                    () -> {
                        int current = inFlight.incrementAndGet();
                        maxObserved.accumulateAndGet(current, Math::max);
                        try {
                            // Hold the slot long enough that the cap is exercised.
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            inFlight.decrementAndGet();
                        }
                        return TaskResult.of(Map.of("output", example.expectedOutput()));
                    },
                    pool);

            var result = Experiment.builder()
                    .name("async-concurrency-cap")
                    .dataset(dataset)
                    .asyncTask(task)
                    .evaluator(passingEvaluator())
                    .parallelism(parallelism)
                    .build()
                    .run();

            assertThat(result.totalCount()).isEqualTo(exampleCount);
            assertThat(maxObserved.get()).isLessThanOrEqualTo(parallelism);
            assertThat(maxObserved.get()).isGreaterThan(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void executeAsyncPreservesDatasetOrder() {
        int exampleCount = 30;
        var datasetBuilder = Dataset.builder();
        for (int i = 0; i < exampleCount; i++) {
            datasetBuilder.addExample(Example.of("q" + i, "a" + i));
        }
        var dataset = datasetBuilder.build();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            // Reverse the completion order: earlier examples sleep longer.
            AsyncTask task = example -> CompletableFuture.supplyAsync(
                    () -> {
                        int index = Integer.parseInt(example.input().substring(1));
                        try {
                            Thread.sleep((exampleCount - index));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return TaskResult.of(Map.of("output", example.expectedOutput()));
                    },
                    pool);

            var result = Experiment.builder()
                    .name("async-order")
                    .dataset(dataset)
                    .asyncTask(task)
                    .evaluator(passingEvaluator())
                    .parallelism(4)
                    .build()
                    .run();

            assertThat(result.itemResults()).hasSize(exampleCount);
            for (int i = 0; i < exampleCount; i++) {
                assertThat(result.itemResults().get(i).example().input()).isEqualTo("q" + i);
                assertThat(result.itemResults().get(i).actualOutputs()).containsEntry("output", "a" + i);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void asyncTaskSatisfiesTaskRequirement() {
        var dataset = Dataset.builder().addExample(Example.of("q", "a")).build();

        // No task(...) or measuredTask(...) set — asyncTask alone must satisfy build().
        var experiment = Experiment.builder()
                .name("async-only")
                .dataset(dataset)
                .asyncTask(example -> CompletableFuture.completedFuture(TaskResult.of(Map.of("output", "a"))))
                .evaluator(passingEvaluator())
                .build();

        assertThat(experiment.run().totalCount()).isEqualTo(1);
    }

    @Test
    void buildStillRequiresAtLeastATask() {
        var dataset = Dataset.builder().addExample(Example.of("q", "a")).build();

        assertThatThrownBy(() -> Experiment.builder()
                        .name("no-task")
                        .dataset(dataset)
                        .evaluator(passingEvaluator())
                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Task");
    }

    @Test
    void asyncTaskTakesPrecedenceOverSyncTask() {
        var dataset = Dataset.builder().addExample(Example.of("q", "a")).build();

        // A sync task that would throw, plus an async task that succeeds: the async path must win.
        var result = Experiment.builder()
                .name("async-precedence")
                .dataset(dataset)
                .task(example -> {
                    throw new RuntimeException("sync task should not run");
                })
                .asyncTask(example -> CompletableFuture.completedFuture(TaskResult.of(Map.of("output", "async"))))
                .evaluator(passingEvaluator())
                .build()
                .run();

        assertThat(result.itemResults()).hasSize(1);
        assertThat(result.itemResults().get(0).success()).isTrue();
        assertThat(result.itemResults().get(0).actualOutputs()).containsEntry("output", "async");
    }

    @Test
    void asyncRunReportsEachItemThroughReporter() {
        var dataset = Dataset.builder()
                .addExample(Example.of("q1", "a1"))
                .addExample(Example.of("q2", "a2"))
                .build();

        var tracker = new ItemTrackingReporter();

        Experiment.builder()
                .name("async-reporter")
                .dataset(dataset)
                .asyncTask(example ->
                        CompletableFuture.completedFuture(TaskResult.of(Map.of("output", example.expectedOutput()))))
                .evaluator(passingEvaluator())
                .reporter(tracker)
                .build()
                .run();

        assertThat(tracker.reportedInputs).containsExactly("q1", "q2");
    }

    @Test
    void asyncTaskReturningNullFutureIsIsolatedAsFailedItem() {
        var dataset = Dataset.builder()
                .addExample(Example.of("ok1", "a1"))
                .addExample(Example.of("nullfuture", "a2"))
                .addExample(Example.of("ok3", "a3"))
                .build();

        AsyncTask task = example -> {
            if ("nullfuture".equals(example.input())) {
                return null;
            }
            return CompletableFuture.completedFuture(TaskResult.of(Map.of("output", example.expectedOutput())));
        };

        // parallelism(1) means a leaked permit on the null-future branch would deadlock the run;
        // an inner timeout turns that hang into a test failure rather than a stuck build.
        var result = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> Experiment.builder()
                .name("async-null-future")
                .dataset(dataset)
                .asyncTask(task)
                .evaluator(passingEvaluator())
                .parallelism(1)
                .build()
                .run());

        assertThat(result.itemResults()).hasSize(3);
        assertThat(result.itemResults().get(0).success()).isTrue();
        assertThat(result.itemResults().get(1).success()).isFalse();
        assertThat(result.itemResults().get(1).evalResults()).isEmpty();
        assertThat(result.itemResults().get(2).success()).isTrue();
    }

    @Test
    void asyncFailuresAtTightCapDoNotDeadlock() {
        int exampleCount = 50;
        var datasetBuilder = Dataset.builder();
        for (int i = 0; i < exampleCount; i++) {
            datasetBuilder.addExample(Example.of("q" + i, "a" + i));
        }
        var dataset = datasetBuilder.build();

        // Every future completes exceptionally. With parallelism(1), a permit leak on the failure
        // branch of whenComplete would block forever in allOf(...).join() — the timeout catches it.
        AsyncTask task = example -> CompletableFuture.failedFuture(new RuntimeException("always fails"));

        var result = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> Experiment.builder()
                .name("async-all-fail-tight-cap")
                .dataset(dataset)
                .asyncTask(task)
                .evaluator(passingEvaluator())
                .parallelism(1)
                .build()
                .run());

        assertThat(result.itemResults()).hasSize(exampleCount);
        assertThat(result.itemResults()).allMatch(item -> !item.success());
    }

    @Test
    void asyncEvaluatorThrowIsIsolatedAsFailedItem() {
        var dataset = Dataset.builder()
                .addExample(Example.of("ok1", "a1"))
                .addExample(Example.of("evalboom", "a2"))
                .addExample(Example.of("ok3", "a3"))
                .build();

        // The task future succeeds for every example; the evaluator throws for one of them.
        // The failure originates inside evaluation on the completion stage, exercising the
        // separate try/catch in toItemResult rather than the failed-future branch.
        Evaluator throwingEvaluator = new Evaluator() {
            @Override
            public EvalResult evaluate(EvalTestCase testCase) {
                if ("evalboom".equals(testCase.input())) {
                    throw new RuntimeException("evaluator blew up");
                }
                return EvalResult.success("noop", 1.0, "ok");
            }

            @Override
            public String name() {
                return "noop";
            }

            @Override
            public double threshold() {
                return 0.5;
            }
        };

        AsyncTask task =
                example -> CompletableFuture.completedFuture(TaskResult.of(Map.of("output", example.expectedOutput())));

        var result = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> Experiment.builder()
                .name("async-eval-throw")
                .dataset(dataset)
                .asyncTask(task)
                .evaluator(throwingEvaluator)
                .build()
                .run());

        assertThat(result.itemResults()).hasSize(3);
        assertThat(result.itemResults().get(0).success()).isTrue();
        assertThat(result.itemResults().get(1).success()).isFalse();
        assertThat(result.itemResults().get(1).evalResults()).isEmpty();
        assertThat(result.itemResults().get(2).success()).isTrue();
    }

    @Test
    void asyncWithMultipleRunsProducesOneRunResultPerRunAndAggregates() {
        var dataset = Dataset.builder()
                .addExample(Example.of("q1", "a1"))
                .addExample(Example.of("q2", "a2"))
                .build();

        AsyncTask task =
                example -> CompletableFuture.completedFuture(TaskResult.of(Map.of("output", example.expectedOutput())));

        var result = Experiment.builder()
                .name("async-multi-run")
                .dataset(dataset)
                .asyncTask(task)
                .evaluator(passingEvaluator())
                .parallelism(2)
                .runs(3)
                .build()
                .run();

        assertThat(result.runs()).hasSize(3);
        assertThat(result.runs()).allSatisfy(run -> {
            assertThat(run.itemResults()).hasSize(2);
            assertThat(run.itemResults()).allMatch(ItemResult::success);
        });
        assertThat(result.totalCount()).isEqualTo(2);
        // All items pass in every run, so averaged pass count is the full dataset size.
        assertThat(result.passCount()).isEqualTo(2.0);
        assertThat(result.passRate()).isEqualTo(1.0);
    }

    @Test
    void asyncMixedRunReportsCorrectPassAndFailCounts() {
        var dataset = Dataset.builder()
                .addExample(Example.of("ok1", "a1"))
                .addExample(Example.of("boom1", "a2"))
                .addExample(Example.of("ok2", "a3"))
                .addExample(Example.of("boom2", "a4"))
                .addExample(Example.of("ok3", "a5"))
                .build();

        AsyncTask task = example -> {
            if (example.input().startsWith("boom")) {
                return CompletableFuture.failedFuture(new RuntimeException("async kaboom"));
            }
            return CompletableFuture.completedFuture(TaskResult.of(Map.of("output", example.expectedOutput())));
        };

        var result = Experiment.builder()
                .name("async-mixed-counts")
                .dataset(dataset)
                .asyncTask(task)
                .evaluator(passingEvaluator())
                .build()
                .run();

        assertThat(result.runs()).hasSize(1);
        RunResult run = result.runs().get(0);
        assertThat(run.totalCount()).isEqualTo(5);
        assertThat(run.passCount()).isEqualTo(3);
        assertThat(run.failCount()).isEqualTo(2);
        assertThat(run.passRate()).isEqualTo(0.6);

        // Failed items are built via the 3-arg ItemResult ctor, so their metrics are null.
        assertThat(run.itemResults().stream().filter(item -> !item.success()))
                .hasSize(2)
                .allSatisfy(item -> assertThat(item.metrics()).isNull());
    }

    @Test
    void executeParallelShutsDownNowWhenTaskThrows() throws InterruptedException {
        var dataset = Dataset.builder()
                .addExample(Example.of("q1", "a1"))
                .addExample(Example.of("q2", "a2"))
                .build();

        // A reporter that throws inside reportItem forces an exception on the parallel path
        // AFTER the executor's work has been submitted, exercising the shutdownNow() branch.
        Reporter throwingReporter = new Reporter() {
            @Override
            public RunHandle startRun(String experimentName, Map<String, Object> metadata) {
                return new RunHandle("id");
            }

            @Override
            public void reportItem(RunHandle handle, ItemResult result) {
                throw new RuntimeException("reporter blew up");
            }

            @Override
            public void completeRun(RunHandle handle, RunStatus status) {}

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };

        var threads = java.util.Collections.synchronizedList(new java.util.ArrayList<Thread>());

        var experiment = Experiment.builder()
                .name("shutdown-now")
                .dataset(dataset)
                .task(example -> {
                    threads.add(Thread.currentThread());
                    return Map.of("output", example.expectedOutput());
                })
                .evaluator(passingEvaluator())
                .reporter(throwingReporter)
                .parallelism(2)
                .build();

        assertThatThrownBy(experiment::run).hasMessageContaining("reporter blew up");

        // shutdownNow() must terminate the pool's worker threads; they should die promptly.
        boolean allDead = waitForThreadsToDie(threads, 5000);
        assertThat(allDead).isTrue();
    }

    private static boolean waitForThreadsToDie(List<Thread> threads, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            boolean anyAlive;
            synchronized (threads) {
                anyAlive = threads.stream().anyMatch(Thread::isAlive);
            }
            if (!anyAlive) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        synchronized (threads) {
            return threads.stream().noneMatch(Thread::isAlive);
        }
    }

    private static class ItemTrackingReporter implements Reporter {
        final List<String> reportedInputs = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        @Override
        public RunHandle startRun(String experimentName, Map<String, Object> metadata) {
            return new RunHandle("id");
        }

        @Override
        public void reportItem(RunHandle handle, ItemResult result) {
            reportedInputs.add(result.example().input());
        }

        @Override
        public void completeRun(RunHandle handle, RunStatus status) {}

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }
}
