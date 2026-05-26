package dev.dokimos.mcp.store;

import java.util.List;
import java.util.Optional;

/**
 * Persistence layer for evaluation run records.
 */
public interface ResultStore {

    /**
     * Saves a run record. Overwrites if a record with the same ID exists.
     */
    void save(RunRecord record);

    /**
     * Returns a run by ID, or empty if not found.
     */
    Optional<RunRecord> get(String runId);

    /**
     * Lists runs, most recent first.
     *
     * @param datasetName optional filter by dataset name (null for all)
     * @param limit       maximum number to return (0 for all)
     * @return matching records
     */
    List<RunRecord> list(String datasetName, int limit);
}
