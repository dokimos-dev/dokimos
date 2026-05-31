package dev.dokimos.core.evaluators.agents;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default {@link ArgumentMatcher} that compares arguments structurally with a few
 * deliberate tolerances.
 * <p>
 * Value comparison rules:
 * <ul>
 *   <li><b>Numbers</b> are compared by numeric value, so {@code 1}, {@code 1.0} and
 *       {@code 1L} are equal. This is always on, because exact {@code Map.equals}
 *       treating {@code 1} and {@code 1.0} as different is a JSON-widening artifact,
 *       not intended behavior.</li>
 *   <li><b>Strings</b> are compared exactly by default. {@link Builder#trimStrings(boolean)}
 *       and {@link Builder#caseInsensitive(boolean)} relax this, but both are off by
 *       default so existing pass/fail outcomes are never silently changed.</li>
 *   <li><b>Maps and lists</b> are compared recursively using the same rules.</li>
 * </ul>
 * Key-set handling follows the configured {@link ArgMatchMode}.
 *
 * <pre>{@code
 * ArgumentMatcher matcher = TolerantArgumentMatcher.builder()
 *         .mode(ArgMatchMode.SUBSET)   // only expected keys must be present and correct
 *         .trimStrings(true)
 *         .build();
 * }</pre>
 */
public final class TolerantArgumentMatcher implements ArgumentMatcher {

    private final ArgMatchMode mode;
    private final boolean trimStrings;
    private final boolean caseInsensitive;

    private TolerantArgumentMatcher(Builder builder) {
        this.mode = builder.mode;
        this.trimStrings = builder.trimStrings;
        this.caseInsensitive = builder.caseInsensitive;
    }

    /**
     * Creates a new builder for constructing the matcher.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean matches(Map<String, Object> expected, Map<String, Object> actual) {
        Map<String, Object> exp = expected != null ? expected : Map.of();
        Map<String, Object> act = actual != null ? actual : Map.of();

        return switch (mode) {
            case IGNORE -> true;
            case EXACT -> exp.keySet().equals(act.keySet()) && allEntriesMatch(exp, act);
            case SUBSET -> act.keySet().containsAll(exp.keySet()) && allEntriesMatch(exp, act);
            case SUPERSET -> exp.keySet().containsAll(act.keySet()) && allEntriesMatch(act, exp);
        };
    }

    /**
     * Returns true when every entry in {@code lhs} has a value-equal entry in {@code rhs}.
     */
    private boolean allEntriesMatch(Map<String, Object> lhs, Map<String, Object> rhs) {
        for (Map.Entry<String, Object> entry : lhs.entrySet()) {
            if (!rhs.containsKey(entry.getKey())) {
                return false;
            }
            if (!valuesEqual(entry.getValue(), rhs.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean valuesEqual(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a instanceof Number na && b instanceof Number nb) {
            return numbersEqual(na, nb);
        }
        if (a instanceof String sa && b instanceof String sb) {
            return stringsEqual(sa, sb);
        }
        if (a instanceof Map<?, ?> ma && b instanceof Map<?, ?> mb) {
            return mapsEqual((Map<String, Object>) ma, (Map<String, Object>) mb);
        }
        if (a instanceof List<?> la && b instanceof List<?> lb) {
            return listsEqual(la, lb);
        }
        return Objects.equals(a, b);
    }

    private boolean numbersEqual(Number a, Number b) {
        if (isNonFinite(a) || isNonFinite(b)) {
            // BigDecimal cannot parse "NaN" / "Infinity"; compare these directly.
            return Double.compare(a.doubleValue(), b.doubleValue()) == 0;
        }
        return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
    }

    private boolean isNonFinite(Number n) {
        return (n instanceof Double d && !Double.isFinite(d)) || (n instanceof Float f && !Float.isFinite(f));
    }

    private boolean stringsEqual(String a, String b) {
        String left = trimStrings ? a.trim() : a;
        String right = trimStrings ? b.trim() : b;
        return caseInsensitive ? left.equalsIgnoreCase(right) : left.equals(right);
    }

    private boolean mapsEqual(Map<String, Object> a, Map<String, Object> b) {
        if (!a.keySet().equals(b.keySet())) {
            return false;
        }
        for (Map.Entry<String, Object> entry : a.entrySet()) {
            if (!valuesEqual(entry.getValue(), b.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private boolean listsEqual(List<?> a, List<?> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!valuesEqual(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Builder for constructing the matcher.
     */
    public static final class Builder {
        private ArgMatchMode mode = ArgMatchMode.EXACT;
        private boolean trimStrings = false;
        private boolean caseInsensitive = false;

        /**
         * Sets the key-set comparison mode.
         *
         * @param mode the mode (default {@link ArgMatchMode#EXACT})
         * @return this builder
         */
        public Builder mode(ArgMatchMode mode) {
            this.mode = mode;
            return this;
        }

        /**
         * Trims leading and trailing whitespace from strings before comparison.
         *
         * @param trimStrings true to trim strings (default false)
         * @return this builder
         */
        public Builder trimStrings(boolean trimStrings) {
            this.trimStrings = trimStrings;
            return this;
        }

        /**
         * Compares strings case-insensitively.
         *
         * @param caseInsensitive true for case-insensitive comparison (default false)
         * @return this builder
         */
        public Builder caseInsensitive(boolean caseInsensitive) {
            this.caseInsensitive = caseInsensitive;
            return this;
        }

        /**
         * Builds the matcher.
         *
         * @return a new matcher
         */
        public TolerantArgumentMatcher build() {
            return new TolerantArgumentMatcher(this);
        }
    }
}
