package dev.dokimos.server.dto.v1.otlp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** The spans emitted by one instrumentation scope. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OtlpScopeSpans(List<OtlpSpan> spans) {

    public List<OtlpSpan> spans() {
        return spans == null ? List.of() : spans;
    }
}
