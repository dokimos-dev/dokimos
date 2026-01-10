package dev.dokimos.core.conversation;

import dev.dokimos.core.JudgeLM;

import java.util.ArrayList;
import java.util.List;

/**
 * An LLM-based simulated user for multi-turn conversation testing.
 * <p>
 * This implementation uses a {@link JudgeLM} to generate contextually
 * appropriate user messages based on a configured persona and behavior
 * guidelines. It supports both fully dynamic responses and fixed initial
 * responses.
 * <p>
 * Example usage:
 * 
 * <pre>{@code
 * SimulatedUser user = LLMSimulatedUser.builder()
 *         .judge(judgeLM)
 *         .persona("frustrated customer who received a defective product")
 *         .behaviorGuidelines("Be assertive but not abusive. Express dissatisfaction clearly.")
 *         .build();
 * }</pre>
 *
 * @see UserPersonas for pre-built persona configurations
 */
public class LLMSimulatedUser implements SimulatedUser {

    private final JudgeLM judge;
    private final String systemPrompt;
    private final List<String> fixedResponses;

    private LLMSimulatedUser(Builder builder) {
        this.judge = builder.judge;
        this.systemPrompt = builder.systemPrompt;
        this.fixedResponses = List.copyOf(builder.fixedResponses);
    }

    /**
     * Creates a new builder for constructing an LLM simulated user.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Message generateMessage(ConversationTrajectory trajectory) {
        int userMessageCount = trajectory.userMessages().size();

        // Use fixed response if available for this turn
        if (userMessageCount < fixedResponses.size()) {
            return Message.user(fixedResponses.get(userMessageCount));
        }

        // Generate dynamic response using LLM
        String prompt = buildPrompt(trajectory);
        String response = judge.generate(prompt);
        return Message.user(response.trim());
    }

    private String buildPrompt(ConversationTrajectory trajectory) {
        StringBuilder sb = new StringBuilder();

        sb.append(systemPrompt).append("\n\n");

        if (!trajectory.scenario().isEmpty()) {
            sb.append("Scenario: ").append(trajectory.scenario()).append("\n\n");
        }

        if (!trajectory.isEmpty()) {
            sb.append("Conversation so far:\n");
            for (Message message : trajectory.messages()) {
                String role = message.isUser() ? "You" : "Assistant";
                sb.append(role).append(": ").append(message.content()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Generate your next message as the user. ");
        sb.append("Respond with ONLY the message content, no role prefix or quotes.");

        return sb.toString();
    }

    /**
     * Builder for constructing LLM simulated users.
     */
    public static class Builder {
        private JudgeLM judge;
        private String systemPrompt;
        private String persona;
        private String behaviorGuidelines;
        private final List<String> fixedResponses = new ArrayList<>();

        /**
         * Sets the JudgeLM to use for generating responses.
         *
         * @param judge the JudgeLM instance
         * @return this builder
         */
        public Builder judge(JudgeLM judge) {
            this.judge = judge;
            return this;
        }

        /**
         * Sets the complete system prompt directly.
         * <p>
         * This overrides any persona or behavior guidelines. Use this for
         * full control over the user's behavior.
         *
         * @param systemPrompt the system prompt
         * @return this builder
         */
        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        /**
         * Sets a short persona description.
         * <p>
         * This will be combined with behavior guidelines to create the system prompt.
         * Example: "frustrated customer who received a defective product"
         *
         * @param persona the persona description
         * @return this builder
         */
        public Builder persona(String persona) {
            this.persona = persona;
            return this;
        }

        /**
         * Sets additional behavior guidelines for the simulated user.
         * <p>
         * Example: "Be assertive but not abusive. Express dissatisfaction clearly."
         *
         * @param guidelines the behavior guidelines
         * @return this builder
         */
        public Builder behaviorGuidelines(String guidelines) {
            this.behaviorGuidelines = guidelines;
            return this;
        }

        /**
         * Adds a fixed response to use before generating dynamic responses.
         * <p>
         * Fixed responses are used in order for the first N turns, after which
         * the LLM generates responses dynamically. This is useful for testing
         * specific conversation flows.
         *
         * @param response the fixed response
         * @return this builder
         */
        public Builder fixedResponse(String response) {
            this.fixedResponses.add(response);
            return this;
        }

        /**
         * Sets all fixed responses at once.
         * <p>
         * Fixed responses are used in order for the first N turns.
         *
         * @param responses the list of fixed responses
         * @return this builder
         */
        public Builder fixedResponses(List<String> responses) {
            this.fixedResponses.clear();
            this.fixedResponses.addAll(responses);
            return this;
        }

        /**
         * Builds the LLM simulated user.
         *
         * @return a new LLM simulated user
         * @throws IllegalStateException if judge is not set
         */
        public LLMSimulatedUser build() {
            if (judge == null) {
                throw new IllegalStateException("JudgeLM is required");
            }

            // Build system prompt if not explicitly set
            if (systemPrompt == null) {
                systemPrompt = buildDefaultSystemPrompt();
            }

            return new LLMSimulatedUser(this);
        }

        private String buildDefaultSystemPrompt() {
            StringBuilder sb = new StringBuilder();
            sb.append("You are simulating a user in a conversation with an AI assistant.\n\n");

            if (persona != null && !persona.isEmpty()) {
                sb.append("Your persona: ").append(persona).append("\n\n");
            }

            if (behaviorGuidelines != null && !behaviorGuidelines.isEmpty()) {
                sb.append("Behavior guidelines:\n").append(behaviorGuidelines).append("\n\n");
            }

            sb.append("Guidelines:\n");
            sb.append("- Stay in character throughout the conversation\n");
            sb.append("- Respond naturally based on the assistant's messages\n");
            sb.append("- Keep responses concise and realistic\n");
            sb.append("- React appropriately to the assistant's helpfulness or lack thereof");

            return sb.toString();
        }
    }
}
