package dev.dokimos.adk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.Session;
import com.google.adk.tools.Annotations;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.Example;
import dev.dokimos.core.Task;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import dev.dokimos.core.evaluators.agents.ToolDescriptionReliabilityEvaluator;
import dev.dokimos.core.evaluators.agents.ToolErrorEvaluator;
import dev.dokimos.core.evaluators.agents.ToolNameReliabilityEvaluator;
import dev.dokimos.core.evaluators.agents.ToolTrajectoryEvaluator;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AdkAgentTraceTest {

    public static class WeatherTools {
        @Annotations.Schema(
                name = "get_weather",
                description = "Gets the current weather forecast for a given city and temperature unit.")
        public static Map<String, Object> getWeather(
                @Annotations.Schema(name = "city", description = "The city name, e.g. Paris") String city,
                @Annotations.Schema(name = "unit", description = "The temperature unit: celsius or fahrenheit")
                        String unit) {
            return Map.of("city", city, "temp", 22, "unit", unit, "condition", "sunny");
        }
    }

    @Test
    void extractsToolCallsAndFinalResponseFromEvents() {
        Event userEvent = Event.builder()
                .id("e1")
                .author("user")
                .content(Content.fromParts(Part.fromText("What's the weather in Paris?")))
                .build();

        FunctionCall fc = FunctionCall.builder()
                .id("call-1")
                .name("get_weather")
                .args(Map.of("city", "Paris", "unit", "celsius"))
                .build();
        Event toolCallEvent = Event.builder()
                .id("e2")
                .author("weather_agent")
                .content(Content.builder()
                        .role("model")
                        .parts(Part.builder().functionCall(fc).build())
                        .build())
                .build();

        FunctionResponse fr = FunctionResponse.builder()
                .id("call-1")
                .name("get_weather")
                .response(Map.of("temp", 22, "condition", "sunny"))
                .build();
        Event toolResponseEvent = Event.builder()
                .id("e3")
                .author("weather_agent")
                .content(Content.builder()
                        .role("user")
                        .parts(Part.builder().functionResponse(fr).build())
                        .build())
                .build();

        Part thoughtPart = Part.builder()
                .text("The user wants the weather in Paris; I have the tool result now.")
                .thought(true)
                .build();
        Part answerPart = Part.fromText("It is 22°C and sunny in Paris.");
        Event finalEvent = Event.builder()
                .id("e4")
                .author("weather_agent")
                .content(Content.builder()
                        .role("model")
                        .parts(thoughtPart, answerPart)
                        .build())
                .build();

        AgentTrace trace = AdkSupport.toAgentTrace(List.of(userEvent, toolCallEvent, toolResponseEvent, finalEvent));

        assertThat(trace.finalResponse()).isEqualTo("It is 22°C and sunny in Paris.");
        assertThat(trace.reasoningSteps())
                .containsExactly("The user wants the weather in Paris; I have the tool result now.");
        assertThat(trace.toolCalls()).hasSize(1);

        ToolCall call = trace.toolCalls().get(0);
        assertThat(call.name()).isEqualTo("get_weather");
        assertThat(call.arguments()).containsEntry("city", "Paris").containsEntry("unit", "celsius");
        assertThat(call.resultAs(Map.class)).containsEntry("temp", 22).containsEntry("condition", "sunny");
    }

    @Test
    void matchesRepeatedCallIdsWithinOwnTurnWindow() {
        // Turn 1 uses id "call_1"
        Event call1 = Event.builder()
                .id("e1")
                .author("agent")
                .content(Content.fromParts(Part.builder()
                        .functionCall(FunctionCall.builder()
                                .id("call_1")
                                .name("search")
                                .args(Map.of("q", "first"))
                                .build())
                        .build()))
                .build();
        Event resp1 = Event.builder()
                .id("e2")
                .author("agent")
                .content(Content.fromParts(Part.builder()
                        .functionResponse(FunctionResponse.builder()
                                .id("call_1")
                                .name("search")
                                .response(Map.of("result", "first-result"))
                                .build())
                        .build()))
                .build();

        // Turn 2 also reuses id "call_1"
        Event call2 = Event.builder()
                .id("e3")
                .author("agent")
                .content(Content.fromParts(Part.builder()
                        .functionCall(FunctionCall.builder()
                                .id("call_1")
                                .name("search")
                                .args(Map.of("q", "second"))
                                .build())
                        .build()))
                .build();
        Event resp2 = Event.builder()
                .id("e4")
                .author("agent")
                .content(Content.fromParts(Part.builder()
                        .functionResponse(FunctionResponse.builder()
                                .id("call_1")
                                .name("search")
                                .response(Map.of("result", "second-result"))
                                .build())
                        .build()))
                .build();

        List<ToolCall> calls = AdkSupport.toToolCalls(List.of(call1, resp1, call2, resp2));

        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).arguments()).containsEntry("q", "first");
        assertThat(calls.get(0).resultAs(Map.class)).containsEntry("result", "first-result");
        assertThat(calls.get(1).arguments()).containsEntry("q", "second");
        assertThat(calls.get(1).resultAs(Map.class)).containsEntry("result", "second-result");
    }

    @Test
    void fallsBackToMatchingByNameWhenCallIdsAreAbsent() {
        Event callEvent = Event.builder()
                .id("e1")
                .author("agent")
                .content(Content.fromParts(
                        Part.fromFunctionCall("lookup_user", Map.of("id", 1)),
                        Part.fromFunctionCall("lookup_user", Map.of("id", 2))))
                .build();

        Event respEvent = Event.builder()
                .id("e2")
                .author("agent")
                .content(Content.fromParts(
                        Part.fromFunctionResponse("lookup_user", Map.of("name", "Alice")),
                        Part.fromFunctionResponse("lookup_user", Map.of("name", "Bob"))))
                .build();

        List<ToolCall> calls = AdkSupport.toToolCalls(List.of(callEvent, respEvent));

        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).arguments()).containsEntry("id", 1);
        assertThat(calls.get(0).resultAs(Map.class)).containsEntry("name", "Alice");
        assertThat(calls.get(1).arguments()).containsEntry("id", 2);
        assertThat(calls.get(1).resultAs(Map.class)).containsEntry("name", "Bob");
    }

    @Test
    void ignoresPartialStreamingEventsWhenNonPartialEventsExist() {
        Event partialChunk = Event.builder()
                .id("e1")
                .author("agent")
                .partial(true)
                .content(Content.fromParts(Part.fromText("Hel")))
                .build();
        Event fullEvent = Event.builder()
                .id("e2")
                .author("agent")
                .partial(false)
                .content(Content.fromParts(Part.fromText("Hello world!")))
                .build();

        AgentTrace trace = AdkSupport.toAgentTrace(List.of(partialChunk, fullEvent));
        assertThat(trace.finalResponse()).isEqualTo("Hello world!");
    }

    @Test
    void extractsTraceFromSession() {
        Event event = Event.builder()
                .id("e1")
                .author("assistant")
                .content(Content.fromParts(Part.fromText("Session reply")))
                .build();
        Session session = Session.builder("s1")
                .appName("app")
                .userId("u1")
                .events(List.of(event))
                .build();

        AgentTrace trace = AdkSupport.toAgentTrace(session);
        assertThat(trace.finalResponse()).isEqualTo("Session reply");
    }

    @Test
    void runsRealLlmAgentEndToEndWithFunctionTool() {
        FunctionTool weatherTool = FunctionTool.create(WeatherTools.class, "getWeather");

        AtomicInteger step = new AtomicInteger(0);
        BaseLlm fakeLlm = new BaseLlm("fake-gemini") {
            @Override
            public Flowable<LlmResponse> generateContent(LlmRequest llmRequest, boolean stream) {
                if (step.getAndIncrement() == 0) {
                    FunctionCall call = FunctionCall.builder()
                            .id("call-weather-1")
                            .name("get_weather")
                            .args(Map.of("city", "Paris", "unit", "celsius"))
                            .build();
                    return Flowable.just(LlmResponse.builder()
                            .content(Content.builder()
                                    .role("model")
                                    .parts(Part.builder().functionCall(call).build())
                                    .build())
                            .build());
                }
                return Flowable.just(LlmResponse.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.fromText("Paris is 22°C and sunny."))
                                .build())
                        .build());
            }

            @Override
            public BaseLlmConnection connect(LlmRequest llmRequest) {
                throw new UnsupportedOperationException();
            }
        };

        LlmAgent agent = LlmAgent.builder()
                .name("weather_assistant")
                .description("Answers weather questions")
                .model(fakeLlm)
                .instruction("Use the get_weather tool to check the weather.")
                .tools(weatherTool)
                .build();

        Task task = AdkSupport.agentTask(agent);
        Map<String, Object> outputs = task.run(Example.of("What is the weather in Paris?", "Paris is 22°C and sunny."));

        assertThat(outputs).containsEntry("output", "Paris is 22°C and sunny.");
        @SuppressWarnings("unchecked")
        List<ToolCall> toolCalls = (List<ToolCall>) outputs.get("toolCalls");
        assertThat(toolCalls).hasSize(1);
        assertThat(toolCalls.get(0).name()).isEqualTo("get_weather");
        assertThat(toolCalls.get(0).arguments()).containsEntry("city", "Paris").containsEntry("unit", "celsius");
        assertThat(toolCalls.get(0).resultAs(Map.class)).containsEntry("temp", 22);

        // Verify ToolDefinition extraction from LlmAgent and evaluation with Dokimos evaluators
        List<ToolDefinition> toolDefs = AdkSupport.toToolDefinitions(agent);
        assertThat(toolDefs).hasSize(1);
        ToolDefinition def = toolDefs.get(0);
        assertThat(def.name()).isEqualTo("get_weather");
        assertThat(def.description())
                .isEqualTo("Gets the current weather forecast for a given city and temperature unit.");
        assertThat(def.parameterNames()).containsExactlyInAnyOrder("city", "unit");
        assertThat(def.parameterSchema("city")).containsEntry("type", "string");

        EvalTestCase testCase = EvalTestCase.builder()
                .input("What is the weather in Paris?")
                .actualOutputs(outputs)
                .expectedOutput(
                        "toolCalls", List.of(ToolCall.of("get_weather", Map.of("city", "Paris", "unit", "celsius"))))
                .metadata("tools", toolDefs)
                .build();

        EvalResult validityResult =
                ToolCallValidityEvaluator.builder().strictMode(true).build().evaluate(testCase);
        assertThat(validityResult.success()).isTrue();
        assertThat(validityResult.score()).isEqualTo(1.0);

        EvalResult trajectoryResult = ToolTrajectoryEvaluator.builder().build().evaluate(testCase);
        assertThat(trajectoryResult.success()).isTrue();
        assertThat(trajectoryResult.score()).isEqualTo(1.0);

        EvalResult errorResult = ToolErrorEvaluator.builder().build().evaluate(testCase);
        assertThat(errorResult.success()).isTrue();
        assertThat(errorResult.score()).isEqualTo(1.0);

        EvalResult descriptionResult =
                ToolDescriptionReliabilityEvaluator.builder().build().evaluate(testCase);
        assertThat(descriptionResult.success()).isTrue();
        assertThat(descriptionResult.score()).isEqualTo(1.0);

        EvalResult nameResult = ToolNameReliabilityEvaluator.builder().build().evaluate(testCase);
        assertThat(nameResult.success()).isTrue();
        assertThat(nameResult.score()).isEqualTo(1.0);
    }

    @Test
    void detectsToolErrorInAdkFunctionResponseWithToolErrorEvaluator() {
        FunctionCall fc = FunctionCall.builder()
                .id("c1")
                .name("charge_card")
                .args(Map.of("amount", 100))
                .build();
        FunctionResponse fr = FunctionResponse.builder()
                .id("c1")
                .name("charge_card")
                .response(Map.of("error", "Card declined"))
                .build();

        ToolCall toolCall = AdkSupport.toToolCall(fc, fr);
        EvalTestCase testCase = EvalTestCase.builder()
                .input("Charge $100")
                .actualOutput("toolCalls", List.of(toolCall))
                .build();

        EvalResult result = ToolErrorEvaluator.builder().build().evaluate(testCase);
        assertThat(result.success()).isFalse();
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void convertsComplexGenAiSchemaToLowercaseJsonSchemaMap() {
        Schema itemSchema =
                Schema.builder().type(Type.Known.STRING).description("Tag name").build();
        Schema statusSchema = Schema.builder()
                .type(Type.Known.STRING)
                .description("Filter by status")
                .enum_(List.of("OPEN", "CLOSED"))
                .build();
        Schema limitSchema = Schema.builder()
                .type(Type.Known.INTEGER)
                .description("Max items")
                .build();
        Schema tagsSchema = Schema.builder()
                .type(Type.Known.ARRAY)
                .description("List of tags")
                .items(itemSchema)
                .build();
        Schema rootSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "status", statusSchema,
                        "limit", limitSchema,
                        "tags", tagsSchema))
                .required(List.of("status"))
                .build();

        FunctionDeclaration declaration = FunctionDeclaration.builder()
                .name("list_issues")
                .description("Lists issues matching the filter")
                .parameters(rootSchema)
                .build();

        ToolDefinition def = AdkSupport.toToolDefinition(declaration);

        assertThat(def.name()).isEqualTo("list_issues");
        assertThat(def.description()).isEqualTo("Lists issues matching the filter");
        assertThat(def.requiredParameters()).containsExactly("status");
        assertThat(def.parameterNames()).containsExactlyInAnyOrder("status", "limit", "tags");
        assertThat(def.parameterSchema("status"))
                .containsEntry("type", "string")
                .containsEntry("enum", List.of("OPEN", "CLOSED"));
        assertThat(def.parameterSchema("limit")).containsEntry("type", "integer");
        assertThat(def.parameterSchema("tags")).containsEntry("type", "array");
    }

    @Test
    void handlesToolWithoutDeclarationGracefully() {
        BaseTool customTool = new BaseTool("custom_ping", "Pings a host") {};
        ToolDefinition def = AdkSupport.toToolDefinition(customTool);

        assertThat(def.name()).isEqualTo("custom_ping");
        assertThat(def.description()).isEqualTo("Pings a host");
        assertThat(def.inputSchema()).containsEntry("type", "object");
    }

    @Test
    void handlesNullAndEmptyInputsGracefully() {
        assertThat(AdkSupport.toAgentTrace((List<Event>) null).finalResponse()).isEmpty();
        assertThat(AdkSupport.toAgentTrace(List.of()).toolCalls()).isEmpty();
        assertThat(AdkSupport.toAgentTrace((Session) null).finalResponse()).isEmpty();
        assertThat(AdkSupport.toToolCalls(null)).isEmpty();
        assertThat(AdkSupport.toToolCalls(new ArrayList<>(Arrays.asList(null, null))))
                .isEmpty();
        assertThat(AdkSupport.toToolDefinitions((List<BaseTool>) null)).isEmpty();
        assertThat(AdkSupport.toToolDefinitions((LlmAgent) null)).isEmpty();
        assertThat(AdkSupport.toSchemaMap(null)).isEmpty();

        assertThatThrownBy(() -> AdkSupport.toToolDefinition((BaseTool) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AdkSupport.toToolDefinition((FunctionDeclaration) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AdkSupport.toToolCall(null, (FunctionResponse) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void extractsAndDeduplicatesToolDefinitionsAcrossSubAgents() {
        FunctionTool weatherTool = FunctionTool.create(WeatherTools.class, "getWeather");
        BaseTool pingTool = new BaseTool("custom_ping", "Pings a host") {};

        LlmAgent subAgent = LlmAgent.builder()
                .name("sub_agent")
                .description("Sub-agent")
                .tools(weatherTool, pingTool)
                .build();
        LlmAgent rootAgent = LlmAgent.builder()
                .name("root_agent")
                .description("Root agent")
                .tools(weatherTool)
                .subAgents(subAgent)
                .build();

        List<ToolDefinition> defs = AdkSupport.toToolDefinitions((com.google.adk.agents.BaseAgent) rootAgent);
        assertThat(defs).extracting(ToolDefinition::name).containsExactly("get_weather", "custom_ping");
    }
}
