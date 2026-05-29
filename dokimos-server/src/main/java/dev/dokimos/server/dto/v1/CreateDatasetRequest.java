package dev.dokimos.server.dto.v1;

import jakarta.validation.constraints.NotBlank;

public record CreateDatasetRequest(@NotBlank String name, String description) {}
