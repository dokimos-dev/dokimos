package dev.dokimos.server.repository;

import dev.dokimos.server.entity.LlmConnection;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Tenant-scoped repository for {@link LlmConnection}. Extends only the empty {@link Repository} plus the
 * scoped fragments, so every read takes a {@code TenantScope}. Connection names stay globally unique (the
 * DB constraint is unchanged), so the uniqueness guard uses {@code existsByName} which ignores scope.
 */
public interface LlmConnectionRepository extends Repository<LlmConnection, UUID>, LlmConnectionRepositoryFragment {}
