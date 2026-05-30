package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.tenant.AbstractScopedRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.List;
import java.util.Optional;

/** Tenant-scoped implementation of the {@link Experiment} finders. */
public class ExperimentRepositoryFragmentImpl extends AbstractScopedRepository<Experiment>
        implements ExperimentRepositoryFragment {

    public ExperimentRepositoryFragmentImpl() {
        super(Experiment.class);
    }

    @Override
    public Optional<Experiment> findByProjectAndName(Project project, String name, TenantScope scope) {
        return finder().findFirst(
                        scope,
                        (cb, root) -> cb.and(cb.equal(root.get("project"), project), cb.equal(root.get("name"), name)),
                        null);
    }

    @Override
    public List<Experiment> findByProject(Project project, TenantScope scope) {
        return finder().findWhere(
                        scope,
                        (cb, root) -> cb.equal(root.get("project"), project),
                        (cb, root) -> List.of(cb.desc(root.get("createdAt"))));
    }
}
