package dev.dokimos.server.repository;

import dev.dokimos.server.entity.LlmConnection;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Tenant-scoped repository for {@link LlmConnection}. Extends only the empty {@link Repository} plus the
 * scoped fragments, so every read takes a {@code TenantScope}. Connection names are unique per tenant, so
 * the uniqueness guard uses {@code existsByName} scoped to the caller.
 */
public interface LlmConnectionRepository extends Repository<LlmConnection, UUID>, LlmConnectionRepositoryFragment {}
