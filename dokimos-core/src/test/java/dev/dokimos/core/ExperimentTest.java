package dev.dokimos.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.dokimos.core.evaluators.ExactMatchEvaluator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExperimentTest {

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
    void shouldRunTaskOnEachExample() {
        var dataset = Dataset.builder()
                .name("math-qa")
                .addExample(Example.of("What is 2+2?", "4"))
                .addExample(Example.of("What is 3*3?", "9"))
                .build();

        Task task = example -> Map.of("output", example.expectedOutput());

        var result = Experiment.builder()
                .name("math-experiment")
                .dataset(dataset)
                .task(task)
                .evaluator(passingEvaluator())
                .build()
                .run();

        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.itemResults().get(0).actualOutputs()).containsEntry("output", "4");
        assertThat(result.itemResults().get(1).actualOutputs()).containsEntry("output", "9");
    }

    @Test
    void shouldRunEvaluatorsOnEachResult() {
        var dataset = Dataset.builder()
                .addExample(Example.of("Capital of France?", "Paris"))
                .build();

        Task task = example -> Map.of("output", "Paris");

        Evaluator alwaysPass = new Evaluator() {
            @Override
            public EvalResult evaluate(EvalTestCase testCase) {
                return EvalResult.success("correctness", 0.9, "Correct");
            }

            @Override
            public String name() {
                return "correctness";
            }

            @Override
            public double threshold() {
                return 0.5;
            }
        };

        var result = Experiment.builder()
                .name("geography-test")
                .dataset(dataset)
                .task(task)
                .evaluator(alwaysPass)
                .build()
                .run();

        assertThat(result.itemResults().get(0).evalResults()).hasSize(1);
        assertThat(result.itemResults().get(0).evalResults().get(0).name()).isEqualTo("correctness");
    }

    @Test
    void shouldSupportMultipleEvaluators() {
        var dataset = Dataset.builder()
                .addExample(Example.of("Explain photosynthesis", "Plants convert sunlight to energy"))
                .build();

        Task task = example -> Map.of("output", "Plants use sunlight to make food");

        Evaluator correctness = new Evaluator() {
            @Override
            public EvalResult evaluate(EvalTestCase testCase) {
                return EvalResult.success("correctness", 0.8, "Semantically correct");
            }

            @Override
            public String name() {
                return "correctness";
            }

            @Override
            public double threshold() {
                return 0.5;
            }
        };

        Evaluator clarity = new Evaluator() {
            @Override
            public EvalResult evaluate(EvalTestCase testCase) {
                return EvalResult.success("clarity", 0.9, "Clear explanation");
            }

            @Override
            public String name() {
                return "clarity";
            }

            @Override
            public double threshold() {
                return 0.5;
            }
        };

        var result = Experiment.builder()
                .name("biology-qa")
                .dataset(dataset)
                .task(task)
                .evaluators(List.of(correctness, clarity))
                .build()
                .run();

        assertThat(result.itemResults().get(0).evalResults()).hasSize(2);
        assertThat(result.averageScore("correctness")).isEqualTo(0.8);
        assertThat(result.averageScore("clarity")).isEqualTo(0.9);
    }

    @Test
    void shouldPreserveMetadata() {
        var dataset = Dataset.builder().addExample(Example.of("Hello", "Hi")).build();

        var result = Experiment.builder()
                .name("greeting-test")
                .description("Testing greeting responses")
                .dataset(dataset)
                .task(example -> Map.of("output", "Hi there"))
                .evaluator(passingEvaluator())
                .metadata("model", "gpt-5")
                .metadata("promptVersion", "2.1")
                .build()
                .run();

        assertThat(result.name()).isEqualTo("greeting-test");
        assertThat(result.description()).isEqualTo("Testing greeting responses");
        assertThat(result.metadata()).containsEntry("model", "gpt-5");
        assertThat(result.metadata()).containsEntry("promptVersion", "2.1");
    }

    @Test
    void shouldRequireDataset() {
        assertThatThrownBy(() -> Experiment.builder()
                        .name("test")
                        .task(example -> Map.of())
                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Dataset");
    }

    @Test
    void shouldRequireTask() {
        var dataset = Dataset.builder().addExample(Example.of("q", "a")).build();

        assertThatThrownBy(
                        () -> Experiment.builder().name("test").dataset(dataset).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Task");
    }

    @Test
    void shouldCallReporterMethodsInCorrectOrder() {
        var dataset = Dataset.builder()
                .addExample(Example.of("q1", "a1"))
                .addExample(Example.of("q2", "a2"))
                .build();

        var tracker = new TrackingReporter();

        Experiment.builder()
                .name("reporter-test")
                .dataset(dataset)
                .task(example -> Map.of("output", example.expectedOutput()))
                .evaluator(passingEvaluator())
                .reporter(tracker)
                .build()
                .run();

        assertThat(tracker.calls).containsExactly("startRun", "reportItem", "reportItem", "completeRun", "flush");
    }

    @Test
    void shouldPassExperimentNameAndMetadataToStartRun() {
        var dataset = Dataset.builder().addExample(Example.of("q", "a")).build();

        var tracker = new TrackingReporter();

        Experiment.builder()
                .name("my-experiment")
                .dataset(dataset)
                .task(example -> Map.of("output", "result"))
                .evaluator(passingEvaluator())
                .metadata("model", "gpt-5")
                .metadata("version", "1.0")
                .reporter(tracker)
                .build()
                .run();

        assertThat(tracker.startRunName).isEqualTo("my-experiment");
        assertThat(tracker.startRunMetadata).containsEntry("model", "gpt-5");
        assertThat(tracker.startRunMetadata).containsEntry("version", "1.0");
    }

    @Test
    void shouldReportEachItemImmediatelyAfterEvaluation() {
        var dataset = Dataset.builder()
                .addExample(Example.of("q1", "a1"))
                .addExample(Example.of("q2", "a2"))
                .addExample(Example.of("q3", "a3"))
                .build();

        var tracker = new TrackingReporter();

        Experiment.builder()
                .name("item-test")
                .dataset(dataset)
                .task(example -> Map.of("output", example.expectedOutput()))
                .evaluator(new ExactMatchEvaluator.Builder().build())
                .reporter(tracker)
                .build()
                .run();

        assertThat(tracker.reportedItems).hasSize(3);
        assertThat(tracker.reportedItems.get(0).example().input()).isEqualTo("q1");
        assertThat(tracker.reportedItems.get(1).example().input()).isEqualTo("q2");
        assertThat(tracker.reportedItems.get(2).example().input()).isEqualTo("q3");
    }

    @Test
    void shouldCompleteRunWithSuccessOnNormalCompletion() {
        var dataset = Dataset.builder().addExample(Example.of("q", "a")).build();

        var tracker = new TrackingReporter();

        Experiment.builder()
                .name("success-test")
                .dataset(dataset)
                .task(example -> Map.of("output", "result"))
                .evaluator(passingEvaluator())
                .reporter(tracker)
                .build()
                .run();

        assertThat(tracker.completeRunStatus).isEqualTo(RunStatus.SUCCESS);
    }

    @Test
    void shouldIsolateTaskExceptionAndCompleteRun() {
        var dataset = Dataset.builder().addExample(Example.of("q", "a")).build();

        var tracker = new TrackingReporter();

        var result = Experiment.builder()
                .name("failure-test")
                .dataset(dataset)
                .task(example -> {
                    throw new RuntimeException("Task failed");
                })
                .evaluator(passingEvaluator())
                .reporter(tracker)
                .build()
                .run();

        // A failing item no longer aborts the run; it is recorded as unsuccessful.
        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.itemResults().get(0).success()).isFalse();
        assertThat(tracker.completeRunStatus).isEqualTo(RunStatus.SUCCESS);
        assertThat(tracker.calls).contains("reportItem", "completeRun", "flush");
    }

    @Test
    void shouldUseRunHandleForAllReporterCalls() {
        var dataset = Dataset.builder().addExample(Example.of("q", "a")).build();

        var tracker = new TrackingReporter();

        Experiment.builder()
                .name("handle-test")
                .dataset(dataset)
                .task(example -> Map.of("output", "result"))
                .evaluator(passingEvaluator())
                .reporter(tracker)
                .build()
                .run();

        assertThat(tracker.startRunHandle).isNotNull();
        assertThat(tracker.reportItemHandles).allMatch(h -> h.equals(tracker.startRunHandle));
        assertThat(tracker.completeRunHandle).isEqualTo(tracker.startRunHandle);
    }

    @Test
    void shouldWorkWithDefaultNoOpReporter() {
        var dataset = Dataset.builder().addExample(Example.of("q", "a")).build();

        var result = Experiment.builder()
                .name("noop-test")
                .dataset(dataset)
                .task(example -> Map.of("output", "result"))
                .evaluator(passingEvaluator())
                .build()
                .run();

        assertThat(result.totalCount()).isEqualTo(1);
    }

    @Test
    void shouldHandleNullReporter() {
        var dataset = Dataset.builder().addExample(Example.of("q", "a")).build();

        var result = Experiment.builder()
                .name("null-reporter-test")
                .dataset(dataset)
                .task(example -> Map.of("output", "result"))
                .evaluator(passingEvaluator())
                .reporter(null)
                .build()
                .run();

        assertThat(result.totalCount()).isEqualTo(1);
    }

    @Test
    void shouldRunWithParallelism() {
        var dataset = Dataset.builder()
                .addExample(Example.of("q1", "a1"))
                .addExample(Example.of("q2", "a2"))
                .addExample(Example.of("q3", "a3"))
                .build();

        var result = Experiment.builder()
                .name("parallel-test")
                .dataset(dataset)
                .task(example -> Map.of("output", example.expectedOutput()))
                .evaluator(passingEvaluator())
                .parallelism(2)
                .build()
                .run();

        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.itemResults()).hasSize(3);
    }

    @Test
    void shouldRunMultipleTimes() {
        var dataset = Dataset.builder().addExample(Example.of("q", "a")).build();

        var result = Experiment.builder()
                .name("multi-run-test")
                .dataset(dataset)
                .task(example -> Map.of("output", example.expectedOutput()))
                .evaluator(passingEvaluator())
                .runs(3)
                .build()
                .run();

        assertThat(result.runCount()).isEqualTo(3);
        assertThat(result.runs()).hasSize(3);
        assertThat(result.itemResults()).hasSize(3); // 1 example * 3 runs
    }

    @Test
    void shouldCombineParallelismAndMultipleRuns() {
        var dataset = Dataset.builder()
                .addExample(Example.of("q1", "a1"))
                .addExample(Example.of("q2", "a2"))
                .build();

        var result = Experiment.builder()
                .name("parallel-multi-run-test")
                .dataset(dataset)
                .task(example -> Map.of("output", example.expectedOutput()))
                .evaluator(passingEvaluator())
                .parallelism(2)
                .runs(2)
                .build()
                .run();

        assertThat(result.runCount()).isEqualTo(2);
        assertThat(result.totalCount()).isEqualTo(2); // Examples per run
        assertThat(result.itemResults()).hasSize(4); // 2 examples * 2 runs
    }

    @Test
    void shouldRejectInvalidParallelism() {
        assertThatThrownBy(() -> Experiment.builder().parallelism(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Parallelism must be at least 1");

        assertThatThrownBy(() -> Experiment.builder().parallelism(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Parallelism must be at least 1");
    }

    @Test
    void shouldRejectInvalidRuns() {
        assertThatThrownBy(() -> Experiment.builder().runs(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Runs must be at least 1");

        assertThatThrownBy(() -> Experiment.builder().runs(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Runs must be at least 1");
    }

    @Test
    void shouldReportForEachRun() {
        var dataset = Dataset.builder().addExample(Example.of("q", "a")).build();

        var tracker = new TrackingReporter();

        Experiment.builder()
                .name("multi-run-reporter-test")
                .dataset(dataset)
                .task(example -> Map.of("output", "result"))
                .evaluator(passingEvaluator())
                .reporter(tracker)
                .runs(2)
                .build()
                .run();

        // Should call startRun, reportItem, completeRun, flush twice
        assertThat(tracker.calls)
                .containsExactly(
                        "startRun",
                        "reportItem",
                        "completeRun",
                        "flush",
                        "startRun",
                        "reportItem",
                        "completeRun",
                        "flush");
    }

    @Test
    void shouldRunWhenTaskReturnsMapWithNullValue() {
        var dataset = Dataset.builder().addExample(Example.of("q", "a")).build();

        Task task = example -> {
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("output", null);
            return outputs;
        };

        var result = Experiment.builder()
                .name("null-value-test")
                .dataset(dataset)
                .task(task)
                .evaluator(passingEvaluator())
                .build()
                .run();

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.itemResults().get(0).actualOutputs()).containsKey("output");
        assertThat(result.itemResults().get(0).actualOutputs().get("output")).isNull();
    }

    @Test
    void shouldIsolateOneFailingExampleAndCompleteRunSequentially() {
        var result = runWithOneFailingExample(1);

        assertThat(result.itemResults()).hasSize(3);
        assertThat(result.itemResults().stream().filter(r -> !r.success()).count())
                .isEqualTo(1);
        assertThat(result.itemResults().stream().filter(ItemResult::success).count())
                .isEqualTo(2);
    }

    @Test
    void shouldIsolateOneFailingExampleAndCompleteRunInParallel() {
        var result = runWithOneFailingExample(2);

        assertThat(result.itemResults()).hasSize(3);
        assertThat(result.itemResults().stream().filter(r -> !r.success()).count())
                .isEqualTo(1);
        assertThat(result.itemResults().stream().filter(ItemResult::success).count())
                .isEqualTo(2);
    }

    private ExperimentResult runWithOneFailingExample(int parallelism) {
        var dataset = Dataset.builder()
                .addExample(Example.of("q1", "a1"))
                .addExample(Example.of("boom", "a2"))
                .addExample(Example.of("q3", "a3"))
                .build();

        Task task = example -> {
            if (example.input().equals("boom")) {
                throw new RuntimeException("task blew up");
            }
            return Map.of("output", example.expectedOutput());
        };

        return Experiment.builder()
                .name("isolation-test")
                .dataset(dataset)
                .task(task)
                .evaluator(passingEvaluator())
                .parallelism(parallelism)
                .build()
                .run();
    }

    @Test
    void shouldIsolateFailingEvaluator() {
        var dataset = Dataset.builder().addExample(Example.of("q", "a")).build();

        Evaluator throwing = new Evaluator() {
            @Override
            public EvalResult evaluate(EvalTestCase testCase) {
                throw new RuntimeException("evaluator blew up");
            }

            @Override
            public String name() {
                return "throwing";
            }

            @Override
            public double threshold() {
                return 0.5;
            }
        };

        var result = Experiment.builder()
                .name("evaluator-failure-test")
                .dataset(dataset)
                .task(example -> Map.of("output", "result"))
                .evaluator(throwing)
                .build()
                .run();

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.itemResults().get(0).success()).isFalse();
    }

    @Test
    void shouldCloseReporterWhenAutoCloseEnabled() {
        var dataset = Dataset.builder().addExample(Example.of("q", "a")).build();
        var reporter = new RecordingReporter();

        Experiment.builder()
                .name("auto-close-test")
                .dataset(dataset)
                .task(example -> Map.of("output", "result"))
                .evaluator(passingEvaluator())
                .reporter(reporter)
                .autoCloseReporter(true)
                .build()
                .run();

        assertThat(reporter.closed).isTrue();
    }

    @Test
    void shouldNotCloseReporterByDefault() {
        var dataset = Dataset.builder().addExample(Example.of("q", "a")).build();
        var reporter = new RecordingReporter();

        Experiment.builder()
                .name("no-auto-close-test")
                .dataset(dataset)
                .task(example -> Map.of("output", "result"))
                .evaluator(passingEvaluator())
                .reporter(reporter)
                .build()
                .run();

        assertThat(reporter.closed).isFalse();
    }

    @Test
    void shouldThrowOnEmptyDataset() {
        var dataset = Dataset.builder().name("empty").build();

        assertThatThrownBy(() -> Experiment.builder()
                        .name("empty-dataset-test")
                        .dataset(dataset)
                        .task(example -> Map.of())
                        .evaluator(passingEvaluator())
                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one example");
    }

    @Test
    void shouldThrowWhenNoEvaluators() {
        var dataset = Dataset.builder().addExample(Example.of("q", "a")).build();

        assertThatThrownBy(() -> Experiment.builder()
                        .name("no-evaluator-test")
                        .dataset(dataset)
                        .task(example -> Map.of())
                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("evaluator");
    }

    @Test
    void measuredTaskCarriesMetricsThroughToItemResult() {
        var dataset = Dataset.builder().addExample(Example.of("q", "a")).build();
        var metrics = new CallMetrics(10, 20, 0.5, 123L);

        MeasuredTask task = example -> new TaskResult(Map.of("output", "a"), metrics);

        var result = Experiment.builder()
                .name("measured")
                .dataset(dataset)
                .measuredTask(task)
                .evaluator(passingEvaluator())
                .build()
                .run();

        assertThat(result.itemResults()).hasSize(1);
        assertThat(result.itemResults().get(0).metrics()).isEqualTo(metrics);
        assertThat(result.itemResults().get(0).actualOutputs()).containsEntry("output", "a");
    }

    @Test
    void plainTaskStillWorksWithNullMetrics() {
        var dataset = Dataset.builder().addExample(Example.of("q", "a")).build();

        Task task = example -> Map.of("output", "a");

        var result = Experiment.builder()
                .name("plain")
                .dataset(dataset)
                .task(task)
                .evaluator(passingEvaluator())
                .build()
                .run();

        assertThat(result.itemResults()).hasSize(1);
        assertThat(result.itemResults().get(0).metrics()).isNull();
    }

    @Test
    void measuredTaskThatThrowsIsIsolated() {
        var dataset = Dataset.builder()
                .addExample(Example.of("ok", "a"))
                .addExample(Example.of("boom", "a"))
                .build();

        MeasuredTask task = example -> {
            if ("boom".equals(example.input())) {
                throw new RuntimeException("kaboom");
            }
            return new TaskResult(Map.of("output", "a"), new CallMetrics(1, 1, 0.0, 1L));
        };

        var result = Experiment.builder()
                .name("isolated")
                .dataset(dataset)
                .measuredTask(task)
                .evaluator(passingEvaluator())
                .build()
                .run();

        assertThat(result.itemResults()).hasSize(2);
        assertThat(result.itemResults().get(0).success()).isTrue();
        assertThat(result.itemResults().get(0).metrics()).isNotNull();
        assertThat(result.itemResults().get(1).success()).isFalse();
        assertThat(result.itemResults().get(1).evalResults()).isEmpty();
    }

    private static class RecordingReporter implements Reporter {
        final List<String> calls = new ArrayList<>();
        boolean closed = false;

        @Override
        public RunHandle startRun(String experimentName, Map<String, Object> metadata) {
            calls.add("startRun");
            return new RunHandle("recording-run-id");
        }

        @Override
        public void reportItem(RunHandle handle, ItemResult result) {
            calls.add("reportItem");
        }

        @Override
        public void completeRun(RunHandle handle, RunStatus status) {
            calls.add("completeRun");
        }

        @Override
        public void flush() {
            calls.add("flush");
        }

        @Override
        public void close() {
            calls.add("close");
            closed = true;
        }
    }

    /**
     * A test reporter that tracks all method calls and their arguments.
     */
    private static class TrackingReporter implements Reporter {
        final List<String> calls = new ArrayList<>();
        final List<ItemResult> reportedItems = new ArrayList<>();
        final List<RunHandle> reportItemHandles = new ArrayList<>();
        String startRunName;
        Map<String, Object> startRunMetadata;
        RunHandle startRunHandle;
        RunHandle completeRunHandle;
        RunStatus completeRunStatus;

        @Override
        public RunHandle startRun(String experimentName, Map<String, Object> metadata) {
            calls.add("startRun");
            this.startRunName = experimentName;
            this.startRunMetadata = metadata;
            this.startRunHandle = new RunHandle("test-run-id");
            return startRunHandle;
        }

        @Override
        public void reportItem(RunHandle handle, ItemResult result) {
            calls.add("reportItem");
            reportedItems.add(result);
            reportItemHandles.add(handle);
        }

        @Override
        public void completeRun(RunHandle handle, RunStatus status) {
            calls.add("completeRun");
            this.completeRunHandle = handle;
            this.completeRunStatus = status;
        }

        @Override
        public void flush() {
            calls.add("flush");
        }

        @Override
        public void close() {
            calls.add("close");
        }
    }
}
