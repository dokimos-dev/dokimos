package dev.dokimos.core.conversation;

import java.util.List;
import java.util.Map;

/**
 * Strategies for aggregating scores from multiple evaluation criteria.
 */
public enum AggregationStrategy {

    /**
     * Simple arithmetic mean of all scores.
     * All criteria are weighted equally regardless of their weight values.
     */
    MEAN {
        @Override
        public double aggregate(List<Map.Entry<EvaluationCriterion, Double>> scores) {
            if (scores.isEmpty()) {
                return 0.0;
            }
            return scores.stream().mapToDouble(Map.Entry::getValue).average().orElse(0.0);
        }
    },

    /**
     * Weighted mean based on criterion weights.
     * Each criterion's score is multiplied by its weight before averaging.
     */
    WEIGHTED_MEAN {
        @Override
        public double aggregate(List<Map.Entry<EvaluationCriterion, Double>> scores) {
            if (scores.isEmpty()) {
                return 0.0;
            }
            double totalWeight =
                    scores.stream().mapToDouble(e -> e.getKey().weight()).sum();
            if (totalWeight == 0) {
                return MEAN.aggregate(scores);
            }
            double weightedSum = scores.stream()
                    .mapToDouble(e -> e.getValue() * e.getKey().weight())
                    .sum();
            return weightedSum / totalWeight;
        }
    },

    /**
     * Returns the minimum score across all criteria.
     * This is the strictest aggregation - all criteria must pass for overall
     * success.
     */
    MIN {
        @Override
        public double aggregate(List<Map.Entry<EvaluationCriterion, Double>> scores) {
            return scores.stream().mapToDouble(Map.Entry::getValue).min().orElse(0.0);
        }
    },

    /**
     * Returns the maximum score across all criteria.
     * This is the most lenient aggregation - only one criterion needs to pass.
     */
    MAX {
        @Override
        public double aggregate(List<Map.Entry<EvaluationCriterion, Double>> scores) {
            return scores.stream().mapToDouble(Map.Entry::getValue).max().orElse(0.0);
        }
    };

    /**
     * Aggregates the given criterion scores into a single score.
     *
     * @param scores list of criterion-score pairs
     * @return the aggregated score
     */
    public abstract double aggregate(List<Map.Entry<EvaluationCriterion, Double>> scores);
}
