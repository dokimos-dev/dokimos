package dev.dokimos.server.repository;

import dev.dokimos.server.entity.LlmConnection;
import dev.dokimos.server.tenant.AbstractScopedRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.List;
import java.util.Optional;

/** Tenant-scoped implementation of the {@link LlmConnection} finders. */
public class LlmConnectionRepositoryFragmentImpl extends AbstractScopedRepository<LlmConnection>
        implements LlmConnectionRepositoryFragment {

    public LlmConnectionRepositoryFragmentImpl() {
        super(LlmConnection.class);
    }

    @Override
    public Optional<LlmConnection> findByName(String name, TenantScope scope) {
        return finder().findFirst(scope, (cb, root) -> cb.equal(root.get("name"), name), null);
    }

    @Override
    public List<LlmConnection> findAllOrdered(TenantScope scope) {
        return finder().findAll(scope, (cb, root) -> List.of(cb.desc(root.get("createdAt"))));
    }

    @Override
    public boolean existsByName(String name, TenantScope scope) {
        return finder().findFirst(scope, (cb, root) -> cb.equal(root.get("name"), name), null)
                .isPresent();
    }
}
