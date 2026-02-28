package dev.dokimos.core.conversation;

/**
 * Functional interface representing an application that can engage in
 * multi-turn conversations.
 * <p>
 * This interface wraps the application under test, allowing it to receive
 * conversation
 * context and generate responses. Implementations typically delegate to a chat
 * client
 * or LLM service.
 * <p>
 * Example usage with Spring AI:
 *
 * <pre>{@code
 * ConversationalApplication app = trajectory -> {
 *     String response = chatClient.prompt()
 *             .messages(formatMessages(trajectory))
 *             .call()
 *             .content();
 *     return Message.assistant(response);
 * };
 * }</pre>
 */
@FunctionalInterface
public interface ConversationalApplication {

    /**
     * Generates a response to the current conversation state.
     * <p>
     * The implementation should process the entire trajectory to maintain
     * context awareness and generate an appropriate assistant response.
     *
     * @param trajectory the current conversation trajectory
     * @return the assistant's response message
     */
    Message respond(ConversationTrajectory trajectory);
}
