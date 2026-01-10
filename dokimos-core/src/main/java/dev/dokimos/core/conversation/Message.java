package dev.dokimos.core.conversation;

import java.util.Map;

/**
 * Represents a single message in a conversation.
 * <p>
 * Each message has a role (USER, ASSISTANT, or SYSTEM), content, and optional
 * metadata.
 *
 * @param role     the role of the message sender
 * @param content  the message content
 * @param metadata additional metadata about the message
 */
public record Message(Role role, String content, Map<String, Object> metadata) {

    /**
     * The role of a message sender in a conversation.
     */
    public enum Role {
        /**
         * A message from the user.
         */
        USER,
        /**
         * A message from the assistant.
         */
        ASSISTANT,
        /**
         * A system message providing context or instructions.
         */
        SYSTEM
    }

    /**
     * Compact constructor.
     */
    public Message {
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null");
        }
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Creates a message with just role and content.
     *
     * @param role    the message role
     * @param content the message content
     * @return a new message
     */
    public static Message of(Role role, String content) {
        return new Message(role, content, Map.of());
    }

    /**
     * Creates a user message.
     *
     * @param content the message content
     * @return a new user message
     */
    public static Message user(String content) {
        return new Message(Role.USER, content, Map.of());
    }

    /**
     * Creates an assistant message.
     *
     * @param content the message content
     * @return a new assistant message
     */
    public static Message assistant(String content) {
        return new Message(Role.ASSISTANT, content, Map.of());
    }

    /**
     * Creates a system message.
     *
     * @param content the message content
     * @return a new system message
     */
    public static Message system(String content) {
        return new Message(Role.SYSTEM, content, Map.of());
    }

    /**
     * Checks if this message is from a user.
     *
     * @return true if the role is USER
     */
    public boolean isUser() {
        return role == Role.USER;
    }

    /**
     * Checks if this message is from an assistant.
     *
     * @return true if the role is ASSISTANT
     */
    public boolean isAssistant() {
        return role == Role.ASSISTANT;
    }

    /**
     * Checks if this message is a system message.
     *
     * @return true if the role is SYSTEM
     */
    public boolean isSystem() {
        return role == Role.SYSTEM;
    }
}
