package dev.dokimos.core.comparison;

/**
 * Change in a single evaluator's mean score between baseline and candidate.
 *
 * @param baselineMean  mean on the baseline side, or null when the evaluator is absent there
 * @param candidateMean mean on the candidate side, or null when the evaluator is absent there
 * @param delta         candidateMean minus baselineMean, or 0.0 when either side is missing
 */
public record EvaluatorDelta(
        String evaluatorName,
        Double baselineMean,
        Double candidateMean,
        double delta,
        ComparisonStatus status,
        SignificanceResult significance) {}
