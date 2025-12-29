package dev.dokimos.server.dto.v1;

import jakarta.validation.constraints.NotBlank;
import org.springframework.lang.NonNull;

import java.util.Map;

public record CreateRunRequest(
                @NotBlank @NonNull String experimentName,
                Map<String, Object> metadata) {
}
