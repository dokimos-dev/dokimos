package dev.dokimos.server.dto.v1;

import dev.dokimos.server.entity.TraceSpan;
import java.util.Map;
import java.util.UUID;

/** Public view of a {@link TraceSpan}, including its derived input/output text and flattened attributes. */
public record SpanView(
        UUID id,
        String spanId,
        String parentSpanId,
        String name,
        String kind,
        String statusCode,
        Long startTimeUnixNano,
        Long endTimeUnixNano,
        String inputText,
        String outputText,
        Map<String, Object> attributes) {

    public static SpanView from(TraceSpan span) {
        return new SpanView(
                span.getId(),
                span.getSpanId(),
                span.getParentSpanId(),
                span.getName(),
                span.getKind(),
                span.getStatusCode(),
                span.getStartTimeUnixNano(),
                span.getEndTimeUnixNano(),
                span.getInputText(),
                span.getOutputText(),
                span.getAttributes());
    }
}
