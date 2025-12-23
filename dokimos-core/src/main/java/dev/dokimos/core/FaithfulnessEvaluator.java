package dev.dokimos.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Evaluator that uses an LLM to check how much of the actual output is backed by the given context.
 */
public class FaithfulnessEvaluator extends BaseEvaluator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final String contextKey;
    private final JudgeLM judge;
    private final boolean includeReason;

    private FaithfulnessEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        this.contextKey = builder.contextKey;
        this.judge = builder.judge;
        this.includeReason = builder.includeReason;
    }

    /**
     * Creates a new builder for constructing the faithfulness evaluator.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        List<String> truths = extractTruths(testCase.inputs().get(contextKey).toString());
        List<String> claims = extractClaims(testCase.actualOutput());

        List<ClaimVerdict> verdicts = generateVerdicts(claims, truths);

        long supportedClaims = verdicts.stream()
                .filter(v -> "Yes".equalsIgnoreCase(v.verdict()))
                .count();

        double score = claims.isEmpty() ? 1.0 : (double) supportedClaims / claims.size();
        final String reason = includeReason ? generateReason(verdicts) : "Reasoning was disabled";

        return EvalResult.builder()
                .name(name)
                .score(score)
                .threshold(threshold)
                .reason(reason)
                .build();
    }

    private List<ClaimVerdict> generateVerdicts(List<String> extractedClaims, List<String> truths) {
        var prompt = """
                Compare each CLAIM against the reference TRUTHS.
                
                TRUTHS: %s
                CLAIMS: %s
                
                For each individual claim, provide a verdict (Yes/No/IDK) and a brief reasoning.
                Respond ONLY as a JSON array in the following format:
                [{"verdict": "...", "reasoning": "...", ...]
                """.formatted(truths, extractedClaims);

        String response = judge.generate(prompt);

        try {
            // Convert the JSON directly into a List of verdicts
            return OBJECT_MAPPER.readValue(response, new TypeReference<List<ClaimVerdict>>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String generateReason(List<ClaimVerdict> claimVerdicts) {
        if (claimVerdicts.isEmpty()) {
            return "No claims were found to evaluate.";
        }

        var prompt = """
                Summarize the faithfulness of the following claim verdicts into exactly one brief sentence.
                Focus on the primary reason why any claims were rejected or marked as unknown.
                
                VERDICTS:
                %s
                
                One-sentence summary:
                """.formatted(claimVerdicts);

        return judge.generate(prompt).trim();
    }

    private List<String> extractTruths(String context) {
        var prompt = """
                Extract the factual truths from the context below.
                Return ONLY a JSON array of strings.
                Do not include any markdown formatting or extra text.
                
                Example:
                ["Fact 1", "Fact 2", "Fact 3"]
                
                Context: %s
                """.formatted(context);

        String response = judge.generate(prompt);

        try {
            return OBJECT_MAPPER.readValue(response, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not parse JSON response to extract truths!");
        }
    }

    private List<String> extractClaims(String actualOutput) {
        var prompt = """
                Examine the following AI output and break it down into individual claims or statements.
                Each claim must represent a discrete piece of information that the AI asserted in its response.
                Return ONLY a JSON array of strings.
                Do not include any markdown formatting or extra text.
                
                Example:
                ["Statement 1", "Statement 2", "Statement 3"]
                
                AI Output: %s
                """.formatted(actualOutput);

        String response = judge.generate(prompt);

        try {
            return OBJECT_MAPPER.readValue(response, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not parse JSON response to extract statements!");
        }
    }

    private record ClaimVerdict(String verdict, String reasoning) {
    }

    public static class Builder {
        private String name = "Faithfulness";
        private String contextKey = "context";
        private double threshold = 0.8;
        private List<EvalTestCaseParam> evaluationParams = List.of(
                EvalTestCaseParam.INPUT,
                EvalTestCaseParam.ACTUAL_OUTPUT
        );
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
            this.evaluationParams = params;
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

        public FaithfulnessEvaluator build() {
            return new FaithfulnessEvaluator(this);
        }
    }

}
