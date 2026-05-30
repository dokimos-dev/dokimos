package dev.dokimos.server.dto.v1.otlp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** The spans emitted by one resource, with that resource's attributes (for example {@code service.name}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OtlpResourceSpans(OtlpResource resource, List<OtlpScopeSpans> scopeSpans) {

    public List<OtlpScopeSpans> scopeSpans() {
        return scopeSpans == null ? List.of() : scopeSpans;
    }
}
