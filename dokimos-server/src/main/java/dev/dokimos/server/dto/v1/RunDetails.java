package dev.dokimos.server.dto.v1;

import dev.dokimos.server.entity.RunStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;

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
        Page<ItemSummary> items) {
    public record ItemSummary(
            UUID id,
            Map<String, Object> input,
            Map<String, Object> expectedOutput,
            Map<String, Object> actualOutput,
            Map<String, Object> metadata,
            List<EvalSummary> evalResults,
            Instant createdAt) {}

    public record EvalSummary(
            UUID id, String evaluatorName, double score, Double threshold, boolean success, String reason) {}
}
