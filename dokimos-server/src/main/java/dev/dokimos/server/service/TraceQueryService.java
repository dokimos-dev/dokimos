package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.SpanView;
import dev.dokimos.server.dto.v1.TraceDetail;
import dev.dokimos.server.dto.v1.TraceEvalJobView;
import dev.dokimos.server.dto.v1.TraceSummary;
import dev.dokimos.server.entity.Trace;
import dev.dokimos.server.repository.TraceEvalJobRepository;
import dev.dokimos.server.repository.TraceRepository;
import dev.dokimos.server.repository.TraceSpanRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side for ingested traces: a paginated list and a per-trace detail with spans and eval jobs. */
@Service
public class TraceQueryService {

    private final TraceRepository traceRepository;
    private final TraceSpanRepository spanRepository;
    private final TraceEvalJobRepository jobRepository;

    public TraceQueryService(
            TraceRepository traceRepository, TraceSpanRepository spanRepository, TraceEvalJobRepository jobRepository) {
        this.traceRepository = traceRepository;
        this.spanRepository = spanRepository;
        this.jobRepository = jobRepository;
    }

    /**
     * Lists traces newest first, optionally filtered to a project.
     *
     * @param projectId restrict to this project when non-null
     * @param pageable  the page request
     * @return a page of trace summaries
     */
    @Transactional(readOnly = true)
    public Page<TraceSummary> listTraces(UUID projectId, Pageable pageable, TenantScope scope) {
        Page<Trace> page = projectId == null
                ? traceRepository.findAllOrdered(pageable, scope)
                : traceRepository.findByProjectId(projectId, pageable, scope);
        return page.map(TraceSummary::from);
    }

    /**
     * Returns the full detail of one trace: its metadata, spans, and the online eval jobs scoped to its
     * spans.
     *
     * @param id the trace primary key
     * @return the trace detail
     * @throws IllegalArgumentException if no trace has the id (mapped to 404)
     */
    @Transactional(readOnly = true)
    public TraceDetail getTrace(UUID id, TenantScope scope) {
        Trace trace = traceRepository
                .findById(id, scope)
                .orElseThrow(() -> new IllegalArgumentException("Trace not found: " + id));

        List<SpanView> spans = spanRepository.findByTrace_IdOrderByStartTimeUnixNanoAsc(id).stream()
                .map(SpanView::from)
                .toList();
        List<TraceEvalJobView> jobs = jobRepository.findByTracePk(id).stream()
                .map(TraceEvalJobView::from)
                .toList();

        return new TraceDetail(
                trace.getId(),
                trace.getTraceId(),
                trace.getProjectId(),
                trace.getTenantId(),
                trace.getRootSpanName(),
                trace.getSpanCount(),
                trace.getStartTimeUnixNano(),
                trace.getEndTimeUnixNano(),
                trace.getCreatedAt(),
                trace.getExpiresAt(),
                spans,
                jobs);
    }
}
