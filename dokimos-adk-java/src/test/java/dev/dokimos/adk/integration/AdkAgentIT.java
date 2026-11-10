package dev.dokimos.adk.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.agents.LlmAgent;
import com.google.adk.models.langchain4j.LangChain4j;
import com.google.adk.runner.Runner;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;
import dev.dokimos.adk.AdkSupport;
import dev.dokimos.adk.AdkTraceCollector;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import dev.dokimos.core.evaluators.agents.ToolCorrectnessEvaluator;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Live integration tests that run a real {@link LlmAgent} with a {@link FunctionTool}, capture its
 * trace via {@link AdkTraceCollector} and {@link AdkSupport#toAgentTrace}, and score tool calls
 * with Dokimos agent evaluators.
 *
 * <p>Supports two model backends so tests run in CI with {@code OPENAI_API_KEY} and locally (or in
 * CI) when {@code GEMINI_API_KEY} is provided:
 *
 * <ul>
 *   <li><b>Gemini</b> ({@code GEMINI_API_KEY}): native ADK {@code Gemini} model
 *       ({@code gemini-3.8-flash}).
 *   <li><b>OpenAI</b> ({@code OPENAI_API_KEY}): {@code gpt-4o-mini} wired through ADK Java's
 *       {@code com.google.adk:google-adk-langchain4j} integration ({@link LangChain4j} wrapping a
 *       LangChain4j {@link OpenAiChatModel}).
 * </ul>
 */
@Tag("integration")
class AdkAgentIT {

    public static class WeatherTools {
        @Schema(name = "get_weather", description = "Get the current weather for a given city")
        public static Map<String, Object> getWeather(
                @Schema(name = "city", description = "The city name, e.g. Paris") String city) {
            return Map.of("city", city, "temperatureC", 21, "conditions", "Sunny");
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
    void capturesToolCallsAndMetricsFromLiveGeminiAgent() {
        FunctionTool weatherTool = FunctionTool.create(WeatherTools.class, "getWeather");
        LlmAgent agent = LlmAgent.builder()
                .name("weather_agent")
                .model("gemini-3.8-flash")
                .instruction("You are a helpful weather assistant. Always call get_weather when asked about weather.")
                .tools(weatherTool)
                .build();

        assertAgentTraceAndEvaluators(agent, "Live Gemini agent should invoke get_weather");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void capturesToolCallsAndMetricsFromLiveOpenAiAgentViaLangChain4j() {
        FunctionTool weatherTool = FunctionTool.create(WeatherTools.class, "getWeather");
        OpenAiChatModel openAiModel = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(OpenAiChatModelName.GPT_4_O_MINI)
                .build();
        LangChain4j langChain4jModel = LangChain4j.builder()
                .chatModel(openAiModel)
                .modelName(OpenAiChatModelName.GPT_4_O_MINI.toString())
                .build();

        LlmAgent agent = LlmAgent.builder()
                .name("weather_agent")
                .model(langChain4jModel)
                .instruction("You are a helpful weather assistant. Always call get_weather when asked about weather.")
                .tools(weatherTool)
                .build();

        assertAgentTraceAndEvaluators(
                agent, "Live OpenAI agent (via google-adk-langchain4j) should invoke get_weather");
    }

    private static void assertAgentTraceAndEvaluators(LlmAgent agent, String description) {
        Runner.Builder runnerBuilder = Runner.builder().agent(agent).appName("dokimos-adk-it");
        AdkTraceCollector collector = AdkSupport.attach(runnerBuilder);
        Runner runner = runnerBuilder.build();

        String query = "What is the weather in Paris right now?";
        AgentTrace trace = AdkSupport.toAgentTrace(runner, query);

        assertThat(trace.toolNames()).as(description).contains("get_weather");
        assertThat(trace.finalResponse()).isNotBlank();

        // Verify collector also captured full tool definitions and metrics
        List<ToolDefinition> tools = AdkSupport.toToolDefinitions(collector);
        assertThat(tools).extracting(ToolDefinition::name).contains("get_weather");
        assertThat(collector.callMetrics()).isNotNull();

        EvalTestCase testCase = trace.toTestCase(query, tools);
        EvalResult validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
        assertThat(validity.success()).isTrue();

        EvalResult correctness = ToolCorrectnessEvaluator.builder()
                .build()
                .evaluate(EvalTestCase.builder()
                        .actualOutputs(testCase.actualOutputs())
                        .metadata(testCase.metadata())
                        .expectedOutput("toolCalls", List.of(ToolCall.of("get_weather", Map.of())))
                        .build());
        assertThat(correctness.score()).isEqualTo(1.0);
    }
}
