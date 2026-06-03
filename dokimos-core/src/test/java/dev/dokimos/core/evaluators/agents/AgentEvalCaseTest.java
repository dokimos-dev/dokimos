package dev.dokimos.core.evaluators.agents;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentEvalCaseTest {

    private static final ToolDefinition SEARCH_TOOL = ToolDefinition.of(
            "search_flights",
            "Search for flights",
            Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of(
                            "origin", Map.of("type", "string"),
                            "destination", Map.of("type", "string")),
                    "required",
                    List.of("origin", "destination")));

    @Test
    void shouldBuildValidTestCaseEvaluatedByToolCallValidityWithoutStringKeys() {
        EvalTestCase testCase = AgentEvalCase.builder()
                .input("Find me a flight from NYC to LAX")
                .toolCalls(List.of(ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "LAX"))))
                .tools(List.of(SEARCH_TOOL))
                .build();

        var result = ToolCallValidityEvaluator.builder().build().evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldReflectInvalidCallInScore() {
        EvalTestCase testCase = AgentEvalCase.builder()
                .toolCalls(List.of(
                        ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "LAX")),
                        ToolCall.of("search_flights", Map.of("origin", "NYC"))))
                .tools(List.of(SEARCH_TOOL))
                .build();

        var result = ToolCallValidityEvaluator.builder().build().evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.5);
    }

    @Test
    void shouldPopulateExactlyTheKeysTheEvaluatorsRead() {
        EvalTestCase testCase = AgentEvalCase.builder()
                .input("hi")
                .toolCalls(List.of(ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "LAX"))))
                .tools(List.of(SEARCH_TOOL))
                .expectedToolCalls(
                        List.of(ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "LAX"))))
                .tasks(List.of("Book a flight"))
                .build();

        assertThat(testCase.inputs()).containsKey("input");
        assertThat(testCase.actualOutputs()).containsOnlyKeys("toolCalls");
        assertThat(testCase.expectedOutputs()).containsOnlyKeys("toolCalls");
        assertThat(testCase.metadata()).containsOnlyKeys("tools", "tasks");
    }

    @Test
    void shouldOmitUnsetSlots() {
        EvalTestCase testCase = AgentEvalCase.builder()
                .toolCalls(List.of(ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "LAX"))))
                .tools(List.of(SEARCH_TOOL))
                .build();

        assertThat(testCase.inputs()).isEmpty();
        assertThat(testCase.expectedOutputs()).isEmpty();
        assertThat(testCase.metadata()).containsOnlyKeys("tools");
    }
}
