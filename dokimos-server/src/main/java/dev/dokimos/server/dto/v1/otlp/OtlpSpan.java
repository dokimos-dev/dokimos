package dev.dokimos.server.dto.v1.otlp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * One OTLP span in the JSON encoding. {@code traceId} and {@code spanId} are hex strings;
 * {@code startTimeUnixNano} and {@code endTimeUnixNano} are uint64 values encoded as JSON strings, so
 * they are mapped as strings and parsed leniently. {@code kind} may be a numeric or symbolic enum.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OtlpSpan(
        String traceId,
        String spanId,
        String parentSpanId,
        String name,
        String kind,
        String startTimeUnixNano,
        String endTimeUnixNano,
        OtlpStatus status,
        List<OtlpKeyValue> attributes) {

    public List<OtlpKeyValue> attributes() {
        return attributes == null ? List.of() : attributes;
    }
}
