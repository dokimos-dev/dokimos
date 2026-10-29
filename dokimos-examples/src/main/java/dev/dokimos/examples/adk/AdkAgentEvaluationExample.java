package dev.dokimos.examples.adk;

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
import dev.dokimos.adk.AdkSupport;
import dev.dokimos.adk.AdkTraceCollector;
import dev.dokimos.core.CallMetrics;
import dev.dokimos.core.Dataset;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.Example;
import dev.dokimos.core.Experiment;
import dev.dokimos.core.ExperimentResult;
import dev.dokimos.core.PriceTable;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import dev.dokimos.core.evaluators.agents.ToolCorrectnessEvaluator;
import dev.dokimos.core.evaluators.agents.ToolEfficiencyEvaluator;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runnable example demonstrating how to evaluate a Google ADK for Java {@link LlmAgent}
 * with Dokimos:
 * <ul>
 *   <li>Passive trace and tool-schema collection via {@link AdkTraceCollector}</li>
 *   <li>Tool-call evaluation with {@link ToolCallValidityEvaluator},
 *       {@link ToolCorrectnessEvaluator}, and {@link ToolEfficiencyEvaluator}</li>
 *   <li>Token, latency, and cost tracking via {@link AdkSupport#measuredAgentTask} and
 *       {@link PriceTable}</li>
 *   <li>RAG context extraction via {@link AdkSupport#ragTask}</li>
 * </ul>
 *
 * <p>Uses live {@code gemini-3.8-flash} when {@code GOOGLE_API_KEY} or {@code GEMINI_API_KEY}
 * is set in the environment, and falls back to an offline simulated {@link BaseLlm} otherwise
 * so the example can be run out of the box.
 */
public class AdkAgentEvaluationExample {

    /** Tool functions exposed to the ADK {@link LlmAgent}. */
    public static class TravelTools {
        @Schema(name = "get_weather", description = "Get the current weather for a city")
        public static Map<String, Object> getWeather(
                @Schema(name = "city", description = "City name, e.g. Paris") String city) {
            return Map.of("city", city, "temperatureC", 21, "conditions", "Sunny");
        }

        @Schema(name = "search_policy", description = "Search the company travel policy knowledge base")
        public static Map<String, Object> searchPolicy(
                @Schema(name = "query", description = "Search query") String query) {
            return Map.of(
                    "response",
                    List.of(
                            "Economy class is required for flights under 6 hours.",
                            "Hotel bookings are reimbursed up to $250 per night."));
        }
    }

    public static void main(String[] args) {
        FunctionTool weatherTool = FunctionTool.create(TravelTools.class, "getWeather");
        FunctionTool policyTool = FunctionTool.create(TravelTools.class, "searchPolicy");

        LlmAgent agent = buildAgent(weatherTool, policyTool);

        // ---------------------------------------------------------------------
        // Part 1: Passive trace collection with AdkTraceCollector plugin
        // ---------------------------------------------------------------------
        Runner.Builder runnerBuilder = Runner.builder().agent(agent).appName("travel-assistant");
        AdkTraceCollector collector = AdkSupport.attach(runnerBuilder);
        Runner runner = runnerBuilder.build();

        String userQuery = "What is the weather in Paris?";
        RunConfig runConfig = RunConfig.builder().autoCreateSession(true).build();
        runner.runAsync("user-1", "session-1", Content.fromParts(Part.fromText(userQuery)), runConfig)
                .toList()
                .blockingGet();

        AgentTrace trace = collector.trace();
        List<ToolDefinition> tools = AdkSupport.toToolDefinitions(collector);

        PriceTable prices = (model, in, out) -> (in * 0.00015 / 1000.0) + (out * 0.00060 / 1000.0);
        CallMetrics metrics = collector.callMetrics("gemini-3.8-flash", prices);

        EvalTestCase testCase = EvalTestCase.builder()
                .input(userQuery)
                .actualOutputs(trace.toOutputMap())
                .expectedOutput("toolCalls", List.of(ToolCall.of("get_weather", Map.of("city", "Paris"))))
                .metadata("tools", tools)
                .build();

        EvalResult validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
        EvalResult correctness = ToolCorrectnessEvaluator.builder().build().evaluate(testCase);
        EvalResult efficiency = ToolEfficiencyEvaluator.builder().build().evaluate(testCase);

        System.out.println("=== Part 1: Single-run ADK Trace Collector ===");
        System.out.println("User query:          " + userQuery);
        System.out.println("Observed tools:      " + trace.toolNames());
        System.out.println("Final response:      " + trace.finalResponse());
        System.out.printf("Tool Call Validity:  %.2f (%s)%n", validity.score(), validity.reason());
        System.out.printf("Tool Correctness:    %.2f (%s)%n", correctness.score(), correctness.reason());
        System.out.printf("Tool Efficiency:     %.2f (%s)%n", efficiency.score(), efficiency.reason());
        if (metrics != null) {
            System.out.printf(
                    "Call Metrics:        tokensIn=%s, tokensOut=%s, latencyMs=%s, costUsd=%s%n",
                    metrics.tokensIn(), metrics.tokensOut(), metrics.latencyMs(), metrics.costUsd());
        }

        // ---------------------------------------------------------------------
        // Part 2: Dataset Experiment with measuredAgentTask
        // ---------------------------------------------------------------------
        Dataset dataset = Dataset.builder()
                .name("adk-weather-eval")
                .addExample(Example.builder()
                        .input("input", "What is the weather in Paris?")
                        .expectedOutput("toolCalls", List.of(ToolCall.of("get_weather", Map.of("city", "Paris"))))
                        .metadata("tools", AdkSupport.toToolDefinitions(agent))
                        .build())
                .build();

        ExperimentResult expResult = Experiment.builder()
                .name("ADK Weather Agent Evaluation")
                .dataset(dataset)
                .measuredTask(AdkSupport.measuredAgentTask(agent, "gemini-3.8-flash", prices))
                .evaluators(List.of(
                        ToolCallValidityEvaluator.builder().build(),
                        ToolCorrectnessEvaluator.builder().build()))
                .build()
                .run();

        System.out.println("\n=== Part 2: Dokimos Experiment ===");
        System.out.printf("Pass rate:           %.0f%%%n", expResult.passRate() * 100);
    }

    private static LlmAgent buildAgent(FunctionTool weatherTool, FunctionTool policyTool) {
        String apiKey = System.getenv("GOOGLE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("GEMINI_API_KEY");
        }
        LlmAgent.Builder builder = LlmAgent.builder()
                .name("travel_assistant")
                .instruction("You are a travel assistant. Use get_weather for weather questions "
                        + "and search_policy for policy questions.")
                .tools(weatherTool, policyTool);
        if (apiKey != null && !apiKey.isBlank()) {
            return builder.model("gemini-3.8-flash").build();
        }
        return builder.model(simulatedLlm()).build();
    }

    private static BaseLlm simulatedLlm() {
        AtomicInteger callCount = new AtomicInteger(0);
        return new BaseLlm("gemini-3.8-flash-simulated") {
            @Override
            public Flowable<LlmResponse> generateContent(LlmRequest llmRequest, boolean stream) {
                int step = callCount.incrementAndGet();
                if (step % 2 == 1) {
                    Part callPart = Part.builder()
                            .functionCall(FunctionCall.builder()
                                    .id("call-" + step)
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
                                    .promptTokenCount(40)
                                    .candidatesTokenCount(12)
                                    .build())
                            .build());
                }
                return Flowable.just(LlmResponse.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.fromText("In Paris it is currently 21C and Sunny."))
                                .build())
                        .usageMetadata(GenerateContentResponseUsageMetadata.builder()
                                .promptTokenCount(55)
                                .candidatesTokenCount(18)
                                .build())
                        .build());
            }

            @Override
            public BaseLlmConnection connect(LlmRequest llmRequest) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
