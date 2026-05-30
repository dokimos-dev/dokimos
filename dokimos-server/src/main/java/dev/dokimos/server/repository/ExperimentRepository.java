package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Experiment;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Tenant-scoped repository for {@link Experiment}. Extends only the empty {@link Repository} plus the
 * scoped fragments, so every read takes a {@code TenantScope} and the inherited unscoped finders do not
 * exist.
 */
public interface ExperimentRepository extends Repository<Experiment, UUID>, ExperimentRepositoryFragment {}
