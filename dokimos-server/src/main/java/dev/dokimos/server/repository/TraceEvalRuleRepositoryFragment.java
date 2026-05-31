package dev.dokimos.server.repository;

import dev.dokimos.server.entity.TraceEvalRule;
import dev.dokimos.server.tenant.ScopedRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.List;
import java.util.UUID;

/** Entity-specific scoped finders for {@link TraceEvalRule}. */
public interface TraceEvalRuleRepositoryFragment extends ScopedRepository<TraceEvalRule> {

    /** Lists a project's rules oldest first, within the scope. */
    List<TraceEvalRule> findByProjectId(UUID projectId, TenantScope scope);

    /**
     * Enabled rules for a project regardless of tenant, used by ingestion to decide which spans to
     * enqueue. Ingestion runs off the request thread, so it lists unrestricted.
     */
    List<TraceEvalRule> findByProjectIdAndEnabledTrue(UUID projectId);

    /** Returns whether a rule of the name already exists in the project, within the scope. */
    boolean existsByProjectIdAndName(UUID projectId, String name, TenantScope scope);
}
