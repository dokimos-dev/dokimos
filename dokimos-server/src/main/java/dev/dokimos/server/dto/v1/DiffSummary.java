package dev.dokimos.server.dto.v1;

import java.util.UUID;

/**
 * Whole-run summary of a per-case run diff. Mirrors the headline numbers of the gate verdict but
 * is framed for a view rather than a CI branch: it always compares the two specific runs the user
 * picked. Counts are significance-gated by the core comparison engine.
 *
 * @param pairing           how items were paired: {@code dataset_item_id} or {@code positional}
 * @param baselineRunId     the baseline run being compared
 * @param candidateRunId    the candidate run being compared
 * @param baselinePassRate  baseline overall pass rate
 * @param candidatePassRate candidate overall pass rate
 * @param passRateDelta     candidate minus baseline pass rate
 * @param significant       whether the pass-rate change is statistically significant
 * @param improvedCount     count of items significantly improved
 * @param regressedCount    count of items significantly regressed
 * @param unchangedCount    count of items with no significant change
 * @param addedCount        count of items present only in the candidate
 * @param removedCount      count of items present only in the baseline
 */
public record DiffSummary(
        String pairing,
        UUID baselineRunId,
        UUID candidateRunId,
        Double baselinePassRate,
        Double candidatePassRate,
        Double passRateDelta,
        boolean significant,
        int improvedCount,
        int regressedCount,
        int unchangedCount,
        int addedCount,
        int removedCount) {}
