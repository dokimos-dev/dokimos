package dev.dokimos.server.dto.v1;

/**
 * Combined per-case run-diff payload: the whole-run summary plus the first (or requested) page of
 * cases. The UI gets the summary counts and the case table in a single request. The comparison is
 * whole-run by nature (significance needs all paired items), so the summary always reflects the
 * full comparison while {@code cases} is a paginated, filtered slice of it.
 *
 * @param summary whole-run comparison summary (counts, pass rates, pairing)
 * @param cases   the requested page of per-case rows after filtering and sorting
 */
public record DiffView(DiffSummary summary, PageResponse<DiffCase> cases) {}
