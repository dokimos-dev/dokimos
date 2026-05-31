package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.tenant.ScopedRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.List;
import java.util.Optional;

/** Entity-specific scoped finders for {@link Experiment}. */
public interface ExperimentRepositoryFragment extends ScopedRepository<Experiment> {

    /** Finds an experiment of a project by name within the scope. */
    Optional<Experiment> findByProjectAndName(Project project, String name, TenantScope scope);

    /** Lists a project's experiments newest first, within the scope. */
    List<Experiment> findByProject(Project project, TenantScope scope);
}
