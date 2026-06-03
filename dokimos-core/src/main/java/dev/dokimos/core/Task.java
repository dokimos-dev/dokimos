package dev.dokimos.core;

import java.util.Map;
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
     * directly as the output map rather than being nested under {@code "output"}. This lets a task
     * that already speaks the multi-key output convention coexist with the typed convenience without
     * accidental double-nesting. The map is assumed to use {@code String} keys.
     *
     * @param fn the function producing the output value for an {@link Example} (may return a
     *     {@link Map}, which is used as the output map directly)
     * @param <T> the produced value type
     * @return a {@link Task} that wraps the produced value under {@code "output"} (or uses a returned
     *     map directly)
     */
    @SuppressWarnings("unchecked")
    static <T> Task typed(Function<Example, T> fn) {
        return example -> {
            T value = fn.apply(example);
            if (value instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return Map.of("output", value);
        };
    }
}
