package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.AnnotationRequest;
import dev.dokimos.server.dto.v1.AnnotationView;
import dev.dokimos.server.entity.Annotation;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.ItemResult;
import dev.dokimos.server.repository.AnnotationRepository;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.repository.ItemResultRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for human review annotations on run item results. Each item result holds at most one
 * annotation; {@link #upsert} creates it on first call and updates the same row thereafter.
 *
 * <p>The run-membership gate is tenant-scoped. {@link #requireItemResultInRun} loads the run through the
 * scoped run finder rather than trusting the URL {@code runId}, so a tenant cannot annotate another
 * tenant's item by presenting a (foreign-run, foreign-item) pair: the foreign run is invisible and the
 * gate returns a 404.
 */
@Service
public class AnnotationService {

    private final AnnotationRepository annotationRepository;
    private final ItemResultRepository itemResultRepository;
    private final ExperimentRunRepository runRepository;

    public AnnotationService(
            AnnotationRepository annotationRepository,
            ItemResultRepository itemResultRepository,
            ExperimentRunRepository runRepository) {
        this.annotationRepository = annotationRepository;
        this.itemResultRepository = itemResultRepository;
        this.runRepository = runRepository;
    }

    /**
     * Creates or updates the single annotation for the given item result. The run must be visible under
     * the scope and the item result must belong to it. On an existing annotation this overwrites its
     * fields and stamps {@code updatedAt}; the {@code createdBy} principal is recorded only when the
     * annotation is first created. The new annotation is stamped with the run's tenant.
     *
     * @throws IllegalArgumentException if the run is not visible under the scope, or the item result does
     *     not exist or does not belong to the run (mapped to 404)
     */
    @Transactional
    public AnnotationView upsert(
            UUID runId, UUID itemResultId, AnnotationRequest req, String principalId, TenantScope scope) {
        ExperimentRun run = requireItemResultInRun(runId, itemResultId, scope);

        Annotation annotation = annotationRepository
                .findByItemResultId(itemResultId)
                .orElseGet(() -> {
                    ItemResult itemResult = itemResultRepository.getReferenceById(itemResultId);
                    Annotation created = new Annotation(itemResult, req.verdict());
                    created.setCreatedBy(principalId);
                    created.setTenantId(run.getTenantId());
                    return created;
                });

        annotation.setVerdict(req.verdict());
        annotation.setOverriddenExpectedOutput(copyNullable(req.overriddenExpectedOutput()));
        annotation.setNote(req.note());
        annotation.touchUpdatedAt();

        return AnnotationView.from(annotationRepository.save(annotation));
    }

    /**
     * Returns the annotation for the given run item result, scoped to the caller's tenant.
     *
     * @throws IllegalArgumentException if the run is not visible under the scope, the item result does
     *     not belong to the run, or it has no annotation (mapped to 404)
     */
    @Transactional(readOnly = true)
    public AnnotationView get(UUID runId, UUID itemResultId, TenantScope scope) {
        requireItemResultInRun(runId, itemResultId, scope);
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
     * @throws IllegalArgumentException if the run is not visible under the scope or the item result does
     *     not belong to the run (mapped to 404)
     */
    @Transactional
    public void delete(UUID runId, UUID itemResultId, TenantScope scope) {
        requireItemResultInRun(runId, itemResultId, scope);
        annotationRepository.deleteByItemResultId(itemResultId);
    }

    /**
     * Copies a request map into an owned, immutable map. Unlike {@code Map.copyOf}, this tolerates
     * null values, which are valid in a JSON expected output (e.g. {@code {"answer": null}}).
     */
    private static Map<String, Object> copyNullable(Map<String, Object> map) {
        return map == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    /**
     * Loads the run through the tenant-scoped finder, then verifies the item result belongs to it. The
     * scoped run load is the trust boundary: an item is reachable only through a run the caller can
     * actually see, closing the circular gate where the URL {@code runId} was trusted without a tenant
     * check.
     *
     * @return the loaded, visible run
     */
    private ExperimentRun requireItemResultInRun(UUID runId, UUID itemResultId, TenantScope scope) {
        ExperimentRun run = runRepository
                .findById(runId, scope)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
        ItemResult itemResult = itemResultRepository
                .findById(itemResultId)
                .orElseThrow(() -> new IllegalArgumentException("Item result not found: " + itemResultId));
        if (!itemResult.getRun().getId().equals(runId)) {
            throw new IllegalArgumentException("Item result " + itemResultId + " does not belong to run " + runId);
        }
        return run;
    }
}
