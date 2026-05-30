package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.tenant.AbstractScopedRepository;
import dev.dokimos.server.tenant.TenantPredicate;
import dev.dokimos.server.tenant.TenantScope;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.List;
import java.util.Optional;

/** Tenant-scoped implementation of the {@link Project} finders. */
public class ProjectRepositoryFragmentImpl extends AbstractScopedRepository<Project>
        implements ProjectRepositoryFragment {

    public ProjectRepositoryFragmentImpl() {
        super(Project.class);
    }

    @Override
    public Optional<Project> findByName(String name, TenantScope scope) {
        return finder().findFirst(scope, (cb, root) -> cb.equal(root.get("name"), name), null);
    }

    @Override
    public List<Object[]> findAllWithExperimentCount(TenantScope scope) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<Project> project = query.from(Project.class);

        // Correlated count of the project's experiments. A subquery keeps the projection a clean
        // [Project, Long] without the GROUP BY a join-and-count would require.
        Subquery<Long> countSub = query.subquery(Long.class);
        Root<Experiment> experiment = countSub.from(Experiment.class);
        countSub.select(cb.count(experiment))
                .where(cb.equal(experiment.get("project").get("id"), project.get("id")));

        query.multiselect(project, countSub.getSelection())
                .where(TenantPredicate.forScope(cb, project.get("tenantId"), scope))
                .orderBy(cb.desc(project.get("createdAt")));

        return entityManager.createQuery(query).getResultList();
    }

    @Override
    public List<Project> findAll(TenantScope scope) {
        return finder().findAll(scope, (cb, root) -> List.of(cb.desc(root.get("createdAt"))));
    }
}
