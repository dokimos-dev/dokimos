package dev.dokimos.core.conversation;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.ToolCall;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConversationTrajectoryTest {

    @Test
    void shouldCreateEmptyTrajectory() {
        ConversationTrajectory trajectory = ConversationTrajectory.empty();

        assertThat(trajectory.messages()).isEmpty();
        assertThat(trajectory.scenario()).isEmpty();
        assertThat(trajectory.metadata()).isEmpty();
        assertThat(trajectory.isEmpty()).isTrue();
        assertThat(trajectory.turnCount()).isZero();
    }

    @Test
    void shouldBuildTrajectoryWithMessages() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .scenario("Customer support")
                .userMessage("I need help")
                .assistantMessage("How can I assist you?")
                .userMessage("I have a problem")
                .assistantMessage("Let me help with that")
                .build();

        assertThat(trajectory.messages()).hasSize(4);
        assertThat(trajectory.scenario()).isEqualTo("Customer support");
        assertThat(trajectory.turnCount()).isEqualTo(2);
        assertThat(trajectory.isEmpty()).isFalse();
    }

    @Test
    void shouldFilterUserMessages() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("User 1")
                .assistantMessage("Assistant 1")
                .userMessage("User 2")
                .build();

        List<Message> userMessages = trajectory.userMessages();

        assertThat(userMessages).hasSize(2);
        assertThat(userMessages).allMatch(Message::isUser);
    }

    @Test
    void shouldFilterAssistantMessages() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("User 1")
                .assistantMessage("Assistant 1")
                .assistantMessage("Assistant 2")
                .build();

        List<Message> assistantMessages = trajectory.assistantMessages();

        assertThat(assistantMessages).hasSize(2);
        assertThat(assistantMessages).allMatch(Message::isAssistant);
    }

    @Test
    void shouldGetLastMessage() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("First")
                .assistantMessage("Last")
                .build();

        assertThat(trajectory.lastMessage().content()).isEqualTo("Last");
    }

    @Test
    void shouldReturnNullForLastMessageWhenEmpty() {
        ConversationTrajectory trajectory = ConversationTrajectory.empty();

        assertThat(trajectory.lastMessage()).isNull();
        assertThat(trajectory.lastUserMessage()).isNull();
        assertThat(trajectory.lastAssistantMessage()).isNull();
    }

    @Test
    void shouldAppendMessageImmutably() {
        ConversationTrajectory original =
                ConversationTrajectory.builder().userMessage("Original").build();

        ConversationTrajectory updated = original.withMessage(Message.assistant("New"));

        assertThat(original.messages()).hasSize(1);
        assertThat(updated.messages()).hasSize(2);
        assertThat(updated.lastMessage().content()).isEqualTo("New");
    }

    @Test
    void shouldCalculateTurnCountCorrectly() {
        // A turn is a user message followed by an assistant response
        ConversationTrajectory incomplete = ConversationTrajectory.builder()
                .userMessage("U1")
                .assistantMessage("A1")
                .userMessage("U2") // No response yet
                .build();

        // min(2 users, 1 assistant) = 1
        assertThat(incomplete.turnCount()).isEqualTo(1);

        ConversationTrajectory complete = ConversationTrajectory.builder()
                .userMessage("U1")
                .assistantMessage("A1")
                .userMessage("U2")
                .assistantMessage("A2")
                .build();

        assertThat(complete.turnCount()).isEqualTo(2);
    }

    @Test
    void shouldConvertToText() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .scenario("Test scenario")
                .userMessage("Hello")
                .assistantMessage("Hi there")
                .build();

        String text = trajectory.toText();

        assertThat(text).contains("Scenario: Test scenario");
        assertThat(text).contains("USER: Hello");
        assertThat(text).contains("ASSISTANT: Hi there");
    }

    @Test
    void shouldConvertToJson() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .scenario("Test")
                .userMessage("Hello")
                .build();

        String json = trajectory.toJson();

        assertThat(json).contains("\"scenario\"");
        assertThat(json).contains("\"Test\"");
        assertThat(json).contains("\"messages\"");
        assertThat(json).contains("\"role\"");
        assertThat(json).contains("\"user\"");
        assertThat(json).contains("\"content\"");
        assertThat(json).contains("\"Hello\"");
    }

    @Test
    void shouldAddMetadata() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .metadata("key1", "value1")
                .metadata(Map.of("key2", "value2"))
                .build();

        assertThat(trajectory.metadata()).containsEntry("key1", "value1").containsEntry("key2", "value2");
    }

    @Test
    void shouldMakeMessagesImmutable() {
        ConversationTrajectory trajectory =
                ConversationTrajectory.builder().userMessage("Test").build();

        assertThatThrownBy(() -> trajectory.messages().add(Message.user("New")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldFlattenToolCallsInChronologicalOrder() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("Plan my trip")
                .assistantMessage("Searching.", List.of(call("search", "flights"), call("search", "hotels")))
                .userMessage("Book it")
                .assistantMessage("Booking.", List.of(call("book", "flight")))
                .build();

        List<ToolCall> calls = trajectory.toolCalls();

        // Flattened across turns, in the order the assistant made them.
        assertThat(calls).extracting(ToolCall::name).containsExactly("search", "search", "book");
        assertThat(calls.get(0).arguments()).containsEntry("query", "flights");
        assertThat(calls.get(1).arguments()).containsEntry("query", "hotels");
        assertThat(calls.get(2).arguments()).containsEntry("query", "flight");
    }

    @Test
    void shouldReturnEmptyToolCallsWhenNoneCalled() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("Hello")
                .assistantMessage("Hi there")
                .build();

        assertThat(trajectory.toolCalls()).isEmpty();
    }

    @Test
    void shouldGroupToolCallsOnePerAssistantMessage() {
        // Leading, trailing, and consecutive assistant turns, plus one tool-free assistant turn.
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .assistantMessage("Leading.", List.of(call("greet", "hi"))) // leading assistant
                .userMessage("What is the weather?")
                .assistantMessage("No tools needed here.") // tool-free assistant turn
                .assistantMessage("Let me check.", List.of(call("weather", "Paris"))) // consecutive assistant
                .assistantMessage("Done.", List.of(call("log", "a"), call("log", "b"))) // trailing assistant
                .build();

        List<List<ToolCall>> byTurn = trajectory.toolCallsByTurn();

        // One inner list per assistant message, in order, including the empty list for the tool-free turn.
        assertThat(byTurn).hasSize(4);
        assertThat(byTurn.get(0)).extracting(ToolCall::name).containsExactly("greet");
        assertThat(byTurn.get(1)).isEmpty();
        assertThat(byTurn.get(2)).extracting(ToolCall::name).containsExactly("weather");
        assertThat(byTurn.get(3)).extracting(ToolCall::name).containsExactly("log", "log");
    }

    @Test
    void shouldGroupToolCallsByTurnForEmptyTrajectory() {
        assertThat(ConversationTrajectory.empty().toolCallsByTurn()).isEmpty();
        assertThat(ConversationTrajectory.empty().toolCalls()).isEmpty();
    }

    @Test
    void shouldRenderToolFreeToJsonByteIdenticalToSnapshot() throws Exception {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .scenario("Test scenario")
                .userMessage("Hello")
                .assistantMessage("Hi there")
                .build();

        // Tool-free messages keep the exact pre-feature JSON shape. The key order within each map is
        // not load-bearing (and Map.of randomizes it per JVM run), so the snapshot is compared after
        // canonicalizing the key order; the structure and values are locked.
        String expected = """
                {
                  "messages" : [ {
                    "content" : "Hello",
                    "metadata" : { },
                    "role" : "user"
                  }, {
                    "content" : "Hi there",
                    "metadata" : { },
                    "role" : "assistant"
                  } ],
                  "metadata" : { },
                  "scenario" : "Test scenario",
                  "turnCount" : 1
                }""";

        assertThat(canonicalize(trajectory.toJson())).isEqualTo(expected);
    }

    @Test
    void shouldRenderToolFreeToTextByteIdenticalToSnapshot() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .scenario("Test scenario")
                .userMessage("Hello")
                .assistantMessage("Hi there")
                .build();

        String expected = """
                Scenario: Test scenario

                USER: Hello

                ASSISTANT: Hi there""";

        assertThat(trajectory.toText()).isEqualTo(expected);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldEmitToolCallsEntryOnlyOnAssistantTurnsThatCalledTools() throws Exception {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("Weather in Paris?")
                .assistantMessage("Let me check.", List.of(call("get_weather", "Paris")))
                .assistantMessage("It is sunny.") // tool-free assistant turn
                .build();

        Map<String, Object> json = new ObjectMapper().readValue(trajectory.toJson(), Map.class);
        List<Map<String, Object>> messages = (List<Map<String, Object>>) json.get("messages");

        assertThat(messages).hasSize(3);
        // The user turn and the tool-free assistant turn carry no toolCalls entry at all.
        assertThat(messages.get(0)).doesNotContainKey("toolCalls");
        assertThat(messages.get(2)).doesNotContainKey("toolCalls");

        // Only the assistant turn that called a tool has a toolCalls entry, and it round-trips the
        // tool name and arguments.
        List<Map<String, Object>> toolCalls =
                (List<Map<String, Object>>) messages.get(1).get("toolCalls");
        assertThat(toolCalls).hasSize(1);
        assertThat(toolCalls.get(0)).containsEntry("name", "get_weather");
        assertThat((Map<String, Object>) toolCalls.get(0).get("arguments")).containsEntry("query", "Paris");
    }

    @Test
    void shouldRenderToolCallLineInToText() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("Weather in Paris?")
                .assistantMessage("Let me check.", List.of(call("get_weather", "Paris")))
                .build();

        String text = trajectory.toText();

        assertThat(text).contains("ASSISTANT: Let me check.");
        assertThat(text).contains("[tool: get_weather(");
        assertThat(text).contains("query=Paris");
    }

    @Test
    void shouldUseLastUserMessageAsInputForDeterministicTestCase() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .userMessage("First question")
                .assistantMessage("First answer", List.of(call("search", "a")))
                .userMessage("Second question")
                .assistantMessage("Second answer", List.of(call("search", "b")))
                .build();

        EvalTestCase deterministic = trajectory.toTestCase();

        // The deterministic path scores the last turn: input is the last user message only.
        assertThat(deterministic.input()).isEqualTo("Second question");
        assertThat(deterministic.actualOutput()).isEqualTo("Second answer");
        assertThat(deterministic.<List<ToolCall>>actualOutputAs("toolCalls", OUTPUT_TOOL_CALLS))
                .extracting(ToolCall::name)
                .containsExactly("search", "search");
    }

    @Test
    void shouldUseFullTranscriptAsInputForJudgeTestCase() {
        ConversationTrajectory trajectory = ConversationTrajectory.builder()
                .scenario("Trip planning")
                .userMessage("First question")
                .assistantMessage("First answer", List.of(call("search", "a")))
                .userMessage("Second question")
                .assistantMessage("Second answer", List.of(call("search", "b")))
                .build();

        EvalTestCase judge = trajectory.toTestCase(List.of(), List.of("plan the trip"));

        // The judge path reasons over the whole conversation: input is the full rendered transcript,
        // not just the last user message, so the transcript is not re-wrapped/duplicated downstream.
        assertThat(judge.input()).contains("First question").contains("Second question");
        // The grounding transcript names the tool calls but omits their arguments, so the args under
        // test never leak into the hallucination evaluator's grounding source.
        assertThat(judge.input()).contains("[tool: search]").doesNotContain("[tool: search(");
        // No separate output is set on the judge case (the transcript is the input).
        assertThat(judge.actualOutput()).isNull();
    }

    private static final dev.dokimos.core.OutputType<List<ToolCall>> OUTPUT_TOOL_CALLS =
            new dev.dokimos.core.OutputType<>() {};

    /** Builds a simple single-argument tool call. */
    private static ToolCall call(String name, String query) {
        return ToolCall.of(name, Map.of("query", query));
    }

    /**
     * Parses then re-serializes the JSON with map keys sorted, so byte-identical snapshot comparisons
     * are stable regardless of the per-JVM-run iteration order of {@code Map.of(...)}.
     */
    private static String canonicalize(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        return mapper.writeValueAsString(mapper.readValue(json, Object.class));
    }
}
