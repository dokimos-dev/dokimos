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
    void mismatchStillFailsAfterTheServerHop() throws Exception {
        // The negative counterpart to the round-trip tests: re-typing (5 vs 5.0) must be absorbed, but a
        // genuine difference (count 5 vs 6) must still register as a mismatch. This guards against the
        // opposite failure mode — a comparator bug that makes everything "match" would pass every
        // positive assertion above while silently erasing real regressions.
        StructuralMatchEvaluator evaluator = StructuralMatchEvaluator.builder()
                .mode(StructuralMatchMode.STRICT)
                .binary()
                .build();

        Map<String, Object> expected = Map.of("output", Map.of("count", 5));
        Map<String, Object> actualOverWire = roundTrip(Map.of("output", Map.of("count", 6)));

        EvalTestCase testCase = EvalTestCase.builder()
                .input("query", "count")
                .actualOutputs(actualOverWire)
                .expectedOutputs(expected)
                .build();

        EvalResult result = evaluator.evaluate(testCase);
        assertThat(result.score()).isLessThan(1.0);
        assertThat(result.success()).isFalse();
    }

    /** A typed target with an integral field — the shape a user reads a server-pulled dataset into. */
    record WhiskyRecord(String name, int age) {}

    @Test
    void typedAccessorReadsAnIntegralFieldBackAcrossTheServerHop() throws Exception {
        // The StructuralMatch round-trip tests above only exercise the BigDecimal-aware comparator. The
        // typed-accessor path (EvalTestCase.expectedOutputAs -> convertFrom -> Json.convert ->
        // MAPPER.convertValue) over the wire-retyped map had zero coverage in this module. This is the
        // exact user flow: pull a dataset from the server, then read it back into a record. The dataset
        // stored age as a floating-point literal (5.0); after the hop it is a Double, and the record
        // field is an int. Json's MAPPER sets no number-coercion features, yet the integral-valued
        // Double materializes cleanly into the int field. Pin that so the read does not start throwing.
        Map<String, Object> expected = Map.of("output", Map.of("name", "Oban", "age", 5.0));
        Map<String, Object> overWire = roundTrip(expected);

        @SuppressWarnings("unchecked")
        Map<String, Object> wireInner = (Map<String, Object>) overWire.get("output");
        // Sanity: the hop really did box age as a floating-point Double, not an Integer.
        assertThat(wireInner.get("age")).isInstanceOf(Double.class).isEqualTo(5.0);

        EvalTestCase testCase = EvalTestCase.builder()
                .input("query", "describe the whisky")
                .expectedOutputs(overWire)
                .build();

        assertThat(testCase.expectedOutputAs(WhiskyRecord.class)).isEqualTo(new WhiskyRecord("Oban", 5));
    }

    @Test
    void typedAccessorTruncatesANonIntegralFieldAcrossTheServerHop() throws Exception {
        // Companion to the test above, documenting the sharp edge of the same path: a NON-integral
        // value (5.5) read into an int record field does NOT throw — Jackson's convertValue silently
        // truncates it to 5. This is the default, unguarded coercion behavior; pinning it makes the
        // data-loss visible so a future tightening (e.g. enabling a fail-on-coercion feature) is a
        // deliberate, reviewed change rather than a silent behavior swing.
        Map<String, Object> overWire = roundTrip(Map.of("output", Map.of("name", "Oban", "age", 5.5)));

        EvalTestCase testCase = EvalTestCase.builder()
                .input("query", "describe the whisky")
                .expectedOutputs(overWire)
                .build();

        assertThat(testCase.expectedOutputAs(WhiskyRecord.class)).isEqualTo(new WhiskyRecord("Oban", 5));
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
