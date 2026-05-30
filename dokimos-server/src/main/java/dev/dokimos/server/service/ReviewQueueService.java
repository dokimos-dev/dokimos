package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.ReviewQueueItem;
import dev.dokimos.server.dto.v1.RunDetails;
import dev.dokimos.server.entity.Annotation;
import dev.dokimos.server.entity.AnnotationVerdict;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.ItemResult;
import dev.dokimos.server.repository.AnnotationRepository;
import dev.dokimos.server.repository.ItemResultRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Surfaces run items that still need a human verdict so a reviewer can work through a single queue
 * instead of opening runs one at a time. An item qualifies when it has no annotation or only an
 * {@code UNSURE} one; the queue can be scoped to a project, experiment, or run.
 */
@Service
public class ReviewQueueService {

    private final ItemResultRepository itemResultRepository;
    private final AnnotationRepository annotationRepository;

    public ReviewQueueService(ItemResultRepository itemResultRepository, AnnotationRepository annotationRepository) {
        this.itemResultRepository = itemResultRepository;
        this.annotationRepository = annotationRepository;
    }

    /**
     * Returns a page of items awaiting review, oldest first. Eval results and any existing
     * {@code UNSURE} verdict are batch-loaded for the page so rendering never fans out into a query per
     * item.
     *
     * @param projectName  restrict to this project, or null for any
     * @param experimentId restrict to this experiment, or null for any
     * @param runId        restrict to this run, or null for any
     * @param pageable     the page to return
     * @return the page of review items
     */
    @Transactional(readOnly = true)
    public Page<ReviewQueueItem> list(String projectName, UUID experimentId, UUID runId, Pageable pageable) {
        Page<ItemResult> page = itemResultRepository.findItemsNeedingReview(projectName, experimentId, runId, pageable);

        Map<UUID, AnnotationVerdict> verdictByItem = new HashMap<>();
        List<ItemResult> items = page.getContent();
        if (!items.isEmpty()) {
            List<UUID> ids = items.stream().map(ItemResult::getId).toList();
            itemResultRepository.findAllWithEvalsByIdIn(ids);
            for (Annotation annotation : annotationRepository.findByItemResultIdIn(ids)) {
                verdictByItem.put(annotation.getItemResult().getId(), annotation.getVerdict());
            }
        }

        return page.map(item -> toReviewQueueItem(item, verdictByItem.get(item.getId())));
    }

    private ReviewQueueItem toReviewQueueItem(ItemResult item, AnnotationVerdict currentVerdict) {
        ExperimentRun run = item.getRun();
        Experiment experiment = run.getExperiment();
        List<RunDetails.EvalSummary> evalSummaries = item.getEvalResults().stream()
                .map(e -> new RunDetails.EvalSummary(
                        e.getId(), e.getEvaluatorName(), e.getScore(), e.getThreshold(), e.isSuccess(), e.getReason()))
                .toList();

        return new ReviewQueueItem(
                item.getId(),
                run.getId(),
                experiment.getId(),
                experiment.getName(),
                experiment.getProject().getName(),
                item.getInput(),
                item.getExpectedOutput(),
                item.getActualOutput(),
                evalSummaries,
                currentVerdict,
                item.getCreatedAt());
    }
}
