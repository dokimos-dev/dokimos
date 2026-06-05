package dev.dokimos.springai.alibaba;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.OverAllStateBuilder;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.OutputType;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.ArgMatchMode;
import dev.dokimos.core.evaluators.agents.ArgumentMatcher;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import dev.dokimos.core.evaluators.agents.ToolTrajectoryEvaluator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;

class SpringAiAlibabaAgentTraceTest {

    private static OverAllState state(List<Message> messages) {
        return OverAllStateBuilder.builder()
                .putData(SpringAiAlibabaSupport.MESSAGES_KEY, messages)
                .build();
    }

    private static AssistantMessage assistant(String text, AssistantMessage.ToolCall... calls) {
        return AssistantMessage.builder()
                .content(text)
                .toolCalls(List.of(calls))
                .build();
    }

    private static AssistantMessage.ToolCall call(String id, String name, String argumentsJson) {
        return new AssistantMessage.ToolCall(id, "function", name, argumentsJson);
    }

    private static ToolResponseMessage responses(ToolResponseMessage.ToolResponse... responses) {
        return ToolResponseMessage.builder().responses(List.of(responses)).build();
    }

    @Test
    @DisplayName("folds a multi-turn conversation: calls across turns, results correlated, final text")
    void happyMultiTurn() {
        OverAllState state = state(List.of(
                new UserMessage("Plan my trip"),
                assistant(
                        "Working on it.",
                        call("c1", "search_flights", "{\"origin\":\"JFK\"}"),
                        call("c2", "book_hotel", "{\"city\":\"Paris\",\"nights\":3}")),
                responses(
                        new ToolResponseMessage.ToolResponse("c1", "search_flights", "[{\"id\":\"AF1\"}]"),
                        new ToolResponseMessage.ToolResponse("c2", "book_hotel", "{\"confirmation\":\"X\"}")),
                new AssistantMessage("All booked.")));

        AgentTrace trace = SpringAiAlibabaSupport.toAgentTrace(state);

        assertThat(trace.toolCalls()).hasSize(2);
        assertThat(trace.toolCalls().get(0).name()).isEqualTo("search_flights");
        assertThat(trace.toolCalls().get(0).result()).isEqualTo("[{\"id\":\"AF1\"}]");
        assertThat(trace.toolCalls().get(1).arguments()).containsEntry("nights", 3);
        assertThat(trace.toolCalls().get(1).result()).isEqualTo("{\"confirmation\":\"X\"}");
        assertThat(trace.finalResponse()).isEqualTo("All booked.");
    }

    @Test
    @DisplayName("typed reads: structured arguments and a JSON result read back as records")
    void structuredArgumentsAndJsonResultReadBackTyped() {
        // Spring AI Alibaba carries tool arguments as a JSON string and tool results as a String. The
        // fold parses the arguments into a Map and keeps the result verbatim, so the structured-output
        // typed-read API works on the captured trace: argumentsAs(...) over the Map and resultAs(...)
        // over the JSON result string.
        OverAllState state = state(List.of(
                new UserMessage("Plan my trip"),
                assistant("Booking.", call("c1", "book_hotel", "{\"city\":\"Paris\",\"nights\":3}")),
                responses(new ToolResponseMessage.ToolResponse(
                        "c1", "book_hotel", "{\"confirmation\":\"X\",\"price\":120}")),
                new AssistantMessage("Done.")));

        ToolCall call = SpringAiAlibabaSupport.toAgentTrace(state).toolCalls().get(0);

        BookingArgs args = call.argumentsAs(BookingArgs.class);
        assertThat(args).isEqualTo(new BookingArgs("Paris", 3));

        // 120 in the JSON result parses structurally to the record's double field (120.0).
        Booking booking = call.resultAs(Booking.class);
        assertThat(booking).isEqualTo(new Booking("X", 120.0));
    }

    @Test
    @DisplayName("typed reads: a JSON array result reads back as a typed list via OutputType")
    void jsonArrayResultReadsBackAsTypedListViaOutputType() {
        OverAllState state = state(List.of(
                assistant("Searching.", call("c1", "search_flights", "{}")),
                responses(new ToolResponseMessage.ToolResponse(
                        "c1", "search_flights", "[{\"id\":\"AF1\"},{\"id\":\"AF2\"}]")),
                new AssistantMessage("Found.")));

        ToolCall call = SpringAiAlibabaSupport.toAgentTrace(state).toolCalls().get(0);
        List<FlightRef> flights = call.resultAs(new OutputType<List<FlightRef>>() {});
        assertThat(flights).containsExactly(new FlightRef("AF1"), new FlightRef("AF2"));
    }

