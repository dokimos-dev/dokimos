package dev.dokimos.core.evaluators;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.core.BaseEvaluator;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.LlmResponseUtils;

import java.util.List;

/**
 * Evaluator that uses an LLM to detect hallucinations in the actual output.
 * <p>
 * A hallucination occurs when the actual output contains information that is
 * not supported by the provided context.
 * The score represents the ratio of hallucinated statements to total statements
 * (lower is better).
 */
public class HallucinationEvaluator extends BaseEvaluator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final String contextKey;
    private final JudgeLM judge;
    private final boolean includeReason;

    private HallucinationEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        this.contextKey = builder.contextKey;
        this.judge = builder.judge;
        this.includeReason = builder.includeReason;
    }

    /**
     * Creates a new builder for constructing the hallucination evaluator.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        Object context = testCase.actualOutputs().get(contextKey);
        if (context == null) {
            throw new EvaluationException(
                    "Hallucination evaluator requires '%s' in actualOutputs".formatted(contextKey));
        }

        List<HallucinationVerdict> verdicts = generateVerdicts(
                testCase.actualOutput(),
                context.toString());

        double score = calculateScore(verdicts);
        final String reason = includeReason ? generateReason(verdicts, score) : "Reasoning was disabled";

        // For hallucination, lower scores are better (0 = no hallucinations, 1 = all
        // hallucinated)
        // So success is when score <= threshold
        boolean success = score <= threshold;

        return new EvalResult(name, score, success, reason, java.util.Map.of());
    }

    private List<HallucinationVerdict> generateVerdicts(String actualOutput, String context) {
        var prompt = """
                Given the CONTEXT and the ACTUAL OUTPUT, determine whether each statement in the actual output is factually aligned with the context.

                CONTEXT: %s

                ACTUAL OUTPUT: %s

                Break down the actual output into individual statements and for each statement determine:
                - verdict: "yes" if the statement is supported by the context, "no" if it contradicts or is not supported by the context
                - reason: a brief explanation for the verdict

                Respond ONLY as a JSON array in the following format:
                Do not include any markdown formatting or extra text.

                Example:
                [{"verdict": "yes", "reason": "..."}, {"verdict": "no", "reason": "..."}]
                """
                .formatted(context, actualOutput);

        String response = LlmResponseUtils.stripMarkdown(judge.generate(prompt));

        try {
            return OBJECT_MAPPER.readValue(response, new TypeReference<List<HallucinationVerdict>>() {
            });
        } catch (Exception e) {
            throw new EvaluationException("Failed to parse verdict response from LLM judge", e);
        }
    }

    private double calculateScore(List<HallucinationVerdict> verdicts) {
        if (verdicts.isEmpty()) {
            return 0.0;
        }

        long hallucinationCount = verdicts.stream()
                .filter(v -> "no".equalsIgnoreCase(v.verdict().strip()))
                .count();

        return (double) hallucinationCount / verdicts.size();
    }

    private String generateReason(List<HallucinationVerdict> verdicts, double score) {
        if (verdicts.isEmpty()) {
            return "No statements were found to evaluate.";
        }

        var factualAlignments = new StringBuilder();
        var contradictions = new StringBuilder();

        for (var verdict : verdicts) {
            if ("yes".equalsIgnoreCase(verdict.verdict().strip())) {
                if (factualAlignments.length() > 0)
                    factualAlignments.append("; ");
                factualAlignments.append(verdict.reason());
            } else {
                if (contradictions.length() > 0)
                    contradictions.append("; ");
                contradictions.append(verdict.reason());
            }
        }

        var prompt = """
                Summarize the hallucination evaluation results into exactly one brief sentence.
                Focus on the primary reasons for hallucinations if any were detected.

                Factual Alignments: %s
                Contradictions: %s
                Score: %.2f

                One-sentence summary:
                """.formatted(
                factualAlignments.length() > 0 ? factualAlignments.toString() : "None",
                contradictions.length() > 0 ? contradictions.toString() : "None",
                score);

        return judge.generate(prompt).trim();
    }


    private record HallucinationVerdict(String verdict, String reason) {
    }

    public static class Builder {
        private String name = "Hallucination";
        private String contextKey = "context";
        private double threshold = 0.5;
        private List<EvalTestCaseParam> evaluationParams = List.of(
                EvalTestCaseParam.INPUT,
                EvalTestCaseParam.ACTUAL_OUTPUT);
        private JudgeLM judge;
        private boolean includeReason = true;

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
         * Sets which test case parameters to use for the evaluation.
         *
         * @param params the parameters to use for the evaluation
         * @return this builder
         */
        public Builder evaluationParams(List<EvalTestCaseParam> params) {
            this.evaluationParams = List.copyOf(params);
            return this;
        }

        /**
         * Sets the maximum score threshold for success.
         * Since the score represents hallucination ratio, lower scores are better.
         *
         * @param threshold the threshold value
         * @return this builder
         */
        public Builder threshold(double threshold) {
            this.threshold = threshold;
            return this;
        }

        /**
         * Sets the context key to use.
         *
         * @param contextKey the context key to use
         * @return this builder
         */
        public Builder contextKey(String contextKey) {
            this.contextKey = contextKey;
            return this;
        }

        /**
         * Sets the model to use for the evaluation.
         *
         * @param judge the model
         * @return this builder
         */
        public Builder judge(JudgeLM judge) {
            this.judge = judge;
            return this;
        }

        /**
         * Sets the flag whether to provide a reason or not.
         *
         * @param includeReason true, if a reason should be provided
         * @return this builder
         */
        public Builder includeReason(boolean includeReason) {
            this.includeReason = includeReason;
            return this;
        }

        public HallucinationEvaluator build() {
            if (judge == null) {
                throw new IllegalStateException("JudgeLM is required");
            }
            return new HallucinationEvaluator(this);
        }
    }
}
