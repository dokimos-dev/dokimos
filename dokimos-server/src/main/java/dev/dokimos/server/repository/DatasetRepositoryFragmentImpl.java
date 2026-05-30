package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Dataset;
import dev.dokimos.server.tenant.AbstractScopedRepository;
import dev.dokimos.server.tenant.TenantScope;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Optional;

/** Tenant-scoped implementation of the {@link Dataset} finders. */
public class DatasetRepositoryFragmentImpl extends AbstractScopedRepository<Dataset>
        implements DatasetRepositoryFragment {

    public DatasetRepositoryFragmentImpl() {
        super(Dataset.class);
    }

    @Override
    public Optional<Dataset> findByName(String name, TenantScope scope) {
        return finder().findFirst(scope, (cb, root) -> cb.equal(root.get("name"), name), null);
    }

    @Override
    public Optional<Dataset> findByNameForUpdate(String name, TenantScope scope) {
        return finder().findFirstForUpdate(scope, (cb, root) -> cb.equal(root.get("name"), name));
    }

    @Override
    public List<Dataset> findAllOrdered(TenantScope scope) {
        return finder().findAll(scope, (cb, root) -> List.of(cb.desc(root.get("createdAt"))));
    }

    @Override
    public boolean existsByName(String name) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Dataset> root = query.from(Dataset.class);
        query.select(cb.count(root)).where(cb.equal(root.get("name"), name));
        return entityManager.createQuery(query).getSingleResult() > 0;
    }
}
