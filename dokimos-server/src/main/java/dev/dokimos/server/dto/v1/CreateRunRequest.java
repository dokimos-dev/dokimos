package dev.dokimos.server.dto.v1;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.lang.NonNull;

public record CreateRunRequest(@NotBlank @NonNull String experimentName, Map<String, Object> metadata) {}
