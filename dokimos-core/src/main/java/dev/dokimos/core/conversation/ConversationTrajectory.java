package dev.dokimos.core.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a complete conversation trajectory between a simulated user and an
 * application.
 * <p>
 * A trajectory captures the full history of messages exchanged during a
 * multi-turn conversation, along with scenario information and metadata for
 * evaluation.
 *
 * @param messages the list of messages in chronological order
 * @param scenario a description of the test scenario
 * @param metadata additional metadata about the conversation
 */
public record ConversationTrajectory(List<Message> messages, String scenario, Map<String, Object> metadata) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Compact constructor ensuring immutability.
     */
    public ConversationTrajectory {
        messages = messages != null ? List.copyOf(messages) : List.of();
        scenario = scenario != null ? scenario : "";
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Creates an empty trajectory with no messages.
     *
     * @return an empty trajectory
     */
    public static ConversationTrajectory empty() {
        return new ConversationTrajectory(List.of(), "", Map.of());
    }

    /**
     * Creates a new builder for constructing trajectories.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the number of conversation turns (user-assistant message pairs).
     * <p>
     * A turn is counted as a user message followed by an assistant response.
     *
     * @return the number of complete turns
     */
    public int turnCount() {
        int userMessages = (int) messages.stream().filter(Message::isUser).count();
        int assistantMessages =
                (int) messages.stream().filter(Message::isAssistant).count();
        return Math.min(userMessages, assistantMessages);
    }

    /**
     * Returns only the user messages from the conversation.
     *
     * @return list of user messages
     */
    public List<Message> userMessages() {
        return messages.stream().filter(Message::isUser).toList();
    }

    /**
     * Returns only the assistant messages from the conversation.
     *
     * @return list of assistant messages
     */
    public List<Message> assistantMessages() {
        return messages.stream().filter(Message::isAssistant).toList();
    }

    /**
     * Returns only the system messages from the conversation.
     *
     * @return list of system messages
     */
    public List<Message> systemMessages() {
        return messages.stream().filter(Message::isSystem).toList();
    }

    /**
     * Returns the last message in the conversation, if any.
     *
     * @return the last message or null if empty
     */
    public Message lastMessage() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    /**
     * Returns the last user message in the conversation, if any.
     *
     * @return the last user message or null if none
     */
    public Message lastUserMessage() {
        List<Message> userMsgs = userMessages();
        return userMsgs.isEmpty() ? null : userMsgs.get(userMsgs.size() - 1);
    }

    /**
     * Returns the last assistant message in the conversation, if any.
     *
     * @return the last assistant message or null if none
     */
    public Message lastAssistantMessage() {
        List<Message> assistantMsgs = assistantMessages();
        return assistantMsgs.isEmpty() ? null : assistantMsgs.get(assistantMsgs.size() - 1);
    }

    /**
     * Creates a new trajectory with an additional message appended.
     *
     * @param message the message to append
     * @return a new trajectory with the message added
     */
    public ConversationTrajectory withMessage(Message message) {
        List<Message> newMessages = new ArrayList<>(messages);
        newMessages.add(message);
        return new ConversationTrajectory(newMessages, scenario, metadata);
    }

    /**
     * Checks if the conversation is empty.
     *
     * @return true if there are no messages
     */
    public boolean isEmpty() {
        return messages.isEmpty();
    }

    /**
     * Formats the conversation as a simple text transcript.
     *
     * @return the conversation as text
     */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        if (!scenario.isEmpty()) {
            sb.append("Scenario: ").append(scenario).append("\n\n");
        }
        for (Message message : messages) {
            sb.append(message.role().name())
                    .append(": ")
                    .append(message.content())
                    .append("\n\n");
        }
        return sb.toString().trim();
    }

    /**
     * Serializes the trajectory to JSON for debugging and logging.
     *
     * @return JSON representation of the trajectory
     */
    public String toJson() {
        try {
            Map<String, Object> json = new HashMap<>();
            json.put("scenario", scenario);
            json.put("turnCount", turnCount());
            json.put(
                    "messages",
                    messages.stream()
                            .map(m -> Map.of(
                                    "role", m.role().name().toLowerCase(),
                                    "content", m.content(),
                                    "metadata", m.metadata()))
                            .toList());
            json.put("metadata", metadata);
            return OBJECT_MAPPER.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize trajectory to JSON", e);
        }
    }

    /**
     * Builder for constructing conversation trajectories.
     */
    public static class Builder {
        private final List<Message> messages = new ArrayList<>();
        private String scenario = "";
        private final Map<String, Object> metadata = new HashMap<>();

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
         * Adds a message to the trajectory.
         *
         * @param message the message to add
         * @return this builder
         */
        public Builder message(Message message) {
            this.messages.add(message);
            return this;
        }

        /**
         * Adds a user message to the trajectory.
         *
         * @param content the message content
         * @return this builder
         */
        public Builder userMessage(String content) {
            return message(Message.user(content));
        }

        /**
         * Adds an assistant message to the trajectory.
         *
         * @param content the message content
         * @return this builder
         */
        public Builder assistantMessage(String content) {
            return message(Message.assistant(content));
        }

        /**
         * Adds a system message to the trajectory.
         *
         * @param content the message content
         * @return this builder
         */
        public Builder systemMessage(String content) {
            return message(Message.system(content));
        }

        /**
         * Adds all messages from a list.
         *
         * @param messages the messages to add
         * @return this builder
         */
        public Builder messages(List<Message> messages) {
            this.messages.addAll(messages);
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
         * Builds the conversation trajectory.
         *
         * @return a new conversation trajectory
         */
        public ConversationTrajectory build() {
            return new ConversationTrajectory(messages, scenario, metadata);
        }
    }
}
