package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.otlp.OtlpAnyValue;
import dev.dokimos.server.dto.v1.otlp.OtlpExportTraceServiceRequest;
import dev.dokimos.server.dto.v1.otlp.OtlpKeyValue;
import dev.dokimos.server.dto.v1.otlp.OtlpResource;
import dev.dokimos.server.dto.v1.otlp.OtlpResourceSpans;
import dev.dokimos.server.dto.v1.otlp.OtlpScopeSpans;
import dev.dokimos.server.dto.v1.otlp.OtlpSpan;
import dev.dokimos.server.dto.v1.otlp.OtlpStatus;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Translates the protobuf {@code ExportTraceServiceRequest} into the same DTO tree Jackson produces for
 * the OTLP/HTTP JSON encoding, so both encodings share one parser and persistence path. Normalizes the
 * wire differences: ids to lowercase hex, uint64 nanos to decimal strings, enums to their symbolic names,
 * and unset defaults to null, matching what a JSON exporter sends.
 */
public final class OtlpProtobufConverter {

    private static final HexFormat HEX = HexFormat.of();

    private OtlpProtobufConverter() {}

    /**
     * Converts a decoded protobuf request into the common OTLP request DTO.
     *
     * @param request the protobuf message parsed from the request body
     * @return the equivalent DTO, ready for the shared ingestion path
     */
    public static OtlpExportTraceServiceRequest toDto(ExportTraceServiceRequest request) {
        List<OtlpResourceSpans> resourceSpans = new ArrayList<>();
        for (ResourceSpans rs : request.getResourceSpansList()) {
            resourceSpans.add(toResourceSpans(rs));
        }
        return new OtlpExportTraceServiceRequest(resourceSpans);
    }

    private static OtlpResourceSpans toResourceSpans(ResourceSpans rs) {
        OtlpResource resource = null;
        if (rs.hasResource()) {
            resource = new OtlpResource(toKeyValues(rs.getResource().getAttributesList()));
        }
        List<OtlpScopeSpans> scopeSpans = new ArrayList<>();
        for (ScopeSpans ss : rs.getScopeSpansList()) {
            List<OtlpSpan> spans = new ArrayList<>();
            for (Span span : ss.getSpansList()) {
                spans.add(toSpan(span));
            }
            scopeSpans.add(new OtlpScopeSpans(spans));
        }
        return new OtlpResourceSpans(resource, scopeSpans);
    }

    private static OtlpSpan toSpan(Span span) {
        OtlpStatus status = null;
        if (span.hasStatus()) {
            status = new OtlpStatus(
                    statusCode(span.getStatus()), nullIfEmpty(span.getStatus().getMessage()));
        }
        return new OtlpSpan(
                hex(span.getTraceId()),
                hex(span.getSpanId()),
                hex(span.getParentSpanId()),
                nullIfEmpty(span.getName()),
                spanKind(span.getKind()),
                unixNano(span.getStartTimeUnixNano()),
                unixNano(span.getEndTimeUnixNano()),
                status,
                toKeyValues(span.getAttributesList()));
    }

    private static List<OtlpKeyValue> toKeyValues(List<KeyValue> attributes) {
        List<OtlpKeyValue> result = new ArrayList<>();
        for (KeyValue kv : attributes) {
            result.add(new OtlpKeyValue(kv.getKey(), toAnyValue(kv.getValue())));
        }
        return result;
    }

    private static OtlpAnyValue toAnyValue(AnyValue value) {
        return switch (value.getValueCase()) {
            case STRING_VALUE -> new OtlpAnyValue(value.getStringValue(), null, null, null);
            case BOOL_VALUE -> new OtlpAnyValue(null, value.getBoolValue(), null, null);
            case INT_VALUE -> new OtlpAnyValue(null, null, Long.toString(value.getIntValue()), null);
            case DOUBLE_VALUE -> new OtlpAnyValue(null, null, null, value.getDoubleValue());
            // Array, kvlist, and bytes map to null, matching the JSON DTO's scalar-only unwrap.
            default -> new OtlpAnyValue(null, null, null, null);
        };
    }

    private static String spanKind(Span.SpanKind kind) {
        if (kind == Span.SpanKind.SPAN_KIND_UNSPECIFIED || kind == Span.SpanKind.UNRECOGNIZED) {
            return null;
        }
        return kind.name();
    }

    private static String statusCode(Status status) {
        Status.StatusCode code = status.getCode();
        if (code == Status.StatusCode.STATUS_CODE_UNSET || code == Status.StatusCode.UNRECOGNIZED) {
            return null;
        }
        return code.name();
    }

    private static String hex(com.google.protobuf.ByteString bytes) {
        if (bytes == null || bytes.isEmpty()) {
            return null;
        }
        return HEX.formatHex(bytes.toByteArray());
    }

    private static String unixNano(long value) {
        return value == 0 ? null : Long.toString(value);
    }

    private static String nullIfEmpty(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
