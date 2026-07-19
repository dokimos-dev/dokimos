package dev.dokimos.openai;

import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionTool;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Utilities for integrating with the OpenAI Java SDK.
 *
 * <p>This class provides factory methods to create {@link JudgeLM}s and to capture an OpenAI
 * agent's tool calls as Dokimos {@link AgentTrace}, {@link ToolCall}, and {@link ToolDefinition}
 * objects the agent evaluators understand.
 *
 * <h2>Agent Evaluation</h2>
 * <pre>{@code
 * ChatCompletion completion = client.chat().completions().create(params);
 * ChatCompletionMessage message = completion.choices().get(0).message();
 *
 * AgentTrace trace = OpenAiSupport.toAgentTrace(message, id -> myApp.resultFor(id));
 * EvalTestCase testCase = trace.toTestCase(userMessage, OpenAiSupport.toToolDefinitions(tools));
 * }</pre>
 */
public final class OpenAiSupport {

    private OpenAiSupport() {}

    /**
     * Creates a {@link JudgeLM} from an OpenAI {@link OpenAIClient} and a specific
     * {@link ChatModel}.
     *
     * <p>Use this to create judges for LLM-based evaluators like {@code LLMJudgeEvaluator},
     * {@code TaskCompletionEvaluator}, etc.
     *
     * @param client the OpenAI client to use as judge
     * @param model  the chat model the judge uses
     * @return a JudgeLM that delegates to the client
     */
    public static JudgeLM asJudge(OpenAIClient client, ChatModel model) {
        return prompt -> {
            String content = client.chat()
                    .completions()
                    .create(ChatCompletionCreateParams.builder()
                            .addUserMessage(prompt)
                            .model(model)
                            .build())
                    .choices()
                    .get(0)
                    .message()
                    .content()
                    .orElse(null);
            if (content == null) {
                throw new IllegalStateException("Judge response content was null");
            }
            return content;
        };
    }

    /**
     * Builds an {@link AgentTrace} from an OpenAI {@link ChatCompletionMessage}.
     *
     * <p>The message's {@code content()} becomes the final response and its function
     * {@code toolCalls()} become {@link ToolCall}s carrying the tool name and parsed arguments.
     *
     * @param message the assistant message (may be null)
     * @return an agent trace, never null
     */
    public static AgentTrace toAgentTrace(ChatCompletionMessage message) {
        return toAgentTrace(message, id -> null);
    }

    /**
     * Builds an {@link AgentTrace} from an OpenAI {@link ChatCompletionMessage}, looking up each
     * tool call's result by its id.
     *
     * @param message      the assistant message (may be null)
     * @param resultLookup maps a tool call id to its execution result (may return null)
     * @return an agent trace, never null
     */
    public static AgentTrace toAgentTrace(ChatCompletionMessage message, Function<String, String> resultLookup) {
        AgentTrace.Builder builder = AgentTrace.builder().toolCalls(toToolCalls(message, resultLookup));
        if (message != null) {
            message.content().ifPresent(builder::finalResponse);
        }
        return builder.build();
    }

    /**
     * Extracts function {@link ToolCall}s from an OpenAI {@link ChatCompletionMessage} in order.
     *
     * @param message the assistant message (may be null)
     * @return the tool calls, or an empty list when there are none
     */
    public static List<ToolCall> toToolCalls(ChatCompletionMessage message) {
        return toToolCalls(message, id -> null);
    }

    /**
     * Extracts function {@link ToolCall}s from an OpenAI {@link ChatCompletionMessage}, looking up
     * each tool call's result by its id.
     *
     * @param message      the assistant message (may be null)
     * @param resultLookup maps a tool call id to its execution result (may return null)
     * @return the tool calls, or an empty list when there are none
     */
    public static List<ToolCall> toToolCalls(ChatCompletionMessage message, Function<String, String> resultLookup) {
        if (message == null) {
            return List.of();
        }
        return message.toolCalls().orElse(List.of()).stream()
                .filter(ChatCompletionMessageToolCall::isFunction)
                .map(call ->
                        toToolCall(call, resultLookup.apply(call.asFunction().id())))
                .toList();
    }

    /**
     * Converts a single OpenAI function {@link ChatCompletionMessageToolCall} to a {@link ToolCall}.
     *
     * @param toolCall the OpenAI tool call (must be a function tool call)
     * @param result   the result of executing the tool, or null if not executed
     * @return the tool call
     */
    public static ToolCall toToolCall(ChatCompletionMessageToolCall toolCall, String result) {
        ChatCompletionMessageFunctionToolCall.Function function =
                toolCall.asFunction().function();
        return ToolCall.builder()
                .name(function.name())
                .arguments(parseArguments(function))
                .result(result)
                .build();
    }

    /**
     * Converts OpenAI {@link ChatCompletionTool}s to {@link ToolDefinition}s so tool calls can be
     * evaluated against the tools the agent was given.
     *
     * @param tools the OpenAI tools (may be null)
     * @return the tool definitions, or an empty list
     */
    public static List<ToolDefinition> toToolDefinitions(List<ChatCompletionTool> tools) {
        if (tools == null) {
            return List.of();
        }
        return tools.stream()
                .filter(ChatCompletionTool::isFunction)
                .map(OpenAiSupport::toToolDefinition)
                .toList();
    }

    /**
     * Converts a single OpenAI function {@link ChatCompletionTool} to a {@link ToolDefinition}.
     *
     * @param tool the OpenAI tool (must be a function tool)
     * @return the tool definition
     */
    public static ToolDefinition toToolDefinition(ChatCompletionTool tool) {
        FunctionDefinition function = tool.asFunction().function();
        return ToolDefinition.builder()
                .name(function.name())
                .description(function.description().orElse(""))
                .inputSchema(
                        function.parameters().map(OpenAiSupport::toSchemaMap).orElse(Map.of()))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArguments(ChatCompletionMessageFunctionToolCall.Function function) {
        try {
            Map<String, Object> args = (Map<String, Object>) function.arguments(Map.class);
            return args != null ? args : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static Map<String, Object> toSchemaMap(FunctionParameters params) {
        Map<String, Object> map = new LinkedHashMap<>();
        params._additionalProperties().forEach((key, value) -> map.put(key, value.convert(Object.class)));
        return map;
    }
}
