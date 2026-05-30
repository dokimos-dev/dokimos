package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.otlp.OtlpExportTraceServiceRequest;
import dev.dokimos.server.dto.v1.otlp.OtlpKeyValue;
import dev.dokimos.server.dto.v1.otlp.OtlpResourceSpans;
import dev.dokimos.server.dto.v1.otlp.OtlpScopeSpans;
import dev.dokimos.server.dto.v1.otlp.OtlpSpan;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Pure translation from the OTLP/HTTP JSON request DTOs into the server's own span model, with no
 * persistence. Malformed spans (missing a trace id, span id, or name) are skipped non-fatally and
 * reported through the {@link Result} counters so one bad span never fails the whole batch. Resource
 * attributes are merged onto each span so a project-name attribute on the resource is visible to rule
 * matching and to project resolution.
 */
@Component
public class OtlpTraceParser {

    /**
     * Attribute keys, in priority order, from which a span's input text is derived. The first present
     * non-empty value wins. Covers common GenAI and LangChain style conventions.
     */
    private static final List<String> INPUT_KEYS =
            List.of("dokimos.input", "input.value", "gen_ai.prompt", "llm.input", "input", "prompt");

    /** Attribute keys, in priority order, from which a span's output text is derived. */
    private static final List<String> OUTPUT_KEYS =
            List.of("dokimos.output", "output.value", "gen_ai.completion", "llm.output", "output", "completion");

    /** Resource attribute keys that, when present, name the owning project for soft linkage. */
    private static final List<String> PROJECT_NAME_KEYS = List.of("dokimos.project", "dokimos.project.name");

    /**
     * Flattens the request into parsed spans, skipping malformed ones.
     *
     * @param request the decoded OTLP request
     * @return the parsed spans and the count of rejected (malformed) spans
     */
    public Result parse(OtlpExportTraceServiceRequest request) {
        java.util.ArrayList<ParsedSpan> parsed = new java.util.ArrayList<>();
        int rejected = 0;
        for (OtlpResourceSpans resourceSpans : request.resourceSpans()) {
            Map<String, Object> resourceAttributes = flatten(
                    resourceSpans.resource() == null
                            ? List.of()
                            : resourceSpans.resource().attributes());
            String projectName = firstPresent(resourceAttributes, PROJECT_NAME_KEYS);
            for (OtlpScopeSpans scopeSpans : resourceSpans.scopeSpans()) {
                for (OtlpSpan span : scopeSpans.spans()) {
                    try {
                        parsed.add(toParsedSpan(span, resourceAttributes, projectName));
                    } catch (RuntimeException e) {
                        // Non-fatal: a single malformed span is skipped and counted, never aborting the
                        // batch. The caller logs at warn with the reason.
                        rejected++;
                    }
                }
            }
        }
        return new Result(parsed, rejected);
    }

    private ParsedSpan toParsedSpan(OtlpSpan span, Map<String, Object> resourceAttributes, String projectName) {
        String traceId = requireNonBlank(span.traceId(), "traceId");
        String spanId = requireNonBlank(span.spanId(), "spanId");
        String name = requireNonBlank(span.name(), "name");

        Map<String, Object> attributes = new LinkedHashMap<>(resourceAttributes);
        attributes.putAll(flatten(span.attributes()));

        String inputText = firstPresent(attributes, INPUT_KEYS);
        String outputText = firstPresent(attributes, OUTPUT_KEYS);
        String statusCode = span.status() == null ? null : span.status().code();

        return new ParsedSpan(
                traceId,
                spanId,
                blankToNull(span.parentSpanId()),
                name,
                blankToNull(span.kind()),
                statusCode,
                parseUnixNano(span.startTimeUnixNano()),
                parseUnixNano(span.endTimeUnixNano()),
                attributes,
                inputText,
                outputText,
                projectName);
    }

    private static Map<String, Object> flatten(List<OtlpKeyValue> attributes) {
        Map<String, Object> flat = new LinkedHashMap<>();
        if (attributes == null) {
            return flat;
        }
        for (OtlpKeyValue kv : attributes) {
            if (kv == null || kv.key() == null || kv.value() == null) {
                continue;
            }
            Object value = kv.value().unwrap();
            if (value != null) {
                flat.put(kv.key(), value);
            }
        }
        return flat;
    }

    private static String firstPresent(Map<String, Object> attributes, List<String> keys) {
        for (String key : keys) {
            Object value = attributes.get(key);
            if (value != null) {
                String text = String.valueOf(value);
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static Long parseUnixNano(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Span missing required field: " + field);
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** A span translated from OTLP, carrying the merged attributes and derived input/output text. */
    public record ParsedSpan(
            String traceId,
            String spanId,
            String parentSpanId,
            String name,
            String kind,
            String statusCode,
            Long startTimeUnixNano,
            Long endTimeUnixNano,
            Map<String, Object> attributes,
            String inputText,
            String outputText,
            String projectName) {}

    /** The outcome of parsing: the valid spans and the count of malformed spans that were skipped. */
    public record Result(List<ParsedSpan> spans, int rejected) {}
}
