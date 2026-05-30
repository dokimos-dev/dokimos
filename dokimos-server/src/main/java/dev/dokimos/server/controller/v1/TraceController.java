package dev.dokimos.server.controller.v1;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.dokimos.server.dto.v1.PageResponse;
import dev.dokimos.server.dto.v1.TraceDetail;
import dev.dokimos.server.dto.v1.TraceIngestResponse;
import dev.dokimos.server.dto.v1.TraceSummary;
import dev.dokimos.server.dto.v1.otlp.OtlpExportTraceServiceRequest;
import dev.dokimos.server.service.OtlpProtobufConverter;
import dev.dokimos.server.service.TraceIngestService;
import dev.dokimos.server.service.TraceQueryService;
import dev.dokimos.server.tenant.TenantScopeResolver;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * OTLP trace ingestion and read endpoints. Ingestion accepts both the JSON ({@code application/json}) and
 * protobuf ({@code application/x-protobuf}) encodings of {@code ExportTraceServiceRequest}, which converge
 * on the same internal shape, so span counts, derived input/output, and project linking match across them.
 * Writes pass through the API key auth filter; reads are open.
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
    public TraceIngestResponse ingestTraces(@RequestBody OtlpExportTraceServiceRequest request) {
        return ingestService.ingest(request);
    }

    /**
     * Ingests an OTLP protobuf trace export. The binary {@code ExportTraceServiceRequest} body is decoded
     * and mapped onto the same internal shape the JSON path uses. A body that is not a valid request
     * yields 400.
     */
    @PostMapping(consumes = {"application/x-protobuf", "application/protobuf"})
    public TraceIngestResponse ingestTracesProtobuf(@RequestBody byte[] body) {
        ExportTraceServiceRequest message;
        try {
            message = ExportTraceServiceRequest.parseFrom(body);
        } catch (InvalidProtocolBufferException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed OTLP protobuf body", e);
        }
        return ingestService.ingest(OtlpProtobufConverter.toDto(message));
    }

    /** Lists ingested traces newest first, optionally filtered to a project. */
    @GetMapping
    public PageResponse<TraceSummary> listTraces(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest http) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
        return PageResponse.of(queryService.listTraces(projectId, pageable, TenantScopeResolver.scope(http)));
    }

    /** Returns a single trace with its spans and online eval jobs, or 404 if it does not exist or belongs to another tenant. */
    @GetMapping("/{id}")
    public TraceDetail getTrace(@PathVariable UUID id, HttpServletRequest http) {
        return queryService.getTrace(id, TenantScopeResolver.scope(http));
    }

    private static int clampSize(int size) {
        if (size < 1) {
            return 1;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
