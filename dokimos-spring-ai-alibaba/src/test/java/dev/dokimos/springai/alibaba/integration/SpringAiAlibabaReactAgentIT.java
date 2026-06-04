package dev.dokimos.springai.alibaba.integration;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Verifies that {@link SpringAiAlibabaSupport#toAgentTrace} captures real tool calls from a live
 * Spring AI Alibaba {@link ReactAgent} run, folding the graph's {@code "messages"} state into a
 * Dokimos {@link AgentTrace}. Requires {@code OPENAI_API_KEY}.
 *
 * <p>At spring-ai-alibaba-graph-core {@code 1.0.0.2} only {@code ReactAgent}/{@code
 * ReactAgentWithHuman}/{@code ReflectAgent} exist; the {@code SequentialAgent}/{@code ParallelAgent}
 * types are unreleased (1.1.x), so the multi-turn fold is covered here through a {@code ReactAgent}.
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
    void capturesToolCallsFromLiveReactAgent() throws Exception {
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
                .maxIterations(5)
                .build();

        Map<String, Object> inputs =
                Map.of(SpringAiAlibabaSupport.MESSAGES_KEY, List.of(new UserMessage("What is the weather in Paris?")));

        AgentTrace trace = SpringAiAlibabaSupport.toAgentTrace(agent, inputs, null);

        assertThat(trace.toolNames()).contains("getWeather");

        List<ToolDefinition> tools = SpringAiAlibabaSupport.toToolDefinitions(callbacks);
        EvalTestCase testCase = trace.toTestCase("What is the weather in Paris?", tools);

        var result = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
        assertThat(result.success()).isTrue();
    }
}
