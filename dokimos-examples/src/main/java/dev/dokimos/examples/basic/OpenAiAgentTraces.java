package dev.dokimos.examples.basic;

import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Bridges OpenAI Java SDK tool calls into Dokimos {@link AgentTrace} and {@link ToolCall} objects.
 *
 * <p>This is the glue an application writes once to evaluate an OpenAI agent: capture the model's
 * tool calls as the conversation runs, then turn them into a trace the agent evaluators understand.
 * It lives in the examples module rather than a published artifact because it depends on the OpenAI
 * SDK, which callers bring themselves.
 *
 * <p>Typical use inside a tool-calling loop:
 *
 * <pre>{@code
 * AgentTrace.Builder trace = AgentTrace.builder();
 * for (var toolCall : message.toolCalls().orElse(List.of())) {
 *     String result = myApp.execute(toolCall);          // run the tool
 *     trace.addToolCall(OpenAiAgentTraces.toToolCall(toolCall, result));
 * }
 * trace.finalResponse(finalMessage.content().orElse(""));
 * }</pre>
 */
public final class OpenAiAgentTraces {

    private OpenAiAgentTraces() {}

    /**
     * Converts a single OpenAI function tool call to a Dokimos {@link ToolCall}.
     *
     * <p>The function name and arguments are read from the SDK's function tool call. Arguments are
     * deserialized from the model's JSON into a {@code Map}; if they cannot be parsed they default
     * to an empty map rather than failing the trace.
     *
     * @param toolCall the OpenAI tool call (must be a function tool call)
     * @param result   the result of executing the tool, or null if not executed
     * @return a Dokimos tool call
     */
    @SuppressWarnings("unchecked")
    public static ToolCall toToolCall(ChatCompletionMessageToolCall toolCall, String result) {
        var function = toolCall.asFunction().function();
        Map<String, Object> arguments;
        try {
            arguments = (Map<String, Object>) function.arguments(Map.class);
        } catch (Exception e) {
            arguments = Map.of();
        }
        return ToolCall.builder()
                .name(function.name())
                .arguments(arguments != null ? arguments : Map.of())
                .result(result)
                .build();
    }

    /**
     * Converts the function tool calls on an assistant message to Dokimos {@link ToolCall}s.
     *
     * <p>The {@code resultLookup} is asked for the result of each tool call by its id; return null
     * for calls whose result is not (yet) known.
     *
     * @param message      the assistant message that may contain tool calls
     * @param resultLookup maps a tool call id to its execution result (may return null)
     * @return the tool calls in order, or an empty list if the message has none
     */
    public static List<ToolCall> toToolCalls(ChatCompletionMessage message, Function<String, String> resultLookup) {
        return message.toolCalls().orElse(List.of()).stream()
                .map(call ->
                        toToolCall(call, resultLookup.apply(call.asFunction().id())))
                .toList();
    }
}
