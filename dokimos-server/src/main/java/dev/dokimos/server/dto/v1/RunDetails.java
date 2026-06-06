package dev.dokimos.server.dto.v1;

import dev.dokimos.server.entity.RunStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;

/**
 * Detail view of a single run. The two coverage counts let the UI flag partial pricing: when
 * {@code pricedItemCount < tokenizedItemCount} the summed {@code totalCostUsd} omits the unpriced
 * items. Both are nullable and additive; older clients ignore them.
 *
 * @param pricedItemCount    items that carried a non-null cost, or null if not computed
 * @param tokenizedItemCount items that carried a non-null prompt-token count, or null if not computed
 */
public record RunDetails(
        UUID id,
        UUID experimentId,
        String experimentName,
        String projectName,
        RunStatus status,
        Map<String, Object> config,
        long totalItems,
        long passedItems,
        Double passRate,
        Instant startedAt,
        Instant completedAt,
        UUID datasetVersionId,
        Integer datasetVersion,
        Long totalTokensIn,
        Long totalTokensOut,
        Double totalCostUsd,
        Double avgLatencyMs,
        Long pricedItemCount,
        Long tokenizedItemCount,
        Page<ItemSummary> items) {
    public record ItemSummary(
            UUID id,
            Map<String, Object> input,
            Map<String, Object> expectedOutput,
            Map<String, Object> actualOutput,
            Map<String, Object> metadata,
            List<EvalSummary> evalResults,
            Instant createdAt,
            UUID datasetItemId,
            AnnotationView annotation,
            Integer tokensIn,
            Integer tokensOut,
            Double costUsd,
            Long latencyMs) {}

    public record EvalSummary(
            UUID id, String evaluatorName, double score, Double threshold, boolean success, String reason) {}
}
