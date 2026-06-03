package dev.dokimos.core.evaluators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import dev.dokimos.core.BaseEvaluator;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.internal.Json;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Compares the actual output against the expected output as JSON structures rather than as opaque
 * strings.
 *
 * <p>Both operands are normalized to a Jackson {@link JsonNode} tree and compared with a hand-written
 * recursive comparator (Jackson's {@link JsonNode#equals(Object)} is deliberately <em>not</em> used —
 * it treats {@code 5} and {@code 5.0} as different and would corrupt eval results). The comparator:
 *
 * <ul>
 *   <li>Compares numeric leaves by {@link BigDecimal#compareTo(BigDecimal)} (scale-insensitive but
 *       otherwise exact, so {@code 1.0} equals {@code 1.00} and {@code 5} equals {@code 5.0}, while
 *       genuinely distinct values never collapse). Matches by value in <em>both</em> modes.
 *   <li>Honors {@link StructuralMatchMode}: {@link StructuralMatchMode#STRICT STRICT} requires the
 *       exact field set and exact array order; {@link StructuralMatchMode#LENIENT LENIENT} allows
 *       extra actual fields (subset match) and ignores array order as a multiset.
 *   <li>Treats a {@code null} value and a missing field as equal in LENIENT and distinct in STRICT.
 *   <li>Fails loudly on duplicate object keys (via the strict-duplicate-detection comparison reader)
 *       and on non-finite numbers (NaN / Infinity).
 *   <li>Parses a {@code String} operand as JSON before comparing, so CSV / JSON-string datasets
 *       compare object-against-object rather than against a quoted string.
 * </ul>
 *
 * <p><strong>Scoring.</strong> By default the score is the fraction of matching leaf paths in
 * {@code [0.0, 1.0]}. In STRICT the denominator is the union of the expected and actual leaf paths
 * (extra fields penalize); in LENIENT the denominator is the expected leaf paths only (extras are
 * ignored). Calling {@link Builder#binary()} collapses the score to {@code 1.0} (everything matches)
 * or {@code 0.0} (anything differs) for exact-contract gates.
 */
public class StructuralMatchEvaluator extends BaseEvaluator {

    private final String outputKey;
    private final StructuralMatchMode mode;
    private final boolean binary;

    private StructuralMatchEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        this.outputKey = builder.outputKey;
        this.mode = builder.mode;
        this.binary = builder.binary;
    }

    /**
     * Creates a new builder for constructing structural match evaluators.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        Object rawExpected = testCase.expectedOutputs().get(outputKey);
        if (rawExpected == null) {
            throw new EvaluationException(
                    "StructuralMatchEvaluator requires '%s' in expectedOutputs".formatted(outputKey));
        }
        Object rawActual = testCase.actualOutputs().get(outputKey);

        JsonNode expected = normalize(rawExpected, "expected");
        JsonNode actual = normalize(rawActual, "actual");

        Diff diff = new Diff();
        compare(expected, actual, "$", diff);

        double score;
        if (binary) {
            score = diff.differences.isEmpty() ? 1.0 : 0.0;
        } else {
            score = diff.denominator == 0 ? 1.0 : (double) diff.matched / diff.denominator;
        }

        String reason;
        if (diff.differences.isEmpty()) {
            reason = "The actual and expected structures match.";
        } else {
            reason = "Structural differences (%d of %d leaf paths matched): %s"
                    .formatted(diff.matched, diff.denominator, String.join("; ", diff.differences));
        }

        return EvalResult.builder()
                .name(name)
                .score(score)
                .threshold(threshold)
                .reason(reason)
                .build();
    }

    /**
     * Normalizes an operand into a {@link JsonNode}. A {@code String} is parsed as JSON (using the
     * strict-duplicate-detection comparison reader); any other value is converted via the shared
     * mapper. A {@code null} operand becomes a JSON null node. Non-finite numbers are rejected.
     */
    private JsonNode normalize(Object value, String side) {
        JsonNode node;
        if (value == null) {
            node = Json.toNode(null);
        } else if (value instanceof String s) {
            try {
                node = Json.comparisonReader().readTree(s);
            } catch (JsonProcessingException e) {
                throw new EvaluationException(
                        "The %s output is not valid JSON: %s".formatted(side, e.getOriginalMessage()), e);
            }
            if (node == null) {
                node = Json.toNode(null);
            }
        } else {
            try {
                node = Json.toNode(value);
            } catch (RuntimeException e) {
                throw new EvaluationException(
                        "The %s output could not be normalized to JSON: %s".formatted(side, e.getMessage()), e);
            }
        }
        rejectNonFinite(node, side);
        return node;
    }

    private void rejectNonFinite(JsonNode node, String side) {
        if (node.isNumber()) {
            if ((node.isDouble() || node.isFloat()) && !Double.isFinite(node.doubleValue())) {
                throw new EvaluationException(
                        "The %s output contains a non-finite number (NaN or Infinity), which cannot be compared structurally."
                                .formatted(side));
            }
        } else if (node.isObject()) {
            Iterator<JsonNode> it = node.elements();
            while (it.hasNext()) {
                rejectNonFinite(it.next(), side);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                rejectNonFinite(child, side);
            }
        }
    }

    /**
     * Recursively compares {@code expected} against {@code actual} at the given JSON path, recording
     * matched / denominator leaf counts and human-readable differences into {@code diff}.
     */
    private void compare(JsonNode expected, JsonNode actual, String path, Diff diff) {
        // null-vs-missing: a Jackson null node vs a missing node.
        boolean expectedMissing = expected == null || expected.isMissingNode();
        boolean actualMissing = actual == null || actual.isMissingNode();

        if (expected != null && expected.isObject() && actual != null && actual.isObject()) {
            compareObjects(expected, actual, path, diff);
            return;
        }
        if (expected != null && expected.isArray() && actual != null && actual.isArray()) {
            compareArrays(expected, actual, path, diff);
            return;
        }

        // Leaf comparison (scalars, null, or type mismatches such as object-vs-array).
        diff.denominator++;
        if (expectedMissing && actualMissing) {
            diff.matched++;
            return;
        }

        // null and missing: in LENIENT a null value and a missing field are equal; in STRICT every
        // distinction matters (null != missing, and a present value != null/missing).
        boolean expectedNull = expected != null && expected.isNull();
        boolean actualNull = actual != null && actual.isNull();
        boolean expectedAbsent = expectedMissing || expectedNull;
        boolean actualAbsent = actualMissing || actualNull;
        if (expectedAbsent || actualAbsent) {
            boolean match;
            if (mode == StructuralMatchMode.LENIENT) {
                match = expectedAbsent && actualAbsent;
            } else {
                // STRICT: null matches null, missing matches missing, but not across.
                match = (expectedNull && actualNull) || (expectedMissing && actualMissing);
            }
            if (match) {
                diff.matched++;
            } else {
                diff.differences.add("%s: expected %s but was %s".formatted(path, render(expected), render(actual)));
            }
            return;
        }

        if (leafEquals(expected, actual)) {
            diff.matched++;
        } else {
            diff.differences.add("%s: expected %s but was %s".formatted(path, render(expected), render(actual)));
        }
    }

    private void compareObjects(JsonNode expected, JsonNode actual, String path, Diff diff) {
        Set<String> fields = new LinkedHashSet<>();
        expected.fieldNames().forEachRemaining(fields::add);
        // In STRICT, an extra field in the actual output is part of the comparison (it penalizes).
        if (mode == StructuralMatchMode.STRICT) {
            actual.fieldNames().forEachRemaining(fields::add);
        }

        for (String field : fields) {
            JsonNode expectedChild = expected.get(field);
            JsonNode actualChild = actual.get(field);
            compare(expectedChild, actualChild, path + "." + field, diff);
        }
    }

    private void compareArrays(JsonNode expected, JsonNode actual, String path, Diff diff) {
        if (mode == StructuralMatchMode.STRICT) {
            int max = Math.max(expected.size(), actual.size());
            for (int i = 0; i < max; i++) {
                JsonNode expectedChild = i < expected.size() ? expected.get(i) : null;
                JsonNode actualChild = i < actual.size() ? actual.get(i) : null;
                compare(expectedChild, actualChild, path + "[" + i + "]", diff);
            }
            return;
        }

        // LENIENT: multiset semantics — order ignored, multiplicity preserved, extras ignored. Use
        // maximum bipartite matching (not greedy first-fit) so a subset element cannot steal the
        // match a more specific element needs: e.g. expected [{a:1},{a:1,b:2}] against actual
        // [{a:1,b:2},{a:1}] pairs fully instead of scoring a false partial.
        int n = expected.size();
        int m = actual.size();
        List<List<Integer>> candidates = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            List<Integer> matches = new ArrayList<>();
            for (int j = 0; j < m; j++) {
                if (deepMatches(expected.get(i), actual.get(j))) {
                    matches.add(j);
                }
            }
            candidates.add(matches);
        }

        int[] actualMatchedBy = new int[m];
        Arrays.fill(actualMatchedBy, -1);
        boolean[] expectedMatched = new boolean[n];
        for (int i = 0; i < n; i++) {
            expectedMatched[i] = augment(i, candidates, actualMatchedBy, new boolean[m]);
        }

        for (int i = 0; i < n; i++) {
            int leaves = leafCount(expected.get(i));
            diff.denominator += leaves;
            if (expectedMatched[i]) {
                diff.matched += leaves;
            } else {
                diff.differences.add("%s: no actual element matched expected %s"
                        .formatted(path + "[" + i + "]", render(expected.get(i))));
            }
        }
    }

    /** Kuhn's augmenting-path step for maximum bipartite matching of expected to actual elements. */
    private boolean augment(int i, List<List<Integer>> candidates, int[] actualMatchedBy, boolean[] seen) {
        for (int j : candidates.get(i)) {
            if (seen[j]) {
                continue;
            }
            seen[j] = true;
            if (actualMatchedBy[j] == -1 || augment(actualMatchedBy[j], candidates, actualMatchedBy, seen)) {
                actualMatchedBy[j] = i;
                return true;
            }
        }
        return false;
    }

    /** Whether two nodes match completely under the current mode (used for LENIENT array matching). */
    private boolean deepMatches(JsonNode a, JsonNode b) {
        Diff probe = new Diff();
        compare(a, b, "$", probe);
        return probe.differences.isEmpty();
    }

    /** Compares two present, non-null scalar nodes by value (null/missing handled by the caller). */
    private boolean leafEquals(JsonNode expected, JsonNode actual) {
        if (expected.isNumber() && actual.isNumber()) {
            // Scale-insensitive but exact: 5 == 5.0 and 1.0 == 1.00, while genuinely distinct values
            // (including tiny near-zero ones) never collapse.
            return expected.decimalValue().compareTo(actual.decimalValue()) == 0;
        }
        if (expected.getNodeType() != actual.getNodeType()) {
            return false;
        }
        return switch (expected.getNodeType()) {
            case STRING -> expected.textValue().equals(actual.textValue());
            case BOOLEAN -> expected.booleanValue() == actual.booleanValue();
            default -> expected.equals(actual);
        };
    }

    /** Counts the leaf paths under a node (scalars and empty containers each count as one). */
    private int leafCount(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return 1;
        }
        if (node.isObject()) {
            if (node.isEmpty()) {
                return 1;
            }
            int count = 0;
            for (JsonNode child : node) {
                count += leafCount(child);
            }
            return count;
        }
        if (node.isArray()) {
            if (node.isEmpty()) {
                return 1;
            }
            int count = 0;
            for (JsonNode child : node) {
                count += leafCount(child);
            }
            return count;
        }
        return 1;
    }

    private String render(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return "<missing>";
        }
        if (node.getNodeType() == JsonNodeType.NULL) {
            return "null";
        }
        return node.toString();
    }

    /** Accumulates the running comparison result. */
    private static final class Diff {
        private int matched;
        private int denominator;
        private final List<String> differences = new ArrayList<>();
    }

    /**
     * Builder for constructing structural match evaluators.
     */
    public static class Builder {
        private String name = "Structural Match";
        private double threshold = 1.0;
        private List<EvalTestCaseParam> evaluationParams = List.of();
        private String outputKey = "output";
        private StructuralMatchMode mode = StructuralMatchMode.STRICT;
        private boolean binary = false;

        /**
         * Sets the evaluator name.
         *
         * @param name the evaluator name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the minimum score threshold for success.
         *
         * @param threshold the threshold value
         * @return this builder
         */
        public Builder threshold(double threshold) {
            this.threshold = threshold;
            return this;
        }

        /**
         * Sets which test case parameters to validate.
         *
         * @param params the parameters
         * @return this builder
         */
        public Builder evaluationParams(List<EvalTestCaseParam> params) {
            this.evaluationParams = List.copyOf(params);
            return this;
        }

        /**
         * Sets the map key used to read the value from both the actual and expected output maps.
         * Defaults to {@code "output"}.
         *
         * @param outputKey the output map key
         * @return this builder
         */
        public Builder outputKey(String outputKey) {
            this.outputKey = outputKey;
            return this;
        }

        /**
         * Sets the comparison mode. Defaults to {@link StructuralMatchMode#STRICT}.
         *
         * @param mode the comparison mode
         * @return this builder
         */
        public Builder mode(StructuralMatchMode mode) {
            this.mode = mode;
            return this;
        }

        /**
         * Switches scoring to binary: {@code 1.0} when the structures match completely, {@code 0.0}
         * otherwise. Without this, the score is the fraction of matching leaf paths.
         *
         * @return this builder
         */
        public Builder binary() {
            this.binary = true;
            return this;
        }

        /**
         * Builds the evaluator.
         *
         * @return a new evaluator
         */
        public StructuralMatchEvaluator build() {
            return new StructuralMatchEvaluator(this);
        }
    }
}
