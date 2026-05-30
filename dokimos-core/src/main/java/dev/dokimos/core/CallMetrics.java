package dev.dokimos.core;

/**
 * Optional metrics describing the LLM call that produced an item result. Any field may be null when
 * the corresponding measurement is not available.
 *
 * @param tokensIn prompt tokens consumed by the call, or null if not measured
 * @param tokensOut completion tokens produced by the call, or null if not measured
 * @param costUsd cost of the call in US dollars, or null if not measured
 * @param latencyMs wall-clock latency of the call in milliseconds, or null if not measured
 */
public record CallMetrics(Integer tokensIn, Integer tokensOut, Double costUsd, Long latencyMs) {}
