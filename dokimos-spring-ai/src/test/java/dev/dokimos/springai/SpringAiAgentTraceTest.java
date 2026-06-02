package dev.dokimos.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import dev.dokimos.core.evaluators.agents.ToolTrajectoryEvaluator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

class SpringAiAgentTraceTest {

    private static AssistantMessage message(String text, List<AssistantMessage.ToolCall> calls) {
        return AssistantMessage.builder().content(text).toolCalls(calls).build();
    }

    private static AssistantMessage.ToolCall call(String id, String name, String argumentsJson) {
        return new AssistantMessage.ToolCall(id, "function", name, argumentsJson);
    }

    private static ToolResponseMessage responses(ToolResponseMessage.ToolResponse... responses) {
        return ToolResponseMessage.builder().responses(List.of(responses)).build();
    }

    @Test
    @DisplayName("maps tool calls and correlates results by id")
    void mapsAndCorrelates() {
        AssistantMessage message = message(
                "Booked.",
                List.of(
                        call("c1", "search_flights", "{\"origin\":\"JFK\"}"),
                        call("c2", "book_hotel", "{\"city\":\"Paris\",\"nights\":3}")));
        ToolResponseMessage results = responses(
                new ToolResponseMessage.ToolResponse("c1", "search_flights", "[{\"id\":\"AF1\"}]"),
                new ToolResponseMessage.ToolResponse("c2", "book_hotel", "{\"confirmation\":\"X\"}"));

        AgentTrace trace = SpringAiSupport.toAgentTrace(message, List.of(results));

        assertThat(trace.finalResponse()).isEqualTo("Booked.");
        assertThat(trace.toolCalls()).hasSize(2);
        assertThat(trace.toolCalls().get(0).name()).isEqualTo("search_flights");
        assertThat(trace.toolCalls().get(0).result()).isEqualTo("[{\"id\":\"AF1\"}]");
        assertThat(trace.toolCalls().get(1).arguments()).containsEntry("nights", 3);
        assertThat(trace.toolCalls().get(1).result()).isEqualTo("{\"confirmation\":\"X\"}");
    }

    @Test
    @DisplayName("calls-only overload leaves results null")
    void callsOnly() {
        AssistantMessage message = message("done", List.of(call("c1", "search", "{\"q\":\"x\"}")));

        AgentTrace trace = SpringAiSupport.toAgentTrace(message);

        assertThat(trace.toolCalls()).hasSize(1);
        assertThat(trace.toolCalls().get(0).result()).isNull();
    }

    @Test
    @DisplayName("a tool call with no matching response id gets a null result")
    void unmatchedId() {
        AssistantMessage message = message("done", List.of(call("c1", "search", "{}")));
        ToolResponseMessage results = responses(new ToolResponseMessage.ToolResponse("other", "search", "data"));

        AgentTrace trace = SpringAiSupport.toAgentTrace(message, List.of(results));

        assertThat(trace.toolCalls().get(0).result()).isNull();
    }

    @Test
    @DisplayName("numeric and nested argument values survive JSON parsing")
    void parsesNumbersAndNesting() {
        AssistantMessage message =
                message("ok", List.of(call("c1", "search", "{\"max\":5,\"filter\":{\"area\":\"EU\"}}")));

        ToolCall call = SpringAiSupport.toToolCalls(message).get(0);

        assertThat(call.arguments().get("max")).isEqualTo(5);
        assertThat(call.arguments().get("filter")).isEqualTo(Map.of("area", "EU"));
    }

    @Test
    @DisplayName("malformed and blank argument JSON yield empty arguments")
    void malformedArgs() {
        AssistantMessage message = message("ok", List.of(call("c1", "a", "{bad"), call("c2", "b", "")));

        List<ToolCall> calls = SpringAiSupport.toToolCalls(message);

        assertThat(calls.get(0).arguments()).isEmpty();
        assertThat(calls.get(1).arguments()).isEmpty();
    }

    @Test
    @DisplayName("a message with no tool calls yields a trace with the final response only")
    void noToolCalls() {
        AssistantMessage message = new AssistantMessage("Just an answer.");

        AgentTrace trace = SpringAiSupport.toAgentTrace(message);

        assertThat(trace.toolCalls()).isEmpty();
        assertThat(trace.finalResponse()).isEqualTo("Just an answer.");
    }

    @Test
    @DisplayName("a null message yields an empty trace without throwing")
    void nullMessage() {
        AgentTrace trace = SpringAiSupport.toAgentTrace((AssistantMessage) null);

        assertThat(trace.toolCalls()).isEmpty();
        assertThat(trace.finalResponse()).isNull();
    }

    @Test
    @DisplayName("more calls than responses leaves the unanswered call's result null")
    void countMismatch() {
        AssistantMessage message = message("done", List.of(call("c1", "a", "{}"), call("c2", "b", "{}")));
        ToolResponseMessage results = responses(new ToolResponseMessage.ToolResponse("c1", "a", "r1"));

        AgentTrace trace = SpringAiSupport.toAgentTrace(message, List.of(results));

        assertThat(trace.toolCalls().get(0).result()).isEqualTo("r1");
        assertThat(trace.toolCalls().get(1).result()).isNull();
    }

    @Test
    @DisplayName("tool definitions parse from the Spring inputSchema JSON string")
    void toolDefinitions() {
        var springDef = mock(org.springframework.ai.tool.definition.ToolDefinition.class);
        when(springDef.name()).thenReturn("search_flights");
        when(springDef.description()).thenReturn("Search for flights");
        when(springDef.inputSchema())
                .thenReturn(
                        "{\"type\":\"object\",\"properties\":{\"origin\":{\"type\":\"string\"}},\"required\":[\"origin\"]}");

        List<ToolDefinition> defs = SpringAiSupport.toToolDefinitions(List.of(springDef));

        assertThat(defs).hasSize(1);
        assertThat(defs.get(0).name()).isEqualTo("search_flights");
        assertThat(defs.get(0).parameterNames()).containsExactly("origin");
        assertThat(defs.get(0).requiredParameters()).containsExactly("origin");
    }

    @Test
    @DisplayName("a malformed inputSchema yields an empty schema without throwing")
    void malformedSchema() {
        var springDef = mock(org.springframework.ai.tool.definition.ToolDefinition.class);
        when(springDef.name()).thenReturn("t");
        when(springDef.description()).thenReturn("d");
        when(springDef.inputSchema()).thenReturn("{not json");

        List<ToolDefinition> defs = SpringAiSupport.toToolDefinitions(List.of(springDef));

        assertThat(defs.get(0).inputSchema()).isEmpty();
    }

    @Test
    @DisplayName("the produced trace satisfies the agent evaluators without throwing")
    void roundTripThroughEvaluators() {
        AssistantMessage message = message("done", List.of(call("c1", "search_flights", "{\"origin\":\"JFK\"}")));
        var testCase = EvalTestCase.builder()
                .input("Fly from JFK")
                .actualOutputs(SpringAiSupport.toAgentTrace(message).toOutputMap())
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
                        .build()
                        .evaluate(testCase)
                        .score())
                .isEqualTo(1.0);
    }
}
