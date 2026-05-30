package dev.dokimos.server.dto.v1.otlp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** A resource and its attributes. Resource attributes can name the owning project for soft linkage. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OtlpResource(List<OtlpKeyValue> attributes) {

    public List<OtlpKeyValue> attributes() {
        return attributes == null ? List.of() : attributes;
    }
}
