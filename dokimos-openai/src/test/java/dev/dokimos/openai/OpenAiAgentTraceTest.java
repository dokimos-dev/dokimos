package dev.dokimos.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.ChatModel;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.services.blocking.ChatService;
import com.openai.services.blocking.chat.ChatCompletionService;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.ArgMatchMode;
import dev.dokimos.core.evaluators.agents.ArgumentMatcher;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import dev.dokimos.core.evaluators.agents.ToolTrajectoryEvaluator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenAiAgentTraceTest {

    private static ChatCompletionMessageToolCall functionCall(String id, String name, Map<String, Object> arguments) {
        ChatCompletionMessageFunctionToolCall.Function function =
                mock(ChatCompletionMessageFunctionToolCall.Function.class);
        when(function.name()).thenReturn(name);
        when(function.arguments(Map.class)).thenReturn(arguments);

        ChatCompletionMessageFunctionToolCall functionCall = mock(ChatCompletionMessageFunctionToolCall.class);
        lenient().when(functionCall.id()).thenReturn(id);
        when(functionCall.function()).thenReturn(function);

        ChatCompletionMessageToolCall toolCall = mock(ChatCompletionMessageToolCall.class);
        when(toolCall.isFunction()).thenReturn(true);
        when(toolCall.asFunction()).thenReturn(functionCall);
        return toolCall;
    }

    private static ChatCompletionMessage message(String content, List<ChatCompletionMessageToolCall> toolCalls) {
        ChatCompletionMessage message = mock(ChatCompletionMessage.class);
        lenient().when(message.content()).thenReturn(Optional.ofNullable(content));
        lenient().when(message.toolCalls()).thenReturn(Optional.ofNullable(toolCalls));
        return message;
    }

    private static OpenAIClient clientReturning(String content) {
        ChatCompletionMessage message = mock(ChatCompletionMessage.class);
        when(message.content()).thenReturn(Optional.ofNullable(content));
        ChatCompletion.Choice choice = mock(ChatCompletion.Choice.class);
        when(choice.message()).thenReturn(message);
        ChatCompletion completion = mock(ChatCompletion.class);
        when(completion.choices()).thenReturn(List.of(choice));
        ChatCompletionService completions = mock(ChatCompletionService.class);
        when(completions.create(any(ChatCompletionCreateParams.class))).thenReturn(completion);
        ChatService chat = mock(ChatService.class);
        when(chat.completions()).thenReturn(completions);
        OpenAIClient client = mock(OpenAIClient.class);
        when(client.chat()).thenReturn(chat);
        return client;
    }

    @Test
    @DisplayName("maps content and tool calls into a trace")
    void mapsTrace() {
        ChatCompletionMessage message = message(
                "Done",
                List.of(
                        functionCall("c1", "search_flights", Map.of("origin", "JFK", "destination", "CDG")),
                        functionCall("c2", "book_hotel", Map.of("city", "Paris", "nights", 3))));

        AgentTrace trace = OpenAiSupport.toAgentTrace(message, id -> "result-" + id);

        assertThat(trace.finalResponse()).isEqualTo("Done");
        assertThat(trace.toolCalls()).hasSize(2);
        ToolCall first = trace.toolCalls().get(0);
        assertThat(first.name()).isEqualTo("search_flights");
        assertThat(first.arguments()).containsEntry("origin", "JFK").containsEntry("destination", "CDG");
        assertThat(first.result()).isEqualTo("result-c1");
        ToolCall second = trace.toolCalls().get(1);
        assertThat(second.name()).isEqualTo("book_hotel");
        assertThat(second.result()).isEqualTo("result-c2");
    }

    @Test
    @DisplayName("numeric and nested argument values survive")
    void parsesNumbersAndNesting() {
        ChatCompletionMessageToolCall call =
                functionCall("c1", "search", Map.of("nights", 3, "filter", Map.of("area", "EU")));

        ToolCall toolCall = OpenAiSupport.toToolCall(call, "ok");

        assertThat(toolCall.arguments().get("nights")).isEqualTo(3);
        assertThat(toolCall.arguments().get("filter")).isEqualTo(Map.of("area", "EU"));
    }

    @Test
    @DisplayName("malformed argument JSON yields empty arguments without throwing")
    void malformedArgs() {
        ChatCompletionMessageFunctionToolCall.Function function =
                mock(ChatCompletionMessageFunctionToolCall.Function.class);
        when(function.name()).thenReturn("search");
        when(function.arguments(Map.class)).thenThrow(new RuntimeException("bad json"));
        ChatCompletionMessageFunctionToolCall functionCall = mock(ChatCompletionMessageFunctionToolCall.class);
        when(functionCall.function()).thenReturn(function);
        ChatCompletionMessageToolCall call = mock(ChatCompletionMessageToolCall.class);
        when(call.asFunction()).thenReturn(functionCall);

        assertThatCode(() -> OpenAiSupport.toToolCall(call, "r")).doesNotThrowAnyException();
        ToolCall toolCall = OpenAiSupport.toToolCall(call, "r");
        assertThat(toolCall.name()).isEqualTo("search");
        assertThat(toolCall.arguments()).isEmpty();
        assertThat(toolCall.result()).isEqualTo("r");
    }

    @Test
    @DisplayName("blank and null arguments yield empty arguments")
    void nullArgs() {
        ChatCompletionMessageToolCall call = functionCall("c1", "search", null);

        ToolCall toolCall = OpenAiSupport.toToolCall(call, "r");

        assertThat(toolCall.arguments()).isEmpty();
    }

    @Test
    @DisplayName("a message with no tool calls yields a trace with the final response only")
    void noToolCalls() {
        ChatCompletionMessage message = message("answer", List.of());

        AgentTrace trace = OpenAiSupport.toAgentTrace(message);

        assertThat(trace.toolCalls()).isEmpty();
        assertThat(trace.finalResponse()).isEqualTo("answer");
    }

    @Test
    @DisplayName("a null message yields an empty trace without throwing")
    void nullMessage() {
        AgentTrace trace = OpenAiSupport.toAgentTrace(null);

        assertThat(trace.toolCalls()).isEmpty();
        assertThat(trace.finalResponse()).isNull();
        assertThat(OpenAiSupport.toToolCalls(null)).isEmpty();
    }

    @Test
    @DisplayName("custom (non-function) tool calls are filtered out")
    void filtersCustomToolCalls() {
        ChatCompletionMessageToolCall custom = mock(ChatCompletionMessageToolCall.class);
        when(custom.isFunction()).thenReturn(false);
        ChatCompletionMessageToolCall function = functionCall("c1", "search", Map.of("q", "x"));
        ChatCompletionMessage message = message("ok", List.of(custom, function));

        List<ToolCall> calls = OpenAiSupport.toToolCalls(message);

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).name()).isEqualTo("search");
        verify(custom, never()).asFunction();
    }

    @Test
    @DisplayName("tools convert to tool definitions with a nested schema")
    void toolDefinitions() {
        FunctionParameters parameters = mock(FunctionParameters.class);
        when(parameters._additionalProperties())
                .thenReturn(Map.of(
                        "type",
                        JsonValue.from("object"),
                        "properties",
                        JsonValue.from(Map.of("nights", Map.of("type", "integer"))),
                        "required",
                        JsonValue.from(List.of("nights"))));
        FunctionDefinition definition = mock(FunctionDefinition.class);
        when(definition.name()).thenReturn("book_hotel");
        when(definition.description()).thenReturn(Optional.of("Book a hotel"));
        when(definition.parameters()).thenReturn(Optional.of(parameters));
        ChatCompletionFunctionTool functionTool = mock(ChatCompletionFunctionTool.class);
        when(functionTool.function()).thenReturn(definition);
        ChatCompletionTool tool = mock(ChatCompletionTool.class);
        when(tool.isFunction()).thenReturn(true);
        when(tool.asFunction()).thenReturn(functionTool);

        List<ToolDefinition> defs = OpenAiSupport.toToolDefinitions(List.of(tool));

        assertThat(defs).hasSize(1);
        ToolDefinition def = defs.get(0);
        assertThat(def.name()).isEqualTo("book_hotel");
        assertThat(def.description()).isEqualTo("Book a hotel");
        assertThat(def.parameterNames()).contains("nights");
        assertThat(def.requiredParameters()).contains("nights");
        assertThat(def.parameterSchema("nights")).containsEntry("type", "integer");
    }

    @Test
    @DisplayName("null tool definitions yield an empty list")
    void nullToolDefinitions() {
        assertThat(OpenAiSupport.toToolDefinitions(null)).isEmpty();
    }

    @Test
    @DisplayName("asJudge delegates to the client and returns the response content")
    void asJudgeDelegates() {
        OpenAIClient client = clientReturning("Judge response");

        String response = OpenAiSupport.asJudge(client, ChatModel.GPT_5_NANO).generate("Test prompt");

        assertThat(response).isEqualTo("Judge response");
    }

    @Test
    @DisplayName("asJudge throws when the response has no content")
    void asJudgeThrowsWhenContentAbsent() {
        OpenAIClient client = clientReturning(null);

        assertThatThrownBy(() ->
                        OpenAiSupport.asJudge(client, ChatModel.GPT_5_NANO).generate("Test prompt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Judge response content was null");
    }

    @Test
    @DisplayName("the produced trace satisfies the agent evaluators without throwing")
    void roundTripThroughEvaluators() {
        ChatCompletionMessage message = message(
                "done", List.of(functionCall("c1", "search_flights", Map.of("origin", "JFK", "destination", "CDG"))));

        EvalTestCase testCase = OpenAiSupport.toAgentTrace(message).toTestCase("Fly JFK to CDG", List.of());
        testCase = EvalTestCase.builder()
                .inputs(testCase.inputs())
                .actualOutputs(testCase.actualOutputs())
                .expectedOutput("toolCalls", List.of(ToolCall.of("search_flights", Map.of())))
                .metadata(testCase.metadata())
                .build();

        EvalTestCase tc = testCase;
        assertThatCode(() -> ToolCallValidityEvaluator.builder().build().evaluate(tc))
                .doesNotThrowAnyException();
        assertThat(ToolTrajectoryEvaluator.builder()
                        .matchMode(ToolTrajectoryEvaluator.MatchMode.ANY_ORDER)
                        .argumentMatcher(ArgumentMatcher.of(ArgMatchMode.IGNORE))
                        .build()
                        .evaluate(tc)
                        .score())
                .isEqualTo(1.0);
    }
}
