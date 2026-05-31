package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Trace;
import dev.dokimos.server.tenant.ScopedRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Entity-specific scoped finders for {@link Trace} plus the unrestricted ingestion and sweeper paths. */
public interface TraceRepositoryFragment extends ScopedRepository<Trace> {

    /**
     * Looks up a trace by its OTLP trace id regardless of tenant. Ingestion reuses an existing trace row
     * so spans accumulate across batches, so this lookup is unrestricted.
     */
    Optional<Trace> findByTraceId(String traceId);

    /** Lists traces newest first within the scope, paginated. */
    Page<Trace> findAllOrdered(Pageable pageable, TenantScope scope);

    /** Lists a project's traces newest first within the scope, paginated. */
    Page<Trace> findByProjectId(UUID projectId, Pageable pageable, TenantScope scope);

    /**
     * Deletes traces whose retention window has closed, across all tenants; spans and trace eval jobs
     * cascade. Runs off the request thread, so it is unrestricted.
     */
    int deleteExpired(Instant cutoff);
}
