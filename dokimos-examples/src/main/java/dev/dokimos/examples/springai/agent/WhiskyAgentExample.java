package dev.dokimos.examples.springai.agent;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import dev.dokimos.core.evaluators.agents.ToolCorrectnessEvaluator;
import dev.dokimos.springai.SpringAiSupport;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;

/**
 * Runs a Spring AI agent over a whisky-catalog tool, captures the trace, and evaluates the agent's
 * tool use with Dokimos.
 *
 * <p>Demonstrates two things:
 *
 * <ol>
 *   <li>The {@link AssistantMessage} plus the {@link ToolResponseMessage}s become a Dokimos
 *       {@link AgentTrace} in a single {@link SpringAiSupport#toAgentTrace(AssistantMessage, List)}
 *       call.
 *   <li>Tool results stay structured ({@code List<Whisky>}) rather than escaped JSON strings — see
 *       {@link WhiskyAgentEvaluationTest} for the structured-output comparison.
 * </ol>
 *
 * <p>Requires {@code OPENAI_API_KEY} at runtime; it compiles without one.
 */
public class WhiskyAgentExample {

    public static void main(String[] args) {
        if (System.getenv("OPENAI_API_KEY") == null) {
            System.err.println("OPENAI_API_KEY not set");
            System.exit(1);
        }

        OpenAiApi openAiApi =
                OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build();
        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model("gpt-5-nano").build())
                .build();

        // Build the tool callback from the @Tool-annotated method.
        WhiskyTools tools = new WhiskyTools(new WhiskyCatalog());
        Method searchMethod = findToolMethod(WhiskyTools.class, "searchWhiskies");
        ToolCallback searchCallback = MethodToolCallback.builder()
                .toolDefinition(ToolDefinitions.from(searchMethod))
                .toolMethod(searchMethod)
                .toolObject(tools)
                .build();

        // Internal tool execution OFF so both the assistant message (with its tool calls) and the
        // tool responses run here are available.
        ChatClient client = ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder()
                        .internalToolExecutionEnabled(false)
                        .toolCallbacks(searchCallback)
                        .build())
                .build();

        String userQuery = "Find me a peaty Islay whisky around 12 years old";
        AssistantMessage assistantMessage = client.prompt()
                .user(userQuery)
                .call()
                .chatResponse()
                .getResult()
                .getOutput();

        // Execute each tool call to collect its result.
        List<ToolResponseMessage> toolResponses = runToolCalls(assistantMessage, searchCallback);

        // One call turns the Spring AI messages into a Dokimos trace.
        AgentTrace trace = SpringAiSupport.toAgentTrace(assistantMessage, toolResponses);

        // Tool definitions come from the Spring AI tool definition.
        List<ToolDefinition> toolDefs = SpringAiSupport.toToolDefinitions(List.of(searchCallback.getToolDefinition()));

        // toTestCase wires output, toolCalls, and the tools/tasks metadata the evaluators need.
        EvalTestCase testCase = trace.toTestCase(userQuery, toolDefs, List.of(userQuery));

        EvalResult validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
        EvalResult correctness = ToolCorrectnessEvaluator.builder()
                .build()
                .evaluate(EvalTestCase.builder()
                        .actualOutputs(testCase.actualOutputs())
                        .metadata(testCase.metadata())
                        // The agent was expected to reach for searchWhiskies.
                        .expectedOutput(
                                "toolCalls",
                                List.of(dev.dokimos.core.agents.ToolCall.of("searchWhiskies", java.util.Map.of())))
                        .build());

        System.out.println("User query: " + userQuery);
        System.out.println("Tool calls: " + trace.toolNames());
        System.out.printf("Tool Call Validity: %.2f (%s)%n", validity.score(), validity.reason());
        System.out.printf("Tool Correctness:   %.2f (%s)%n", correctness.score(), correctness.reason());
        System.out.println("Final response: " + trace.finalResponse());
    }

    private static List<ToolResponseMessage> runToolCalls(AssistantMessage message, ToolCallback callback) {
        List<ToolResponseMessage> responses = new ArrayList<>();
        if (message == null || message.getToolCalls() == null) {
            return responses;
        }
        for (AssistantMessage.ToolCall call : message.getToolCalls()) {
            String result = callback.call(call.arguments());
            responses.add(ToolResponseMessage.builder()
                    .responses(List.of(new ToolResponseMessage.ToolResponse(call.id(), call.name(), result)))
                    .build());
        }
        return responses;
    }

    private static Method findToolMethod(Class<?> type, String name) {
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalStateException("No method named " + name + " on " + type);
    }
}
