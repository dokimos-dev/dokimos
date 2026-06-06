package dev.dokimos.server.repository;

import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.ItemResult;
import java.util.Collection;
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
     * Counts the run's items that carry a non-null cost, i.e. the items that contributed to
     * {@link #sumCostByRun(ExperimentRun)}. Read together with
     * {@link #countTokenizedItemsByRun(ExperimentRun)} this is the numerator of the cost-coverage
     * signal: when fewer items are priced than tokenized, {@code sumCostByRun} omits the unpriced
     * items and the surfaced total understates the true cost rather than failing.
     *
     * @param run the run to aggregate
     * @return the number of items with a non-null {@code costUsd}; zero if none
     */
    @Query("SELECT COUNT(i) FROM ItemResult i WHERE i.run = :run AND i.costUsd IS NOT NULL")
    long countPricedItemsByRun(ExperimentRun run);

    /**
     * Counts the run's items that carry a non-null prompt-token count, used as the denominator of the
     * cost-coverage signal: an item with tokens but no cost is one a {@code PriceTable} could not price
     * (unknown model or null token count), which is exactly the gap the signal reports. An item with no
     * tokens at all was never measured (a plain {@code .task}) and counts toward neither this nor
     * {@link #countPricedItemsByRun(ExperimentRun)}.
     *
     * @param run the run to aggregate
     * @return the number of items with a non-null {@code tokensIn}; zero if none
     */
    @Query("SELECT COUNT(i) FROM ItemResult i WHERE i.run = :run AND i.tokensIn IS NOT NULL")
    long countTokenizedItemsByRun(ExperimentRun run);

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

    /**
     * Page of items still awaiting a human verdict, optionally scoped to a project, experiment, or run.
     * An item needs review when it carries no annotation or only an {@code UNSURE} one: the
     * {@code NOT EXISTS} clause treats a {@code CORRECT}/{@code INCORRECT} annotation as resolved (there
     * is at most one annotation per item). Run, experiment, and project are fetch-joined so the queue can
     * render each item's context without a per-row lookup, and the null-guarded filters let the one query
     * serve both the global queue and the scoped views. The tenant predicate filters on the item's own
     * stamped tenant so a reviewer only sees items of their own tenant plus shared (null-tenant) items;
     * it is applied to both the page query and the count query so paging metadata stays consistent.
     *
     * @param projectName  restrict to this project, or null for any
     * @param experimentId restrict to this experiment, or null for any
     * @param runId        restrict to this run, or null for any
     * @param restricted   whether the tenant predicate applies; false sees every tenant
     * @param tenantId     the tenant to filter by when restricted, or null for shared-only
     * @param pageable     the page to return, ordered oldest-first
     * @return the matching items, each with run, experiment, and project initialized
     */
    @Query(value = """
                    SELECT i FROM ItemResult i
                    JOIN FETCH i.run r
                    JOIN FETCH r.experiment e
                    JOIN FETCH e.project p
                    WHERE (:projectName IS NULL OR p.name = :projectName)
                    AND (:experimentId IS NULL OR e.id = :experimentId)
                    AND (:runId IS NULL OR r.id = :runId)
                    AND (:restricted = false OR i.tenantId = :tenantId OR i.tenantId IS NULL)
                    AND NOT EXISTS (
                        SELECT a FROM Annotation a
                        WHERE a.itemResult = i AND a.verdict <> dev.dokimos.server.entity.AnnotationVerdict.UNSURE
                    )
                    ORDER BY i.createdAt ASC, i.id ASC
                    """, countQuery = """
                    SELECT COUNT(i) FROM ItemResult i
                    WHERE (:projectName IS NULL OR i.run.experiment.project.name = :projectName)
                    AND (:experimentId IS NULL OR i.run.experiment.id = :experimentId)
                    AND (:runId IS NULL OR i.run.id = :runId)
                    AND (:restricted = false OR i.tenantId = :tenantId OR i.tenantId IS NULL)
                    AND NOT EXISTS (
                        SELECT a FROM Annotation a
                        WHERE a.itemResult = i AND a.verdict <> dev.dokimos.server.entity.AnnotationVerdict.UNSURE
                    )
                    """)
    Page<ItemResult> findItemsNeedingReview(
            @Param("projectName") String projectName,
            @Param("experimentId") UUID experimentId,
            @Param("runId") UUID runId,
            @Param("restricted") boolean restricted,
            @Param("tenantId") String tenantId,
            Pageable pageable);

    /**
     * Loads the given items with their eval results fetch-joined, used to initialize the lazy
     * {@code evalResults} collection on a page of items in one query rather than per row.
     *
     * @param ids the item ids to load
     * @return the items with eval results initialized
     */
    @Query("SELECT DISTINCT i FROM ItemResult i LEFT JOIN FETCH i.evalResults WHERE i.id IN :ids")
    List<ItemResult> findAllWithEvalsByIdIn(@Param("ids") Collection<UUID> ids);
}
