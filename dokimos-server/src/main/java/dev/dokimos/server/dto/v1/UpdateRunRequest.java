package dev.dokimos.server.dto.v1;

import dev.dokimos.server.entity.RunStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateRunRequest(
                @NotNull RunStatus status) {
}
