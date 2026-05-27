package dev.dokimos.core.comparison;

/**
 * The change in a single evaluator's mean score between baseline and candidate.
 *
 * @param evaluatorName the evaluator's name
 * @param baselineMean  the mean score on the baseline side, or null when the evaluator is absent there
 * @param candidateMean the mean score on the candidate side, or null when the evaluator is absent there
 * @param delta         candidateMean minus baselineMean, or 0.0 when either side is missing
 * @param status        the classification of the change
 * @param significance  the significance test result backing the classification
 */
public record EvaluatorDelta(
        String evaluatorName,
        Double baselineMean,
        Double candidateMean,
        double delta,
        ComparisonStatus status,
        SignificanceResult significance) {}
