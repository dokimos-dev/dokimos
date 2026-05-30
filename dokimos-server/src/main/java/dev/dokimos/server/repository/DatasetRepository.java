package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Dataset;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Tenant-scoped repository for {@link Dataset}. Extends only the empty {@link Repository} plus the scoped
 * fragments, so every read takes a {@code TenantScope}. Dataset names stay globally unique (the DB
 * constraint is unchanged), so the create guard uses {@code existsByName} which ignores scope.
 */
public interface DatasetRepository extends Repository<Dataset, UUID>, DatasetRepositoryFragment {}
