package dev.dokimos.server.dto.v1;

import dev.dokimos.server.entity.AnnotationVerdict;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Payload for creating or updating the annotation on a run item result.
 *
 * @param verdict the reviewer's verdict (required)
 * @param overriddenExpectedOutput a corrected expected output to record for later promotion, or null
 * @param note an optional free-text reviewer note (bounded to keep run-detail payloads sane)
 */
public record AnnotationRequest(
        @NotNull AnnotationVerdict verdict,
        Map<String, Object> overriddenExpectedOutput,
        @Size(max = 10_000) String note) {}
