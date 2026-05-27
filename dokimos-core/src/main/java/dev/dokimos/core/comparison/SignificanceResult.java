package dev.dokimos.core.comparison;

/**
 * Outcome of a statistical significance test on a paired comparison.
 *
 * @param method      the test used ("mcnemar" or "permutation")
 * @param pValue      the two-sided p-value
 * @param significant whether the p-value is below the configured alpha
 * @param ciLow       lower bound of the bootstrap confidence interval, or null when not computed
 * @param ciHigh      upper bound of the bootstrap confidence interval, or null when not computed
 */
public record SignificanceResult(String method, double pValue, boolean significant, Double ciLow, Double ciHigh) {

    /**
     * Returns a non-significant result with a p-value of 1.0 and no confidence interval.
     *
     * @param method the test name to record
     * @return a non-significant result
     */
    public static SignificanceResult notSignificant(String method) {
        return new SignificanceResult(method, 1.0, false, null, null);
    }
}
