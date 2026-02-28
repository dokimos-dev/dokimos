package dev.dokimos.core.agents;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentTraceTest {

    @Test
    void shouldBuildWithAllFields() {
        var trace = AgentTrace.builder()
                .finalResponse("I've booked your hotel.")
                .addToolCall(ToolCall.of("search_hotels", Map.of("city", "Paris")))
                .addToolCall(ToolCall.of("book_hotel", Map.of("hotelId", "H123")))
                .addReasoningStep("User wants to book a hotel in Paris")
                .addReasoningStep("Found available hotels, selecting cheapest")
                .metadata("totalLatencyMs", 500)
                .build();

        assertThat(trace.finalResponse()).isEqualTo("I've booked your hotel.");
        assertThat(trace.toolCalls()).hasSize(2);
        assertThat(trace.reasoningSteps()).hasSize(2);
        assertThat(trace.metadata()).containsEntry("totalLatencyMs", 500);
    }

    @Test
    void shouldConvertToOutputMap() {
        var trace = AgentTrace.builder()
                .finalResponse("Done!")
                .addToolCall(ToolCall.of("search", Map.of("q", "test")))
                .addReasoningStep("Thinking...")
                .build();

        var map = trace.toOutputMap();

        assertThat(map.get("output")).isEqualTo("Done!");
        assertThat(map.get("toolCalls")).isInstanceOf(List.class);
        assertThat((List<?>) map.get("toolCalls")).hasSize(1);
        assertThat(map.get("reasoningSteps")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> steps = (List<String>) map.get("reasoningSteps");
        assertThat(steps).containsExactly("Thinking...");
    }

    @Test
    void shouldReturnToolNames() {
        var trace = AgentTrace.builder()
                .addToolCall(ToolCall.of("search", Map.of()))
                .addToolCall(ToolCall.of("book", Map.of()))
                .addToolCall(ToolCall.of("search", Map.of()))
                .build();

        assertThat(trace.toolNames()).containsExactlyInAnyOrder("search", "book");
    }

    @Test
    void shouldHandleNullFields() {
        var trace = new AgentTrace(null, null, null, null);

        assertThat(trace.finalResponse()).isNull();
        assertThat(trace.toolCalls()).isEmpty();
        assertThat(trace.reasoningSteps()).isEmpty();
        assertThat(trace.metadata()).isEmpty();
    }

    @Test
    void shouldBeImmutable() {
        var trace = AgentTrace.builder()
                .addToolCall(ToolCall.of("search", Map.of()))
                .build();

        assertThatThrownBy(() -> trace.toolCalls().add(ToolCall.of("hack", Map.of())))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> trace.reasoningSteps().add("hack")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> trace.metadata().put("hack", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldSetToolCallsFromList() {
        var calls = List.of(ToolCall.of("a", Map.of()), ToolCall.of("b", Map.of()));

        var trace = AgentTrace.builder().toolCalls(calls).build();

        assertThat(trace.toolCalls()).hasSize(2);
    }

    @Test
    void shouldHandleNullFinalResponseInOutputMap() {
        var trace = AgentTrace.builder().build();

        var map = trace.toOutputMap();

        assertThat(map.get("output")).isEqualTo("");
    }
}
