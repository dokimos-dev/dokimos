package dev.dokimos.adk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.runner.Runner;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import dev.dokimos.core.CallMetrics;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.PriceTable;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.conversation.ConversationTrajectory;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import dev.dokimos.core.evaluators.agents.ToolCorrectnessEvaluator;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AdkTraceCollectorTest {

    public static class WeatherToolSet {
        @Schema(name = "get_weather", description = "Get the current weather for a city")
        public static Map<String, Object> getWeather(
                @Schema(name = "city", description = "The city name") String city) {
            return Map.of("city", city, "tempC", 22, "condition", "Sunny");
        }
    }

    private static BaseLlm toolCallingFakeLlm() {
        AtomicInteger step = new AtomicInteger(0);
        return new BaseLlm("gemini-3.8-flash") {
            @Override
            public Flowable<LlmResponse> generateContent(LlmRequest llmRequest, boolean stream) {
                int current = step.incrementAndGet();
                if (current == 1) {
                    Part callPart = Part.builder()
                            .functionCall(FunctionCall.builder()
                                    .id("call-weather-1")
                                    .name("get_weather")
                                    .args(Map.of("city", "Paris"))
                                    .build())
                            .build();
                    return Flowable.just(LlmResponse.builder()
                            .content(Content.builder()
                                    .role("model")
                                    .parts(callPart)
                                    .build())
                            .usageMetadata(GenerateContentResponseUsageMetadata.builder()
                                    .promptTokenCount(30)
                                    .candidatesTokenCount(10)
                                    .build())
                            .build());
                }
                return Flowable.just(LlmResponse.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.fromText("The weather in Paris is 22C and sunny."))
                                .build())
                        .usageMetadata(GenerateContentResponseUsageMetadata.builder()
                                .promptTokenCount(45)
                                .candidatesTokenCount(15)
                                .build())
                        .build());
            }

            @Override
            public BaseLlmConnection connect(LlmRequest llmRequest) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void freshCollectorReturnsEmptyTraceAndNullMetrics() {
        AdkTraceCollector collector = new AdkTraceCollector();

        assertThat(collector.getName()).isEqualTo(AdkTraceCollector.DEFAULT_PLUGIN_NAME);
        assertThat(collector.events()).isEmpty();
        assertThat(collector.toolCalls()).isEmpty();
        assertThat(collector.observedToolNames()).isEmpty();
        assertThat(collector.toolDefinitions()).isEmpty();
        assertThat(collector.trace().toolCalls()).isEmpty();
        assertThat(collector.trace().finalResponse()).isEmpty();
        assertThat(collector.callMetrics()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void attachCapturesToolCallsFullToolSchemasTrajectoryAndMetricsFromRunner() {
        FunctionTool weatherTool = FunctionTool.create(WeatherToolSet.class, "getWeather");
        LlmAgent agent = LlmAgent.builder()
                .name("weather_assistant")
                .model(toolCallingFakeLlm())
                .instruction("Use get_weather to answer weather questions.")
                .tools(weatherTool)
                .build();

        Runner.Builder runnerBuilder = Runner.builder().agent(agent).appName("weather-app");
        AdkTraceCollector collector = AdkSupport.attach(runnerBuilder);
        Runner runner = runnerBuilder.build();

        RunConfig runConfig = RunConfig.builder().autoCreateSession(true).build();
        runner.runAsync(
                        "user-1",
                        "session-1",
                        Content.fromParts(Part.fromText("What is the weather in Paris?")),
                        runConfig)
                .toList()
                .blockingGet();

        // 1. AgentTrace & ToolCalls
        AgentTrace trace = collector.trace();
        assertThat(trace.finalResponse()).isEqualTo("The weather in Paris is 22C and sunny.");
        assertThat(trace.toolCalls()).hasSize(1);
        ToolCall call = trace.toolCalls().get(0);
        assertThat(call.name()).isEqualTo("get_weather");
        assertThat(call.arguments()).containsEntry("city", "Paris");
        assertThat((Map<String, Object>) call.resultAs(Map.class))
                .containsEntry("city", "Paris")
                .containsEntry("tempC", 22);

        // 2. Full ToolDefinitions captured from afterToolCallback(BaseTool, ...)
        List<ToolDefinition> tools = AdkSupport.toToolDefinitions(collector);
        assertThat(tools).hasSize(1);
        ToolDefinition def = tools.get(0);
        assertThat(def.name()).isEqualTo("get_weather");
        assertThat(def.description()).isEqualTo("Get the current weather for a city");
        assertThat(def.parameterNames()).containsExactly("city");

        // 3. Deterministic agent evaluators pass against the captured trace and definitions
        EvalTestCase testCase = trace.toTestCase("What is the weather in Paris?", tools);
        EvalResult validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
        assertThat(validity.success()).isTrue();
        assertThat(validity.score()).isEqualTo(1.0);

        EvalResult correctness = ToolCorrectnessEvaluator.builder()
                .build()
                .evaluate(EvalTestCase.builder()
                        .actualOutputs(testCase.actualOutputs())
                        .metadata(testCase.metadata())
                        .expectedOutput("toolCalls", List.of(ToolCall.of("get_weather", Map.of("city", "Paris"))))
                        .build());
        assertThat(correctness.success()).isTrue();

        // 4. Multi-turn ConversationTrajectory includes the user message and assistant turn
        ConversationTrajectory trajectory = collector.trajectory("Weather lookup");
        assertThat(trajectory.scenario()).isEqualTo("Weather lookup");
        assertThat(trajectory.turnCount()).isEqualTo(1);
        assertThat(trajectory.userMessages().get(0).content()).isEqualTo("What is the weather in Paris?");
        assertThat(trajectory.assistantMessages().get(0).content()).isEqualTo("The weather in Paris is 22C and sunny.");
        assertThat(trajectory.assistantMessages().get(0).toolCalls()).hasSize(1);

        // 5. CallMetrics aggregates tokens across both model turns and computes cost (including PriceTable-only
        // overload)
        PriceTable prices = (model, in, out) -> (in * 0.001) + (out * 0.002);
        CallMetrics metrics = collector.callMetrics(prices);
        assertThat(metrics).isNotNull();
        assertThat(metrics.tokensIn()).isEqualTo(75); // 30 + 45
        assertThat(metrics.tokensOut()).isEqualTo(25); // 10 + 15
        assertThat(metrics.costUsd()).isCloseTo(0.125, within(1e-6));
        assertThat(metrics.latencyMs()).isNotNull().isGreaterThanOrEqualTo(0L);

        // 6. reset() clears all state
        collector.reset();
        assertThat(collector.events()).isEmpty();
        assertThat(collector.toolCalls()).isEmpty();
        assertThat(collector.toolDefinitions()).isEmpty();
        assertThat(collector.callMetrics()).isNull();
    }

    @Test
    void handlesNullToolArgumentValuesAndToolErrorsForToolErrorEvaluator() {
        AdkTraceCollector collector = new AdkTraceCollector();
        FunctionTool weatherTool = FunctionTool.create(WeatherToolSet.class, "getWeather");

        java.util.Map<String, Object> argsWithNull = new java.util.LinkedHashMap<>();
        argsWithNull.put("city", null);

        // Should not throw NPE when toolArgs contains a null value
        collector
                .onToolErrorCallback(weatherTool, argsWithNull, null, new IllegalStateException("Weather API timeout"))
                .blockingGet();

        assertThat(collector.observedToolNames()).containsExactly("get_weather");
        List<ToolCall> calls = collector.toolCalls();
        assertThat(calls).hasSize(1);
        ToolCall errorCall = calls.get(0);
        assertThat(errorCall.name()).isEqualTo("get_weather");
        assertThat(errorCall.arguments()).containsKey("city");
        assertThat(errorCall.arguments().get("city")).isNull();
        assertThat(errorCall.metadata()).containsEntry("error", true);

        EvalTestCase errorCase = EvalTestCase.builder()
                .input("What is the weather?")
                .actualOutput("toolCalls", calls)
                .build();
        EvalResult errorEval = dev.dokimos.core.evaluators.agents.ToolErrorEvaluator.builder()
                .build()
                .evaluate(errorCase);
        assertThat(errorEval.success()).isFalse();
        assertThat(errorEval.score()).isEqualTo(0.0);
    }
}
