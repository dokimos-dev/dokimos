package dev.dokimos.server.repository;

import dev.dokimos.server.entity.ExperimentRun;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Tenant-scoped repository for {@link ExperimentRun}. Extends only the empty {@link Repository} plus the
 * scoped fragments. Background workers (baseline resolution, judge finalization) pass {@code
 * TenantScope.unrestricted()} explicitly; request paths pass the principal's scope.
 */
public interface ExperimentRunRepository extends Repository<ExperimentRun, UUID>, ExperimentRunRepositoryFragment {}
