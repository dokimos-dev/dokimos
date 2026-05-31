package dev.dokimos.server.service;

import dev.dokimos.core.comparison.ComparisonStatus;
import dev.dokimos.core.comparison.EvaluatorDelta;
import dev.dokimos.core.comparison.ItemComparison;
import dev.dokimos.core.comparison.RunComparisonResult;
import dev.dokimos.server.dto.v1.DiffCase;
import dev.dokimos.server.dto.v1.DiffSummary;
import dev.dokimos.server.dto.v1.DiffView;
import dev.dokimos.server.dto.v1.PageResponse;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.ItemResult;
import dev.dokimos.server.repository.ExperimentRepository;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the per-case run-diff view for the UI. It runs the same core comparison the CI gate uses
 * (through {@link ComparisonSupport}) but presents every case (regressed, improved, unchanged,
 * added, removed) as a full, sortable, paginated table with per-evaluator old-to-new deltas.
 *
 * <p>Unlike the gate, the diff always compares two specific runs the user picked, so the baseline
 * run id is required (there is no automatic baseline resolution here).
 *
 * <p>The comparison is whole-run by nature: significance requires all paired items, so it cannot be
 * computed over a DB-level page. Pagination is therefore an in-memory slice over the already
 * computed comparison result, not a database join.
 */
@Service
public class DiffService {

    /** Sort priority of each status; lower sorts first. */
    private static final Map<ComparisonStatus, Integer> STATUS_ORDER = new EnumMap<>(ComparisonStatus.class);

    static {
        STATUS_ORDER.put(ComparisonStatus.REGRESSED, 0);
        STATUS_ORDER.put(ComparisonStatus.IMPROVED, 1);
        STATUS_ORDER.put(ComparisonStatus.UNCHANGED, 2);
        STATUS_ORDER.put(ComparisonStatus.ADDED, 3);
        STATUS_ORDER.put(ComparisonStatus.REMOVED, 4);
    }

    private final ExperimentRepository experimentRepository;
    private final ExperimentRunRepository runRepository;
    private final ComparisonSupport comparisonSupport;

    public DiffService(
            ExperimentRepository experimentRepository,
            ExperimentRunRepository runRepository,
            ComparisonSupport comparisonSupport) {
        this.experimentRepository = experimentRepository;
        this.runRepository = runRepository;
        this.comparisonSupport = comparisonSupport;
    }

    /**
     * Builds the per-case diff between a candidate run and an explicit baseline run, returning the
     * whole-run summary together with the requested, filtered, sorted page of cases.
     *
     * @param experimentId  the experiment both runs belong to
     * @param candidateRunId the candidate run (the "new" side)
     * @param baselineRunId  the baseline run (the "old" side); required
     * @param statusFilter   optional case filter: ALL (default), REGRESSED, IMPROVED, or CHANGED
     *                       (regressed + improved). Case-insensitive; null or blank means ALL
     * @param pageable       pagination over the filtered, sorted case list
     * @return the summary plus the requested page of cases
     * @throws IllegalArgumentException if the experiment or a run is missing, a run does not belong
     *     to the experiment, or {@code statusFilter} is not a recognized value (surfaces as 404 for
     *     missing entities; an unknown filter surfaces as a 400 via the controller)
     * @throws IllegalStateException if either run is not terminal (surfaces as 409)
     */
    @Transactional(readOnly = true)
    public DiffView listDiff(
            UUID experimentId,
            UUID candidateRunId,
            UUID baselineRunId,
            String statusFilter,
            Pageable pageable,
            TenantScope scope) {
        StatusFilter filter = StatusFilter.parse(statusFilter);

        Experiment experiment = getExperiment(experimentId, scope);
        ExperimentRun candidate =
                comparisonSupport.getRunInExperiment(candidateRunId, experiment, "Candidate run", runRepository, scope);
        comparisonSupport.requireTerminal(candidate, "Candidate run");
        ExperimentRun baseline =
                comparisonSupport.getRunInExperiment(baselineRunId, experiment, "Baseline run", runRepository, scope);
        comparisonSupport.requireTerminal(baseline, "Baseline run");

        ComparisonSupport.ComparisonOutcome outcome = comparisonSupport.compare(baseline, candidate);
        RunComparisonResult comparison = outcome.result();
        String pairing = outcome.pairing();

        Map<String, String> inputByKey = buildInputLookup(outcome.candidateItems(), outcome.baselineItems(), pairing);

        List<DiffCase> all = comparison.items().stream()
                .map(item -> toDiffCase(item, pairing, inputByKey))
                .sorted(caseComparator())
                .toList();

        List<DiffCase> filtered =
                all.stream().filter(c -> filter.accepts(c.status())).toList();

        DiffSummary summary = toSummary(comparison, pairing, candidate, baseline);
        return new DiffView(summary, PageResponse.of(slice(filtered, pageable)));
    }

