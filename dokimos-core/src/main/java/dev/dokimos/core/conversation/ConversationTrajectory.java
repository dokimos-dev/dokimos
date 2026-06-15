package dev.dokimos.core.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
     * Flattens every assistant turn's tool calls into one list, in chronological order. This is the
     * flat {@code List<ToolCall>} the agent tool-call evaluators read.
     *
     * @return all tool calls across the conversation, never null
     */
    public List<ToolCall> toolCalls() {
        return messages.stream()
                .filter(Message::isAssistant)
                .flatMap(m -> m.toolCalls().stream())
                .toList();
    }

    /**
     * Groups tool calls per assistant turn, in order: one inner list per assistant message (each
     * possibly empty). This is the per-turn view for scoring each turn against its own expected
     * calls. The unit of grouping is an assistant message, which can differ from {@link #turnCount()}
     * (which counts user/assistant pairs) when a conversation has consecutive or trailing assistant
     * messages.
     *
     * @return tool calls grouped by assistant message, never null
     */
    public List<List<ToolCall>> toolCallsByTurn() {
        return assistantMessages().stream().map(Message::toolCalls).toList();
    }

    /**
     * Collapses the conversation into a single {@link AgentTrace}: the final response is the last
     * assistant message's content (or empty), the tool calls are {@link #toolCalls()}, reasoning is
     * empty, and metadata carries the scenario and turn count. Intended for deterministic, scripted
     * conversations; for non-deterministic simulated runs, prefer per-turn evaluation over
     * {@link #toolCallsByTurn()}.
     *
     * @return the collapsed agent trace
     */
    public AgentTrace toAgentTrace() {
        Message last = lastAssistantMessage();
        return AgentTrace.builder()
                .finalResponse(last != null ? last.content() : "")
                .toolCalls(toolCalls())
                .metadata("scenario", scenario)
                .metadata("turnCount", turnCount())
                .build();
    }

    /**
     * Builds the actual-outputs map the agent evaluators read, identical in shape to
     * {@link AgentTrace#toOutputMap()}.
     *
     * @return the actual outputs map
     */
    public Map<String, Object> toAgentOutputs() {
        return toAgentTrace().toOutputMap();
    }

    /**
     * Builds a test case for the deterministic tool-call evaluators, using the last user message as
     * input.
     *
     * @return a new test case
     */
    public EvalTestCase toTestCase() {
        return toAgentTrace().toTestCase(lastUserContent());
    }

    /**
     * Builds a test case for the deterministic tool-call and tool-definition evaluators, adding the
     * tools the agent could call.
     *
     * @param tools the available tool definitions
     * @return a new test case
     */
    public EvalTestCase toTestCase(List<ToolDefinition> tools) {
        return toAgentTrace().toTestCase(lastUserContent(), tools);
    }

    /**
     * Builds a test case for the judge-based evaluators ({@code TaskCompletionEvaluator},
     * {@code ToolArgumentHallucinationEvaluator}). Unlike the deterministic overloads, the input is
     * the full rendered transcript ({@link #toText()}) so the judge reasons over the whole
     * conversation rather than only the last turn.
     *
     * @param tools the available tool definitions
     * @param tasks the tasks the user asked the agent to complete
     * @return a new test case
     */
    public EvalTestCase toTestCase(List<ToolDefinition> tools, List<String> tasks) {
        // The rendered transcript is already a role-labeled dialog, so it is passed as the input
        // and no separate "output" is set. Otherwise TaskCompletionEvaluator.resolveDialog would
        // wrap the whole transcript under a second "User:/Agent:" layer and duplicate the last turn.
        return EvalTestCase.builder()
                .input(toText())
                .actualOutput("toolCalls", toolCalls())
                .metadata("tools", tools)
                .metadata("tasks", tasks)
                .build();
    }

    private String lastUserContent() {
        Message user = lastUserMessage();
        return user != null ? user.content() : null;
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
            sb.append(message.role().name()).append(": ").append(message.content());
            appendToolLines(sb, message);
            sb.append("\n\n");
        }
        return sb.toString().trim();
    }

    /** Appends one compact {@code [tool: name(args)]} line per tool call on the message. */
    static void appendToolLines(StringBuilder sb, Message message) {
        for (ToolCall call : message.toolCalls()) {
            sb.append("\n  [tool: ")
                    .append(call.name())
                    .append("(")
                    .append(call.arguments())
                    .append(")]");
        }
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
            json.put("messages", messages.stream().map(ConversationTrajectory::toJsonMessage).toList());
            json.put("metadata", metadata);
            return OBJECT_MAPPER.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize trajectory to JSON", e);
        }
    }

    private static Object toJsonMessage(Message m) {
        // Tool-free messages keep the exact pre-feature shape so their JSON stays byte-identical.
        if (!m.hasToolCalls()) {
            return Map.of("role", m.role().name().toLowerCase(), "content", m.content(), "metadata", m.metadata());
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("role", m.role().name().toLowerCase());
        json.put("content", m.content());
        json.put("metadata", m.metadata());
        json.put("toolCalls", m.toolCalls());
        return json;
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
         * Adds an assistant message carrying the tool calls it made.
         *
         * @param content   the message content
         * @param toolCalls the tool calls made on this turn
         * @return this builder
         */
        public Builder assistantMessage(String content, List<ToolCall> toolCalls) {
            return message(Message.assistant(content, toolCalls));
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
