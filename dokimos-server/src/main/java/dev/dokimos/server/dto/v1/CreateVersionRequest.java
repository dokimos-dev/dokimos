package dev.dokimos.server.dto.v1;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record CreateVersionRequest(
        String description, @NotEmpty @Valid List<ItemPayload> items) {

    public record ItemPayload(
            @NotNull Map<String, Object> inputs, Map<String, Object> expectedOutputs, Map<String, Object> metadata) {}
}
