package dev.dokimos.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import dev.dokimos.core.evaluators.agents.ToolTrajectoryEvaluator;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LangChain4jAgentTraceTest {

    private static ToolExecution execution(String name, String argumentsJson, String result) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name(name)
                .arguments(argumentsJson)
                .build();
        ToolExecution execution = mock(ToolExecution.class);
        when(execution.request()).thenReturn(request);
        when(execution.result()).thenReturn(result);
        return execution;
    }

    @SuppressWarnings("unchecked")
    private static Result<String> result(String content, List<ToolExecution> executions) {
        Result<String> result = mock(Result.class);
        when(result.content()).thenReturn(content);
        when(result.toolExecutions()).thenReturn(executions);
        return result;
    }

    @Test
    @DisplayName("maps content and tool executions into a trace")
    void mapsTrace() {
        Result<String> result = result(
                "Booked.",
                List.of(
                        execution(
                                "search_flights", "{\"origin\":\"JFK\",\"destination\":\"CDG\"}", "[{\"id\":\"AF1\"}]"),
                        execution("book_hotel", "{\"city\":\"Paris\",\"nights\":3}", "{\"confirmation\":\"X\"}")));

        AgentTrace trace = LangChain4jSupport.toAgentTrace(result);

        assertThat(trace.finalResponse()).isEqualTo("Booked.");
        assertThat(trace.toolCalls()).hasSize(2);
        ToolCall first = trace.toolCalls().get(0);
        assertThat(first.name()).isEqualTo("search_flights");
        assertThat(first.arguments()).containsEntry("origin", "JFK").containsEntry("destination", "CDG");
        assertThat(first.result()).isEqualTo("[{\"id\":\"AF1\"}]");
        assertThat(trace.toolCalls().get(1).arguments()).containsEntry("nights", 3);
    }

    @Test
    @DisplayName("numeric and nested argument values survive JSON parsing")
    void parsesNumbersAndNesting() {
        Result<String> result =
                result("ok", List.of(execution("search", "{\"max\":5,\"filter\":{\"area\":\"EU\"}}", "ok")));

        ToolCall call = LangChain4jSupport.toAgentTrace(result).toolCalls().get(0);

        assertThat(call.arguments().get("max")).isEqualTo(5);
        assertThat(call.arguments().get("filter")).isEqualTo(Map.of("area", "EU"));
    }

    @Test
    @DisplayName("malformed argument JSON yields empty arguments without throwing")
    void malformedArgs() {
        Result<String> result = result("ok", List.of(execution("search", "{not valid", "r")));

        ToolCall call = LangChain4jSupport.toAgentTrace(result).toolCalls().get(0);

        assertThat(call.name()).isEqualTo("search");
        assertThat(call.arguments()).isEmpty();
        assertThat(call.result()).isEqualTo("r");
    }

    @Test
    @DisplayName("blank and null argument strings yield empty arguments")
    void blankArgs() {
        Result<String> result = result("ok", List.of(execution("a", "", "r1"), execution("b", null, "r2")));

        List<ToolCall> calls = LangChain4jSupport.toToolCalls(result);

        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).arguments()).isEmpty();
        assertThat(calls.get(1).arguments()).isEmpty();
    }

    @Test
    @DisplayName("a result with no tool executions yields a trace with the final response only")
    void noToolExecutions() {
        Result<String> result = result("Just an answer.", List.of());

        AgentTrace trace = LangChain4jSupport.toAgentTrace(result);

        assertThat(trace.toolCalls()).isEmpty();
        assertThat(trace.finalResponse()).isEqualTo("Just an answer.");
    }

    @Test
    @DisplayName("a null result yields an empty trace without throwing")
    void nullResult() {
        AgentTrace trace = LangChain4jSupport.toAgentTrace(null);

        assertThat(trace.toolCalls()).isEmpty();
        assertThat(trace.finalResponse()).isNull();
    }

    @Test
    @DisplayName("tool specifications convert to tool definitions with a shallow schema")
    void toolDefinitions() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("search_flights")
                .description("Search for flights")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("origin", JsonStringSchema.builder().build())
                        .addProperty("nights", JsonIntegerSchema.builder().build())
                        .required("origin")
                        .build())
                .build();

        List<ToolDefinition> defs = LangChain4jSupport.toToolDefinitions(List.of(spec));

        assertThat(defs).hasSize(1);
        ToolDefinition def = defs.get(0);
        assertThat(def.name()).isEqualTo("search_flights");
        assertThat(def.description()).isEqualTo("Search for flights");
        assertThat(def.parameterNames()).containsExactlyInAnyOrder("origin", "nights");
        assertThat(def.requiredParameters()).containsExactly("origin");
        assertThat(def.parameterSchema("nights")).containsEntry("type", "integer");
    }

    @Test
    @DisplayName("null tool specifications yield an empty list")
    void nullToolDefinitions() {
        assertThat(LangChain4jSupport.toToolDefinitions(null)).isEmpty();
    }

    @Test
    @DisplayName("the produced trace satisfies the agent evaluators without throwing")
    void roundTripThroughEvaluators() {
        Result<String> result = result(
                "done", List.of(execution("search_flights", "{\"origin\":\"JFK\",\"destination\":\"CDG\"}", "[]")));
        ToolSpecification spec = ToolSpecification.builder()
                .name("search_flights")
                .description("Search for flights")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("origin", JsonStringSchema.builder().build())
                        .addProperty("destination", JsonStringSchema.builder().build())
                        .build())
                .build();

        EvalTestCase testCase = LangChain4jSupport.toAgentTrace(result)
                .toTestCase("Fly JFK to CDG", LangChain4jSupport.toToolDefinitions(List.of(spec)));
        testCase = EvalTestCase.builder()
                .inputs(testCase.inputs())
                .actualOutputs(testCase.actualOutputs())
                .expectedOutput("toolCalls", List.of(ToolCall.of("search_flights", Map.of())))
                .metadata(testCase.metadata())
                .build();

        EvalTestCase tc = testCase;
        assertThatCode(() -> ToolCallValidityEvaluator.builder().build().evaluate(tc))
                .doesNotThrowAnyException();
        assertThat(ToolTrajectoryEvaluator.builder()
                        .matchMode(ToolTrajectoryEvaluator.MatchMode.ANY_ORDER)
                        .build()
                        .evaluate(tc)
                        .score())
                .isEqualTo(1.0);
    }
}
