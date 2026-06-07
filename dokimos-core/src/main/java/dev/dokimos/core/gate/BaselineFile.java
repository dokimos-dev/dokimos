package dev.dokimos.core.gate;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The committed regression-gate baseline (format v1).
 *
 * <p>This is a <em>stable projection</em> of a run, not a serialized {@code RunResult}. It excludes
 * the model's actual outputs, each evaluator's prose reason, and call metrics — the comparison
 * engine reads none of them — so the file changes only when measured quality changes, keeping git
 * diffs minimal. It carries exactly what the comparison needs: a stable item key plus per-evaluator
 * score and verdict.
 *
 * <p>Determinism contract (enforced by {@link BaselineStore}): scores are rounded to 6 decimals
 * (matching the engine), evaluators are a list sorted by name, and items are sorted by key, so two
 * writes of the same run are byte-identical.
 *
 * @param formatVersion the format version; readers reject a version newer than they support
 * @param experiment the experiment name
 * @param dataset dataset summary (advisory)
 * @param pairing {@code "positional"} or {@code "dataset_item_id"}
 * @param runsPerItem observations per item this file represents (v1: always 1)
 * @param items the per-item projections, sorted by key
 * @param provenance build/judge context, quarantined and excluded from the stability guarantee
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BaselineFile(
        int formatVersion,
        String experiment,
        DatasetInfo dataset,
        String pairing,
        int runsPerItem,
        List<BaselineItem> items,
        Provenance provenance) {

    /** The current supported format version. */
    public static final int CURRENT_VERSION = 1;

    /**
     * Dataset summary metadata (advisory; never used for pairing).
     *
     * @param name the dataset name, or null if unknown
     * @param itemCount the number of items in the baseline
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DatasetInfo(String name, int itemCount) {}

    /**
     * One item's projection.
     *
     * @param key the engine pairing key: {@code "item-<index>"} (positional) or a {@code datasetItemId}
     * @param fingerprint a stable hash of inputs + expected outputs, to detect silent mis-pairing
     * @param input a truncated human anchor for readable diffs; advisory, never paired on
     * @param evaluators the per-evaluator entries, sorted by name
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BaselineItem(String key, String fingerprint, String input, List<EvaluatorEntry> evaluators) {}

    /**
     * One evaluator's recorded outcome for an item.
     *
     * @param name the evaluator name
     * @param score the score, rounded to 6 decimals
     * @param threshold the evaluator threshold; <em>advisory</em> — the verdict uses {@code pass},
     *     never a recomputed threshold comparison. May be null.
     * @param pass the recorded pass/fail verdict (the single source of truth for the comparison)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EvaluatorEntry(String name, double score, Double threshold, boolean pass) {}

    /**
     * Build/judge context, kept separate from the measured signal and excluded from the byte-stability
     * guarantee. There is intentionally no {@code createdAt} — git records when the file changed.
     *
     * @param dokimosVersion the Dokimos version that wrote the file, or null in dev builds
     * @param judge the judge provenance, or null
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Provenance(String dokimosVersion, JudgeInfo judge) {}

    /**
     * Judge provenance (advisory).
     *
     * @param model the judge model id (pin a dated snapshot, never a floating alias)
     * @param temperature the judge temperature, or null
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JudgeInfo(String model, Double temperature) {}
}
