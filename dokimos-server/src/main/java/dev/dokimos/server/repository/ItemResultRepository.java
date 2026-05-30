package dev.dokimos.server.repository;

import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.ItemResult;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Sums the prompt tokens across a run's items.
     *
     * @param run the run to aggregate
     * @return the total prompt tokens, or null if no item carries a token count
     */
    @Query("SELECT SUM(i.tokensIn) FROM ItemResult i WHERE i.run = :run")
    Long sumTokensInByRun(ExperimentRun run);

    /**
     * Sums the completion tokens across a run's items.
     *
     * @param run the run to aggregate
     * @return the total completion tokens, or null if no item carries a token count
     */
    @Query("SELECT SUM(i.tokensOut) FROM ItemResult i WHERE i.run = :run")
    Long sumTokensOutByRun(ExperimentRun run);

    /**
     * Sums the call cost across a run's items.
     *
     * @param run the run to aggregate
     * @return the total cost in USD, or null if no item carries a cost
     */
    @Query("SELECT SUM(i.costUsd) FROM ItemResult i WHERE i.run = :run")
    Double sumCostByRun(ExperimentRun run);

    /**
     * Averages the call latency across a run's items.
     *
     * @param run the run to aggregate
     * @return the mean latency in milliseconds, or null if no item carries a latency
     */
    @Query("SELECT AVG(i.latencyMs) FROM ItemResult i WHERE i.run = :run")
    Double avgLatencyByRun(ExperimentRun run);

    /**
     * Seek-based page of run items that carry no eval result for the given evaluator yet. Items are
     * ordered by id so the worker can page forward by passing the last id it saw as {@code afterId};
     * the {@code NOT EXISTS} filter is scoped by evaluator name so adding an evaluator to a previously
     * scored run still picks up every item for that evaluator. Pass the all-zero UUID for the first
     * page.
     *
     * @param runId         the run whose items to scan
     * @param evaluatorName the evaluator whose results gate the scan
     * @param afterId       the seek cursor; rows with a strictly greater id are returned
     * @param pageable      a one-page request bounding the result size
     * @return the next page of unevaluated items, ordered by id
     */
    @Query("""
            SELECT i FROM ItemResult i
            WHERE i.run.id = :runId
            AND i.id > :afterId
            AND NOT EXISTS (
                SELECT e FROM EvalResult e
                WHERE e.itemResult = i AND e.evaluatorName = :evaluatorName
            )
            ORDER BY i.id ASC
            """)
    List<ItemResult> findItemsNotYetEvaluated(
            @Param("runId") UUID runId,
            @Param("evaluatorName") String evaluatorName,
            @Param("afterId") UUID afterId,
            Pageable pageable);

    /**
     * Loads all item results for a run with their eval results fetch-joined in one query. Annotations
     * are deliberately not fetch-joined here: each item has at most one annotation but many eval
     * results, so joining both would multiply rows in a Cartesian product. Callers that also need
     * annotations batch-load them separately by item id.
     *
     * @param runId the run whose items to load
     * @return the run's item results, each with its eval results initialized
     */
    @Query("""
            SELECT DISTINCT i FROM ItemResult i
            LEFT JOIN FETCH i.evalResults
            WHERE i.run.id = :runId
            ORDER BY i.createdAt ASC, i.id ASC
            """)
    List<ItemResult> findByRunIdWithEvals(@Param("runId") UUID runId);
}
