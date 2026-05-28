package dev.dokimos.core.comparison;

import java.util.Random;

/** Hand-rolled statistical primitives used by the comparison engine. Randomized procedures take a seeded {@link Random}. */
final class Statistics {

    private static final double PRECISION_SCALE = 1_000_000.0;

    private Statistics() {}

    /** Rounds to six decimal places. */
    static double round(double value) {
        return Math.round(value * PRECISION_SCALE) / PRECISION_SCALE;
    }

    /** Complementary error function via the Abramowitz and Stegun 7.1.26 approximation. */
    static double erfc(double x) {
        double z = Math.abs(x);
        double t = 1.0 / (1.0 + 0.5 * z);
        // Horner evaluation of the A&S 7.1.26 polynomial in t.
        double poly = 0.17087277;
        poly = -0.82215223 + t * poly;
        poly = 1.48851587 + t * poly;
        poly = -1.13520398 + t * poly;
        poly = 0.27886807 + t * poly;
        poly = -0.18628806 + t * poly;
        poly = 0.09678418 + t * poly;
        poly = 0.37409196 + t * poly;
        poly = 1.00002368 + t * poly;
        poly = -1.26551223 + t * poly;
        double ans = t * Math.exp(-z * z + poly);
        return x >= 0.0 ? ans : 2.0 - ans;
    }

    /**
     * McNemar's test with continuity correction on discordant paired binary outcomes.
     * {@code b} = baseline pass, candidate fail; {@code c} = baseline fail, candidate pass.
     * The {@code max(|b - c| - 1, 0)} clamp keeps balanced discordant counts at chi2 = 0
     * (p = 1.0). With no discordants the p-value is 1.0.
     */
    static double mcnemarPValue(int b, int c) {
        int discordant = b + c;
        if (discordant == 0) {
            return 1.0;
        }
        double numerator = Math.max(0.0, Math.abs(b - c) - 1.0);
        double chi2 = (numerator * numerator) / discordant;
        if (chi2 == 0.0) {
            return 1.0;
        }
        return Math.min(1.0, erfc(Math.sqrt(chi2 / 2.0)));
    }

    /**
     * Two-sided paired sign-flip permutation test on per-item deltas. Observed statistic is the
     * mean; each iteration randomly negates each delta and recomputes. P-value is
     * {@code (count(|perm| >= |observed|) + 1) / (iterations + 1)}. Returns 1.0 for fewer than two
     * deltas or all-zero deltas.
     */
    static double permutationPValue(double[] deltas, int iterations, Random random) {
        if (deltas.length < 2) {
            return 1.0;
        }
        boolean allZero = true;
        for (double d : deltas) {
            if (d != 0.0) {
                allZero = false;
                break;
            }
        }
        if (allZero) {
            return 1.0;
        }

        double observed = Math.abs(mean(deltas));
        int atLeastAsExtreme = 0;
        for (int i = 0; i < iterations; i++) {
            double sum = 0.0;
            for (double d : deltas) {
                sum += random.nextBoolean() ? d : -d;
            }
            double permMean = Math.abs(sum / deltas.length);
            if (permMean >= observed) {
                atLeastAsExtreme++;
            }
        }
        return (atLeastAsExtreme + 1.0) / (iterations + 1.0);
    }

    /**
     * Bootstrap percentile CI (2.5/97.5) for the mean of the deltas. Returns null with fewer than
     * two deltas.
     */
    static double[] bootstrapMeanCi(double[] deltas, int iterations, Random random) {
        if (deltas.length < 2) {
            return null;
        }
        double[] means = new double[iterations];
        for (int i = 0; i < iterations; i++) {
            double sum = 0.0;
            for (int j = 0; j < deltas.length; j++) {
                sum += deltas[random.nextInt(deltas.length)];
            }
            means[i] = sum / deltas.length;
        }
        java.util.Arrays.sort(means);
        double low = percentile(means, 2.5);
        double high = percentile(means, 97.5);
        return new double[] {round(low), round(high)};
    }

    static double mean(double[] values) {
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return values.length > 0 ? sum / values.length : 0.0;
    }

    /** Linear-interpolation percentile over a pre-sorted ascending array. {@code p} in [0, 100]. */
    private static double percentile(double[] sorted, double p) {
        if (sorted.length == 1) {
            return sorted[0];
        }
        double rank = (p / 100.0) * (sorted.length - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (lower == upper) {
            return sorted[lower];
        }
        double weight = rank - lower;
        return sorted[lower] * (1.0 - weight) + sorted[upper] * weight;
    }
}
