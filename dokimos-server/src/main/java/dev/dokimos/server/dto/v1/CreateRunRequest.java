package dev.dokimos.server.dto.v1;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record CreateRunRequest(
        @NotBlank String experimentName,
        Map<String, Object> metadata
) {
}