    private DiffSummary toSummary(
            RunComparisonResult comparison, String pairing, ExperimentRun candidate, ExperimentRun baseline) {
        boolean significant = comparison.passRateSignificance() != null
                && comparison.passRateSignificance().significant();
        return new DiffSummary(
                pairing,
                baseline.getId(),
                candidate.getId(),
                comparison.baselinePassRate(),
                comparison.candidatePassRate(),
                comparison.passRateDelta(),
                significant,
                comparison.improvedCount(),
                comparison.regressedCount(),
                comparison.unchangedCount(),
                comparison.addedCount(),
                comparison.removedCount());
    }

    private DiffCase toDiffCase(ItemComparison item, String pairing, Map<String, String> inputByKey) {
        String key = item.key();
        String datasetItemId = comparisonSupport.isDatasetItemKey(key) ? key : null;
        List<DiffCase.EvaluatorDiff> evaluators =
                item.evaluatorDeltas().stream().map(this::toEvaluatorDiff).toList();
        return new DiffCase(datasetItemId, key, item.status().name(), item.passFlip(), inputByKey.get(key), evaluators);
    }

    private DiffCase.EvaluatorDiff toEvaluatorDiff(EvaluatorDelta delta) {
        boolean significant =
                delta.significance() != null && delta.significance().significant();
        return new DiffCase.EvaluatorDiff(
                delta.evaluatorName(),
                delta.baselineMean(),
                delta.candidateMean(),
                delta.delta(),
                delta.status().name(),
                significant);
    }

    /** Sort REGRESSED first, then IMPROVED, UNCHANGED, ADDED, REMOVED; stable by key within a group. */
    private Comparator<DiffCase> caseComparator() {
        return Comparator.<DiffCase>comparingInt(
                        c -> STATUS_ORDER.getOrDefault(ComparisonStatus.valueOf(c.status()), 99))
                .thenComparing(DiffCase::index, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /**
     * Maps each comparison key to its input text. For dataset_item_id pairing the key is the dataset
     * item id and items are matched on their linked dataset item; for positional pairing the key is
     * {@code item-<index>} and items are matched by their position in the run's ordered item list
     * (the same order the comparison engine assigns). The candidate side wins; the baseline side
     * fills in inputs for REMOVED cases that exist only in the baseline.
     */
    private Map<String, String> buildInputLookup(
            List<ItemResult> candidateItems, List<ItemResult> baselineItems, String pairing) {
        Map<String, String> byKey = new HashMap<>();
        // Baseline first so candidate values override on shared keys. These are the same item lists
        // the engine paired, so the positional keys here match the engine's item-<index> keys.
        putInputs(byKey, baselineItems, pairing);
        putInputs(byKey, candidateItems, pairing);
        return byKey;
    }

    private void putInputs(Map<String, String> target, List<ItemResult> items, String pairing) {
        boolean byId = "dataset_item_id".equals(pairing);
        for (int i = 0; i < items.size(); i++) {
            ItemResult item = items.get(i);
            String key = byId && item.getDatasetItem() != null
                    ? item.getDatasetItem().getId().toString()
                    : "item-" + i;
            target.put(key, inputText(item.getInput()));
        }
    }

    /** Renders an item's input map as display text, preferring the conventional {@code input} key. */
    private static String inputText(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        Object primary = input.get("input");
        if (primary != null) {
            return primary.toString();
        }
        return input.toString();
    }

    private org.springframework.data.domain.Page<DiffCase> slice(List<DiffCase> cases, Pageable pageable) {
        int from = (int) Math.min((long) pageable.getOffset(), cases.size());
        int to = Math.min(from + pageable.getPageSize(), cases.size());
        List<DiffCase> content = cases.subList(from, to);
        return new org.springframework.data.domain.PageImpl<>(content, pageable, cases.size());
    }

    private Experiment getExperiment(UUID experimentId, TenantScope scope) {
        if (experimentId == null) {
            throw new IllegalArgumentException("Experiment ID cannot be null");
        }
        return experimentRepository
                .findById(experimentId, scope)
                .orElseThrow(() -> new IllegalArgumentException("Experiment not found: " + experimentId));
    }

    /** Optional case filter applied before pagination. */
    private enum StatusFilter {
        ALL,
        REGRESSED,
        IMPROVED,
        CHANGED;

        // The controller validates the status parameter and returns 400 on an unknown value, so this
        // is a tolerant backstop: an unrecognized value falls back to ALL rather than throwing an
        // IllegalArgumentException (which the global handler would map to 404, the wrong status).
        static StatusFilter parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return ALL;
            }
            try {
                return StatusFilter.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ALL;
            }
        }

        boolean accepts(String status) {
            return switch (this) {
                case ALL -> true;
                case REGRESSED -> "REGRESSED".equals(status);
                case IMPROVED -> "IMPROVED".equals(status);
                case CHANGED -> "REGRESSED".equals(status) || "IMPROVED".equals(status);
            };
        }
    }
}
