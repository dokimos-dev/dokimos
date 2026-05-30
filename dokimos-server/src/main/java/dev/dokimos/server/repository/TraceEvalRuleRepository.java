package dev.dokimos.server.repository;

import dev.dokimos.server.entity.TraceEvalRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TraceEvalRuleRepository extends JpaRepository<TraceEvalRule, UUID> {

    List<TraceEvalRule> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

    /** Enabled rules for a project, used by ingestion to decide which spans to enqueue. */
    List<TraceEvalRule> findByProjectIdAndEnabledTrue(UUID projectId);

    boolean existsByProjectIdAndName(UUID projectId, String name);

    boolean existsByConnection_Id(UUID connectionId);
}
