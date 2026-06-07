package dev.dokimos.core.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.Example;
import dev.dokimos.core.ExperimentResult;
import dev.dokimos.core.ItemResult;
import dev.dokimos.core.RunResult;
import dev.dokimos.core.comparison.RunComparison;
import dev.dokimos.core.comparison.RunComparisonResult;
import dev.dokimos.core.gate.BaselineFile.BaselineItem;
import dev.dokimos.core.internal.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BaselineStoreTest {

    private static EvalResult ev(String name, double score, double threshold, boolean pass) {
        return new EvalResult(name, score, threshold, pass, "", Map.of());
    }

    private static ItemResult item(String input, String expected, EvalResult... evals) {
        return new ItemResult(Example.of(input, expected), Map.of("output", "x"), List.of(evals));
    }

    private static ExperimentResult standard() {
        RunResult run = new RunResult(
                0,
                List.of(
                        item("What is 2+2?", "4", ev("correctness", 0.95, 0.7, true)),
                        item(
                                "Capital of France?",
                                "Paris",
                                ev("correctness", 0.40, 0.7, false),
                                ev("faithfulness", 0.88, 0.7, true)),
                        item("Largest planet?", "Jupiter", ev("correctness", 0.72, 0.7, true))));
        return new ExperimentResult("rag-eval", "", Map.of(), List.of(run));
    }

    @Test
    void projectionIsByteStableAcrossWrites(@TempDir Path dir) throws IOException {
        String a = Json.writePretty(BaselineStore.project(standard(), GateConfig.defaults()));
        String b = Json.writePretty(BaselineStore.project(standard(), GateConfig.defaults()));
        assertThat(a).isEqualTo(b);

        Path p = dir.resolve("rag.json");
        BaselineStore.write(p, standard(), GateConfig.defaults());
        String f1 = Files.readString(p);
        BaselineStore.write(p, standard(), GateConfig.defaults());
        String f2 = Files.readString(p);
        assertThat(f1).isEqualTo(f2);
    }

    @Test
    void roundTripReconstructionHasNoDeltas(@TempDir Path dir) throws IOException {
        ExperimentResult e = standard();
        Path p = dir.resolve("rag.json");
        BaselineStore.write(p, e, GateConfig.defaults());

        BaselineFile baseline = BaselineStore.read(p);
        assertThat(baseline.pairing()).isEqualTo("positional");

        List<RunResult> reconstructed = BaselineStore.toRunResults(baseline);
        RunComparisonResult result = RunComparison.create().compare(reconstructed, e.runResults());

        assertThat(result.hasRegressions()).isFalse();
        assertThat(result.passRateDelta()).isZero();
        assertThat(result.evaluatorDeltas()).isNotEmpty().allSatisfy(d -> assertThat(d.delta())
                .isZero());
    }

    @Test
    void writtenFileEqualsReReadProjection(@TempDir Path dir) throws IOException {
        Path p = dir.resolve("rag.json");
        BaselineStore.write(p, standard(), GateConfig.defaults());
        String content = Files.readString(p);
        BaselineFile baseline = BaselineStore.read(p);
        assertThat(Json.writePretty(baseline)).isEqualTo(content);
    }

    @Test
    void scoresAreRoundedToSixDecimals() {
        ExperimentResult e = new ExperimentResult(
                "e",
                "",
                Map.of(),
                List.of(new RunResult(0, List.of(item("q", "a", ev("correctness", 0.123456789, 0.7, false))))));
        BaselineFile baseline = BaselineStore.project(e, GateConfig.defaults());
        assertThat(baseline.items().get(0).evaluators().get(0).score()).isEqualTo(0.123457);
    }

    @Test
    void positionalItemsAreSortedNumericallyNotLexically() {
        List<ItemResult> items = new ArrayList<>();
        List<String> expectedKeys = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            items.add(item("q" + i, "a" + i, ev("correctness", 0.9, 0.7, true)));
            expectedKeys.add("item-" + i);
        }
        ExperimentResult e = new ExperimentResult("e", "", Map.of(), List.of(new RunResult(0, items)));
        BaselineFile baseline = BaselineStore.project(e, GateConfig.defaults());
        assertThat(baseline.items().stream().map(BaselineItem::key).toList()).isEqualTo(expectedKeys);
    }

    @Test
    void pairsByDatasetItemIdWhenEveryItemCarriesOne(@TempDir Path dir) throws IOException {
        Example exB = Example.builder()
                .input("input", "q-b")
                .expectedOutput("output", "a")
                .datasetItemId("id-b")
                .build();
        Example exA = Example.builder()
                .input("input", "q-a")
                .expectedOutput("output", "a")
                .datasetItemId("id-a")
                .build();
        ExperimentResult e = new ExperimentResult(
                "e",
                "",
                Map.of(),
                List.of(new RunResult(
                        0,
                        List.of(
                                new ItemResult(exB, Map.of("output", "x"), List.of(ev("correctness", 0.90, 0.7, true))),
                                new ItemResult(
                                        exA, Map.of("output", "x"), List.of(ev("correctness", 0.95, 0.7, true)))))));

        Path p = dir.resolve("ids.json");
        BaselineStore.write(p, e, GateConfig.defaults());
        BaselineFile baseline = BaselineStore.read(p);

        assertThat(baseline.pairing()).isEqualTo("dataset_item_id");
        assertThat(baseline.items().stream().map(BaselineItem::key).toList()).containsExactly("id-a", "id-b");

        RunComparisonResult result = RunComparison.builder()
                .itemKey(ir -> ir.example().datasetItemId())
                .build()
                .compare(BaselineStore.toRunResults(baseline), e.runResults());
        assertThat(result.hasRegressions()).isFalse();
        assertThat(result.passRateDelta()).isZero();
    }

    @Test
    void readRejectsNewerFormatVersion(@TempDir Path dir) throws IOException {
        Path p = dir.resolve("b.json");
        Files.writeString(p, """
                {"formatVersion":2,"experiment":"e","pairing":"positional","runsPerItem":1,
                 "items":[{"key":"item-0","fingerprint":"f","input":"a",
                   "evaluators":[{"name":"c","score":1.0,"threshold":0.7,"pass":true}]}]}
                """);
        assertThatThrownBy(() -> BaselineStore.read(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("newer");
    }

    @Test
    void readRejectsDuplicateKeys(@TempDir Path dir) throws IOException {
        Path p = dir.resolve("b.json");
        Files.writeString(p, """
                {"formatVersion":1,"experiment":"e","pairing":"positional","runsPerItem":1,
                 "items":[
                   {"key":"item-0","fingerprint":"f","input":"a",
                    "evaluators":[{"name":"c","score":1.0,"threshold":0.7,"pass":true}]},
                   {"key":"item-0","fingerprint":"f","input":"b",
                    "evaluators":[{"name":"c","score":1.0,"threshold":0.7,"pass":true}]}]}
                """);
        assertThatThrownBy(() -> BaselineStore.read(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void readRejectsItemWithNoEvaluators(@TempDir Path dir) throws IOException {
        Path p = dir.resolve("b.json");
        Files.writeString(p, """
                {"formatVersion":1,"experiment":"e","pairing":"positional","runsPerItem":1,
                 "items":[{"key":"item-0","fingerprint":"f","input":"a","evaluators":[]}]}
                """);
        assertThatThrownBy(() -> BaselineStore.read(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no evaluators");
    }
}
