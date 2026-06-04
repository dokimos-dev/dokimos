package dev.dokimos.core.agents;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.OutputType;
import dev.dokimos.core.exceptions.DokimosTypeConversionException;
import java.util.HashMap;
import java.util.List;
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
    void shouldSerializeResultJsonToCompactJson() {
        var call = ToolCall.builder()
                .name("book_hotel")
                .resultJson(Map.of("confirmation", "ABC123"))
                .build();

        assertThat(call.result()).isEqualTo("{\"confirmation\":\"ABC123\"}");
    }

    @Test
    void shouldSerializeResultJsonForRecordValue() {
        record Booking(String confirmation, int nights) {}

        var call = ToolCall.builder()
                .name("book_hotel")
                .resultJson(new Booking("ABC123", 3))
                .build();

        assertThat(call.result()).isEqualTo("{\"confirmation\":\"ABC123\",\"nights\":3}");
    }

    @Test
    void shouldKeepResultStringVerbatim() {
        var raw = "{\"confirmation\": \"ABC123\"}";

        var call = ToolCall.builder().name("book_hotel").result(raw).build();

        assertThat(call.result()).isEqualTo(raw);
    }

    @Test
    void shouldSerializeNullResultJsonToJsonNullLiteral() {
        var call = ToolCall.builder().name("book_hotel").resultJson(null).build();

        assertThat(call.result()).isEqualTo("null");
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

    record Booking(String confirmation, int nights) {}

    @Test
    void resultAsReadsBackWhatResultJsonWrote() {
        var booking = new Booking("ABC123", 3);
        var call = ToolCall.builder().name("book_hotel").resultJson(booking).build();

        assertThat(call.resultAs(Booking.class)).isEqualTo(booking);
    }

    @Test
    void resultAsReadsAGenericListViaOutputType() {
        var call = ToolCall.builder()
                .name("list_bookings")
                .resultJson(List.of(new Booking("A", 1), new Booking("B", 2)))
                .build();

        List<Booking> bookings = call.resultAs(new OutputType<List<Booking>>() {});

        assertThat(bookings).containsExactly(new Booking("A", 1), new Booking("B", 2));
    }

    @Test
    void resultAsReturnsNullForNullResult() {
        var call = ToolCall.of("get_weather", Map.of());

        assertThat(call.result()).isNull();
        assertThat(call.resultAs(Booking.class)).isNull();
    }

    @Test
    void resultAsReturnsNullForBlankResult() {
        var call = ToolCall.builder().name("get_weather").result("   ").build();

        assertThat(call.resultAs(Booking.class)).isNull();
    }

    @Test
    void resultAsThrowsForNonJsonResult() {
        var call = ToolCall.builder().name("get_weather").result("not json").build();

        assertThatThrownBy(() -> call.resultAs(Booking.class)).isInstanceOf(DokimosTypeConversionException.class);
    }

    @Test
    void resultAsParsesAValidJsonStringResult() {
        var call = ToolCall.builder()
                .name("book_hotel")
                .result("{\"confirmation\":\"ABC123\",\"nights\":3}")
                .build();

        assertThat(call.resultAs(Booking.class)).isEqualTo(new Booking("ABC123", 3));
    }

    record NestedResult(Booking booking, List<String> tags) {}

    @Test
    void resultAsRoundTripsNestedObjects() {
        var nested = new NestedResult(new Booking("XYZ789", 5), List.of("vip", "refundable"));
        var call = ToolCall.builder().name("book_hotel").resultJson(nested).build();

        assertThat(call.resultAs(NestedResult.class)).isEqualTo(nested);
    }

    @Test
    void resultAsWorksAfterFromMapWhenResultIsJsonString() {
        var map = Map.<String, Object>of("name", "book_hotel", "result", "{\"confirmation\":\"ABC123\",\"nights\":3}");

        var call = ToolCall.fromMap(map);

        assertThat(call.resultAs(Booking.class)).isEqualTo(new Booking("ABC123", 3));
    }

    @Test
    void resultAsRoundTripsAfterFromMapWhenResultWasStructuredObject() {
        // fromMap now serializes a structured result to compact JSON, so it round-trips back.
        var map = Map.<String, Object>of("name", "book_hotel", "result", Map.of("confirmation", "ABC123", "nights", 3));

        var call = ToolCall.fromMap(map);

        assertThat(call.result()).startsWith("{\"");
        assertThat(call.resultAs(Booking.class)).isEqualTo(new Booking("ABC123", 3));
    }

    @Test
    void resultAsStringThrowsAfterFromMapWhenResultWasPlainText() {
        // fromMap keeps a plain String result verbatim (it is not re-serialized), so a tool whose
        // result is plain prose cannot be read back as a String: resultAs parses via Json.read, which
        // rejects "Found 5 flights" as invalid JSON. Pin this so the verbatim-String storage contract
        // and the JSON-string read contract cannot drift apart silently in either direction.
        var call = ToolCall.fromMap(Map.<String, Object>of("name", "search_flights", "result", "Found 5 flights"));

        assertThat(call.result()).isEqualTo("Found 5 flights");
        assertThatThrownBy(() -> call.resultAs(String.class)).isInstanceOf(DokimosTypeConversionException.class);
    }

    record Coordinates(double lat, double lon) {}

    @Test
    void argumentsAsRoundTripsARecord() {
        var call = ToolCall.of("locate", Map.of("lat", 48.85, "lon", 2.35));

        assertThat(call.argumentsAs(Coordinates.class)).isEqualTo(new Coordinates(48.85, 2.35));
    }

    @Test
    void argumentsAsReadsAGenericListViaOutputType() {
        var call = ToolCall.of("search", Map.of("tags", List.of("vip", "refundable")));

        Map<String, List<String>> args = call.argumentsAs(new OutputType<Map<String, List<String>>>() {});

        assertThat(args).containsEntry("tags", List.of("vip", "refundable"));
    }

    @Test
    void argumentsAsThrowsForIncompatibleShape() {
        var call = ToolCall.of("locate", Map.of("lat", "not-a-number", "lon", 2.35));

        assertThatThrownBy(() -> call.argumentsAs(Coordinates.class))
                .isInstanceOf(DokimosTypeConversionException.class);
    }

    @Test
    void metadataAsReadsATypedValue() {
        var call = ToolCall.builder()
                .name("search")
                .argument("q", "test")
                .metadata("latencyMs", 150)
                .build();

        assertThat(call.metadataAs("latencyMs", Integer.class)).isEqualTo(150);
    }

    @Test
    void metadataAsReadsAGenericListViaOutputType() {
        var call = ToolCall.builder()
                .name("search")
                .argument("q", "test")
                .metadata("tags", List.of("a", "b"))
                .build();

        List<String> tags = call.metadataAs("tags", new OutputType<List<String>>() {});

        assertThat(tags).containsExactly("a", "b");
    }

    @Test
    void metadataAsReturnsNullForAbsentKey() {
        var call = ToolCall.of("search", Map.of("q", "test"));

        assertThat(call.metadataAs("missing", Integer.class)).isNull();
    }

    @Test
    void metadataAsThrowsForWrongType() {
        var call = ToolCall.builder()
                .name("search")
                .argument("q", "test")
                .metadata("latencyMs", "not-a-number")
                .build();

        assertThatThrownBy(() -> call.metadataAs("latencyMs", Integer.class))
                .isInstanceOf(DokimosTypeConversionException.class);
    }
}
