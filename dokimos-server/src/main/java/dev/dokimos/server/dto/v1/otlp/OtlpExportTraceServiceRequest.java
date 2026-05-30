package dev.dokimos.server.dto.v1.otlp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The top level of the OTLP/HTTP JSON encoding of {@code ExportTraceServiceRequest}. Only the fields the
 * server consumes are mapped; unknown fields are ignored so newer OTLP exporters do not break ingestion.
 *
 * <p>This is the JSON encoding only. The protobuf binary encoding of the same message is deferred: the
 * endpoint documents and accepts {@code application/json} and we parse with Jackson into these DTOs
 * rather than depending on opentelemetry-proto.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OtlpExportTraceServiceRequest(List<OtlpResourceSpans> resourceSpans) {

    public List<OtlpResourceSpans> resourceSpans() {
        return resourceSpans == null ? List.of() : resourceSpans;
    }
}
