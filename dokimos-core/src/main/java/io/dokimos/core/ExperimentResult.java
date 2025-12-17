package io.dokimos.core;

import java.util.List;
import java.util.Map;

public record ExperimentResult(
        String name,
        String description,
        Map<String, Object> metadata,
        List<ItemResult> itemResults
) {
    public ExperimentResult {
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        itemResults = itemResults != null ? List.copyOf(itemResults) : List.of();
    }

    public int totalCount() {
        return itemResults.size();
    }

    public long passCount() {
        return itemResults.stream().filter(ItemResult::success).count();
    }

    public long failCount() {
        return totalCount() - passCount();
    }

    public double passRate() {
        return totalCount() > 0 ? (double) passCount() / totalCount() : 0.0;
    }

    public double averageScore(String evaluatorName) {
        return itemResults.stream()
                .flatMap(item -> item.evalResults().stream())
                .filter(res -> res.name().equals(evaluatorName))
                .mapToDouble(EvalResult::score)
                .average()
                .orElse(0.0);
    }

}
