package dev.dokimos.core.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class JsonTest {

    record Whisky(String name, int age) {}

    @Test
    void readerAndWritersAreStableSingleInstances() {
        ObjectReader reader = Json.reader();
        ObjectWriter compact = Json.compactWriter();
        ObjectWriter pretty = Json.prettyWriter();

        assertThat(Json.reader()).isSameAs(reader);
        assertThat(Json.compactWriter()).isSameAs(compact);
        assertThat(Json.prettyWriter()).isSameAs(pretty);
        assertThat(Json.comparisonReader()).isSameAs(Json.comparisonReader());
    }

    @Test
    void convertRoundTripsARecord() {
        Map<String, Object> map = Map.of("name", "Lagavulin", "age", 16);

        Whisky whisky = Json.convert(map, Whisky.class);

        assertThat(whisky).isEqualTo(new Whisky("Lagavulin", 16));
    }

    // Field used purely to capture the generic type List<Whisky> reflectively for the test.
    @SuppressWarnings("unused")
    private List<Whisky> listOfWhiskyField;

    @Test
    void convertRoundTripsAGenericList() throws Exception {
        List<Map<String, Object>> raw =
                List.of(Map.of("name", "Ardbeg", "age", 10), Map.of("name", "Oban", "age", 14));
        java.lang.reflect.Type listOfWhisky =
                JsonTest.class.getDeclaredField("listOfWhiskyField").getGenericType();
        JavaType listType = Json.resolveType(listOfWhisky);

        List<Whisky> whiskies = Json.convert(raw, listType);

        assertThat(whiskies).containsExactly(new Whisky("Ardbeg", 10), new Whisky("Oban", 14));
    }

    @Test
    void convertNullReturnsNull() {
        assertThat(Json.convert(null, Whisky.class)).isNull();
        assertThat(Json.<Whisky>convert(null, Json.resolveType(Whisky.class))).isNull();
    }

    @Test
    void toNodeAndWritersProduceConsistentJson() {
        Whisky whisky = new Whisky("Talisker", 10);

        JsonNode node = Json.toNode(whisky);
        assertThat(node.get("name").asText()).isEqualTo("Talisker");
        assertThat(node.get("age").asInt()).isEqualTo(10);

        String compact = Json.writeCompact(whisky);
        assertThat(compact).isEqualTo("{\"name\":\"Talisker\",\"age\":10}");

        String pretty = Json.writePretty(whisky);
        assertThat(pretty).contains("\n").contains("\"name\" : \"Talisker\"");
    }

    @Test
    void comparisonReaderRejectsDuplicateKeys() {
        assertThatThrownBy(() -> Json.comparisonReader().readTree("{\"x\":1,\"x\":2}"))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void comparisonReaderRejectsNaN() {
        assertThatThrownBy(() -> Json.comparisonReader().readTree("{\"x\":NaN}"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void mapperIsThreadSafeUnderParallelUse() {
        List<Whisky> results =
                IntStream.range(0, 2000)
                        .parallel()
                        .mapToObj(
                                i -> {
                                    String json = Json.writeCompact(new Whisky("W" + i, i));
                                    try {
                                        return Json.reader().readValue(json, Whisky.class);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                        .toList();

        assertThat(results).hasSize(2000);
        assertThat(results).contains(new Whisky("W7", 7), new Whisky("W1999", 1999));
    }
}
