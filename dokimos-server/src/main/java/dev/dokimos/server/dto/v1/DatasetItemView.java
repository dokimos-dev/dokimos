package dev.dokimos.server.dto.v1;

import java.util.Map;
import java.util.UUID;

public record DatasetItemView(
        UUID id,
        int ordinal,
        Map<String, Object> inputs,
        Map<String, Object> expectedOutputs,
        Map<String, Object> metadata) {}
