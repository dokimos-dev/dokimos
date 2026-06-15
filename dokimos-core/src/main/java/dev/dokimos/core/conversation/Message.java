package dev.dokimos.core.conversation;

import dev.dokimos.core.agents.ToolCall;
import java.util.List;
import java.util.Map;

/**
 * Represents a single message in a conversation.
 * <p>
 * Each message has a role (USER, ASSISTANT, or SYSTEM), content, optional metadata, and, for an
 * assistant turn, the tool calls it made.
 *
 * @param role      the role of the message sender
 * @param content   the message content
 * @param metadata  additional metadata about the message
 * @param toolCalls the tool calls made on this turn; empty for a turn that called no tools
 */
public record Message(Role role, String content, Map<String, Object> metadata, List<ToolCall> toolCalls) {

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
        toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
    }

    /**
     * Creates a message with no tool calls. Preserves the three-argument construction used before
     * tool calls were modeled, so existing callers compile and link unchanged.
     *
     * @param role     the role of the message sender
     * @param content  the message content
     * @param metadata additional metadata about the message
     */
    public Message(Role role, String content, Map<String, Object> metadata) {
        this(role, content, metadata, List.of());
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
     * Creates an assistant message carrying the tool calls it made on this turn.
     * <p>
     * With {@code null} or empty {@code toolCalls} the result equals {@link #assistant(String)}.
     *
     * @param content   the message content
     * @param toolCalls the tool calls made on this turn
     * @return a new assistant message whose {@link #toolCalls()} returns the given calls
     */
    public static Message assistant(String content, List<ToolCall> toolCalls) {
        return new Message(Role.ASSISTANT, content, Map.of(), toolCalls);
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

    /**
     * Checks whether this message carries any tool calls.
     *
     * @return true if at least one tool call is attached
     */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
