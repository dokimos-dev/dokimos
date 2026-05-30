package dev.dokimos.server.dto.v1.otlp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A single OTLP attribute: a key and an {@link OtlpAnyValue} holding the typed value. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OtlpKeyValue(String key, OtlpAnyValue value) {}
