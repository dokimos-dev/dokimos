package dev.dokimos.examples.gate;

import dev.dokimos.core.Assertions;
import dev.dokimos.core.Dataset;
import dev.dokimos.core.Evaluator;
import dev.dokimos.core.Example;
import dev.dokimos.core.Experiment;
import dev.dokimos.core.ExperimentResult;
import dev.dokimos.core.Task;
import dev.dokimos.core.evaluators.ExactMatchEvaluator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The server-free regression gate as a plain unit test. No server, no API key, no LLM — it runs in
 * normal CI on every JDK.
 *
 * <p>The loop is four steps:
 *
 * <ol>
 *   <li>Build a {@link Dataset} of input/expected pairs.
 *   <li>Write a {@link Task} that produces your system's output for each input.
 *   <li>Pick an {@link Evaluator}. {@link ExactMatchEvaluator} is deterministic; swap in an LLM judge
 *       for open-ended outputs.
 *   <li>Run the {@link Experiment} and call {@link Assertions#assertNoRegression}, which compares the
 *       result against {@code src/test/resources/dokimos/baselines/gate-example.json} and throws on a
 *       regression. The failing assertion is the gate — it fires the same way on every runner.
 * </ol>
 *
 * <p>The committed baseline is what makes this green in CI: with the file present the gate takes the
 * compare path instead of bootstrapping. To accept an intended change, re-run with {@code
 * DOKIMOS_UPDATE_BASELINE=true} and commit the regenerated baseline.
 */
class RegressionGateExampleTest {

    @Test
    void gatePassesAgainstCommittedBaseline() {
        Dataset dataset = Dataset.builder()
                .name("Gate Example QA")
                .description("Deterministic QA pairs for the server-free regression gate example")
                .addExample(Example.of("What is 2+2?", "4"))
                .addExample(Example.of("What is the capital of France?", "Paris"))
                .addExample(Example.of("What is the capital of Switzerland?", "Bern"))
                .addExample(Example.of("What is the capital of Japan?", "Tokyo"))
                .build();

        Task task = example -> Map.of("output", answerFor(example.input()));

        Evaluator exactMatch =
                ExactMatchEvaluator.builder().name("Exact Match").threshold(1.0).build();

        ExperimentResult result = Experiment.builder()
                .name("gate-example")
                .dataset(dataset)
                .task(task)
                .evaluators(List.of(exactMatch))
                .build()
                .run();

        // The committed baseline pins every case to pass; assertNoRegression is the gate.
        Assertions.assertNoRegression(result, "gate-example");
    }

    /** Stands in for the system under test. Each answer matches the dataset's expected output. */
    private static String answerFor(String question) {
        return switch (question) {
            case "What is 2+2?" -> "4";
            case "What is the capital of France?" -> "Paris";
            case "What is the capital of Switzerland?" -> "Zurich";
            case "What is the capital of Japan?" -> "Tokyo";
            default -> "I don't know";
        };
    }
}
