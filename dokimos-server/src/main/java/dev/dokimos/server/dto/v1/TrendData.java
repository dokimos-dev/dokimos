package dev.dokimos.server.dto.v1;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TrendData(
                String experimentName,
                List<RunPoint> runs) {
        public record RunPoint(
                        UUID runId,
                        Instant startedAt,
                        double passRate,
                        long totalItems,
                        long passedItems) {
        }
}
