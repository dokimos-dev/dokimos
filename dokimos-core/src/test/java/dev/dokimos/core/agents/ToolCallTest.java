package dev.dokimos.core.agents;

import static org.assertj.core.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolCallTest {

    @Test
    void shouldCreateWithStaticFactory() {
        var call = ToolCall.of("search", Map.of("query", "flights"));

        assertThat(call.name()).isEqualTo("search");
        assertThat(call.arguments()).containsEntry("query", "flights");
        assertThat(call.result()).isNull();
        assertThat(call.metadata()).isEmpty();
    }

    @Test
    void shouldCreateWithBuilder() {
        var call = ToolCall.builder()
                .name("book_hotel")
                .argument("city", "Paris")
                .argument("nights", 3)
                .result("{\"confirmation\": \"ABC123\"}")
                .metadata("latencyMs", 150)
                .build();

        assertThat(call.name()).isEqualTo("book_hotel");
        assertThat(call.arguments()).containsEntry("city", "Paris");
        assertThat(call.arguments()).containsEntry("nights", 3);
        assertThat(call.result()).isEqualTo("{\"confirmation\": \"ABC123\"}");
        assertThat(call.metadata()).containsEntry("latencyMs", 150);
    }

    @Test
    void shouldCreateFromMap() {
        var map = Map.<String, Object>of(
                "name",
                "search_flights",
                "arguments",
                Map.of("origin", "NYC", "destination", "LAX"),
                "result",
                "Found 5 flights",
                "metadata",
                Map.of("tokens", 42));

        var call = ToolCall.fromMap(map);

        assertThat(call.name()).isEqualTo("search_flights");
        assertThat(call.arguments()).containsEntry("origin", "NYC");
        assertThat(call.result()).isEqualTo("Found 5 flights");
        assertThat(call.metadata()).containsEntry("tokens", 42);
    }

    @Test
    void shouldCreateFromMapWithMissingOptionalFields() {
        var map = Map.<String, Object>of("name", "get_weather");

        var call = ToolCall.fromMap(map);

        assertThat(call.name()).isEqualTo("get_weather");
        assertThat(call.arguments()).isEmpty();
        assertThat(call.result()).isNull();
        assertThat(call.metadata()).isEmpty();
    }

    @Test
    void shouldBeImmutable() {
        var call = ToolCall.of("search", Map.of("q", "test"));

        assertThatThrownBy(() -> call.arguments().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> call.metadata().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectNullOrBlankName() {
        assertThatThrownBy(() -> ToolCall.of(null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");

        assertThatThrownBy(() -> ToolCall.of("  ", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void shouldHandleNullArgumentsAndMetadata() {
        var call = new ToolCall("test", null, null, null);

        assertThat(call.arguments()).isEmpty();
        assertThat(call.metadata()).isEmpty();
    }

    @Test
    void shouldHandleExplicitNullResultInFromMap() {
        var map = new HashMap<String, Object>();
        map.put("name", "get_weather");
        map.put("result", null);

        var call = ToolCall.fromMap(map);

        assertThat(call.result()).isNull();
    }

    @Test
    void shouldHandleNestedArguments() {
        var call = ToolCall.of(
                "search",
                Map.of("filter", Map.of("price", Map.of("max", 500, "currency", "USD")), "sort", "price_asc"));

        assertThat(call.arguments()).containsKey("filter");
        @SuppressWarnings("unchecked")
        Map<String, Object> filter = (Map<String, Object>) call.arguments().get("filter");
        assertThat(filter).containsKey("price");
    }
}
