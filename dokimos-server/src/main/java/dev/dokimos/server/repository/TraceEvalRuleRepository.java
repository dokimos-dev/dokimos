package dev.dokimos.server.repository;

import dev.dokimos.server.entity.TraceEvalRule;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Tenant-scoped repository for {@link TraceEvalRule}. Extends only the empty {@link Repository} plus the
 * scoped fragments, so every read takes a {@code TenantScope}. Ingestion (off the request thread) lists a
 * project's enabled rules unrestricted.
 */
public interface TraceEvalRuleRepository extends Repository<TraceEvalRule, UUID>, TraceEvalRuleRepositoryFragment {}
