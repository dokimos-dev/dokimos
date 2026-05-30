package dev.dokimos.server.dto.v1;

/**
 * Result of an OTLP ingestion call. {@code acceptedSpans} and {@code rejectedSpans} count spans persisted
 * versus skipped as malformed, and {@code traces} counts the distinct traces touched. A batch with some
 * rejected spans still returns 200: one bad span never fails the whole batch.
 */
public record TraceIngestResponse(int acceptedSpans, int rejectedSpans, int traces) {}
