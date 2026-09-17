package dev.dokimos.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.dokimos.core.evaluators.ExactMatchEvaluator;
import dev.dokimos.core.evaluators.StructuralMatchEvaluator;
import dev.dokimos.core.evaluators.TypedEvaluator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TypedEvaluatorExperimentTest {

    record Output(String title, String category, List<String> sources) {}

    private static final Output ACTUAL = new Output("Cup final recap", "SPORTS", List.of("doc_1", "doc_2"));

    @Test
    void shouldKeepDistinctFieldResultsForTypedTaskAndJsonGolden() {
        var dataset = Dataset.fromJson("""
                {
                  "examples": [{
                    "input": "Summarize the final",
                    "expectedOutputs": {
                      "output": {
                        "title": "A different headline",
                        "category": "SPORTS",
                        "sources": ["doc_1", "doc_2"]
                      }
                    }
                  }]
                }
                """);

        assertThat(dataset.examples().get(0).expectedOutputs().get("output"))
                .isInstanceOf(Map.class)
                .isEqualTo(Map.of(
                        "title", "A different headline",
                        "category", "SPORTS",
                        "sources", List.of("doc_1", "doc_2")));

        var result = runExperiment(dataset);

        assertThat(result.itemResults()).singleElement().satisfies(item -> {
            assertThat(item.actualOutputs()).containsOnlyKeys("output").containsEntry("output", ACTUAL);
            assertThat(item.evalResults())
                    .extracting(EvalResult::name, EvalResult::score, EvalResult::success)
                    .containsExactly(
                            tuple("Category", 1.0, true), tuple("Title", 0.0, false), tuple("Sources", 1.0, true));
            assertThat(item.success()).isFalse();
        });
    }

    @Test
    void shouldPreserveSiblingResultsWhenGoldenProjectionFailsAndContinueToNextRow() {
        var dataset = Dataset.fromJson("""
                {
                  "examples": [
                    {
                      "input": "Malformed golden",
                      "expectedOutputs": {
                        "output": {"category": "SPORTS", "sources": ["doc_1", "doc_2"]}
                      }
                    },
                    {
                      "input": "Valid golden",
                      "expectedOutputs": {
                        "output": {
                          "title": "Cup final recap",
                          "category": "SPORTS",
                          "sources": ["doc_1", "doc_2"]
                        }
                      }
                    }
                  ]
                }
                """);

        var result = runExperiment(dataset);

        assertThat(result.itemResults()).hasSize(2);
        var malformed = result.itemResults().get(0);
        assertThat(malformed.example().input()).isEqualTo("Malformed golden");
        assertThat(malformed.actualOutputs()).containsEntry("output", ACTUAL);
        assertThat(malformed.evalResults())
                .extracting(EvalResult::name, EvalResult::score, EvalResult::success)
                .containsExactly(tuple("Category", 1.0, true), tuple("Title", 0.0, false), tuple("Sources", 1.0, true));
        assertThat(malformed.evalResults().get(1).reason()).contains("expected slot 'output'", "returned null");
        assertThat(malformed.evalResults().get(1).threshold()).isEqualTo(0.7);
        assertThat(malformed.success()).isFalse();

        var valid = result.itemResults().get(1);
        assertThat(valid.example().input()).isEqualTo("Valid golden");
        assertThat(valid.actualOutputs()).containsEntry("output", ACTUAL);
        assertThat(valid.evalResults())
                .extracting(EvalResult::name, EvalResult::score, EvalResult::success)
                .containsExactly(tuple("Category", 1.0, true), tuple("Title", 1.0, true), tuple("Sources", 1.0, true));
        assertThat(valid.success()).isTrue();
    }

    private static ExperimentResult runExperiment(Dataset dataset) {
        return Experiment.builder()
                .name("Typed field regression")
                .dataset(dataset)
                .task(Task.typed(example -> ACTUAL))
                .evaluator(TypedEvaluator.of(Output.class)
                        .name("Category")
                        .extracting(Output::category)
                        .evaluateWith(ExactMatchEvaluator.builder().build()))
                .evaluator(TypedEvaluator.of(Output.class)
                        .name("Title")
                        .extracting(Output::title)
                        .evaluateWith(
                                ExactMatchEvaluator.builder().threshold(0.7).build()))
                .evaluator(TypedEvaluator.of(Output.class)
                        .name("Sources")
                        .extracting(Output::sources)
                        .evaluateWith(StructuralMatchEvaluator.builder().build()))
                .build()
                .run();
    }
}
