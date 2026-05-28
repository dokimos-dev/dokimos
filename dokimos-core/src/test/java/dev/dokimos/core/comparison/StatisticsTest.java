package dev.dokimos.core.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Random;
import org.junit.jupiter.api.Test;

class StatisticsTest {

    @Test
    void erfcMatchesKnownValues() {
        assertThat(Statistics.erfc(0.0)).isCloseTo(1.0, within(1e-6));
        // erfc(1) approx 0.157299
        assertThat(Statistics.erfc(1.0)).isCloseTo(0.157299, within(1e-6));
        // symmetry: erfc(-x) = 2 - erfc(x)
        assertThat(Statistics.erfc(-1.0)).isCloseTo(2.0 - Statistics.erfc(1.0), within(1e-9));
    }

    @Test
    void mcnemarReturnsOneWithoutDiscordantPairs() {
        assertThat(Statistics.mcnemarPValue(0, 0)).isEqualTo(1.0);
    }

    @Test
    void mcnemarKnownValueIsSignificant() {
        // b=12, c=2: |b-c|-1 = 9, so chi2 = 9^2 / 14 = 81/14 ~= 5.7857, p ~= 0.01616 (significant).
        double p = Statistics.mcnemarPValue(12, 2);
        assertThat(p).isCloseTo(0.01616, within(1e-5));
        assertThat(p).isLessThan(0.05);
    }

    @Test
    void mcnemarBalancedReturnsOne() {
        // b == c: the continuity correction clamps to 0, so chi2 == 0 and the p-value is exactly 1.0.
        assertThat(Statistics.mcnemarPValue(5, 5)).isEqualTo(1.0);
        assertThat(Statistics.mcnemarPValue(1, 1)).isEqualTo(1.0);
        assertThat(Statistics.mcnemarPValue(2, 2)).isEqualTo(1.0);
    }

    @Test
    void permutationReturnsOneForAllZeroDeltas() {
        double[] deltas = {0.0, 0.0, 0.0, 0.0};
        assertThat(Statistics.permutationPValue(deltas, 1000, new Random(42))).isEqualTo(1.0);
    }

    @Test
    void permutationReturnsOneForFewerThanTwoDeltas() {
        assertThat(Statistics.permutationPValue(new double[] {0.5}, 1000, new Random(42)))
                .isEqualTo(1.0);
    }

    @Test
    void permutationIsSignificantForLargeConsistentShift() {
        double[] deltas = {-0.4, -0.42, -0.38, -0.41, -0.39, -0.43, -0.37, -0.4};
        double p = Statistics.permutationPValue(deltas, 10_000, new Random(42));
        assertThat(p).isLessThan(0.05);
    }

    @Test
    void permutationIsDeterministicForSeed() {
        double[] deltas = {-0.4, -0.42, -0.38, -0.41, -0.39, -0.43, -0.37, -0.4};
        double first = Statistics.permutationPValue(deltas, 10_000, new Random(7));
        double second = Statistics.permutationPValue(deltas, 10_000, new Random(7));
        assertThat(first).isEqualTo(second);
    }

    @Test
    void bootstrapCiBracketsTheMean() {
        double[] deltas = {-0.4, -0.42, -0.38, -0.41, -0.39, -0.43, -0.37, -0.4};
        double[] ci = Statistics.bootstrapMeanCi(deltas, 10_000, new Random(42));
        assertThat(ci).isNotNull();
        double mean = Statistics.mean(deltas);
        assertThat(ci[0]).isLessThanOrEqualTo(mean);
        assertThat(ci[1]).isGreaterThanOrEqualTo(mean);
        assertThat(ci[1]).isLessThan(0.0);
    }

    @Test
    void bootstrapCiStraddlesZeroForMixedSignDeltas() {
        // Symmetric mixed-sign deltas with a near-zero mean: the CI should bracket zero, which guards
        // the percentile indexing (a low percentile below zero, a high percentile above zero).
        double[] deltas = {-0.4, 0.4, -0.3, 0.3, -0.2, 0.2, -0.1, 0.1};
        double[] ci = Statistics.bootstrapMeanCi(deltas, 10_000, new Random(42));
        assertThat(ci).isNotNull();
        assertThat(ci[0]).isLessThan(0.0);
        assertThat(ci[1]).isGreaterThan(0.0);
    }

    @Test
    void bootstrapCiNullForFewerThanTwoDeltas() {
        assertThat(Statistics.bootstrapMeanCi(new double[] {0.5}, 1000, new Random(42)))
                .isNull();
    }
}
