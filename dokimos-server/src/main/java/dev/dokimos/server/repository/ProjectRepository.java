package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Project;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Tenant-scoped repository for {@link Project}. Extends only the empty {@link Repository} plus the scoped
 * fragments, so the dangerous inherited finders do not exist and every read takes a {@code TenantScope}.
 */
public interface ProjectRepository extends Repository<Project, UUID>, ProjectRepositoryFragment {}
