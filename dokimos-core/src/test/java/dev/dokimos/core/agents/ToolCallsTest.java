package dev.dokimos.core.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolCallsTest {

    @Test
    void nullCoercesToEmptyList() {
        assertThat(ToolCalls.coerce(null)).isEmpty();
    }

    @Test
    void emptyListCoercesToEmptyList() {
        assertThat(ToolCalls.coerce(List.of())).isEmpty();
    }

    @Test
    void typedToolCallsPassThroughInOrder() {
        ToolCall a = ToolCall.of("search", Map.of("q", "berlin"));
        ToolCall b = ToolCall.of("book", Map.of());
        assertThat(ToolCalls.coerce(List.of(a, b))).containsExactly(a, b);
    }

    @Test
    void resultIsUnmodifiable() {
        List<ToolCall> result = ToolCalls.coerce(List.of(ToolCall.of("t", Map.of())));
        assertThatThrownBy(() -> result.add(ToolCall.of("x", Map.of())))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void listOfMapsIsMappedViaFromMap() {
        Map<String, Object> map = Map.of("name", "search", "arguments", Map.of("q", "berlin"));
        List<ToolCall> result = ToolCalls.coerce(List.of(map));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("search");
        assertThat(result.get(0).arguments()).containsEntry("q", "berlin");
    }

    @Test
    void nonListInputIsRejected() {
        assertThatThrownBy(() -> ToolCalls.coerce("not a list"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("List");
    }

    @Test
    void mixedTypedAndMapListIsRejectedNamingTheIndex() {
        ToolCall typed = ToolCall.of("a", Map.of());
        Map<String, Object> map = Map.of("name", "b");
        assertThatThrownBy(() -> ToolCalls.coerce(List.of(typed, map)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("element 1");
    }

    @Test
    void malformedMapElementIsWrappedWithItsIndex() {
        Map<String, Object> good = Map.of("name", "ok");
        Map<String, Object> bad = Map.of("arguments", Map.of()); // no name -> ToolCall.fromMap throws
        assertThatThrownBy(() -> ToolCalls.coerce(List.of(good, bad)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("index 1");
    }
}
