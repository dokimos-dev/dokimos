package dev.dokimos.server.dto.v1;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

public record AddItemsRequest(
                @NotEmpty List<ItemData> items) {
        public record ItemData(
                        Map<String, Object> inputs,
                        Map<String, Object> expectedOutputs,
                        Map<String, Object> actualOutputs,
                        List<EvalData> evalResults,
                        boolean success) {
        }

        public record EvalData(
                        String name,
                        double score,
                        Double threshold,
                        boolean success,
                        String reason,
                        Map<String, Object> metadata) {
        }
}
