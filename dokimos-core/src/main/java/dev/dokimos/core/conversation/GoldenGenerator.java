package dev.dokimos.core.conversation;

import dev.dokimos.core.Dataset;
import dev.dokimos.core.Example;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.internal.Json;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Turns {@link ScenarioSeed}s into a {@link Dataset} of synthetic multi-turn goldens by running each
 * seed through {@link ConversationSimulator} against the application under test.
 * <p>
 * Each seed yields one example: the rendered transcript under {@code inputs["input"]}, the scenario
 * and turn count in metadata, and a {@code golden-<index>} dataset item id. The result is serialized
 * with {@link #toJson()} or {@link #toJsonl()} into the shape {@code Dataset.fromJson} and
 * {@code Dataset.fromJsonl} read, so generated goldens feed {@code @DatasetSource} directly.
 * <p>
 * Generation is stateless: every call to {@link #generate()} re-runs the seeds.
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * GoldenGenerator generator = GoldenGenerator.builder()
 *         .application(myApp)
 *         .name("support-goldens")
 *         .seed(ScenarioSeed.scripted("Return request", List.of("I want a refund", "Order #123")))
 *         .build();
 *
 * generator.write(Path.of("src/test/resources/datasets/support-goldens.json"));
 * }</pre>
 */
public class GoldenGenerator {

    private final ConversationalApplication application;
    private final JudgeLM judge;
    private final int maxTurns;
    private final String name;
    private final String description;
    private final List<ScenarioSeed> seeds;

    private GoldenGenerator(Builder builder) {
        this.application = builder.application;
        this.judge = builder.judge;
        this.maxTurns = builder.maxTurns;
        this.name = builder.name;
        this.description = builder.description;
        this.seeds = List.copyOf(builder.seeds);
    }

    /**
     * Creates a new builder for constructing a golden generator.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Runs every seed and collects the resulting conversations into a dataset.
     * <p>
     * A failing seed still produces its example, so one bad seed never costs you the others. A
     * simulation that fails keeps the turns gathered so far, and a seed that fails before the
     * conversation starts, such as a persona factory that throws, yields an example with no turns.
     * Either way {@code error} and {@code errorSource} land in the example metadata.
     *
     * @return the generated dataset, one example per seed, in seed order
     * @throws IllegalStateException if a persona-driven seed is present and no judge was supplied
     */
    public Dataset generate() {
        requireJudgeForPersonaSeeds();

        Dataset.Builder dataset =
                Dataset.builder().name(name.isEmpty() ? "goldens" : name).description(description);

        for (int i = 0; i < seeds.size(); i++) {
            ScenarioSeed seed = seeds.get(i);
            dataset.addExample(toExample(seed, runSeed(seed), i));
        }

        return dataset.build();
    }

    private ConversationTrajectory runSeed(ScenarioSeed seed) {
        try {
            return ConversationSimulator.builder()
                    .simulatedUser(resolveUser(seed))
                    .application(application)
                    .scenario(seed.scenario())
                    .initialMessage(seed.initialMessage())
                    .maxTurns(effectiveMaxTurns(seed))
                    .build()
                    .simulate();
        } catch (RuntimeException e) {
            // The simulator reports its own failures. This covers the ones before it starts, so a
            // seed that cannot even be set up does not discard the seeds that already ran.
            return ConversationTrajectory.builder()
                    .scenario(seed.scenario())
                    .metadata("error", e.getMessage() != null ? e.getMessage() : e.toString())
                    .metadata("errorSource", "seed")
                    .build();
        }
    }

    /**
     * Generates the goldens and serializes them as pretty-printed JSON in the dataset format.
     *
     * @return the dataset as JSON
     */
    public String toJson() {
        return Json.writePretty(datasetToMap(generate()));
    }

    /**
     * Generates the goldens and serializes them as JSONL, one example per line.
     *
     * @return the examples as JSONL
     */
    public String toJsonl() {
        return generate().examples().stream()
                .map(example -> Json.writeCompact(exampleToMap(example)))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Generates the goldens and writes them to a JSON file, creating parent directories as needed.
     *
     * @param path the file to write
     * @throws UncheckedIOException if writing fails
     */
    public void write(Path path) {
        writeToFile(path, toJson());
    }

    /**
     * Generates the goldens and writes them to a JSONL file, creating parent directories as needed.
     *
     * @param path the file to write
     * @throws UncheckedIOException if writing fails
     */
    public void writeJsonl(Path path) {
        writeToFile(path, toJsonl());
    }

    private int effectiveMaxTurns(ScenarioSeed seed) {
        int limit = seed.maxTurns() != null ? seed.maxTurns() : maxTurns;
        if (seed.isDynamic()) {
            return limit;
        }
        // A script has nothing to say past its last turn, so stop there instead of padding the
        // transcript with empty user messages. An initial message stands in for the first turn.
        return Math.min(limit, seed.userTurns().size());
    }

    private void requireJudgeForPersonaSeeds() {
        if (judge != null) {
            return;
        }
        for (ScenarioSeed seed : seeds) {
            if (seed.isDynamic()) {
                throw new IllegalStateException("A JudgeLM is required for the persona-driven seed: " + seed.scenario()
                        + ". Supply one via builder().judge(...) or use ScenarioSeed.scripted(...).");
            }
        }
    }

    private SimulatedUser resolveUser(ScenarioSeed seed) {
        if (seed.isDynamic()) {
            return seed.personaFactory().apply(judge);
        }
        return trajectory -> {
            List<String> turns = seed.userTurns();
            int index = trajectory.userMessages().size();
            return Message.user(index < turns.size() ? turns.get(index) : "");
        };
    }

    private Example toExample(ScenarioSeed seed, ConversationTrajectory trajectory, int index) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("input", trajectory.toText());

        Map<String, Object> expectedOutputs = new LinkedHashMap<>();
        Message last = trajectory.lastAssistantMessage();
        if (!seed.isDynamic() && last != null) {
            // A scripted seed records a baseline of the application's own replies. A persona-driven
            // seed gets no default, so the app is never graded against whatever it happened to say.
            // A run that failed before any reply gets none either, rather than an empty answer.
            expectedOutputs.put("output", last.content());
        }
        expectedOutputs.putAll(seed.expectedOutputs());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("scenario", trajectory.scenario());
        metadata.put("turnCount", trajectory.turnCount());
        metadata.putAll(seed.metadata());
        if (seed.expectedOutcome() != null) {
            metadata.put("expectedOutcome", seed.expectedOutcome());
        }
        // Last, so the simulator's error and errorSource always survive a failed or partial run.
        metadata.putAll(trajectory.metadata());

        return Example.builder()
                .inputs(inputs)
                .expectedOutputs(expectedOutputs)
                .metadata(metadata)
                .datasetItemId("golden-" + index)
                .build();
    }

    private static Map<String, Object> datasetToMap(Dataset dataset) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", dataset.name());
        map.put("description", dataset.description());
        map.put(
                "examples",
                dataset.examples().stream().map(GoldenGenerator::exampleToMap).toList());
        return map;
    }

    private static Map<String, Object> exampleToMap(Example example) {
        Map<String, Object> map = new LinkedHashMap<>();
        // Sorted views, because Example holds immutable copies whose iteration order varies between
        // JVM runs. Regenerating a committed golden file has to produce the same bytes.
        map.put("inputs", sortedDeep(example.inputs()));
        map.put("expectedOutputs", sortedDeep(example.expectedOutputs()));
        map.put("metadata", sortedDeep(example.metadata()));
        if (example.datasetItemId() != null) {
            map.put("id", example.datasetItemId());
        }
        return map;
    }

    private static Object sortedDeep(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, nested) -> sorted.put(String.valueOf(key), sortedDeep(nested)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(GoldenGenerator::sortedDeep).toList();
        }
        return value;
    }

    private static void writeToFile(Path path, String content) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write goldens to file: " + path, e);
        }
    }

    /**
     * Builder for constructing golden generators.
     */
    public static class Builder {
        private ConversationalApplication application;
        private JudgeLM judge;
        private int maxTurns = 10;
        private String name = "";
        private String description = "";
        private final List<ScenarioSeed> seeds = new ArrayList<>();

        /**
         * Sets the application under test, which answers every seed.
         *
         * @param application the application to drive
         * @return this builder
         */
        public Builder application(ConversationalApplication application) {
            this.application = application;
            return this;
        }

        /**
         * Sets the judge used to build persona-driven users.
         * <p>
         * Scripted seeds need no judge: their user turns are replayed verbatim. The judge is only
         * requested when a persona-driven seed is generated, and its absence then fails with a message
         * naming the seed.
         *
         * @param judge the judge
         * @return this builder
         */
        public Builder judge(JudgeLM judge) {
            this.judge = judge;
            return this;
        }

        /**
         * Sets the default turn limit for seeds that do not set their own. Default is 10.
         * <p>
         * A scripted seed stops at its last user turn even when the limit is higher.
         *
         * @param maxTurns the maximum number of turns
         * @return this builder
         */
        public Builder maxTurns(int maxTurns) {
            if (maxTurns < 1) {
                throw new IllegalArgumentException("maxTurns must be at least 1");
            }
            this.maxTurns = maxTurns;
            return this;
        }

        /**
         * Sets the name of the generated dataset. Defaults to {@code "goldens"}.
         *
         * @param name the dataset name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the description of the generated dataset.
         *
         * @param description the dataset description
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Adds one seed to generate.
         *
         * @param seed the seed to add
         * @return this builder
         */
        public Builder seed(ScenarioSeed seed) {
            this.seeds.add(seed);
            return this;
        }

        /**
         * Replaces the seeds to generate.
         *
         * @param seeds the seeds, in generation order
         * @return this builder
         */
        public Builder seeds(List<ScenarioSeed> seeds) {
            this.seeds.clear();
            this.seeds.addAll(seeds);
            return this;
        }

        /**
         * Builds the golden generator.
         *
         * @return a new golden generator
         * @throws IllegalStateException if the application or the seeds are missing
         */
        public GoldenGenerator build() {
            if (application == null) {
                throw new IllegalStateException("ConversationalApplication is required");
            }
            if (seeds.isEmpty()) {
                throw new IllegalStateException("At least one ScenarioSeed is required");
            }
            return new GoldenGenerator(this);
        }
    }
}
