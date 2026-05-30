package dev.dokimos.server.dto.v1.otlp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The top level of the OTLP/HTTP JSON encoding of {@code ExportTraceServiceRequest}. Only the fields the
 * server consumes are mapped; unknown fields are ignored so newer OTLP exporters do not break ingestion.
 *
 * <p>This is the JSON encoding, parsed by Jackson into these DTOs. The protobuf encoding of the same
 * message is decoded with the generated opentelemetry-proto classes and converted onto this same DTO
 * tree (see {@code OtlpProtobufConverter}), so both encodings share one ingestion path.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OtlpExportTraceServiceRequest(List<OtlpResourceSpans> resourceSpans) {

    public List<OtlpResourceSpans> resourceSpans() {
        return resourceSpans == null ? List.of() : resourceSpans;
    }
}
