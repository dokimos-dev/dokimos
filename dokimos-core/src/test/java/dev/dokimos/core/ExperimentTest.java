package dev.dokimos.core;

import org.junit.jupiter.api.Test;

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
        assertThatThrownBy(() ->
                Experiment.builder()
                        .name("test")
                        .task(example -> Map.of())
                        .build()
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Dataset");
    }

    @Test
    void shouldRequireTask() {
        var dataset = Dataset.builder()
                .addExample(Example.of("q", "a"))
                .build();

        assertThatThrownBy(() ->
                Experiment.builder()
                        .name("test")
                        .dataset(dataset)
                        .build()
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Task");
    }
}