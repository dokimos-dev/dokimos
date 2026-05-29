package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.AnnotationRequest;
import dev.dokimos.server.dto.v1.AnnotationView;
import dev.dokimos.server.entity.Annotation;
import dev.dokimos.server.entity.ItemResult;
import dev.dokimos.server.repository.AnnotationRepository;
import dev.dokimos.server.repository.ItemResultRepository;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for human review annotations on run item results. Each item result holds at most one
 * annotation; {@link #upsert} creates it on first call and updates the same row thereafter.
 */
@Service
public class AnnotationService {

    private final AnnotationRepository annotationRepository;
    private final ItemResultRepository itemResultRepository;

    public AnnotationService(AnnotationRepository annotationRepository, ItemResultRepository itemResultRepository) {
        this.annotationRepository = annotationRepository;
        this.itemResultRepository = itemResultRepository;
    }

    /**
     * Creates or updates the single annotation for the given item result. The item result must exist
     * and belong to the given run. On an existing annotation this overwrites its fields and stamps
     * {@code updatedAt}; the {@code createdBy} principal is recorded only when the annotation is first
     * created.
     *
     * @throws IllegalArgumentException if the item result does not exist or does not belong to the run
     *     (mapped to 404)
     */
    @Transactional
    public AnnotationView upsert(UUID runId, UUID itemResultId, AnnotationRequest req, String principalId) {
        requireItemResultInRun(runId, itemResultId);

        Annotation annotation = annotationRepository
                .findByItemResultId(itemResultId)
                .orElseGet(() -> {
                    ItemResult itemResult = itemResultRepository.getReferenceById(itemResultId);
                    Annotation created = new Annotation(itemResult, req.verdict());
                    created.setCreatedBy(principalId);
                    return created;
                });

        annotation.setVerdict(req.verdict());
        annotation.setOverriddenExpectedOutput(copyNullable(req.overriddenExpectedOutput()));
        annotation.setNote(req.note());
        annotation.touchUpdatedAt();

        return AnnotationView.from(annotationRepository.save(annotation));
    }

    /**
     * Returns the annotation for the given run item result.
     *
     * @throws IllegalArgumentException if the item result does not belong to the run, or has no
     *     annotation (mapped to 404)
     */
    @Transactional(readOnly = true)
    public AnnotationView get(UUID runId, UUID itemResultId) {
        requireItemResultInRun(runId, itemResultId);
        return annotationRepository
                .findByItemResultId(itemResultId)
                .map(AnnotationView::from)
                .orElseThrow(
                        () -> new IllegalArgumentException("Annotation not found for item result: " + itemResultId));
    }

    /**
     * Removes the annotation for the given run item result if one exists. No-op when the item result
     * is un-annotated.
     *
     * @throws IllegalArgumentException if the item result does not belong to the run (mapped to 404)
     */
    @Transactional
    public void delete(UUID runId, UUID itemResultId) {
        requireItemResultInRun(runId, itemResultId);
        annotationRepository.deleteByItemResultId(itemResultId);
    }

    /**
     * Copies a request map into an owned, immutable map. Unlike {@code Map.copyOf}, this tolerates
     * null values, which are valid in a JSON expected output (e.g. {@code {"answer": null}}).
     */
    private static Map<String, Object> copyNullable(Map<String, Object> map) {
        return map == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    private void requireItemResultInRun(UUID runId, UUID itemResultId) {
        ItemResult itemResult = itemResultRepository
                .findById(itemResultId)
                .orElseThrow(() -> new IllegalArgumentException("Item result not found: " + itemResultId));
        if (!itemResult.getRun().getId().equals(runId)) {
            throw new IllegalArgumentException("Item result " + itemResultId + " does not belong to run " + runId);
        }
    }
}
