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

import java.util.List;
import java.util.Map;

/**
 * Uses a judge LLM to assess whether tool call argument values are factually
 * grounded in the user's input and context.
 * <p>
 * This is a glass-box evaluator for tool proficiency. For each tool call,
 * the judge evaluates whether argument values can be derived from the user's request.
 * The score is the fraction of non-hallucinated tool calls (0.0–1.0).
 */
public class ToolArgumentHallucinationEvaluator extends BaseEvaluator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JudgeLM judge;
    private final String toolCallsKey;

    private ToolArgumentHallucinationEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        this.judge = builder.judge;
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
    @SuppressWarnings("unchecked")
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        Object rawToolCalls = testCase.actualOutputs().get(toolCallsKey);
        if (rawToolCalls == null) {
            throw new EvaluationException(
                    "ToolArgumentHallucinationEvaluator requires '%s' in actualOutputs".formatted(toolCallsKey));
        }

        List<ToolCall> toolCalls = castToolCalls(rawToolCalls);
        String userInput = testCase.input();

        if (toolCalls.isEmpty()) {
            return EvalResult.builder()
                    .name(name)
                    .score(1.0)
                    .threshold(threshold)
                    .reason("No tool calls to evaluate.")
                    .metadata(Map.of("verdicts", List.of()))
                    .build();
        }

        String prompt = buildPrompt(userInput, toolCalls);
        String response = LlmResponseUtils.stripMarkdown(judge.generate(prompt));

        return parseResponse(response, toolCalls.size());
    }

    private String buildPrompt(String userInput, List<ToolCall> toolCalls) {
        var sb = new StringBuilder();
        sb.append("You are evaluating whether tool call arguments are grounded in the user's input.\n");
        sb.append("An argument is 'hallucinated' if its value cannot be derived from the user's request.\n\n");
        sb.append("USER INPUT:\n").append(userInput).append("\n\n");
        sb.append("TOOL CALLS:\n");
        for (int i = 0; i < toolCalls.size(); i++) {
            ToolCall call = toolCalls.get(i);
            sb.append(i + 1).append(". ").append(call.name()).append("(").append(call.arguments()).append(")\n");
        }
        sb.append("\nFor each tool call, determine if the argument values are grounded in the user input.\n");
        sb.append("Respond ONLY as a JSON array (no markdown):\n");
        sb.append("[{\"toolName\": \"...\", \"grounded\": true/false, \"reason\": \"...\"}]\n");
        return sb.toString();
    }

    private EvalResult parseResponse(String response, int totalCalls) {
        try {
            List<Map<String, Object>> verdicts = OBJECT_MAPPER.readValue(response,
                    new TypeReference<List<Map<String, Object>>>() {});

            long grounded = verdicts.stream()
                    .filter(v -> Boolean.TRUE.equals(v.get("grounded")))
                    .count();

            double score = (double) grounded / totalCalls;
            String reason = String.format("%d/%d tool calls have grounded arguments.", grounded, totalCalls);

            return EvalResult.builder()
                    .name(name)
                    .score(score)
                    .threshold(threshold)
                    .reason(reason)
                    .metadata(Map.of("verdicts", verdicts))
                    .build();
        } catch (Exception e) {
            return EvalResult.failure(name, 0.0,
                    "Failed to parse judge response: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<ToolCall> castToolCalls(Object raw) {
        if (raw instanceof List<?> list) {
            if (list.isEmpty()) return List.of();
            if (list.get(0) instanceof ToolCall) {
                return (List<ToolCall>) raw;
            }
            if (list.get(0) instanceof Map) {
                return list.stream()
                        .map(item -> ToolCall.fromMap((Map<String, Object>) item))
                        .toList();
            }
        }
        throw new EvaluationException("Expected a List of ToolCall objects for key '%s'".formatted(toolCallsKey));
    }

    /**
     * Builder for constructing the evaluator.
     */
    public static class Builder {
        private String name = "Tool Argument Hallucination";
        private double threshold = 0.8;
        private List<EvalTestCaseParam> evaluationParams = List.of(EvalTestCaseParam.INPUT);
        private JudgeLM judge;
        private String toolCallsKey = "toolCalls";

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
         * Sets the judge LLM for evaluating argument grounding.
         *
         * @param judge the judge LLM
         * @return this builder
         */
        public Builder judge(JudgeLM judge) {
            this.judge = judge;
            return this;
        }

        /**
         * Sets the key used to retrieve tool calls from actualOutputs.
         *
         * @param toolCallsKey the key
         * @return this builder
         */
        public Builder toolCallsKey(String toolCallsKey) {
            this.toolCallsKey = toolCallsKey;
            return this;
        }

        /**
         * Builds the evaluator.
         *
         * @return a new evaluator
         * @throws IllegalStateException if judge is not set
         */
        public ToolArgumentHallucinationEvaluator build() {
            if (judge == null) {
                throw new IllegalStateException("JudgeLM is required for ToolArgumentHallucinationEvaluator");
            }
            return new ToolArgumentHallucinationEvaluator(this);
        }
    }
}
