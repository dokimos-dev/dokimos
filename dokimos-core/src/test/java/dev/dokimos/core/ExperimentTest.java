package dev.dokimos.core;

import dev.dokimos.core.evaluators.ExactMatchEvaluator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExperimentTest {

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
        var dataset = Dataset.builder()
                .addExample(Example.of("Hello", "Hi"))
                .build();

        var result = Experiment.builder()
                .name("greeting-test")
                .description("Testing greeting responses")
                .dataset(dataset)
                .task(example -> Map.of("output", "Hi there"))
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
                .build()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Dataset");
    }

    @Test
    void shouldRequireTask() {
        var dataset = Dataset.builder()
                .addExample(Example.of("q", "a"))
                .build();

        assertThatThrownBy(() -> Experiment.builder()
                .name("test")
                .dataset(dataset)
                .build()).isInstanceOf(IllegalStateException.class)
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
                .reporter(tracker)
                .build()
                .run();

        assertThat(tracker.calls).containsExactly(
                "startRun",
                "reportItem",
                "reportItem",
                "completeRun",
                "flush");
    }

    @Test
    void shouldPassExperimentNameAndMetadataToStartRun() {
        var dataset = Dataset.builder()
                .addExample(Example.of("q", "a"))
                .build();

        var tracker = new TrackingReporter();

        Experiment.builder()
                .name("my-experiment")
                .dataset(dataset)
                .task(example -> Map.of("output", "result"))
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
        var dataset = Dataset.builder()
                .addExample(Example.of("q", "a"))
                .build();

        var tracker = new TrackingReporter();

        Experiment.builder()
                .name("success-test")
                .dataset(dataset)
                .task(example -> Map.of("output", "result"))
                .reporter(tracker)
                .build()
                .run();

        assertThat(tracker.completeRunStatus).isEqualTo(RunStatus.SUCCESS);
    }

    @Test
    void shouldCompleteRunWithFailedOnException() {
        var dataset = Dataset.builder()
                .addExample(Example.of("q", "a"))
                .build();

        var tracker = new TrackingReporter();

        assertThatThrownBy(() -> Experiment.builder()
                .name("failure-test")
                .dataset(dataset)
                .task(example -> {
                    throw new RuntimeException("Task failed");
                })
                .reporter(tracker)
                .build()
                .run()).isInstanceOf(RuntimeException.class);

        assertThat(tracker.completeRunStatus).isEqualTo(RunStatus.FAILED);
        assertThat(tracker.calls).contains("completeRun", "flush");
    }

    @Test
    void shouldUseRunHandleForAllReporterCalls() {
        var dataset = Dataset.builder()
                .addExample(Example.of("q", "a"))
                .build();

        var tracker = new TrackingReporter();

        Experiment.builder()
                .name("handle-test")
                .dataset(dataset)
                .task(example -> Map.of("output", "result"))
                .reporter(tracker)
                .build()
                .run();

        assertThat(tracker.startRunHandle).isNotNull();
        assertThat(tracker.reportItemHandles).allMatch(h -> h.equals(tracker.startRunHandle));
        assertThat(tracker.completeRunHandle).isEqualTo(tracker.startRunHandle);
    }

    @Test
    void shouldWorkWithDefaultNoOpReporter() {
        var dataset = Dataset.builder()
                .addExample(Example.of("q", "a"))
                .build();

        var result = Experiment.builder()
                .name("noop-test")
                .dataset(dataset)
                .task(example -> Map.of("output", "result"))
                .build()
                .run();

        assertThat(result.totalCount()).isEqualTo(1);
    }

    @Test
    void shouldHandleNullReporter() {
        var dataset = Dataset.builder()
                .addExample(Example.of("q", "a"))
                .build();

        var result = Experiment.builder()
                .name("null-reporter-test")
                .dataset(dataset)
                .task(example -> Map.of("output", "result"))
                .reporter(null)
                .build()
                .run();

        assertThat(result.totalCount()).isEqualTo(1);
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