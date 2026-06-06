package dev.dokimos.core;

/**
 * Turns a model id and token counts into a USD cost, returning null when the model is unknown or a
 * count is missing (never throws, never fabricates a number).
 *
 * <p>This is a pluggable, point-in-time pricing seam. Dokimos ships no price data: the caller
 * supplies an implementation (a lambda over an in-house price map, a copyable reference map from
 * {@code dokimos-examples}, or a live/internal backend). Because no LLM framework or provider
 * returns a dollar cost (only token counts), cost must be computed here, at capture time, where the
 * model id is in scope. The result is frozen into {@link CallMetrics#costUsd()} and is not
 * recomputed downstream.
 *
 * <p>Implementations must be side-effect free and must return null (rather than throw) for an
 * unknown model or a null token count, mirroring the all-nullable {@link CallMetrics} contract so a
 * missing price degrades to a dark cost card rather than a failed run.
 */
@FunctionalInterface
public interface PriceTable {

    /**
     * Computes the USD cost of a single call.
     *
     * @param model     the model id the call used (the lookup key), or null
     * @param tokensIn  prompt tokens consumed, or null if not measured
     * @param tokensOut completion tokens produced, or null if not measured
     * @return the cost in US dollars, or null if the model is unknown or either token count is null
     */
    Double costUsd(String model, Integer tokensIn, Integer tokensOut);
}
