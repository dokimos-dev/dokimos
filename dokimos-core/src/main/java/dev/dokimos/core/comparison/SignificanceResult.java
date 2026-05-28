package dev.dokimos.core.comparison;

/**
 * Outcome of a statistical significance test on a paired comparison.
 *
 * @param method      test used ("mcnemar" or "permutation")
 * @param significant true when {@code pValue} is below the configured alpha
 * @param ciLow       lower bound of the bootstrap CI, or null when not computed
 * @param ciHigh      upper bound of the bootstrap CI, or null when not computed
 */
public record SignificanceResult(String method, double pValue, boolean significant, Double ciLow, Double ciHigh) {

    /** Non-significant placeholder with {@code pValue = 1.0} and no confidence interval. */
    public static SignificanceResult notSignificant(String method) {
        return new SignificanceResult(method, 1.0, false, null, null);
    }
}
