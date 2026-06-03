package dev.dokimos.core;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@FunctionalInterface
public interface Task {

    /**
     * Run the task on the given {@link Example} and produce outputs.
     *
     * @param example the example containing inputs and expected outputs
     * @return the actual outputs produced by the task
     */
    Map<String, Object> run(Example example);

    /**
     * Creates a {@link Task} that produces a single typed value and stores it under the
     * conventional {@code "output"} key, so callers can return a record, list, or other POJO from
     * their task body instead of hand-building the output map.
     * <p>
     * The produced value is read back type-safely via {@code actualOutputAs(...)} on the resulting
     * {@code EvalTestCase} and is matched structurally by {@code StructuralMatchEvaluator}.
     * <p>
     * <strong>Map guard:</strong> if {@code fn} itself returns a {@link Map}, that map is used
     * directly as the output map rather than being nested under {@code "output"}. The map is assumed
     * to use {@code String} keys.
     *
     * @param fn the function producing the output value for an {@link Example} (may return a
     *     {@link Map}, which is used as the output map directly)
     * @param <T> the produced value type
     * @return a {@link Task} that wraps the produced value under {@code "output"} (or uses a returned
     *     map directly)
     * @throws NullPointerException if {@code fn} returns {@code null} (the output map cannot hold a
     *     null value; use a raw {@link Task} if an absent output is intended)
     */
    @SuppressWarnings("unchecked")
    static <T> Task typed(Function<Example, T> fn) {
        return example -> {
            T value = fn.apply(example);
            if (value instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            Objects.requireNonNull(
                    value,
                    "typed task function returned null; return a value (or a Map) — use a raw Task if you need an absent output");
            return Map.of("output", value);
        };
    }
}
