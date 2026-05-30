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
     *
     * @param traceId the OTLP trace id
     * @return the trace if present, otherwise empty
     */
    Optional<Trace> findByTraceId(String traceId);

    /**
     * Lists traces newest first within the scope, paginated.
     *
     * @param pageable the page request
     * @param scope the tenant scope
     * @return the page of visible traces
     */
    Page<Trace> findAllOrdered(Pageable pageable, TenantScope scope);

    /**
     * Lists a project's traces newest first within the scope, paginated.
     *
     * @param projectId the owning project id
     * @param pageable the page request
     * @param scope the tenant scope
     * @return the page of visible traces for the project
     */
    Page<Trace> findByProjectId(UUID projectId, Pageable pageable, TenantScope scope);

    /**
     * Deletes traces whose retention window has closed, across all tenants. Spans and trace eval jobs
     * cascade via their foreign keys. Runs off the request thread, so it is unrestricted.
     *
     * @param cutoff traces with an expiry at or before this instant are removed
     * @return the number of traces deleted
     */
    int deleteExpired(Instant cutoff);
}
