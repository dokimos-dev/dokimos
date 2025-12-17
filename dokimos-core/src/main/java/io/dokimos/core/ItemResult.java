package io.dokimos.core;

import java.util.Map;
import java.util.List;

public record ItemResult(
    Example example,
    Map<String, Object> actualOutputs,
    List<EvalResult> evalResults
) {
    public ItemResult {
        actualOutputs = actualOutputs != null ? Map.copyOf(actualOutputs) : Map.of();
        evalResults = evalResults != null ? List.copyOf(evalResults) : List.of();
    }

    public boolean success() {
        return evalResults.stream().allMatch(EvalResult::success);
    }

    public EvalTestCase toTestCase() {
        return example.toTestCase(actualOutputs);
    }
}
