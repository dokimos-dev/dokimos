package dev.dokimos.server.dto.v1.otlp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A span status. {@code code} is the symbolic or numeric status code; {@code message} is optional. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OtlpStatus(String code, String message) {}
