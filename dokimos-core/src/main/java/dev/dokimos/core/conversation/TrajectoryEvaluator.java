package dev.dokimos.core.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.core.BaseEvaluator;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.LlmResponseUtils;
import dev.dokimos.core.evaluators.EvaluationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates complete conversation trajectories using LLM-as-judge patterns.
 * <p>
 * This evaluator assesses holistic qualities of multi-turn conversations such
 * as
 * user satisfaction, goal completion, conversation quality, and other custom
 * criteria.
 * It processes the entire conversation history rather than individual turns.
 * <p>
 * Example usage:
 * 
 * <pre>{@code
 * TrajectoryEvaluator evaluator = TrajectoryEvaluator.builder()
 *         .name("Customer Service Quality")
 *         .threshold(0.7)
 *         .judge(judgeLM)
 *         .criteria(List.of(
 *                 TrajectoryEvaluationCriteria.userSatisfaction(),
 *                 TrajectoryEvaluationCriteria.problemResolution()))
 *         .aggregationStrategy(AggregationStrategy.WEIGHTED_MEAN)
 *         .build();
 *
 * EvalTestCase testCase = EvalTestCase.builder()
 *         .actualOutput("trajectory", trajectory)
 *         .build();
 *
 * EvalResult result = evaluator.evaluate(testCase);
 * }</pre>
 */
public class TrajectoryEvaluator extends BaseEvaluator {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrajectoryEvaluator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JudgeLM judge;
    private final List<EvaluationCriterion> criteria;
    private final AggregationStrategy aggregationStrategy;
    private final String trajectoryKey;
    private final boolean includePerCriterionScores;

    private TrajectoryEvaluator(Builder builder) {
        super(builder.name, builder.threshold, List.of());
        this.judge = builder.judge;
        this.criteria = List.copyOf(builder.criteria);
        this.aggregationStrategy = builder.aggregationStrategy;
        this.trajectoryKey = builder.trajectoryKey;
        this.includePerCriterionScores = builder.includePerCriterionScores;
    }

    /**
     * Creates a new builder for constructing a trajectory evaluator.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        ConversationTrajectory trajectory = extractTrajectory(testCase);

        if (trajectory.isEmpty()) {
            return EvalResult.builder()
                    .name(name)
                    .score(0.0)
                    .threshold(threshold)
                    .reason("Conversation trajectory is empty")
                    .build();
        }

        // Evaluate each criterion
        List<Map.Entry<EvaluationCriterion, Double>> criterionScores = new ArrayList<>();
        Map<String, Object> criterionDetails = new HashMap<>();

        for (EvaluationCriterion criterion : criteria) {
            CriterionResult result = evaluateCriterion(trajectory, criterion);
            criterionScores.add(new AbstractMap.SimpleEntry<>(criterion, result.score()));

            if (includePerCriterionScores) {
                criterionDetails.put(criterion.name(), Map.of(
                        "score", result.score(),
                        "reason", result.reason()));
            }
        }

        // Aggregate scores
        double aggregatedScore = aggregationStrategy.aggregate(criterionScores);

        // Generate overall reason
        String reason = generateOverallReason(trajectory, criterionScores);

        // Build metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("turnCount", trajectory.turnCount());
        metadata.put("aggregationStrategy", aggregationStrategy.name());
        if (!criterionDetails.isEmpty()) {
            metadata.put("criterionScores", criterionDetails);
        }

        return EvalResult.builder()
                .name(name)
                .score(aggregatedScore)
                .threshold(threshold)
                .reason(reason)
                .metadata(metadata)
                .build();
    }

    private ConversationTrajectory extractTrajectory(EvalTestCase testCase) {
        Object trajectoryObj = testCase.actualOutputs().get(trajectoryKey);

        if (trajectoryObj == null) {
            throw new EvaluationException(
                    "Trajectory not found in actualOutputs with key '%s'".formatted(trajectoryKey));
        }

        if (trajectoryObj instanceof ConversationTrajectory trajectory) {
            return trajectory;
        }

        throw new EvaluationException(
                "Expected ConversationTrajectory but got %s".formatted(trajectoryObj.getClass().getName()));
    }

    private CriterionResult evaluateCriterion(ConversationTrajectory trajectory, EvaluationCriterion criterion) {
        String prompt = buildCriterionPrompt(trajectory, criterion);
        String response = judge.generate(prompt);

        try {
            String json = LlmResponseUtils.stripMarkdown(response);
            JsonNode node = OBJECT_MAPPER.readTree(json);

            double score = node.get("score").asDouble();
            // Normalize score to 0-1 range if needed
            if (score > 1.0) {
                score = score / 10.0; // Handle 0-10 scale
            }
            score = Math.max(0.0, Math.min(1.0, score));

            String reason = node.has("reason") ? node.get("reason").asText() : "No reason provided";

            return new CriterionResult(score, reason);
        } catch (Exception e) {
            LOGGER.error("Failed to parse criterion evaluation response: {}", response, e);
            return new CriterionResult(0.0, "Failed to parse evaluation: " + e.getMessage());
        }
    }

    private String buildCriterionPrompt(ConversationTrajectory trajectory, EvaluationCriterion criterion) {
        return """
                Evaluate the following conversation based on this criterion:

                CRITERION: %s
                %s

                CONVERSATION:
                %s

                Rate the conversation on a scale of 0.0 to 1.0 for this criterion.

                Respond in JSON format only:
                {"score": <number between 0.0 and 1.0>, "reason": "<brief explanation>"}
                """.formatted(
                criterion.name(),
                criterion.description(),
                formatConversation(trajectory));
    }

    private String formatConversation(ConversationTrajectory trajectory) {
        StringBuilder sb = new StringBuilder();
        if (!trajectory.scenario().isEmpty()) {
            sb.append("Scenario: ").append(trajectory.scenario()).append("\n\n");
        }
        for (Message message : trajectory.messages()) {
            sb.append(message.role().name()).append(": ").append(message.content()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private String generateOverallReason(List<Map.Entry<EvaluationCriterion, Double>> criterionScores) {
        if (criteria.size() == 1) {
            // If only one criterion, use its score directly in the reason
            Map.Entry<EvaluationCriterion, Double> entry = criterionScores.get(0);
            return "Evaluated '%s' criterion: %.2f".formatted(entry.getKey().name(), entry.getValue());
        }

        // Summarize multiple criteria
        StringBuilder sb = new StringBuilder();
        sb.append(
                "Evaluated %d criteria using %s aggregation. ".formatted(criteria.size(), aggregationStrategy.name()));

        // Find highest and lowest scoring criteria
        Map.Entry<EvaluationCriterion, Double> highest = criterionScores.stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
        Map.Entry<EvaluationCriterion, Double> lowest = criterionScores.stream()
                .min(Map.Entry.comparingByValue())
                .orElse(null);

        if (highest != null && lowest != null && !highest.getKey().equals(lowest.getKey())) {
            sb.append("Strongest: %s (%.2f). ".formatted(highest.getKey().name(), highest.getValue()));
            sb.append("Weakest: %s (%.2f).".formatted(lowest.getKey().name(), lowest.getValue()));
        }

        return sb.toString();
    }

    private record CriterionResult(double score, String reason) {
    }

    /**
     * Builder for constructing trajectory evaluators.
     */
    public static class Builder {
        private String name = "Trajectory";
        private double threshold = 0.7;
        private JudgeLM judge;
        private List<EvaluationCriterion> criteria = new ArrayList<>();
        private AggregationStrategy aggregationStrategy = AggregationStrategy.MEAN;
        private String trajectoryKey = "trajectory";
        private boolean includePerCriterionScores = true;

