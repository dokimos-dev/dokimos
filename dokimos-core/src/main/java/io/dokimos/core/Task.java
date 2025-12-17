package io.dokimos.core;

import java.util.Map;

@FunctionalInterface
public interface Task {

    /**
     * Run the task on the given {@link Example} and produce outputs.
     *
     * @param example the example containing inputs and expected outputs
     * @return the actual outputs produced by the task
     */
    Map<String, Object> run(Example example);

}
