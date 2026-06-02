package dev.dokimos.langchain4j.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.evaluators.agents.ArgMatchMode;
import dev.dokimos.core.evaluators.agents.ArgumentMatcher;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import dev.dokimos.core.evaluators.agents.ToolTrajectoryEvaluator;
import dev.dokimos.langchain4j.LangChain4jSupport;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Verifies that {@link LangChain4jSupport#toAgentTrace} captures real tool calls from a live
 * LangChain4j {@code AiService} tool-calling run. Requires {@code OPENAI_API_KEY}.
 */
@Tag("integration")
class LangChain4jToolTraceIT {

    /** A tool the model can call. The result is canned; the model's decision to call it is real. */
    static class WeatherTools {
        @Tool("Get the current weather for a city")
        String getWeather(@P("city name") String city) {
            return "{\"city\": \"" + city + "\", \"temperatureC\": 18, \"conditions\": \"cloudy\"}";
        }
    }

    interface WeatherAssistant {
        Result<String> chat(String userMessage);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void capturesToolCallsFromLiveAgent() {
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(OpenAiChatModelName.GPT_4_O_MINI)
                .build();

        WeatherAssistant assistant = AiServices.builder(WeatherAssistant.class)
                .chatModel(chatModel)
                .tools(new WeatherTools())
                .build();

        Result<String> result = assistant.chat("What is the weather in Paris right now?");

        AgentTrace trace = LangChain4jSupport.toAgentTrace(result);

        assertThat(trace.toolNames())
                .as("agent should have called the weather tool")
                .contains("getWeather");
        assertThat(trace.toolCalls()).allSatisfy(call -> {
            assertThat(call.name()).isNotBlank();
            assertThat(call.result()).isNotNull();
        });

        var testCase = EvalTestCase.builder()
                .input("What is the weather in Paris right now?")
                .actualOutput("toolCalls", trace.toolCalls())
                .expectedOutput(
                        "toolCalls", List.of(dev.dokimos.core.agents.ToolCall.of("getWeather", java.util.Map.of())))
                .build();

        var trajectory = ToolTrajectoryEvaluator.builder()
                .matchMode(ToolTrajectoryEvaluator.MatchMode.ANY_ORDER)
                .argumentMatcher(ArgumentMatcher.of(ArgMatchMode.IGNORE))
                .build()
                .evaluate(testCase);
        assertThat(trajectory.score()).isGreaterThanOrEqualTo(0.5);

        var validity = ToolCallValidityEvaluator.builder()
                .build()
                .evaluate(EvalTestCase.builder()
                        .actualOutput("toolCalls", trace.toolCalls())
                        .metadata("tools", LangChain4jSupport.toToolDefinitions(List.of()))
                        .build());
        // No tool specs supplied here, so validity treats unknown tools leniently; just assert it runs.
        assertThat(validity.score()).isBetween(0.0, 1.0);
    }
}
