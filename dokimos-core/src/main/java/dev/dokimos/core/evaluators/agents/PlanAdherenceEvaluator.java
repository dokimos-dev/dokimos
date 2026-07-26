package dev.dokimos.core.evaluators.agents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.core.BaseEvaluator;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.LlmResponseUtils;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.evaluators.EvaluationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates whether an agent's executed tool calls followed its stated plan using a rule-based check
 * and an optional LLM check.
 * <p>
 * The plan is read from {@code actualOutputs[reasoningStepsKey]} (default {@code "reasoningSteps"})
 * as a {@code List<String>} and the tool calls from {@code actualOutputs[toolCallsKey]}
 * (default {@code "toolCalls"}), matching {@link dev.dokimos.core.agents.AgentTrace#toOutputMap()}.
 * <p>
 * Checks performed:
 * <ul>
 *   <li>{@code plan_present} (rule): a plan is present</li>
 *   <li>{@code plan_followed} (LLM): the executed tool calls followed the stated plan</li>
 * </ul>
 * <p>
 * Without a judge LLM, only the rule-based check runs. The score is the fraction of checks that
 * passed. No tool calls short-circuits to a score of 1.0, and a missing tool calls key throws.
 */
public class PlanAdherenceEvaluator extends BaseEvaluator {

    private static final ObjectMapper OBJECT_MAPPER = LlmResponseUtils.lenientMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JudgeLM judge;
    private final String reasoningStepsKey;
    private final String toolCallsKey;

    private PlanAdherenceEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        this.judge = builder.judge;
        this.reasoningStepsKey = builder.reasoningStepsKey;
        this.toolCallsKey = builder.toolCallsKey;
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
        Object rawCalls = testCase.actualOutputs().get(toolCallsKey);
        if (rawCalls == null) {
            throw new EvaluationException(
                    "PlanAdherenceEvaluator requires '%s' in actualOutputs".formatted(toolCallsKey));
        }
        List<ToolCall> toolCalls = AgentEvalCasts.toolCalls(rawCalls, toolCallsKey);

        if (toolCalls.isEmpty()) {
            return EvalResult.builder()
                    .name(name)
                    .score(1.0)
                    .threshold(threshold)
                    .reason("No tool calls to evaluate.")
                    .build();
        }

        List<String> plan = readPlan(testCase.actualOutputs().get(reasoningStepsKey));

        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("plan_present", !plan.isEmpty());

        if (judge != null) {
            try {
                Map<String, Object> parsed =
                        LlmResponseUtils.parse(judge.generate(buildPrompt(plan, toolCalls)), MAP_TYPE);
                checks.put("plan_followed", Boolean.TRUE.equals(parsed.get("plan_followed")));
            } catch (Exception e) {
                return EvalResult.builder()
                        .name(name)
                        .score(0.0)
                        .threshold(threshold)
                        .reason("Failed to parse judge response: " + e.getMessage())
                        .build();
            }
        } else {
            checks.put("plan_followed", "skipped");
        }

        long ran = checks.values().stream().filter(Boolean.class::isInstance).count();
        long passed = checks.values().stream().filter(Boolean.TRUE::equals).count();
        double score = ran > 0 ? (double) passed / ran : 1.0;

        return EvalResult.builder()
                .name(name)
                .score(score)
                .threshold(threshold)
                .reason(String.format(
                        "Plan adherence: %d/%d checks passed over %d tool calls.", passed, ran, toolCalls.size()))
                .metadata(Map.of("checks", checks))
                .build();
    }

    private String buildPrompt(List<String> plan, List<ToolCall> toolCalls) {
        var sb = new StringBuilder();
        sb.append("You are evaluating whether an AI agent's executed tool calls followed its stated plan.\n\n");
        sb.append("PLAN (ordered reasoning steps):\n");
        if (plan.isEmpty()) {
            sb.append("(no plan provided)\n");
        }
        for (int i = 0; i < plan.size(); i++) {
            sb.append(i + 1).append(". ").append(plan.get(i)).append("\n");
        }
        sb.append("\nEXECUTED TOOL CALLS (in order):\n");
        for (int i = 0; i < toolCalls.size(); i++) {
            ToolCall call = toolCalls.get(i);
            String argsJson;
            try {
                argsJson = OBJECT_MAPPER.writeValueAsString(call.arguments());
            } catch (Exception e) {
                argsJson = call.arguments().toString();
            }
            sb.append(i + 1)
                    .append(". ")
                    .append(call.name())
                    .append("(")
                    .append(argsJson)
                    .append(")\n");
        }
        sb.append("\nDid the executed tool calls carry out the stated plan? ");
        sb.append("Some plan steps may be reasoning or decisions that need no tool call; ");
        sb.append("judge whether the tool calls are consistent with the plan and advance it, ");
        sb.append("not whether every step maps to a call. ");
        sb.append("Respond ONLY as JSON (no markdown): {\"plan_followed\": true/false}\n");
        return sb.toString();
    }

    private List<String> readPlan(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        throw new EvaluationException("PlanAdherenceEvaluator requires a List for '%s' in actualOutputs, got %s"
                .formatted(reasoningStepsKey, raw.getClass().getName()));
    }

    /**
     * Builder for constructing the evaluator.
     */
    public static class Builder {
        private String name = "Plan Adherence";
        private double threshold = 0.5;
        private List<EvalTestCaseParam> evaluationParams = List.of();
        private String reasoningStepsKey = "reasoningSteps";
        private String toolCallsKey = "toolCalls";
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
         * Sets the actualOutputs key for the tool calls.
         *
         * @param toolCallsKey the key
         * @return this builder
         */
        public Builder toolCallsKey(String toolCallsKey) {
            this.toolCallsKey = toolCallsKey;
            return this;
        }

        /**
         * Sets an optional judge LLM for the adherence check.
         * Without a judge, only the rule-based {@code plan_present} check runs.
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
        public PlanAdherenceEvaluator build() {
            return new PlanAdherenceEvaluator(this);
        }
    }
}
