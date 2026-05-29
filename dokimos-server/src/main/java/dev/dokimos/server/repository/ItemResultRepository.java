package dev.dokimos.server.repository;

import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.ItemResult;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ItemResultRepository extends JpaRepository<ItemResult, UUID> {

    Page<ItemResult> findByRunOrderByCreatedAtAsc(ExperimentRun run, Pageable pageable);

    long countByRun(ExperimentRun run);

    /**
     * Loads all item results for a run with their eval results and dataset-item link fetch-joined in
     * one query, avoiding the N+1 the lazy {@code evalResults} collection would otherwise trigger when
     * building a core {@code RunResult} for the gate comparison. Ordered by {@code createdAt} for a
     * stable positional pairing fallback.
     */
    @Query("""
            SELECT DISTINCT i FROM ItemResult i
            LEFT JOIN FETCH i.evalResults
            LEFT JOIN FETCH i.datasetItem
            WHERE i.run = :run
            ORDER BY i.createdAt ASC, i.id ASC
            """)
    List<ItemResult> findByRunWithEvals(ExperimentRun run);

    @Query("""
                        SELECT COUNT(i) FROM ItemResult i
                        WHERE i.run = :run
                        AND NOT EXISTS (SELECT e FROM EvalResult e WHERE e.itemResult = i AND e.success = false)
                        AND EXISTS (SELECT e FROM EvalResult e WHERE e.itemResult = i)
                        """)
    long countItemsWithAllEvalsPassed(ExperimentRun run);
}
