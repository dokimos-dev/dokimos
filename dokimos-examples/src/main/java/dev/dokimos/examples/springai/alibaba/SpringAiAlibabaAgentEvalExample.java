package dev.dokimos.examples.springai.alibaba;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import dev.dokimos.core.evaluators.agents.ToolCorrectnessEvaluator;
import dev.dokimos.springai.alibaba.SpringAiAlibabaSupport;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;

/**
 * Runs a Spring AI Alibaba {@link ReactAgent} over a tool, captures the whole multi-turn run as a
 * Dokimos {@link AgentTrace}, and evaluates the agent's tool use.
 *
 * <p>Spring AI Alibaba's graph runtime carries its conversation as standard Spring AI message types
 * under the {@code "messages"} state key. {@link SpringAiAlibabaSupport#toAgentTrace(ReactAgent, Map,
 * com.alibaba.cloud.ai.graph.RunnableConfig)} is the full-fidelity one-liner: it invokes the agent's
 * compiled graph (which preserves every intermediate tool call) and folds the resulting state into a
 * single trace.
 *
 * <p>The adapter has no {@code asJudge}/{@code asyncTask}; Alibaba agents run on a standard Spring AI
 * {@code ChatModel}, so use {@code dev.dokimos.springai.SpringAiSupport} for those.
 *
 * <p>Requires {@code OPENAI_API_KEY} at runtime; it compiles without one. (Alibaba agents accept any
 * Spring AI {@code ChatModel}, so this example reuses {@code OpenAiChatModel}.)
 */
public class SpringAiAlibabaAgentEvalExample {

    /** The agent's tool surface: a single {@code getWeather} tool with a canned result. */
    static class WeatherTools {
        @Tool(name = "getWeather", description = "Get the current weather for a city")
        String getWeather(@ToolParam(description = "city name") String city) {
            return "{\"city\": \"" + city + "\", \"temperatureC\": 18, \"conditions\": \"cloudy\"}";
        }
    }

    public static void main(String[] args) throws Exception {
        if (System.getenv("OPENAI_API_KEY") == null) {
            System.err.println("OPENAI_API_KEY not set");
            System.exit(1);
        }

        OpenAiApi openAiApi =
                OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build();
        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model("gpt-4o-mini").build())
                .build();
        ChatClient chatClient = ChatClient.builder(chatModel).build();

        // Build the tool callback from the @Tool-annotated method.
        Method weatherMethod = WeatherTools.class.getDeclaredMethod("getWeather", String.class);
        ToolCallback weatherCallback = MethodToolCallback.builder()
                .toolDefinition(ToolDefinitions.from(weatherMethod))
                .toolMethod(weatherMethod)
                .toolObject(new WeatherTools())
                .build();
        List<ToolCallback> callbacks = List.of(weatherCallback);

        // Build the ReactAgent over the tool.
        ReactAgent agent = ReactAgent.builder()
                .name("weather-assistant")
                .chatClient(chatClient)
                .tools(callbacks)
                .maxIterations(5)
                .build();

        String userQuery = "What is the weather in Paris?";
        Map<String, Object> inputs = Map.of(SpringAiAlibabaSupport.MESSAGES_KEY, List.of(new UserMessage(userQuery)));

        // One call runs the agent's compiled graph and folds the whole run into a Dokimos trace.
        AgentTrace trace = SpringAiAlibabaSupport.toAgentTrace(agent, inputs, null);

        // Tool definitions come from the callbacks the agent was built with.
        List<ToolDefinition> tools = SpringAiAlibabaSupport.toToolDefinitions(callbacks);

        // toTestCase wires output, toolCalls, and the tools/tasks metadata the evaluators need.
        EvalTestCase testCase = trace.toTestCase(userQuery, tools, List.of(userQuery));

        EvalResult validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
        EvalResult correctness = ToolCorrectnessEvaluator.builder()
                .build()
                .evaluate(EvalTestCase.builder()
                        .actualOutputs(testCase.actualOutputs())
                        .metadata(testCase.metadata())
                        // The agent was expected to reach for getWeather.
                        .expectedOutput(
                                "toolCalls",
                                List.of(dev.dokimos.core.agents.ToolCall.of("getWeather", java.util.Map.of())))
                        .build());

        System.out.println("User query: " + userQuery);
        System.out.println("Tool calls: " + trace.toolNames());
        System.out.printf("Tool Call Validity: %.2f (%s)%n", validity.score(), validity.reason());
        System.out.printf("Tool Correctness:   %.2f (%s)%n", correctness.score(), correctness.reason());
        System.out.println("Final response: " + trace.finalResponse());
    }
}