        /**
         * Sets the evaluator name.
         *
         * @param name the evaluator name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the minimum score threshold for success.
         *
         * @param threshold the threshold value
         * @return this builder
         */
        public Builder threshold(double threshold) {
            this.threshold = threshold;
            return this;
        }

        /**
         * Sets the JudgeLM to use for evaluation.
         *
         * @param judge the JudgeLM instance
         * @return this builder
         */
        public Builder judge(JudgeLM judge) {
            this.judge = judge;
            return this;
        }

        /**
         * Adds an evaluation criterion.
         *
         * @param criterion the criterion to add
         * @return this builder
         */
        public Builder criterion(EvaluationCriterion criterion) {
            this.criteria.add(criterion);
            return this;
        }

        /**
         * Sets all evaluation criteria.
         *
         * @param criteria the list of criteria
         * @return this builder
         */
        public Builder criteria(List<EvaluationCriterion> criteria) {
            this.criteria = new ArrayList<>(criteria);
            return this;
        }

        /**
         * Sets the score aggregation strategy.
         *
         * @param strategy the aggregation strategy
         * @return this builder
         */
        public Builder aggregationStrategy(AggregationStrategy strategy) {
            this.aggregationStrategy = strategy;
            return this;
        }

        /**
         * Sets the key used to retrieve the trajectory from actualOutputs.
         * <p>
         * Default is "trajectory".
         *
         * @param key the key name
         * @return this builder
         */
        public Builder trajectoryKey(String key) {
            this.trajectoryKey = key;
            return this;
        }

        /**
         * Sets whether to include per-criterion scores in the result metadata.
         * <p>
         * Default is true.
         *
         * @param include true to include per-criterion scores
         * @return this builder
         */
        public Builder includePerCriterionScores(boolean include) {
            this.includePerCriterionScores = include;
            return this;
        }

        /**
         * Builds the trajectory evaluator.
         *
         * @return a new trajectory evaluator
         * @throws IllegalStateException if required fields are not set
         */
        public TrajectoryEvaluator build() {
            if (judge == null) {
                throw new IllegalStateException("JudgeLM is required");
            }
            if (criteria.isEmpty()) {
                throw new IllegalStateException("At least one evaluation criterion is required");
            }
            return new TrajectoryEvaluator(this);
        }
    }
}
