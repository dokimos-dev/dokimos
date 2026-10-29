package dev.dokimos.adk.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.agents.LlmAgent;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Live integration test that runs a real Gemini {@link LlmAgent} with a {@link FunctionTool},
 * captures its trace via {@link AdkTraceCollector} and {@link AdkSupport#toAgentTrace}, and
 * scores tool calls with Dokimos agent evaluators.
 *
 * <p>Requires {@code GEMINI_API_KEY} in the environment.
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

        Runner.Builder runnerBuilder = Runner.builder().agent(agent).appName("dokimos-adk-it");
        AdkTraceCollector collector = AdkSupport.attach(runnerBuilder);
        Runner runner = runnerBuilder.build();

        String query = "What is the weather in Paris right now?";
        AgentTrace trace = AdkSupport.toAgentTrace(runner, query);

        assertThat(trace.toolNames())
                .as("Live Gemini agent should invoke get_weather")
                .contains("get_weather");
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
