package dev.dokimos.core.conversation;

import dev.dokimos.core.JudgeLM;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Describes one conversation for {@link GoldenGenerator} to synthesize into a dataset example.
 * <p>
 * A seed is either <em>scripted</em> (a fixed list of user turns, no LLM involved) or
 * <em>persona-driven</em> (a factory that receives the generator's {@code JudgeLM} and returns a
 * {@link SimulatedUser}), never both and never neither. The factory is stored as a plain
 * {@code Function} and is only applied inside {@link GoldenGenerator#generate()}, so a method
 * reference such as {@code UserPersonas::confusedUser} can be passed without a judge in scope.
 * <p>
 * Turn semantics: {@link ConversationSimulator} consumes {@code initialMessage} as turn 0 and only
 * then starts asking the simulated user for messages. A scripted seed that sets both
 * {@code initialMessage} and {@code userTurns} therefore continues at {@code userTurns.get(1)} on
 * the next turn, and {@code userTurns.get(0)} is never sent. Leave {@code initialMessage} empty (as
 * {@link #scripted(String, List)} does) to have the script drive every turn.
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * ScenarioSeed refund = ScenarioSeed.scripted(
 *         "Return request", List.of("I want a refund", "Order #123"));
 *
 * ScenarioSeed escalation = ScenarioSeed.builder()
 *         .scenario("Angry customer escalates")
 *         .initialMessage("This product broke on day one!")
 *         .personaFactory(UserPersonas::aggressiveCustomer)
 *         .expectedOutcome("The agent apologizes and offers a replacement or refund")
 *         .build();
 * }</pre>
 *
 * @param scenario        the scenario description passed to the simulator and stored in metadata
 * @param initialMessage  the first user message, or empty to let the simulated user open
 * @param expectedOutcome a natural-language completion criterion stored in the example metadata
 *                        under {@code "expectedOutcome"}, or null; it never stops the simulation
 * @param userTurns       the scripted user turns, in order; empty for a persona-driven seed
 * @param personaFactory  builds the simulated user from the generator's judge, or null for a
 *                        scripted seed
 * @param maxTurns        the turn limit for this seed, or null to inherit the generator's default
 * @param expectedOutputs expected outputs merged into the example, overriding generated defaults
 * @param metadata        extra metadata merged into the example
 */
public record ScenarioSeed(
        String scenario,
        String initialMessage,
        String expectedOutcome,
        List<String> userTurns,
        Function<JudgeLM, SimulatedUser> personaFactory,
        Integer maxTurns,
        Map<String, Object> expectedOutputs,
        Map<String, Object> metadata) {

    /**
     * Compact constructor ensuring immutability and the exactly-one-of user source invariant.
     */
    public ScenarioSeed {
        scenario = scenario != null ? scenario : "";
        initialMessage = initialMessage != null ? initialMessage : "";
        userTurns = userTurns != null ? List.copyOf(userTurns) : List.of();
        expectedOutputs = expectedOutputs != null ? Map.copyOf(expectedOutputs) : Map.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();

        boolean scripted = !userTurns.isEmpty();
        boolean dynamic = personaFactory != null;
        if (scripted == dynamic) {
            throw new IllegalArgumentException(
                    "A ScenarioSeed needs exactly one of userTurns (scripted) or personaFactory (persona-driven)");
        }
    }

    /**
     * Creates a scripted seed whose user turns are replayed verbatim, with no judge involved.
     *
     * @param scenario  the scenario description
     * @param userTurns the user turns, in order, starting with the opening message
     * @return a new scripted seed
     */
    public static ScenarioSeed scripted(String scenario, List<String> userTurns) {
        return new ScenarioSeed(scenario, "", null, userTurns, null, null, Map.of(), Map.of());
    }

    /**
     * Creates a persona-driven seed. The factory is applied to the generator's judge at generation
     * time, so it is never invoked while the seed is constructed.
     *
     * @param scenario       the scenario description
     * @param initialMessage the first user message, or empty to let the persona open
     * @param personaFactory builds the simulated user from a judge
     * @return a new persona-driven seed
     */
    public static ScenarioSeed persona(
            String scenario, String initialMessage, Function<JudgeLM, SimulatedUser> personaFactory) {
        return new ScenarioSeed(scenario, initialMessage, null, List.of(), personaFactory, null, Map.of(), Map.of());
    }

    /**
     * Creates a new builder for constructing seeds.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns whether this seed drives its user with a persona factory rather than a script.
     *
     * @return true if the seed is persona-driven
     */
    public boolean isDynamic() {
        return personaFactory != null;
    }

    /**
     * Builder for constructing scenario seeds.
     */
    public static class Builder {
        private String scenario = "";
        private String initialMessage = "";
        private String expectedOutcome;
        private final List<String> userTurns = new ArrayList<>();
        private Function<JudgeLM, SimulatedUser> personaFactory;
        private Integer maxTurns;
        private final Map<String, Object> expectedOutputs = new LinkedHashMap<>();
        private final Map<String, Object> metadata = new LinkedHashMap<>();

        /**
         * Sets the scenario description.
         *
         * @param scenario the scenario description
         * @return this builder
         */
        public Builder scenario(String scenario) {
            this.scenario = scenario;
            return this;
        }

        /**
         * Sets the first user message.
         * <p>
         * On a scripted seed this message replaces the first entry of {@code userTurns}, which is then
         * never sent.
         *
         * @param initialMessage the first user message
         * @return this builder
         */
        public Builder initialMessage(String initialMessage) {
            this.initialMessage = initialMessage;
            return this;
        }

        /**
         * Sets the natural-language completion criterion stored under {@code "expectedOutcome"} in the
         * generated example's metadata. It documents what a good conversation achieves and can be fed
         * to a judge, but it never stops the simulation.
         *
         * @param expectedOutcome the expected outcome
         * @return this builder
         */
        public Builder expectedOutcome(String expectedOutcome) {
            this.expectedOutcome = expectedOutcome;
            return this;
        }

        /**
         * Adds one scripted user turn.
         *
         * @param turn the user turn content
         * @return this builder
         */
        public Builder userTurn(String turn) {
            this.userTurns.add(turn);
            return this;
        }

        /**
         * Replaces the scripted user turns.
         *
         * @param turns the user turns, in order
         * @return this builder
         */
        public Builder userTurns(List<String> turns) {
            this.userTurns.clear();
            this.userTurns.addAll(turns);
            return this;
        }

        /**
         * Sets the factory that builds the simulated user from the generator's judge.
         *
         * @param personaFactory the persona factory
         * @return this builder
         */
        public Builder personaFactory(Function<JudgeLM, SimulatedUser> personaFactory) {
            this.personaFactory = personaFactory;
            return this;
        }

        /**
         * Sets the turn limit for this seed, overriding the generator's default. A scripted seed stops
         * at its last user turn regardless of the limit.
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
         * Adds an expected output with the given key and value.
         *
         * @param key   the output key
         * @param value the output value
         * @return this builder
         */
        public Builder expectedOutput(String key, Object value) {
            this.expectedOutputs.put(key, value);
            return this;
        }

        /**
         * Adds all entries from the given expected outputs map.
         *
         * @param expectedOutputs the expected outputs to add
         * @return this builder
         */
        public Builder expectedOutputs(Map<String, Object> expectedOutputs) {
            this.expectedOutputs.putAll(expectedOutputs);
            return this;
        }

        /**
         * Adds metadata with the given key and value.
         *
         * @param key   the metadata key
         * @param value the metadata value
         * @return this builder
         */
        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        /**
         * Adds all entries from the given metadata map.
         *
         * @param metadata the metadata to add
         * @return this builder
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        /**
         * Builds the scenario seed.
         *
         * @return a new scenario seed
         * @throws IllegalArgumentException if neither or both of userTurns and personaFactory are set
         */
        public ScenarioSeed build() {
            return new ScenarioSeed(
                    scenario,
                    initialMessage,
                    expectedOutcome,
                    userTurns,
                    personaFactory,
                    maxTurns,
                    expectedOutputs,
                    metadata);
        }
    }
}