    @Test
    @DisplayName("per-turn windowing binds each call to its own result even when ids repeat across turns")
    void crossTurnWindowing() {
        OverAllState state = state(List.of(
                new UserMessage("Search twice"),
                assistant("turn 1", call("dup", "search", "{\"q\":\"first\"}")),
                responses(new ToolResponseMessage.ToolResponse("dup", "search", "first-result")),
                assistant("turn 2", call("dup", "search", "{\"q\":\"second\"}")),
                responses(new ToolResponseMessage.ToolResponse("dup", "search", "second-result")),
                new AssistantMessage("done")));

        List<ToolCall> calls = SpringAiAlibabaSupport.toToolCalls(state);

        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).result()).isEqualTo("first-result");
        assertThat(calls.get(0).arguments()).containsEntry("q", "first");
        assertThat(calls.get(1).result()).isEqualTo("second-result");
        assertThat(calls.get(1).arguments()).containsEntry("q", "second");
    }

    @Test
    @DisplayName("a calls-only conversation (no responses) leaves results null")
    void callsOnly() {
        OverAllState state =
                state(List.of(new UserMessage("go"), assistant("running", call("c1", "search", "{\"q\":\"x\"}"))));

        List<ToolCall> calls = SpringAiAlibabaSupport.toToolCalls(state);

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).result()).isNull();
    }

    @Test
    @DisplayName("an unmatched id within a turn's window yields a null result")
    void unmatchedId() {
        OverAllState state = state(List.of(
                assistant("x", call("c1", "search", "{}")),
                responses(new ToolResponseMessage.ToolResponse("other", "search", "data"))));

        List<ToolCall> calls = SpringAiAlibabaSupport.toToolCalls(state);

        assertThat(calls.get(0).result()).isNull();
    }

    @Test
    @DisplayName("a later turn's response does not bleed into an earlier turn's call")
    void responseDoesNotCrossTurnBoundary() {
        OverAllState state = state(List.of(
                assistant("turn 1", call("c1", "search", "{}")),
                new AssistantMessage("turn 2 with no tools"),
                responses(new ToolResponseMessage.ToolResponse("c1", "search", "late"))));

        List<ToolCall> calls = SpringAiAlibabaSupport.toToolCalls(state);

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).result()).isNull();
    }

    @Test
    @DisplayName("malformed and blank argument JSON yield empty arguments without throwing")
    void malformedArgs() {
        OverAllState state = state(List.of(assistant("x", call("c1", "a", "{bad"), call("c2", "b", ""))));

        List<ToolCall> calls = SpringAiAlibabaSupport.toToolCalls(state);

        assertThat(calls.get(0).arguments()).isEmpty();
        assertThat(calls.get(1).arguments()).isEmpty();
    }

    @Test
    @DisplayName("a conversation with no tool calls yields a trace with the final response only")
    void noToolCalls() {
        OverAllState state = state(List.of(new UserMessage("hi"), new AssistantMessage("Just an answer.")));

        AgentTrace trace = SpringAiAlibabaSupport.toAgentTrace(state);

        assertThat(trace.toolCalls()).isEmpty();
        assertThat(trace.finalResponse()).isEqualTo("Just an answer.");
    }

    @Test
    @DisplayName("a null state yields an empty trace without throwing")
    void nullState() {
        AgentTrace trace = SpringAiAlibabaSupport.toAgentTrace((OverAllState) null);

        assertThat(trace.toolCalls()).isEmpty();
        assertThat(trace.finalResponse()).isNull();
        assertThat(SpringAiAlibabaSupport.messages(null)).isEmpty();
    }

    @Test
    @DisplayName("an empty Optional yields an empty trace without throwing")
    void emptyOptional() {
        AgentTrace trace = SpringAiAlibabaSupport.toAgentTrace(Optional.empty());

        assertThat(trace.toolCalls()).isEmpty();
        assertThat(trace.finalResponse()).isNull();
    }

    @Test
    @DisplayName("a state missing the messages key yields an empty trace without throwing")
    void missingMessagesKey() {
        OverAllState state =
                OverAllStateBuilder.builder().putData("other", "value").build();

        AgentTrace trace = SpringAiAlibabaSupport.toAgentTrace(state);

        assertThat(trace.toolCalls()).isEmpty();
        assertThat(trace.finalResponse()).isNull();
        assertThat(SpringAiAlibabaSupport.messages(state)).isEmpty();
    }

    @Test
    @DisplayName("unknown / non-Message elements in the messages list are skipped without throwing")
    void unknownSubtypeTolerance() {
        List<Object> mixed = new ArrayList<>();
        mixed.add("a bare string, not a Message");
        mixed.add(42);
        mixed.add(new AssistantMessage("the real answer"));
        OverAllState state = OverAllStateBuilder.builder()
                .putData(SpringAiAlibabaSupport.MESSAGES_KEY, mixed)
                .build();

        AgentTrace trace = SpringAiAlibabaSupport.toAgentTrace(state);

        assertThat(SpringAiAlibabaSupport.messages(state)).hasSize(1);
        assertThat(trace.finalResponse()).isEqualTo("the real answer");
    }

    @Test
    @DisplayName("a non-list value under the messages key yields an empty trace")
    void nonListMessagesValue() {
        OverAllState state = OverAllStateBuilder.builder()
                .putData(SpringAiAlibabaSupport.MESSAGES_KEY, "not a list")
                .build();

        assertThat(SpringAiAlibabaSupport.messages(state)).isEmpty();
        assertThat(SpringAiAlibabaSupport.toAgentTrace(state).toolCalls()).isEmpty();
    }

    @Test
    @DisplayName("tool definitions map from the agent's ToolCallbacks via SpringAiSupport")
    void toolDefinitions() {
        var springDef = mock(org.springframework.ai.tool.definition.ToolDefinition.class);
        when(springDef.name()).thenReturn("search_flights");
        when(springDef.description()).thenReturn("Search for flights");
        when(springDef.inputSchema())
                .thenReturn(
                        "{\"type\":\"object\",\"properties\":{\"origin\":{\"type\":\"string\"}},\"required\":[\"origin\"]}");
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(springDef);

        List<ToolDefinition> defs = SpringAiAlibabaSupport.toToolDefinitions(List.of(callback));

        assertThat(defs).hasSize(1);
        assertThat(defs.get(0).name()).isEqualTo("search_flights");
        assertThat(defs.get(0).parameterNames()).containsExactly("origin");
        assertThat(defs.get(0).requiredParameters()).containsExactly("origin");
    }

    @Test
    @DisplayName("null and empty ToolCallback lists yield no tool definitions")
    void toolDefinitionsEmpty() {
        assertThat(SpringAiAlibabaSupport.toToolDefinitions(null)).isEmpty();
        assertThat(SpringAiAlibabaSupport.toToolDefinitions(List.of())).isEmpty();
    }

    @Test
    @DisplayName("the produced trace satisfies the agent evaluators without throwing")
    void roundTripThroughEvaluators() {
        OverAllState state = state(List.of(
                new UserMessage("Fly from JFK"),
                assistant("done", call("c1", "search_flights", "{\"origin\":\"JFK\"}")),
                responses(new ToolResponseMessage.ToolResponse("c1", "search_flights", "[]"))));

        AgentTrace trace = SpringAiAlibabaSupport.toAgentTrace(state);
        var testCase = EvalTestCase.builder()
                .input("Fly from JFK")
                .actualOutputs(trace.toOutputMap())
                .expectedOutput("toolCalls", List.of(ToolCall.of("search_flights", Map.of())))
                .build();

        assertThatCode(() -> ToolCallValidityEvaluator.builder()
                        .build()
                        .evaluate(EvalTestCase.builder()
                                .actualOutputs(testCase.actualOutputs())
                                .metadata("tools", List.<ToolDefinition>of())
                                .build()))
                .doesNotThrowAnyException();
        assertThat(ToolTrajectoryEvaluator.builder()
                        .matchMode(ToolTrajectoryEvaluator.MatchMode.ANY_ORDER)
                        .argumentMatcher(ArgumentMatcher.of(ArgMatchMode.IGNORE))
                        .build()
                        .evaluate(testCase)
                        .score())
                .isEqualTo(1.0);
    }

    // --- typed-read fixtures ---------------------------------------------------------------------

    record BookingArgs(String city, int nights) {}

    record Booking(String confirmation, double price) {}

    record FlightRef(String id) {}
}
