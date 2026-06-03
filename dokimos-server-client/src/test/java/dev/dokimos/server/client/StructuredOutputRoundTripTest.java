package dev.dokimos.server.client;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.evaluators.StructuralMatchEvaluator;
import dev.dokimos.core.evaluators.StructuralMatchMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Guards the wire mapping that {@link ServerDatasetResolver} performs for structured
 * {@code expectedOutputs}: an {@link dev.dokimos.core.Example}'s {@code expectedOutputs} map is
 * serialized to JSON when cached and re-materialized with {@code convertValue(node, Map.class)} when
 * read back from the server. That hop is where JSON loses the distinction between an integer and a
 * floating-point literal (a Java {@code 5} can come back as {@code 5.0}, and vice versa), and where a
 * nested object or list of objects has to survive intact.
 *
 * <p>The outside-voice concern (design test plan, server/client round-trip) is that numbers re-type
 * across the hop and silently corrupt eval scores. This test serializes a structured payload through
 * the <em>same</em> {@link ObjectMapper} the resolver uses, materializes it the same way the resolver
 * does, and asserts that {@link StructuralMatchEvaluator} produces an identical score before and
 * after the round trip — proving the BigDecimal-aware comparator absorbs the re-typing.
 */
class StructuredOutputRoundTripTest {

    /** Mirrors the mapper {@link ServerDatasetResolver} constructs for the wire mapping. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Replays the exact server hop the resolver performs: serialize the structured map to JSON, then
     * read it back into a {@code Map<String,Object>} via {@code convertValue(node, Map.class)} (the
     * same call {@link ServerDatasetResolver}'s {@code readMap} makes on a server response).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> roundTrip(Map<String, Object> outputs) throws Exception {
        String json = objectMapper.writeValueAsString(outputs);
        JsonNode node = objectMapper.readTree(json);
        return objectMapper.convertValue(node, Map.class);
    }

    /** A structured payload: nested object + a mix of integral and floating-point numbers + a list. */
    private Map<String, Object> structuredExpected() {
        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("width", 5); // integral literal — JSON round-trip may re-type to 5 or 5.0
        dimensions.put("height", 5.0); // floating-point literal
        dimensions.put("depth", 2.50); // trailing-zero scale that BigDecimal must treat as 2.5

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("name", "whisky");
        nested.put("proof", 90);
        nested.put("rating", 4.5);
        nested.put("dimensions", dimensions);
        nested.put("tags", List.of("peaty", "smoky"));
        nested.put("scores", List.of(8, 9.0, 10));

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("output", nested);
        return output;
    }

    private EvalResult evaluate(StructuralMatchEvaluator evaluator, Map<String, Object> expected) {
        EvalTestCase testCase = EvalTestCase.builder()
                .input("query", "describe the whisky")
                // The actual output is the structured value verbatim — the score should be a perfect
                // match both before and after the wire hop.
                .actualOutputs(expected)
                .expectedOutputs(expected)
                .build();
        return evaluator.evaluate(testCase);
    }

    @Test
    void strictScoreIsIdenticalBeforeAndAfterTheServerHop() throws Exception {
        StructuralMatchEvaluator evaluator = StructuralMatchEvaluator.builder()
                .mode(StructuralMatchMode.STRICT)
                .build();

        Map<String, Object> local = structuredExpected();
        Map<String, Object> overWire = roundTrip(structuredExpected());

        // Sanity: the round trip really did move values through Jackson's number boxing (otherwise the
        // test would be vacuous). The integral literal comes back as an Integer and the trailing-zero
        // scale (2.50) is normalized to 2.5 — exactly the re-typing the comparator has to absorb.
        @SuppressWarnings("unchecked")
        Map<String, Object> wireNested = (Map<String, Object>) overWire.get("output");
        @SuppressWarnings("unchecked")
        Map<String, Object> wireDimensions = (Map<String, Object>) wireNested.get("dimensions");
        assertThat(wireDimensions.get("width")).isInstanceOf(Integer.class).isEqualTo(5);
        assertThat(wireDimensions.get("height")).isInstanceOf(Double.class).isEqualTo(5.0);
        assertThat(wireDimensions.get("depth")).isEqualTo(2.5);

        double localScore = evaluate(evaluator, local).score();
        double wireScore = evaluate(evaluator, overWire).score();

        assertThat(localScore).isEqualTo(1.0);
        assertThat(wireScore).isEqualTo(localScore);
    }

    @Test
    void lenientScoreIsIdenticalBeforeAndAfterTheServerHop() throws Exception {
        StructuralMatchEvaluator evaluator = StructuralMatchEvaluator.builder()
                .mode(StructuralMatchMode.LENIENT)
                .build();

        double localScore = evaluate(evaluator, structuredExpected()).score();
        double wireScore = evaluate(evaluator, roundTrip(structuredExpected())).score();

        assertThat(localScore).isEqualTo(1.0);
        assertThat(wireScore).isEqualTo(localScore);
    }

    @Test
    void binaryGateStillPassesAfterTheServerHop() throws Exception {
        StructuralMatchEvaluator evaluator = StructuralMatchEvaluator.builder()
                .mode(StructuralMatchMode.STRICT)
                .binary()
                .build();

        double localScore = evaluate(evaluator, structuredExpected()).score();
        double wireScore = evaluate(evaluator, roundTrip(structuredExpected())).score();

        assertThat(localScore).isEqualTo(1.0);
        assertThat(wireScore).isEqualTo(1.0);
    }

    @Test
    void integerExpectedMatchesFloatingActualAcrossTheHop() throws Exception {
        // The classic 5 vs 5.0 hazard: the dataset stored an integer, the server returned a double
        // (or the JSON literal carried a trailing .0). The BigDecimal comparator must treat them as
        // equal so the eval does not flip from pass to fail purely because of the transport.
        StructuralMatchEvaluator evaluator = StructuralMatchEvaluator.builder()
                .mode(StructuralMatchMode.STRICT)
                .build();

        Map<String, Object> expected = Map.of("output", Map.of("count", 5, "ratio", 2.50));
        Map<String, Object> actualOverWire = roundTrip(Map.of("output", Map.of("count", 5.0, "ratio", 2.5)));

        EvalTestCase testCase = EvalTestCase.builder()
                .input("query", "count")
                .actualOutputs(actualOverWire)
                .expectedOutputs(expected)
                .build();

        EvalResult result = evaluator.evaluate(testCase);
        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.reason()).contains("match");
    }
}
