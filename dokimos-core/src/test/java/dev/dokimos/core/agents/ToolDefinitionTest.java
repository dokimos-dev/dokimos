package dev.dokimos.core.agents;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ToolDefinitionTest {

    @Test
    void shouldCreateWithStaticFactory() {
        var schema = Map.<String, Object>of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string")
                ),
                "required", List.of("query")
        );

        var def = ToolDefinition.of("search_flights", "Search for available flights", schema);

        assertThat(def.name()).isEqualTo("search_flights");
        assertThat(def.description()).isEqualTo("Search for available flights");
        assertThat(def.inputSchema()).containsKey("properties");
    }

    @Test
    void shouldCreateWithBuilder() {
        var def = ToolDefinition.builder()
                .name("book_hotel")
                .description("Book a hotel room")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "city", Map.of("type", "string"),
                                "nights", Map.of("type", "integer")
                        ),
                        "required", List.of("city")
                ))
                .build();

        assertThat(def.name()).isEqualTo("book_hotel");
        assertThat(def.description()).isEqualTo("Book a hotel room");
    }

    @Test
    void shouldCreateFromMap() {
        var map = Map.<String, Object>of(
                "name", "get_weather",
                "description", "Get weather forecast",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of("city", Map.of("type", "string")),
                        "required", List.of("city")
                )
        );

        var def = ToolDefinition.fromMap(map);

        assertThat(def.name()).isEqualTo("get_weather");
        assertThat(def.description()).isEqualTo("Get weather forecast");
        assertThat(def.requiredParameters()).containsExactly("city");
    }

    @Test
    void shouldExtractRequiredParameters() {
        var def = ToolDefinition.of("test", "desc", Map.of(
                "required", List.of("param1", "param2")
        ));

        assertThat(def.requiredParameters()).containsExactly("param1", "param2");
    }

    @Test
    void shouldReturnEmptyRequiredParametersWhenNoneDefined() {
        var def = ToolDefinition.of("test", "desc", Map.of("type", "object"));

        assertThat(def.requiredParameters()).isEmpty();
    }

    @Test
    void shouldExtractParameterNames() {
        var def = ToolDefinition.of("test", "desc", Map.of(
                "properties", Map.of(
                        "city", Map.of("type", "string"),
                        "date", Map.of("type", "string")
                )
        ));

        assertThat(def.parameterNames()).containsExactlyInAnyOrder("city", "date");
    }

    @Test
    void shouldReturnEmptyParameterNamesWhenNoProperties() {
        var def = ToolDefinition.of("test", "desc", Map.of());

        assertThat(def.parameterNames()).isEmpty();
    }

    @Test
    void shouldGetParameterSchema() {
        var def = ToolDefinition.of("test", "desc", Map.of(
                "properties", Map.of(
                        "city", Map.of("type", "string", "description", "City name")
                )
        ));

        assertThat(def.parameterSchema("city")).containsEntry("type", "string");
        assertThat(def.parameterSchema("city")).containsEntry("description", "City name");
    }

    @Test
    void shouldReturnEmptyMapForUnknownParameter() {
        var def = ToolDefinition.of("test", "desc", Map.of(
                "properties", Map.of("city", Map.of("type", "string"))
        ));

        assertThat(def.parameterSchema("unknown")).isEmpty();
    }

    @Test
    void shouldBeImmutable() {
        var def = ToolDefinition.of("test", "desc", Map.of("type", "object"));

        assertThatThrownBy(() -> def.inputSchema().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectNullOrBlankName() {
        assertThatThrownBy(() -> ToolDefinition.of(null, "desc", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");

        assertThatThrownBy(() -> ToolDefinition.of("  ", "desc", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }
}
