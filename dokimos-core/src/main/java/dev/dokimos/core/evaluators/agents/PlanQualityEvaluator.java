package dev.dokimos.core.evaluators.agents;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.dokimos.core.BaseEvaluator;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.LlmResponseUtils;
import dev.dokimos.core.evaluators.EvaluationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates whether an agent's plan is coherent and goal-directed using a rule-based check
 * and an optional LLM check.
 * <p>
 * The plan is read from {@code actualOutputs[reasoningStepsKey]} (default {@code "reasoningSteps"})
 * as a {@code List<String>}, matching {@link dev.dokimos.core.agents.AgentTrace#toOutputMap()}.
 * <p>
 * Checks performed:
 * <ul>
 *   <li>{@code non_empty_reasoning} (rule): a plan is present (non-empty reasoning)</li>
 *   <li>{@code quality} (LLM): the plan is logical, coherent, and goal-directed given the input</li>
 * </ul>
 * <p>
 * Without a judge LLM, only the rule-based check runs. The score is the fraction of checks that
 * passed. An empty plan short-circuits to a score of 1.0, and a missing key throws.
 */
public class PlanQualityEvaluator extends BaseEvaluator {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JudgeLM judge;
    private final String reasoningStepsKey;

    private PlanQualityEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        this.judge = builder.judge;
        this.reasoningStepsKey = builder.reasoningStepsKey;
    }

    /**
     * Creates a new builder for constructing the evaluator.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        Object raw = testCase.actualOutputs().get(reasoningStepsKey);
        if (raw == null) {
            throw new EvaluationException(
                    "PlanQualityEvaluator requires '%s' in actualOutputs".formatted(reasoningStepsKey));
        }
        List<String> plan = readPlan(raw);

        if (plan.isEmpty()) {
            return EvalResult.builder()
                    .name(name)
                    .score(1.0)
                    .threshold(threshold)
                    .reason("No reasoning steps to evaluate.")
                    .build();
        }

        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("non_empty_reasoning", true);

        if (judge != null) {
            try {
                Map<String, Object> parsed =
                        LlmResponseUtils.parse(judge.generate(buildPrompt(testCase.input(), plan)), MAP_TYPE);
                checks.put("quality", Boolean.TRUE.equals(parsed.get("quality")));
            } catch (Exception e) {
                return EvalResult.builder()
                        .name(name)
                        .score(0.0)
                        .threshold(threshold)
                        .reason("Failed to parse judge response: " + e.getMessage())
                        .build();
            }
        } else {
            checks.put("quality", "skipped");
        }

        long ran = checks.values().stream().filter(Boolean.class::isInstance).count();
        long passed = checks.values().stream().filter(Boolean.TRUE::equals).count();
        double score = ran > 0 ? (double) passed / ran : 1.0;

        return EvalResult.builder()
                .name(name)
                .score(score)
                .threshold(threshold)
                .reason(String.format("Plan quality: %d/%d checks passed across %d steps.", passed, ran, plan.size()))
                .metadata(Map.of("checks", checks))
                .build();
    }

    private String buildPrompt(String input, List<String> plan) {
        var sb = new StringBuilder();
        sb.append("You are evaluating whether an AI agent's plan is coherent and goal-directed.\n\n");
        sb.append("TASK:\n").append(input != null ? input : "(none)").append("\n\n");
        sb.append("PLAN (ordered reasoning steps):\n");
        for (int i = 0; i < plan.size(); i++) {
            sb.append(i + 1).append(". ").append(plan.get(i)).append("\n");
        }
        sb.append("\nIs the plan logical, coherent, and goal-directed for the task? ");
        sb.append("Respond ONLY as JSON (no markdown): {\"quality\": true/false}\n");
        return sb.toString();
    }

    private List<String> readPlan(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        throw new EvaluationException("PlanQualityEvaluator requires a List for '%s' in actualOutputs, got %s"
                .formatted(reasoningStepsKey, raw.getClass().getName()));
    }

    /**
     * Builder for constructing the evaluator.
     */
    public static class Builder {
        private String name = "Plan Quality";
        private double threshold = 0.5;
        private List<EvalTestCaseParam> evaluationParams = List.of();
        private String reasoningStepsKey = "reasoningSteps";
        private JudgeLM judge;

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
         * Sets which test case parameters to validate.
         *
         * @param params the parameters
         * @return this builder
         */
        public Builder evaluationParams(List<EvalTestCaseParam> params) {
            this.evaluationParams = List.copyOf(params);
            return this;
        }

        /**
         * Sets the actualOutputs key for the plan (reasoning steps).
         *
         * @param reasoningStepsKey the key
         * @return this builder
         */
        public Builder reasoningStepsKey(String reasoningStepsKey) {
            this.reasoningStepsKey = reasoningStepsKey;
            return this;
        }

        /**
         * Sets an optional judge LLM for the coherence check.
         * Without a judge, only the rule-based {@code non_empty_reasoning} check runs.
         *
         * @param judge the judge LLM
         * @return this builder
         */
        public Builder judge(JudgeLM judge) {
            this.judge = judge;
            return this;
        }

        /**
         * Builds the evaluator.
         *
         * @return a new evaluator
         */
        public PlanQualityEvaluator build() {
            return new PlanQualityEvaluator(this);
        }
    }
}
