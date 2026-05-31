package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Dataset;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Tenant-scoped repository for {@link Dataset}. Extends only the empty {@link Repository} plus the scoped
 * fragments, so every read takes a {@code TenantScope}. Dataset names are unique per tenant, so the
 * create guard uses {@code existsByName} scoped to the caller.
 */
public interface DatasetRepository extends Repository<Dataset, UUID>, DatasetRepositoryFragment {}
