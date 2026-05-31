package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.RunStatus;
import dev.dokimos.server.tenant.AbstractScopedRepository;
import dev.dokimos.server.tenant.TenantPredicate;
import dev.dokimos.server.tenant.TenantScope;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/** Tenant-scoped implementation of the {@link ExperimentRun} finders. */
public class ExperimentRunRepositoryFragmentImpl extends AbstractScopedRepository<ExperimentRun>
        implements ExperimentRunRepositoryFragment {

    public ExperimentRunRepositoryFragmentImpl() {
        super(ExperimentRun.class);
    }

    @Override
    public List<ExperimentRun> findByExperiment(Experiment experiment, TenantScope scope) {
        return finder().findWhere(
                        scope,
                        (cb, root) -> cb.equal(root.get("experiment"), experiment),
                        (cb, root) -> List.of(cb.desc(root.get("startedAt"))));
    }

    @Override
    public Optional<ExperimentRun> findFirstByExperiment(Experiment experiment, TenantScope scope) {
        return finder().findFirst(
                        scope,
                        (cb, root) -> cb.equal(root.get("experiment"), experiment),
                        (cb, root) -> List.of(cb.desc(root.get("startedAt"))));
    }

    @Override
    public Optional<ExperimentRun> findByIdForUpdate(UUID id, TenantScope scope) {
        return finder().findByIdForUpdate(id, scope);
    }

    @Override
    public List<ExperimentRun> findCompletedRunsByExperiment(
            Experiment experiment, Pageable pageable, TenantScope scope) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ExperimentRun> query = cb.createQuery(ExperimentRun.class);
        Root<ExperimentRun> root = query.from(ExperimentRun.class);
        Predicate terminal = root.get("status").in(RunStatus.SUCCESS, RunStatus.FAILED);
        query.select(root)
                .where(cb.and(
                        cb.equal(root.get("experiment"), experiment),
                        terminal,
                        TenantPredicate.forScope(cb, root.get("tenantId"), scope)))
                .orderBy(cb.desc(root.get("startedAt")));
        return entityManager
                .createQuery(query)
                .setMaxResults(pageable.isUnpaged() ? Integer.MAX_VALUE : pageable.getPageSize())
                .getResultList();
    }

    @Override
    public List<ExperimentRun> findBaselineCandidates(
            Experiment experiment,
            UUID candidateId,
            UUID datasetVersionId,
            String branch,
            Pageable pageable,
            TenantScope scope) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ExperimentRun> query = cb.createQuery(ExperimentRun.class);
        Root<ExperimentRun> root = query.from(ExperimentRun.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("experiment"), experiment));
        predicates.add(cb.notEqual(root.get("id"), candidateId));
        predicates.add(cb.equal(root.get("status"), RunStatus.SUCCESS));
        if (datasetVersionId == null) {
            predicates.add(cb.isNull(root.get("datasetVersion")));
        } else {
            predicates.add(cb.equal(root.get("datasetVersion").get("id"), datasetVersionId));
        }
        if (branch != null) {
            predicates.add(cb.equal(root.get("gitBranch"), branch));
        }
        predicates.add(TenantPredicate.forScope(cb, root.get("tenantId"), scope));

        query.select(root).where(cb.and(predicates.toArray(new Predicate[0]))).orderBy(cb.desc(root.get("startedAt")));
        return entityManager
                .createQuery(query)
                .setMaxResults(pageable.isUnpaged() ? Integer.MAX_VALUE : pageable.getPageSize())
                .getResultList();
    }
}
