package dev.dokimos.server.controller.v1;

import dev.dokimos.server.dto.v1.PageResponse;
import dev.dokimos.server.dto.v1.TraceDetail;
import dev.dokimos.server.dto.v1.TraceIngestResponse;
import dev.dokimos.server.dto.v1.TraceSummary;
import dev.dokimos.server.dto.v1.otlp.OtlpExportTraceServiceRequest;
import dev.dokimos.server.service.TraceIngestService;
import dev.dokimos.server.service.TraceQueryService;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OTLP trace ingestion and read endpoints. Ingestion accepts the OTLP/HTTP JSON encoding of
 * {@code ExportTraceServiceRequest}; the protobuf binary encoding is deferred. Writes (ingestion) pass
 * through the API key auth filter; reads are open.
 */
@RestController
@RequestMapping("/api/v1/traces")
public class TraceController {

    private static final int MAX_PAGE_SIZE = 200;

    private final TraceIngestService ingestService;
    private final TraceQueryService queryService;

    public TraceController(TraceIngestService ingestService, TraceQueryService queryService) {
        this.ingestService = ingestService;
        this.queryService = queryService;
    }

    /**
     * Ingests an OTLP/HTTP JSON trace export. Valid spans are persisted; malformed spans are skipped and
     * counted. Returns 200 with the accepted/rejected span counts and the number of traces touched even
     * when some spans were rejected.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public TraceIngestResponse ingest(@RequestBody OtlpExportTraceServiceRequest request) {
        return ingestService.ingest(request);
    }

    /** Lists ingested traces newest first, optionally filtered to a project. */
    @GetMapping
    public PageResponse<TraceSummary> list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
        return PageResponse.of(queryService.listTraces(projectId, pageable));
    }

    /** Returns a single trace with its spans and online eval jobs, or 404 if it does not exist. */
    @GetMapping("/{id}")
    public TraceDetail get(@PathVariable UUID id) {
        return queryService.getTrace(id);
    }

    private static int clampSize(int size) {
        if (size < 1) {
            return 1;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
