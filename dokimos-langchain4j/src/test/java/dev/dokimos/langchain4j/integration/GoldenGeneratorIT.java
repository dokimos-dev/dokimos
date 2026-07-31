package dev.dokimos.langchain4j.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dokimos.core.Dataset;
import dev.dokimos.core.Example;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.conversation.ConversationalApplication;
import dev.dokimos.core.conversation.GoldenGenerator;
import dev.dokimos.core.conversation.Message;
import dev.dokimos.core.conversation.ScenarioSeed;
import dev.dokimos.core.conversation.UserPersonas;
import dev.dokimos.langchain4j.LangChain4jSupport;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * Generates conversation goldens with a real OpenAI-backed simulated user: one scripted seed and
 * one persona-driven seed run against a deterministic scripted application, and the resulting
 * dataset is checked for the golden shape (scripted seeds carry a baseline answer, persona seeds do
 * not, the expected outcome lands in metadata) and reloaded through the JSONL load path. The
 * generator runs exactly once; the JSONL text from that run is asserted and written directly.
 * Requires {@code OPENAI_API_KEY}.
 */
@Tag("integration")
class GoldenGeneratorIT {

    @TempDir
    Path tempDir;

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void generatesGoldensWithRealSimulatedUserAndReloadsThem() throws Exception {
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(OpenAiChatModelName.GPT_5_MINI)
                .build();
        JudgeLM judge = LangChain4jSupport.asJudge(chatModel);

        // Deterministic application: the live part of this test is the simulated user
        ConversationalApplication app = trajectory -> Message.assistant(
                "Support reply " + trajectory.userMessages().size() + ": please check the account page.");

        ScenarioSeed scripted =
                ScenarioSeed.scripted("Password reset", List.of("How do I reset my password?", "Thanks, that worked."));
        ScenarioSeed persona = ScenarioSeed.builder()
                .scenario("A confused user cannot find their order status")
                .initialMessage("Hi, I cannot find where my order is??")
                .expectedOutcome("The user learns where to check the order status")
                .personaFactory(UserPersonas::confusedUser)
                .maxTurns(2)
                .build();

        GoldenGenerator generator = GoldenGenerator.builder()
                .application(app)
                .judge(judge)
                .name("live-goldens")
                .seed(scripted)
                .seed(persona)
                .build();

        String jsonl = generator.toJsonl();

        Dataset dataset = Dataset.fromJsonl(jsonl);
        assertThat(dataset.examples()).as("one golden per seed").hasSize(2);
        assertThat(dataset.examples()).allSatisfy(example -> assertThat(example.input())
                .as("every golden carries a non-blank transcript input")
                .isNotBlank());

        Example scriptedGolden = dataset.examples().get(0);
        assertThat(scriptedGolden.expectedOutput())
                .as("scripted seed records the baseline answer")
                .isNotBlank();

        Example personaGolden = dataset.examples().get(1);
        assertThat(personaGolden.expectedOutputs())
                .as("persona seed gets no default golden answer")
                .doesNotContainKey("output");
        assertThat(personaGolden.metadata())
                .as("the expected outcome lands in metadata for judges")
                .containsEntry("expectedOutcome", "The user learns where to check the order status");

        // Round-trip the captured text rather than generating a second time
        Path file = tempDir.resolve("live-goldens.jsonl");
        Files.writeString(file, jsonl, StandardCharsets.UTF_8);
        Dataset reloaded = Dataset.fromJsonl(Files.readString(file, StandardCharsets.UTF_8));
        assertThat(reloaded.examples()).hasSize(2);
        assertThat(reloaded.examples().get(1).metadata()).containsKey("expectedOutcome");
    }
}
