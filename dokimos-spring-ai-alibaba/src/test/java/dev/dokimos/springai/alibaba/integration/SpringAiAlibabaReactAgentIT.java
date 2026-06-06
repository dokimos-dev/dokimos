package dev.dokimos.springai.alibaba.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import dev.dokimos.springai.alibaba.SpringAiAlibabaSupport;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
 * Drives a live Spring AI Alibaba {@link ReactAgent} run end to end and folds the resulting graph
 * {@code "messages"} state into a Dokimos {@link AgentTrace} with
 * {@link SpringAiAlibabaSupport#toAgentTrace(ReactAgent, Map, com.alibaba.cloud.ai.graph.RunnableConfig)}.
 * Requires {@code OPENAI_API_KEY}.
 *
 * <p>This proves the real compile-and-invoke path (the support class compiles the agent graph with
 * {@code getAndCompileGraph()} and folds the returned state). Tool-call extraction itself is covered
 * exhaustively by the unit tests, which build deterministic states; this test does not assert a
 * specific tool was called, since whether a given model calls a tool on a given turn is not
 * deterministic.
 */
@Tag("integration")
class SpringAiAlibabaReactAgentIT {

    /** A tool the model can call. The result is canned; the model's decision to call it is real. */
    static class WeatherTools {
        @Tool(description = "Get the current weather for a city")
        String getWeather(@ToolParam(description = "city name") String city) {
            return "{\"city\": \"" + city + "\", \"temperatureC\": 18, \"conditions\": \"cloudy\"}";
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void foldsLiveReactAgentRunIntoTrace() throws Exception {
        OpenAiApi openAiApi =
                OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build();
        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model("gpt-4o-mini").build())
                .build();
        ChatClient chatClient = ChatClient.builder(chatModel).build();

        Method weatherMethod = WeatherTools.class.getDeclaredMethod("getWeather", String.class);
        ToolCallback weatherCallback = MethodToolCallback.builder()
                .toolDefinition(ToolDefinitions.from(weatherMethod))
                .toolMethod(weatherMethod)
                .toolObject(new WeatherTools())
                .build();
        List<ToolCallback> callbacks = List.of(weatherCallback);

        ReactAgent agent = ReactAgent.builder()
                .name("weather-assistant")
                .chatClient(chatClient)
                .tools(callbacks)
                .build();

        String prompt = "Use the getWeather tool to look up the current weather in Paris, then tell me.";
        Map<String, Object> inputs = Map.of(SpringAiAlibabaSupport.MESSAGES_KEY, List.of(new UserMessage(prompt)));

        // The real path: compile the agent graph, invoke it against a live model, fold the state.
        AgentTrace trace = SpringAiAlibabaSupport.toAgentTrace(agent, inputs, null);

        // The live agent produced an answer and the trace folds cleanly. Tool-call extraction is
        // asserted in the unit tests; here we only require the live pipeline to round-trip.
        assertThat(trace).isNotNull();
        assertThat(trace.finalResponse()).isNotBlank();

        List<ToolDefinition> tools = SpringAiAlibabaSupport.toToolDefinitions(callbacks);
        EvalTestCase testCase = trace.toTestCase(prompt, tools);
        assertThatCode(() -> ToolCallValidityEvaluator.builder().build().evaluate(testCase))
                .doesNotThrowAnyException();
    }
}
