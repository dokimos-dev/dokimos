package dev.dokimos.server.repository;

import dev.dokimos.server.entity.TraceEvalRule;
import dev.dokimos.server.tenant.ScopedRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.List;
import java.util.UUID;

/** Entity-specific scoped finders for {@link TraceEvalRule}. */
public interface TraceEvalRuleRepositoryFragment extends ScopedRepository<TraceEvalRule> {

    /**
     * Lists a project's rules oldest first, within the scope.
     *
     * @param projectId the owning project id
     * @param scope the tenant scope
     * @return the visible rules, oldest first
     */
    List<TraceEvalRule> findByProjectId(UUID projectId, TenantScope scope);

    /**
     * Enabled rules for a project regardless of tenant, used by ingestion to decide which spans to
     * enqueue. Ingestion runs off the request thread, so it lists unrestricted.
     *
     * @param projectId the owning project id
     * @return the project's enabled rules
     */
    List<TraceEvalRule> findByProjectIdAndEnabledTrue(UUID projectId);

    /**
     * Returns whether a rule with the given name already exists in the project, within the scope. Rule
     * names are unique per project, so the uniqueness guard is scoped like the rest of the rule surface.
     *
     * @param projectId the owning project id
     * @param name the candidate rule name
     * @param scope the tenant scope
     * @return true when a visible rule of that name exists in the project
     */
    boolean existsByProjectIdAndName(UUID projectId, String name, TenantScope scope);
}
