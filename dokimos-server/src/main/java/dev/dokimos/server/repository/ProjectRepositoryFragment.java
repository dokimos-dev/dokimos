package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Project;
import dev.dokimos.server.tenant.ScopedRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.List;
import java.util.Optional;

/** Entity-specific scoped finders for {@link Project}, implemented over the tenant-scoped query helper. */
public interface ProjectRepositoryFragment extends ScopedRepository<Project> {

    /**
     * Looks up a project by name within the scope. A name owned by another tenant is invisible, so two
     * tenants can each own a project with the same name.
     *
     * @param name the project name
     * @param scope the tenant scope
     * @return the project if visible, otherwise empty
     */
    Optional<Project> findByName(String name, TenantScope scope);

    /**
     * Returns each visible project paired with its experiment count, newest first. The aggregate honors
     * the scope so a tenant only counts and lists its own and shared projects.
     *
     * @param scope the tenant scope
     * @return rows of {@code [Project, Long]} ordered by creation time descending
     */
    List<Object[]> findAllWithExperimentCount(TenantScope scope);
}
