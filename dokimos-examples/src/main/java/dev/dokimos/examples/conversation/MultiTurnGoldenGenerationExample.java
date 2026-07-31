package dev.dokimos.examples.conversation;

import dev.dokimos.core.Dataset;
import dev.dokimos.core.Example;
import dev.dokimos.core.conversation.ConversationalApplication;
import dev.dokimos.core.conversation.GoldenGenerator;
import dev.dokimos.core.conversation.Message;
import dev.dokimos.core.conversation.ScenarioSeed;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Demonstrates generating multi-turn goldens from scenario seeds and writing them out as a dataset
 * that {@code @DatasetSource} can read back.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Scripted seeds, whose user turns are replayed verbatim, so no API key is needed.
 *   <li>The generated example shape: the transcript under {@code inputs["input"]}, the application's
 *       last reply as the golden answer, and scenario, turn count, and expected outcome in metadata.
 *   <li>Overriding the golden answer per seed with {@code expectedOutput("output", ...)}.
 *   <li>Writing JSON and JSONL files and loading them back through {@code Dataset}.
 * </ul>
 *
 * <p>Persona-driven seeds swap the script for {@code personaFactory(UserPersonas::aggressiveCustomer)}
 * and a {@code JudgeLM} on the generator, which turns each seed into a fresh LLM-driven conversation.
 * Those seeds get no default golden answer, since an application should not be graded against its own
 * reply.
 *
 * <p>Run with: {@code mvn exec:java -pl dokimos-examples -Dexec.mainClass="dev.dokimos.examples.conversation.MultiTurnGoldenGenerationExample"}
 */
public class MultiTurnGoldenGenerationExample {

    // A deterministic stand-in for the application under test
    private static final ConversationalApplication SUPPORT_DESK = trajectory -> {
        String lastUserMessage = trajectory.lastUserMessage().content().toLowerCase();
        if (lastUserMessage.contains("#")) {
            return Message.assistant("Thanks, I found that order and issued a full refund.");
        }
        if (lastUserMessage.contains("refund") || lastUserMessage.contains("broken")) {
            return Message.assistant("I'm sorry about that. What is your order number?");
        }
        return Message.assistant("Happy to help. Can you tell me more?");
    };

    public static void main(String[] args) throws IOException {
        System.out.println("=== Dokimos Multi-Turn Golden Generation Example ===\n");

        // Describe the conversations to synthesize
        ScenarioSeed refund = ScenarioSeed.builder()
                .scenario("Refund for a broken product")
                .userTurns(List.of("My blender arrived broken and I want a refund", "The order number is #123"))
                .expectedOutcome("The agent asks for the order number and then issues a refund")
                .metadata("suite", "support")
                .build();

        // An explicit expected output replaces the recorded reply
        ScenarioSeed greeting = ScenarioSeed.builder()
                .scenario("Vague opening question")
                .userTurn("Hi, I have a question")
                .expectedOutput("output", "A helpful greeting that invites the user to describe their problem")
                .build();

        // Run every seed and collect the goldens
        GoldenGenerator generator = GoldenGenerator.builder()
                .application(SUPPORT_DESK)
                .name("support-goldens")
                .description("Synthetic multi-turn support conversations")
                .seed(refund)
                .seed(greeting)
                .build();

        Dataset goldens = generator.generate();

        System.out.println("Generated " + goldens.size() + " goldens\n");
        for (Example example : goldens) {
            System.out.println("--- " + example.datasetItemId() + " ---");
            System.out.println(example.input());
            System.out.println("golden answer: " + example.expectedOutput());
            System.out.println("metadata: " + example.metadata());
            System.out.println();
        }

        // Write the goldens out and load them back
        Path outputDir = Path.of("target", "goldens");
        Path jsonPath = outputDir.resolve("support-goldens.json");
        Path jsonlPath = outputDir.resolve("support-goldens.jsonl");

        generator.write(jsonPath);
        generator.writeJsonl(jsonlPath);

        Dataset fromJson = Dataset.fromJson(jsonPath);
        Dataset fromJsonl = Dataset.fromJsonl(jsonlPath);

        System.out.println("=== Round Trip ===");
        System.out.println("wrote " + jsonPath + " and reloaded " + fromJson.size() + " examples");
        System.out.println("wrote " + jsonlPath + " and reloaded " + fromJsonl.size() + " examples");
        System.out.println();

        System.out.println("=== Done ===");
        System.out.println("Point a JUnit test at the file with @DatasetSource(\"" + jsonPath + "\") to replay the");
        System.out.println("goldens, or check them in as a regression suite. See the Multi-Turn Conversations docs.");
    }
}
