package dev.dokimos.core.evaluators.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.agents.ToolCalls;
import dev.dokimos.core.evaluators.EvaluationException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentEvalCastsTest {

    @Test
    void typedListMatchesCoerce() {
        List<ToolCall> raw = List.of(ToolCall.of("search", Map.of("q", "berlin")), ToolCall.of("book", Map.of()));
        assertThat(AgentEvalCasts.toolCalls(raw, "toolCalls")).isEqualTo(ToolCalls.coerce(raw));
    }

    @Test
    void mapListMatchesCoerce() {
        List<Map<String, Object>> raw =
                List.of(Map.of("name", "search", "arguments", Map.of("q", "berlin")), Map.of("name", "book"));
        assertThat(AgentEvalCasts.toolCalls(raw, "toolCalls")).isEqualTo(ToolCalls.coerce(raw));
    }

    @Test
    void nullMatchesCoerce() {
        assertThat(AgentEvalCasts.toolCalls(null, "toolCalls")).isEqualTo(ToolCalls.coerce(null));
    }

    @Test
    void emptyListMatchesCoerce() {
        assertThat(AgentEvalCasts.toolCalls(List.of(), "toolCalls")).isEqualTo(ToolCalls.coerce(List.of()));
    }

    @Test
    void scalarThrowsEvaluationExceptionNamingTheKey() {
        assertThatThrownBy(() -> AgentEvalCasts.toolCalls("not a list", "toolCalls"))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("key 'toolCalls'");
    }

    @Test
    void mixedTypedAndMapListThrowsEvaluationExceptionNamingKeyAndIndex() {
        ToolCall typed = ToolCall.of("a", Map.of());
        Map<String, Object> map = Map.of("name", "b");
        assertThatThrownBy(() -> AgentEvalCasts.toolCalls(Arrays.asList(typed, map), "toolCalls"))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("key 'toolCalls'")
                .hasMessageContaining("element 1");
    }

    @Test
    void malformedMapAtLaterIndexThrowsEvaluationExceptionNamingKeyAndIndex() {
        Map<String, Object> good = Map.of("name", "ok");
        Map<String, Object> malformed = Map.of("arguments", Map.of()); // no name -> ToolCall.fromMap throws
        assertThatThrownBy(() -> AgentEvalCasts.toolCalls(Arrays.asList(good, malformed), "toolCalls"))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("key 'toolCalls'")
                .hasMessageContaining("index 1");
    }
}
