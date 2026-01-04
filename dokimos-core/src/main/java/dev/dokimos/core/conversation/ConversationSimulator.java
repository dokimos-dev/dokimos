package dev.dokimos.core.conversation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

/**
 * Orchestrates multi-turn conversations between a simulated user and an
 * application.
 * <p>
 * The simulator manages turn-taking, enforces turn limits, handles stopping
 * conditions,
 * and captures the complete interaction trajectory for evaluation.
 * <p>
 * Inspired by OpenEvals' {@code run_multiturn_simulation} but adapted for the
 * Dokimos
 * framework with Java idioms and builder pattern configuration.
 * <p>
 * Example usage:
 * 
 * <pre>{@code
 * ConversationSimulator simulator = ConversationSimulator.builder()
 *         .simulatedUser(UserPersonas.aggressiveCustomer(judgeLM))
 *         .application(myApp)
 *         .maxTurns(10)
 *         .scenario("Handle product return request")
 *         .initialMessage("I want to return this defective product!")
 *         .build();
 *
 * ConversationTrajectory trajectory = simulator.simulate();
 * }</pre>
 */
public class ConversationSimulator {

    private final SimulatedUser simulatedUser;
    private final ConversationalApplication application;
    private final int maxTurns;
    private final String scenario;
    private final String initialMessage;
    private final Predicate<ConversationTrajectory> stoppingCondition;

    private ConversationSimulator(Builder builder) {
        this.simulatedUser = builder.simulatedUser;
        this.application = builder.application;
        this.maxTurns = builder.maxTurns;
        this.scenario = builder.scenario;
        this.initialMessage = builder.initialMessage;
        this.stoppingCondition = builder.stoppingCondition;
    }

    /**
     * Creates a new builder for constructing a conversation simulator.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Runs the conversation simulation.
     * <p>
     * The simulation proceeds as follows:
     * <ol>
     * <li>If an initial message is provided, it starts the conversation</li>
     * <li>Otherwise, the simulated user generates the first message</li>
     * <li>The application responds to each user message</li>
     * <li>The simulated user generates the next message based on the response</li>
     * <li>This continues until maxTurns is reached or stoppingCondition returns
     * true</li>
     * </ol>
     *
     * @return the complete conversation trajectory
     */
    public ConversationTrajectory simulate() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .scenario(scenario)
                .build();

        for (int turn = 0; turn < maxTurns; turn++) {
            // Generate user message
            Message userMessage;
            if (turn == 0 && initialMessage != null && !initialMessage.isEmpty()) {
                userMessage = Message.user(initialMessage);
            } else {
                userMessage = simulatedUser.generateMessage(trajectory);
            }
            trajectory = trajectory.withMessage(userMessage);

            // Check stopping condition after user message
            if (stoppingCondition != null && stoppingCondition.test(trajectory)) {
                break;
            }

            // Get application response
            Message assistantMessage = application.respond(trajectory);
            trajectory = trajectory.withMessage(assistantMessage);

            // Check stopping condition after assistant response
            if (stoppingCondition != null && stoppingCondition.test(trajectory)) {
                break;
            }
        }

        return trajectory;
    }

    /**
     * Runs the conversation simulation asynchronously using the common fork-join
     * pool.
     *
     * @return a CompletableFuture that will complete with the trajectory
     */
    public CompletableFuture<ConversationTrajectory> simulateAsync() {
        return CompletableFuture.supplyAsync(this::simulate);
    }

    /**
     * Runs the conversation simulation asynchronously using the provided executor.
     *
     * @param executor the executor to use for async execution
     * @return a CompletableFuture that will complete with the trajectory
     */
    public CompletableFuture<ConversationTrajectory> simulateAsync(ExecutorService executor) {
        return CompletableFuture.supplyAsync(this::simulate, executor);
    }

    /**
     * Builder for constructing conversation simulators.
     */
    public static class Builder {
        private SimulatedUser simulatedUser;
        private ConversationalApplication application;
        private int maxTurns = 10;
        private String scenario = "";
        private String initialMessage;
        private Predicate<ConversationTrajectory> stoppingCondition;

        /**
         * Sets the simulated user for the conversation.
         *
         * @param simulatedUser the simulated user
         * @return this builder
         */
        public Builder simulatedUser(SimulatedUser simulatedUser) {
            this.simulatedUser = simulatedUser;
            return this;
        }

        /**
         * Sets the application under test.
         *
         * @param application the application to test
         * @return this builder
         */
        public Builder application(ConversationalApplication application) {
            this.application = application;
            return this;
        }

        /**
         * Sets the maximum number of conversation turns.
         * <p>
         * A turn consists of one user message followed by one assistant response.
         * Default is 10.
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
         * Sets the scenario description for the conversation.
         * <p>
         * The scenario provides context to the simulated user about what
         * kind of interaction to simulate.
         *
         * @param scenario the scenario description
         * @return this builder
         */
        public Builder scenario(String scenario) {
            this.scenario = scenario;
            return this;
        }

        /**
         * Sets the initial user message to start the conversation.
         * <p>
         * If not set, the simulated user will generate the first message.
         *
         * @param initialMessage the first user message
         * @return this builder
         */
        public Builder initialMessage(String initialMessage) {
            this.initialMessage = initialMessage;
            return this;
        }

        /**
         * Sets a stopping condition for early termination.
         * <p>
         * The simulation will stop if this predicate returns true after
         * any message (user or assistant). This is useful for detecting
         * when a conversation goal has been achieved.
         * <p>
         * Example:
         * 
         * <pre>{@code
         * .stoppingCondition(trajectory -> {
         *     Message last = trajectory.lastAssistantMessage();
         *     return last != null && last.content().contains("goodbye");
         * })
         * }</pre>
         *
         * @param stoppingCondition the stopping condition predicate
         * @return this builder
         */
        public Builder stoppingCondition(Predicate<ConversationTrajectory> stoppingCondition) {
            this.stoppingCondition = stoppingCondition;
            return this;
        }

        /**
         * Builds the conversation simulator.
         *
         * @return a new conversation simulator
         * @throws IllegalStateException if required fields are not set
         */
        public ConversationSimulator build() {
            if (simulatedUser == null) {
                throw new IllegalStateException("SimulatedUser is required");
            }
            if (application == null) {
                throw new IllegalStateException("ConversationalApplication is required");
            }
            return new ConversationSimulator(this);
        }
    }
}
