package dev.dokimos.server.dto.v1;

import dev.dokimos.server.entity.Annotation;
import dev.dokimos.server.entity.AnnotationVerdict;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Read model for an annotation on a run item result. */
public record AnnotationView(
        UUID id,
        AnnotationVerdict verdict,
        Map<String, Object> overriddenExpectedOutput,
        String note,
        String createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public static AnnotationView from(Annotation annotation) {
        return new AnnotationView(
                annotation.getId(),
                annotation.getVerdict(),
                annotation.getOverriddenExpectedOutput(),
                annotation.getNote(),
                annotation.getCreatedBy(),
                annotation.getCreatedAt(),
                annotation.getUpdatedAt());
    }
}
