package dev.dokimos.examples.conversation.goldens;

import dev.dokimos.core.Dataset;
import dev.dokimos.core.Example;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.conversation.GoldenGenerator;
import dev.dokimos.core.conversation.ScenarioSeed;
import dev.dokimos.core.conversation.UserPersonas;
import dev.dokimos.langchain4j.LangChain4jSupport;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates the committed multi-turn regression suite for {@link SupportDesk}.
 *
 * <p>Three scenario seeds describe the conversations to synthesize: two scripted ones, whose user
 * turns are replayed verbatim, and one persona-driven one, where a simulated user writes each turn
 * against the desk's answers. {@code GoldenGenerator} runs every seed, and the resulting dataset is
 * written to {@code src/test/resources/datasets/support-goldens.json}, where
 * {@code SupportGoldenReplayTest} picks it up.
 *
 * <p>Every seed carries an {@code expectedOutcome}: the natural-language criterion the replay test
 * grades each conversation against. It rides along in metadata and never stops the simulation.
 *
 * <p>Prerequisites: {@code export OPENAI_API_KEY='your-key'}
 *
 * <p>Run with:
 * {@code mvn exec:java -pl dokimos-examples -Dexec.mainClass="dev.dokimos.examples.conversation.goldens.GenerateSupportGoldens"}
 *
 * <p>An optional first argument overrides the output path.
 */
public class GenerateSupportGoldens {

    private static final Path OUTPUT_FILE = Path.of("src", "test", "resources", "datasets", "support-goldens.json");

    /**
     * Generates the suite and writes it to disk.
     *
     * @param args an optional output path, replacing the default file
     * @throws IOException if the written file cannot be read back
     */
    public static void main(String[] args) throws IOException {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("ERROR: OPENAI_API_KEY environment variable not set");
            System.err.println("Set it with: export OPENAI_API_KEY='your-api-key'");
            System.exit(1);
        }

        SupportDesk supportDesk = SupportDesk.withOpenAi(apiKey);
        JudgeLM judge = LangChain4jSupport.asJudge(OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(SupportDesk.MODEL_ID)
                .build());

        // Scripted seeds: fixed user turns, no LLM on the user side
        ScenarioSeed refund = ScenarioSeed.builder()
                .scenario("Refund for a blender that arrived cracked")
                .userTurns(List.of(
                        "My blender arrived with a cracked jug and I want my money back",
                        "The order number is KW-4471, it was delivered last Tuesday"))
                .expectedOutcome("The agent asks for the order number and then confirms the refund is being processed")
                .metadata("suite", "support")
                .build();

        ScenarioSeed shipping = ScenarioSeed.builder()
                .scenario("Delivery and shipping cost before ordering")
                .userTurns(List.of(
                        "How long does delivery take if I order a kettle today?",
                        "Do I pay for shipping on a 60 dollar order?"))
                .expectedOutcome("The agent states the 5 to 7 business day delivery window and says shipping is free"
                        + " on orders above 50 USD")
                .metadata("suite", "support")
                .build();

        // Persona-driven seed: the judge writes each user turn
        ScenarioSeed confusedReturn = ScenarioSeed.builder()
                .scenario("Confused customer wants to send an item back")
                .initialMessage("I think I have to send something back but I have no idea where to start")
                .personaFactory(UserPersonas::confusedUser)
                .expectedOutcome("The agent explains the 30 day return window and tells the customer what to do next")
                .metadata("suite", "support")
                .build();

        GoldenGenerator generator = GoldenGenerator.builder()
                .application(supportDesk)
                .judge(judge)
                .name("support-goldens")
                .description("Synthetic multi-turn support conversations recorded against SupportDesk")
                .maxTurns(3)
                .seed(refund)
                .seed(shipping)
                .seed(confusedReturn)
                .build();

        // Generation is stateless, so write once and read the file back for the summary
        Path output = args.length > 0 ? Path.of(args[0]) : resolveOutputFile();
        System.out.println("Generating goldens against " + SupportDesk.MODEL_ID + "...\n");
        generator.write(output);

        Dataset goldens = Dataset.fromJson(output);
        for (Example golden : goldens) {
            System.out.println(golden.datasetItemId() + ": " + golden.metadata().get("scenario"));
            System.out.println("  turns:   " + golden.metadata().get("turnCount"));
            System.out.println("  outcome: " + golden.metadata().get("expectedOutcome"));
            System.out.println("  opening: " + firstUserTurn(golden.input()));
            if (golden.metadata().containsKey("error")) {
                System.out.println("  ERROR (" + golden.metadata().get("errorSource") + "): "
                        + golden.metadata().get("error"));
            }
            System.out.println();
        }

        System.out.println("Wrote " + goldens.size() + " goldens to " + output.toAbsolutePath());
        System.out.println("Replay them with: RUN_EVAL_TESTS=true mvn test -pl dokimos-examples "
                + "-Dtest=SupportGoldenReplayTest");
    }

    private static Path resolveOutputFile() {
        Path module = Path.of("dokimos-examples");
        return Files.isDirectory(module) ? module.resolve(OUTPUT_FILE) : OUTPUT_FILE;
    }

    private static String firstUserTurn(String transcript) {
        for (String line : transcript.split("\n")) {
            if (line.startsWith("USER: ")) {
                return line.substring("USER: ".length());
            }
        }
        return "";
    }
}
