package dev.dokimos.server.service;

import dev.dokimos.server.config.TraceProperties;
import dev.dokimos.server.dto.v1.TraceIngestResponse;
import dev.dokimos.server.dto.v1.otlp.OtlpExportTraceServiceRequest;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.entity.Trace;
import dev.dokimos.server.entity.TraceSpan;
import dev.dokimos.server.repository.ProjectRepository;
import dev.dokimos.server.repository.TraceRepository;
import dev.dokimos.server.service.OtlpTraceParser.ParsedSpan;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists OTLP traces and enqueues online evaluations for matching spans. The parse and persist run in
 * one synchronous transaction per request, which is sufficient for v1.
 *
 * <p>For high ingest volume this is where an async write path would go: the controller would hand the
 * decoded request to a bounded queue and return immediately, a pool of writers would drain it, and
 * backpressure (a full queue) would surface as 429 to the client. The synchronous path is kept for v1
 * because it gives the caller an exact accepted/rejected count and the simplest failure semantics.
 */
@Service
public class TraceIngestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TraceIngestService.class);

    private final OtlpTraceParser parser;
    private final TraceRepository traceRepository;
    private final ProjectRepository projectRepository;
    private final TraceEvalEnqueuer evalEnqueuer;
    private final TraceProperties properties;

    public TraceIngestService(
            OtlpTraceParser parser,
            TraceRepository traceRepository,
            ProjectRepository projectRepository,
            TraceEvalEnqueuer evalEnqueuer,
            TraceProperties properties) {
        this.parser = parser;
        this.traceRepository = traceRepository;
        this.projectRepository = projectRepository;
        this.evalEnqueuer = evalEnqueuer;
        this.properties = properties;
    }

    /**
     * Parses, persists, and schedules online evals for an OTLP request. Malformed spans are skipped and
     * counted; a batch with rejected spans still succeeds.
     *
     * @param request the decoded OTLP request
     * @return the accepted/rejected span counts and the number of traces touched
     */
    @Transactional
    public TraceIngestResponse ingest(OtlpExportTraceServiceRequest request) {
        OtlpTraceParser.Result parsed = parser.parse(request);
        if (parsed.rejected() > 0) {
            LOGGER.warn("Skipped {} malformed span(s) during OTLP ingestion", parsed.rejected());
        }

        Map<String, List<ParsedSpan>> byTraceId = new LinkedHashMap<>();
        for (ParsedSpan span : parsed.spans()) {
            byTraceId
                    .computeIfAbsent(span.traceId(), k -> new java.util.ArrayList<>())
                    .add(span);
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getRetentionDays(), ChronoUnit.DAYS);
        int accepted = 0;
        for (Map.Entry<String, List<ParsedSpan>> entry : byTraceId.entrySet()) {
            Trace trace = persistTrace(entry.getKey(), entry.getValue(), now, expiresAt);
            accepted += trace.getSpanCount();
            evalEnqueuer.enqueueForTrace(trace);
        }

        return new TraceIngestResponse(accepted, parsed.rejected(), byTraceId.size());
    }

    private Trace persistTrace(String traceId, List<ParsedSpan> spans, Instant now, Instant expiresAt) {
        // A trace id may arrive across batches; reuse the existing trace row so its spans accumulate
        // rather than tripping the unique constraint. A columnar replacement could instead append.
        Trace trace = traceRepository.findByTraceId(traceId).orElseGet(() -> new Trace(traceId, now, expiresAt));
        trace.setExpiresAt(expiresAt);

        Project project = resolveProject(spans);
        if (project != null) {
            trace.setProjectId(project.getId());
            // Stamp the trace with its project's tenant so scoped trace reads land in the right tenant.
            // Ingestion runs without a tenant-scoped principal, so the project is the only tenant signal.
            trace.setTenantId(project.getTenantId());
        }

        ParsedSpan root = findRoot(spans);
        if (root != null) {
            trace.setRootSpanName(root.name());
        }
        trace.setStartTimeUnixNano(minStart(spans, trace.getStartTimeUnixNano()));
        trace.setEndTimeUnixNano(maxEnd(spans, trace.getEndTimeUnixNano()));

        for (ParsedSpan parsed : spans) {
            TraceSpan span = new TraceSpan(parsed.traceId(), parsed.spanId(), parsed.name(), now);
            span.setTenantId(trace.getTenantId());
            span.setParentSpanId(parsed.parentSpanId());
            span.setKind(parsed.kind());
            span.setStatusCode(parsed.statusCode());
            span.setStartTimeUnixNano(parsed.startTimeUnixNano());
            span.setEndTimeUnixNano(parsed.endTimeUnixNano());
            span.setAttributes(parsed.attributes());
            span.setInputText(parsed.inputText());
            span.setOutputText(parsed.outputText());
            trace.addSpan(span);
        }
        trace.setSpanCount(trace.getSpans().size());
        return traceRepository.save(trace);
    }

    private Project resolveProject(List<ParsedSpan> spans) {
        for (ParsedSpan span : spans) {
            if (span.projectName() != null && !span.projectName().isBlank()) {
                // Ingestion derives the soft project link regardless of tenant, so the lookup is
                // unrestricted; the resulting tenant is then carried onto the trace and its spans.
                Optional<Project> project = projectRepository.findByName(
                        span.projectName(), dev.dokimos.server.tenant.TenantScope.unrestricted());
                if (project.isPresent()) {
                    return project.get();
                }
            }
        }
        return null;
    }

    private static ParsedSpan findRoot(List<ParsedSpan> spans) {
        for (ParsedSpan span : spans) {
            if (span.parentSpanId() == null) {
                return span;
            }
        }
        return spans.isEmpty() ? null : spans.get(0);
    }

    private static Long minStart(List<ParsedSpan> spans, Long current) {
        Long min = current;
        for (ParsedSpan span : spans) {
            Long start = span.startTimeUnixNano();
            if (start != null && (min == null || start < min)) {
                min = start;
            }
        }
        return min;
    }

    private static Long maxEnd(List<ParsedSpan> spans, Long current) {
        Long max = current;
        for (ParsedSpan span : spans) {
            Long end = span.endTimeUnixNano();
            if (end != null && (max == null || end > max)) {
                max = end;
            }
        }
        return max;
    }
}
