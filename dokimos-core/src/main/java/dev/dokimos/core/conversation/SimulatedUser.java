package dev.dokimos.core.conversation;

/**
 * Functional interface for simulating user behavior in multi-turn
 * conversations.
 * <p>
 * A simulated user generates contextually appropriate messages based on the
 * current
 * conversation trajectory. Implementations can use LLMs, scripted responses, or
 * any other strategy to produce realistic user interactions.
 * <p>
 *
 * @see LLMSimulatedUser for an LLM-based implementation
 * @see UserPersonas for pre-built persona factories
 */
@FunctionalInterface
public interface SimulatedUser {

    /**
     * Generates the next user message based on the current conversation state.
     * <p>
     * The implementation should analyze the trajectory to understand context
     * and generate an appropriate response that advances the conversation
     * in a realistic manner.
     *
     * @param trajectory the current conversation trajectory
     * @return the next user message
     */
    Message generateMessage(ConversationTrajectory trajectory);
}
