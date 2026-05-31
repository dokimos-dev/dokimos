package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Trace;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Tenant-scoped repository for {@link Trace}. Extends only the empty {@link Repository} plus the scoped
 * fragments, so the read surface (by-id and the paginated lists) is tenant-scoped. Ingestion looks up by
 * the OTLP trace id unrestricted, and the retention sweeper deletes expired rows across all tenants.
 */
public interface TraceRepository extends Repository<Trace, UUID>, TraceRepositoryFragment {}
