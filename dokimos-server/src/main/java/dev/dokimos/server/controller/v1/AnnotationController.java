package dev.dokimos.server.controller.v1;

import dev.dokimos.server.dto.v1.AnnotationRequest;
import dev.dokimos.server.dto.v1.AnnotationView;
import dev.dokimos.server.filter.ApiKeyAuthFilter;
import dev.dokimos.server.filter.Principal;
import dev.dokimos.server.service.AnnotationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for the single human review annotation on a run item result. */
@RestController
@RequestMapping("/api/v1/runs/{runId}/items/{itemResultId}/annotation")
public class AnnotationController {

    private final AnnotationService annotationService;

    public AnnotationController(AnnotationService annotationService) {
        this.annotationService = annotationService;
    }

    /**
     * Creates or updates the annotation for the item result and returns it. Responds 200 on success,
     * 404 if the item result does not exist or does not belong to the run, and 400 if the verdict is
     * missing. The {@code created_by} field is taken from the authenticated principal when present.
     */
    @PutMapping
    public AnnotationView upsert(
            @PathVariable UUID runId,
            @PathVariable UUID itemResultId,
            @Valid @RequestBody AnnotationRequest request,
            HttpServletRequest http) {
        return annotationService.upsert(runId, itemResultId, request, currentPrincipalId(http));
    }

    /**
     * Returns the annotation for the item result. Responds 200 on success, or 404 if the item result
     * does not belong to the run or has no annotation.
     */
    @GetMapping
    public AnnotationView get(@PathVariable UUID runId, @PathVariable UUID itemResultId) {
        return annotationService.get(runId, itemResultId);
    }

    /**
     * Removes the annotation for the item result. Responds 204 on success, or 404 if the item result
     * does not belong to the run.
     */
    @DeleteMapping
    public ResponseEntity<Void> delete(@PathVariable UUID runId, @PathVariable UUID itemResultId) {
        annotationService.delete(runId, itemResultId);
        return ResponseEntity.noContent().build();
    }

    private static String currentPrincipalId(HttpServletRequest request) {
        Object attr = request.getAttribute(ApiKeyAuthFilter.PRINCIPAL_ATTRIBUTE);
        return attr instanceof Principal principal ? principal.id() : null;
    }
}
