package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.AlignmentView;
import dev.dokimos.server.entity.Annotation;
import dev.dokimos.server.entity.AnnotationVerdict;
import dev.dokimos.server.entity.EvalResult;
import dev.dokimos.server.entity.ItemResult;
import dev.dokimos.server.repository.AnnotationRepository;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.repository.ItemResultRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes per-run, per-evaluator agreement between automated evaluator verdicts and human
 * annotations. A CORRECT verdict is treated as the item passing and INCORRECT as failing; an
 * evaluator result agrees when its success flag matches that expectation. UNSURE verdicts and items
 * with no annotation are excluded from the rate, and UNSURE counts are reported separately so the
 * caller can show how much of the run is still ambiguous.
 */
@Service
public class AlignmentService {

    private final ExperimentRunRepository runRepository;
    private final ItemResultRepository itemResultRepository;
    private final AnnotationRepository annotationRepository;

    public AlignmentService(
            ExperimentRunRepository runRepository,
            ItemResultRepository itemResultRepository,
            AnnotationRepository annotationRepository) {
        this.runRepository = runRepository;
        this.itemResultRepository = itemResultRepository;
        this.annotationRepository = annotationRepository;
    }

    /**
     * Computes the judge-human alignment breakdown for a run. Items are loaded with their eval
     * results in one query and their annotations batch-loaded by item id to avoid an N+1 fan-out.
     * Each evaluator's rate uses only items it ran on whose human verdict was CORRECT or INCORRECT.
     *
     * @param runId the run to analyze
     * @return the per-evaluator agreement breakdown, ordered by first appearance of each evaluator
     * @throws IllegalArgumentException if {@code runId} is null or the run does not exist
     */
    @Transactional(readOnly = true)
    public AlignmentView getAlignment(UUID runId, TenantScope scope) {
        if (runId == null) {
            throw new IllegalArgumentException("Run ID cannot be null");
        }
        if (runRepository.findById(runId, scope).isEmpty()) {
            throw new IllegalArgumentException("Run not found: " + runId);
        }

        List<ItemResult> items = itemResultRepository.findByRunIdWithEvals(runId);
        Map<UUID, AnnotationVerdict> verdictsByItemId = loadVerdicts(items);

        Map<String, Tally> tallies = new LinkedHashMap<>();
        for (ItemResult item : items) {
            AnnotationVerdict verdict = verdictsByItemId.get(item.getId());
            if (verdict == null) {
                continue;
            }
            for (EvalResult eval : item.getEvalResults()) {
                Tally tally = tallies.computeIfAbsent(eval.getEvaluatorName(), name -> new Tally());
                if (verdict == AnnotationVerdict.UNSURE) {
                    tally.excludedUnsure++;
                    continue;
                }
                boolean expectedPass = verdict == AnnotationVerdict.CORRECT;
                tally.comparableCount++;
                if (eval.isSuccess() == expectedPass) {
                    tally.agreedCount++;
                }
            }
        }

        List<AlignmentView.EvaluatorAlignment> evaluators = new ArrayList<>(tallies.size());
        for (Map.Entry<String, Tally> entry : tallies.entrySet()) {
            Tally tally = entry.getValue();
            Double rate = tally.comparableCount > 0 ? (double) tally.agreedCount / tally.comparableCount : null;
            evaluators.add(new AlignmentView.EvaluatorAlignment(
                    entry.getKey(), tally.comparableCount, tally.agreedCount, tally.excludedUnsure, rate));
        }

        long annotatedItems = items.stream()
                .filter(item -> verdictsByItemId.containsKey(item.getId())
                        && !item.getEvalResults().isEmpty())
                .count();
        return new AlignmentView((int) annotatedItems, evaluators);
    }

    /**
     * Batch-loads the human verdict for each annotated item in one query, keyed by item result id.
     * Replicated rather than shared with {@code RunService} so the two read paths stay independent.
     */
    private Map<UUID, AnnotationVerdict> loadVerdicts(List<ItemResult> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = items.stream().map(ItemResult::getId).toList();
        Map<UUID, AnnotationVerdict> byItemId = new HashMap<>();
        for (Annotation annotation : annotationRepository.findByItemResultIdIn(ids)) {
            byItemId.put(annotation.getItemResult().getId(), annotation.getVerdict());
        }
        return byItemId;
    }

    private static final class Tally {
        private int comparableCount;
        private int agreedCount;
        private int excludedUnsure;
    }
}
