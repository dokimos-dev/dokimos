package dev.dokimos.core.conversation;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.dokimos.core.Dataset;
import dev.dokimos.core.Example;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.internal.Json;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenGeneratorTest {

    private static final ConversationalApplication ECHO_APP = trajectory ->
            Message.assistant("Reply to: " + trajectory.lastUserMessage().content());

    private static GoldenGenerator.Builder scriptedGenerator() {
        return GoldenGenerator.builder()
                .application(ECHO_APP)
                .name("support-goldens")
                .description("Synthetic support conversations")
                .seed(ScenarioSeed.scripted("return request", List.of("I want a refund", "order #123")));
    }

    @Test
    void shouldGenerateDeterministicallyWithoutJudge() {
        Dataset dataset = scriptedGenerator().build().generate();

        assertThat(dataset.name()).isEqualTo("support-goldens");
        assertThat(dataset.size()).isEqualTo(1);

        Example example = dataset.get(0);
        assertThat(example.input()).contains("I want a refund").contains("order #123");
        assertThat(example.datasetItemId()).isEqualTo("golden-0");
        assertThat(example.metadata())
                .containsEntry("scenario", "return request")
                .containsEntry("turnCount", 2);
    }

    @Test
    void shouldRoundTripThroughJsonLoadPath() {
        GoldenGenerator generator = scriptedGenerator().build();

        Dataset generated = generator.generate();
        Dataset reloaded = Dataset.fromJson(generator.toJson());

        assertThat(reloaded.name()).isEqualTo(generated.name());
        assertThat(reloaded.size()).isEqualTo(generated.size());
        assertThat(reloaded.get(0).inputs()).isEqualTo(generated.get(0).inputs());
        assertThat(reloaded.get(0).expectedOutputs()).isEqualTo(generated.get(0).expectedOutputs());
        // Jackson round-trips turnCount as Integer, so map equality holds.
        assertThat(reloaded.get(0).metadata()).isEqualTo(generated.get(0).metadata());
        assertThat(reloaded.get(0).datasetItemId()).isEqualTo("golden-0");
    }

    @Test
    void shouldRoundTripThroughJsonl() {
        GoldenGenerator generator = scriptedGenerator()
                .seed(ScenarioSeed.scripted("second scenario", List.of("hello")))
                .build();

        Dataset generated = generator.generate();
        Dataset reloaded = Dataset.fromJsonl(generator.toJsonl());

        assertThat(reloaded.size()).isEqualTo(2);
        assertThat(reloaded.get(0).input()).isEqualTo(generated.get(0).input());
        assertThat(reloaded.get(0).expectedOutput()).isEqualTo(generated.get(0).expectedOutput());
    }

    @Test
    void shouldMapInputAndExpectedOutputKeys() {
        Dataset reloaded = Dataset.fromJson(scriptedGenerator().build().toJson());

        Example example = reloaded.get(0);
        assertThat(example.input()).startsWith("Scenario: return request");
        assertThat(example.expectedOutput()).isEqualTo("Reply to: order #123");
    }

    @Test
    void shouldLetSeedExpectedOutputsOverrideDefault() {
        ScenarioSeed seed = ScenarioSeed.builder()
                .scenario("return request")
                .userTurns(List.of("I want a refund"))
                .expectedOutput("output", "canonical answer")
                .build();

        Dataset dataset = GoldenGenerator.builder()
                .application(ECHO_APP)
                .seed(seed)
                .build()
                .generate();

        assertThat(dataset.get(0).expectedOutput()).isEqualTo("canonical answer");
    }

    @Test
    void shouldNotDefaultOutputForPersonaSeeds() {
        JudgeLM judge = prompt -> "next question";
        ScenarioSeed seed = ScenarioSeed.persona(
                "curious user", "hi there", judgeLM -> trajectory -> Message.user(judgeLM.generate("go")));

        Dataset dataset = GoldenGenerator.builder()
                .application(ECHO_APP)
                .judge(judge)
                .maxTurns(2)
                .seed(seed)
                .build()
                .generate();

        assertThat(dataset.get(0).expectedOutputs()).doesNotContainKey("output");
        assertThat(dataset.get(0).expectedOutput()).isNull();
        assertThat(dataset.get(0).input()).contains("hi there").contains("next question");
    }

    @Test
    void shouldPutExpectedOutcomeInMetadata() {
        ScenarioSeed seed = ScenarioSeed.builder()
                .scenario("return request")
                .userTurns(List.of("I want a refund"))
                .expectedOutcome("The agent offers a refund")
                .metadata("suite", "support")
                .build();

        Dataset generated = GoldenGenerator.builder()
                .application(ECHO_APP)
                .seed(seed)
                .build()
                .generate();

        assertThat(generated.get(0).metadata())
                .containsEntry("expectedOutcome", "The agent offers a refund")
                .containsEntry("suite", "support");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldEmitKeysInAStableOrder() {
        ScenarioSeed seed = ScenarioSeed.builder()
                .scenario("return request")
                .userTurns(List.of("I want a refund"))
                .expectedOutcome("The agent offers a refund")
                .metadata("suite", "support")
                .build();

        String line = GoldenGenerator.builder()
                .application(ECHO_APP)
                .seed(seed)
                .build()
                .toJsonl();

        Map<String, Object> raw = Json.read(line, new TypeReference<Map<String, Object>>() {});
        Map<String, Object> metadata = (Map<String, Object>) raw.get("metadata");

        // Sorted, so regenerating a committed file produces the same bytes on every JVM run.
        assertThat(metadata.keySet()).containsExactly("expectedOutcome", "scenario", "suite", "turnCount");
    }

    @Test
    void shouldLetSeedOverrideMaxTurns() {
        JudgeLM judge = prompt -> "another question";
        ScenarioSeed seed = ScenarioSeed.builder()
                .scenario("curious user")
                .personaFactory(judgeLM -> trajectory -> Message.user(judgeLM.generate("go")))
                .maxTurns(1)
                .build();

        Dataset dataset = GoldenGenerator.builder()
                .application(ECHO_APP)
                .judge(judge)
                .maxTurns(5)
                .seed(seed)
                .build()
                .generate();

        assertThat(dataset.get(0).metadata()).containsEntry("turnCount", 1);
    }

    @Test
    void shouldLetInitialMessageReplaceTheFirstScriptedTurn() {
        ScenarioSeed seed = ScenarioSeed.builder()
                .scenario("return request")
                .initialMessage("opening")
                .userTurns(List.of("dropped", "second"))
                .build();

        Dataset dataset = GoldenGenerator.builder()
                .application(ECHO_APP)
                .seed(seed)
                .build()
                .generate();

        assertThat(dataset.get(0).input())
                .contains("opening")
                .contains("second")
                .doesNotContain("dropped");
        assertThat(dataset.get(0).metadata()).containsEntry("turnCount", 2);
    }

    @Test
    void shouldWriteFileLoadableByDataset(@TempDir Path tempDir) throws IOException {
        GoldenGenerator generator = scriptedGenerator().build();
        Dataset generated = generator.generate();

        Path jsonPath = tempDir.resolve("nested/goldens.json");
        generator.write(jsonPath);
        Dataset fromJson = Dataset.fromJson(jsonPath);

        assertThat(fromJson.size()).isEqualTo(generated.size());
        assertThat(fromJson.get(0).input()).isEqualTo(generated.get(0).input());

        Path jsonlPath = tempDir.resolve("goldens.jsonl");
        generator.writeJsonl(jsonlPath);
        Dataset fromJsonl = Dataset.fromJsonl(jsonlPath);

        assertThat(fromJsonl.size()).isEqualTo(generated.size());
        assertThat(fromJsonl.get(0).expectedOutput()).isEqualTo(generated.get(0).expectedOutput());
    }

    @Test
    void shouldThrowWhenPersonaSeedHasNoJudge() {
        GoldenGenerator generator = GoldenGenerator.builder()
                .application(ECHO_APP)
                .seed(ScenarioSeed.persona(
                        "angry customer", "this is broken", judge -> trajectory -> Message.user("x")))
                .build();

        assertThatThrownBy(generator::generate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JudgeLM")
                .hasMessageContaining("angry customer");
    }

    @Test
    void shouldNotInvokePersonaFactoryAtSeedConstruction() {
        AtomicInteger invocations = new AtomicInteger();

        ScenarioSeed seed = ScenarioSeed.persona("angry customer", "this is broken", judge -> {
            invocations.incrementAndGet();
            return trajectory -> Message.user("follow-up");
        });

        assertThat(invocations).hasValue(0);
        assertThat(seed.isDynamic()).isTrue();
    }

    @Test
    void shouldApplyPersonaFactoryWithSuppliedJudge() {
        JudgeLM judge = prompt -> "judged";
        AtomicReference<JudgeLM> received = new AtomicReference<>();

        ScenarioSeed seed = ScenarioSeed.persona("angry customer", "this is broken", suppliedJudge -> {
            received.set(suppliedJudge);
            return trajectory -> Message.user("follow-up");
        });

        GoldenGenerator.builder()
                .application(ECHO_APP)
                .judge(judge)
                .maxTurns(2)
                .seed(seed)
                .build()
                .generate();

        assertThat(received).hasValue(judge);
    }

    @Test
    void shouldPreserveErrorMetadataForFailingSeed() {
        ConversationalApplication failing = trajectory -> {
            throw new IllegalStateException("backend down");
        };

        ScenarioSeed seed = ScenarioSeed.builder()
                .scenario("failing scenario")
                .userTurns(List.of("hello"))
                .metadata("error", "seed value must not win")
                .build();

        Dataset dataset = GoldenGenerator.builder()
                .application(failing)
                .seed(seed)
                .build()
                .generate();

        assertThat(dataset.size()).isEqualTo(1);
        assertThat(dataset.get(0).metadata())
                .containsEntry("error", "backend down")
                .containsEntry("errorSource", "application");
        assertThat(dataset.get(0).input()).contains("hello");
        // No reply to record, so no golden answer rather than an empty one.
        assertThat(dataset.get(0).expectedOutput()).isNull();
    }

    @Test
    void shouldValidateBuilder() {
        assertThatThrownBy(() -> GoldenGenerator.builder()
                        .seed(ScenarioSeed.scripted("s", List.of("hello")))
                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ConversationalApplication");

        assertThatThrownBy(() -> GoldenGenerator.builder().application(ECHO_APP).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ScenarioSeed");

        assertThatThrownBy(() -> GoldenGenerator.builder().maxTurns(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTurns");

        assertThatThrownBy(() -> ScenarioSeed.builder().maxTurns(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTurns");
    }

    @Test
    void shouldRejectSeedWithoutExactlyOneUserSource() {
        assertThatThrownBy(() -> ScenarioSeed.builder().scenario("s").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");

        assertThatThrownBy(() -> ScenarioSeed.builder()
                        .scenario("s")
                        .userTurn("hello")
                        .personaFactory(judge -> trajectory -> Message.user("x"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void shouldEmitOnlyTheDatasetEnvelopeKeys() {
        String line = scriptedGenerator().build().toJsonl().lines().findFirst().orElseThrow();

        Map<String, Object> raw = Json.read(line, new TypeReference<Map<String, Object>>() {});

        assertThat(raw.keySet()).containsExactlyInAnyOrder("inputs", "expectedOutputs", "metadata", "id");
    }

    @Test
    void shouldSortNestedMapKeysSoRegeneratingLeavesNoDiff() {
        // A HashMap's iteration order is not its insertion order, so unsorted output would drift.
        Map<String, Object> labels = new HashMap<>();
        labels.put("zone", "eu");
        labels.put("alpha", 1);
        labels.put("mid", true);

        String json = GoldenGenerator.builder()
                .application(ECHO_APP)
                .seed(ScenarioSeed.builder()
                        .scenario("nested metadata")
                        .userTurn("hello")
                        .metadata("labels", labels)
                        .metadata("rows", List.of(labels))
                        .build())
                .build()
                .toJson();

        assertThat(json.indexOf("\"alpha\"")).isLessThan(json.indexOf("\"mid\""));
        assertThat(json.indexOf("\"mid\"")).isLessThan(json.indexOf("\"zone\""));
    }

    @Test
    void shouldRecordASeedThatFailsBeforeTheConversationStarts() {
        Dataset dataset = GoldenGenerator.builder()
                .application(ECHO_APP)
                .judge(prompt -> "irrelevant")
                .seed(ScenarioSeed.scripted("healthy", List.of("hello")))
                .seed(ScenarioSeed.persona("broken factory", "hi", judge -> {
                    throw new IllegalStateException("persona unavailable");
                }))
                .build()
                .generate();

        assertThat(dataset.examples())
                .as("a failing seed does not cost the healthy one")
                .hasSize(2);
        assertThat(dataset.examples().get(0).metadata()).doesNotContainKey("error");

        Example failed = dataset.examples().get(1);
        assertThat(failed.metadata()).containsEntry("errorSource", "seed");
        assertThat(failed.metadata().get("error").toString()).contains("persona unavailable");
        assertThat(failed.expectedOutputs()).doesNotContainKey("output");
    }
}
