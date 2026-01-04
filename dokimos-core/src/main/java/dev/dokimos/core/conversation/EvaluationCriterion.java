package dev.dokimos.core.conversation;

/**
 * Defines a single evaluation dimension for trajectory evaluation.
 * <p>
 * Each criterion specifies what aspect of the conversation to evaluate
 * and how much weight it should carry in the overall score calculation.
 * <p>
 * Example:
 * 
 * <pre>{@code
 * EvaluationCriterion criterion = new EvaluationCriterion(
 *         "User Satisfaction",
 *         "Evaluate whether the user's concerns were adequately addressed and they seem satisfied by the end of the conversation.",
 *         1.0);
 * }</pre>
 *
 * @param name        the name of this evaluation criterion
 * @param description detailed instructions for how to evaluate this criterion
 * @param weight      the weight for score aggregation (default: 1.0)
 * @see TrajectoryEvaluationCriteria for pre-built criteria
 */
public record EvaluationCriterion(String name, String description, double weight) {

    /**
     * Compact constructor with validation.
     */
    public EvaluationCriterion {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Criterion name cannot be null or blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Criterion description cannot be null or blank");
        }
        if (weight < 0) {
            throw new IllegalArgumentException("Weight cannot be negative");
        }
    }

    /**
     * Creates a criterion with default weight of 1.0.
     *
     * @param name        the criterion name
     * @param description the evaluation description
     * @return a new criterion with weight 1.0
     */
    public static EvaluationCriterion of(String name, String description) {
        return new EvaluationCriterion(name, description, 1.0);
    }

    /**
     * Creates a new criterion with a different weight.
     *
     * @param weight the new weight
     * @return a new criterion with the specified weight
     */
    public EvaluationCriterion withWeight(double weight) {
        return new EvaluationCriterion(name, description, weight);
    }
}
