package dev.dokimos.server.dto.v1;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AddItemsRequest(@NotEmpty List<ItemData> items) {
    public record ItemData(
            Map<String, Object> inputs,
            Map<String, Object> expectedOutputs,
            Map<String, Object> actualOutputs,
            Map<String, Object> metadata,
            List<EvalData> evalResults,
            boolean success,
            UUID datasetItemId) {
        /** Backwards-compatible 6-arg constructor: passes null datasetItemId. */
        public ItemData(
                Map<String, Object> inputs,
                Map<String, Object> expectedOutputs,
                Map<String, Object> actualOutputs,
                Map<String, Object> metadata,
                List<EvalData> evalResults,
                boolean success) {
            this(inputs, expectedOutputs, actualOutputs, metadata, evalResults, success, null);
        }

        /** Backwards-compatible 5-arg constructor: passes null metadata and null datasetItemId. */
        public ItemData(
                Map<String, Object> inputs,
                Map<String, Object> expectedOutputs,
                Map<String, Object> actualOutputs,
                List<EvalData> evalResults,
                boolean success) {
            this(inputs, expectedOutputs, actualOutputs, null, evalResults, success, null);
        }
    }

    public record EvalData(
            String name,
            double score,
            Double threshold,
            boolean success,
            String reason,
            Map<String, Object> metadata) {}
}
