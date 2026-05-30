package dev.dokimos.server.dto.v1.otlp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The OTLP {@code AnyValue} union in its JSON encoding. Exactly one field is set per value. Only the
 * scalar variants are unwrapped to a Java value; array and key/value list variants are ignored when
 * flattening attributes, which is sufficient for matching and for deriving input/output text.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OtlpAnyValue(String stringValue, Boolean boolValue, String intValue, Double doubleValue) {

    /**
     * Returns the scalar Java value for this attribute, or null when no scalar variant is set.
     * {@code intValue} arrives as a JSON string per the OTLP encoding and is parsed to a long.
     */
    public Object unwrap() {
        if (stringValue != null) {
            return stringValue;
        }
        if (boolValue != null) {
            return boolValue;
        }
        if (intValue != null) {
            try {
                return Long.parseLong(intValue);
            } catch (NumberFormatException e) {
                return intValue;
            }
        }
        return doubleValue;
    }
}
