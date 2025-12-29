package dev.dokimos.server.repository;

import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.ItemResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ItemResultRepository extends JpaRepository<ItemResult, UUID> {

        Page<ItemResult> findByRunOrderByCreatedAtAsc(ExperimentRun run, Pageable pageable);

        long countByRun(ExperimentRun run);

        @Query("""
                        SELECT COUNT(DISTINCT i) FROM ItemResult i
                        JOIN i.evalResults e
                        WHERE i.run = :run
                        AND e.success = true
                        GROUP BY i
                        HAVING COUNT(e) = (SELECT COUNT(e2) FROM EvalResult e2 WHERE e2.itemResult = i)
                        """)
        long countPassedItems(ExperimentRun run);

        @Query("""
                        SELECT COUNT(i) FROM ItemResult i
                        WHERE i.run = :run
                        AND NOT EXISTS (SELECT e FROM EvalResult e WHERE e.itemResult = i AND e.success = false)
                        AND EXISTS (SELECT e FROM EvalResult e WHERE e.itemResult = i)
                        """)
        long countItemsWithAllEvalsPassed(ExperimentRun run);
}
