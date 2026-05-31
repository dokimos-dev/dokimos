package dev.dokimos.core.evaluators.agents;

import dev.dokimos.core.BaseEvaluator;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.evaluators.EvaluationException;
import java.util.*;

/**
 * Checks whether the agent used the expected set of tools.
 * <p>
 * This is a glass-box evaluator for tool proficiency that compares actual tool calls
 * against expected tool calls. The score is the F1-score of tool name sets,
 * balancing precision and recall. No LLM is required.
 * <p>
 * Supports multiple match modes:
 * <ul>
 *   <li>{@code NAMES_ONLY} — compares tool name sets (default)</li>
 *   <li>{@code NAMES_AND_ORDER} — also checks invocation order</li>
 *   <li>{@code NAMES_AND_ARGS} — full structural comparison including arguments</li>
 * </ul>
 */
public class ToolCorrectnessEvaluator extends BaseEvaluator {

    /**
     * The comparison mode for evaluating tool correctness.
     */
    public enum MatchMode {
        /** Compare only the set of tool names used. */
        NAMES_ONLY,
        /** Compare tool names and their invocation order. */
        NAMES_AND_ORDER,
        /** Compare tool names and their arguments. */
        NAMES_AND_ARGS
    }

    private final String toolCallsKey;
    private final String expectedToolCallsKey;
    private final MatchMode matchMode;
    private final ArgumentMatcher argumentMatcher;

    private ToolCorrectnessEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        this.toolCallsKey = builder.toolCallsKey;
        this.expectedToolCallsKey = builder.expectedToolCallsKey;
        this.matchMode = builder.matchMode;
        this.argumentMatcher = builder.argumentMatcher;
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
        Object rawActual = testCase.actualOutputs().get(toolCallsKey);
        if (rawActual == null) {
            throw new EvaluationException(
                    "ToolCorrectnessEvaluator requires '%s' in actualOutputs".formatted(toolCallsKey));
        }

        Object rawExpected = testCase.expectedOutputs().get(expectedToolCallsKey);
        if (rawExpected == null) {
            throw new EvaluationException(
                    "ToolCorrectnessEvaluator requires '%s' in expectedOutputs".formatted(expectedToolCallsKey));
        }

        List<ToolCall> actualCalls = AgentEvalCasts.toolCalls(rawActual, toolCallsKey);
        List<ToolCall> expectedCalls = AgentEvalCasts.toolCalls(rawExpected, expectedToolCallsKey);

        return switch (matchMode) {
            case NAMES_ONLY -> evaluateNameSets(actualCalls, expectedCalls);
            case NAMES_AND_ORDER -> evaluateNamesAndOrder(actualCalls, expectedCalls);
            case NAMES_AND_ARGS -> evaluateNamesAndArgs(actualCalls, expectedCalls);
        };
    }

    private EvalResult evaluateNameSets(List<ToolCall> actual, List<ToolCall> expected) {
        Set<String> actualNames = extractNames(actual);
        Set<String> expectedNames = extractNames(expected);

        Set<String> correct = new LinkedHashSet<>(actualNames);
        correct.retainAll(expectedNames);

        Set<String> redundant = new LinkedHashSet<>(actualNames);
        redundant.removeAll(expectedNames);

        Set<String> missing = new LinkedHashSet<>(expectedNames);
        missing.removeAll(actualNames);

        double score = f1Score(correct.size(), actualNames.size(), expectedNames.size());
        String reason = String.format("Correct: %s, Redundant: %s, Missing: %s", correct, redundant, missing);

        return EvalResult.builder()
                .name(name)
                .score(score)
                .threshold(threshold)
                .reason(reason)
                .metadata(Map.of(
                        "correctTools", List.copyOf(correct),
                        "redundantTools", List.copyOf(redundant),
                        "missingTools", List.copyOf(missing)))
                .build();
    }

    private EvalResult evaluateNamesAndOrder(List<ToolCall> actual, List<ToolCall> expected) {
        List<String> actualNames = actual.stream().map(ToolCall::name).toList();
        List<String> expectedNames = expected.stream().map(ToolCall::name).toList();

        // LCS-based similarity for order comparison
        int lcs = longestCommonSubsequence(actualNames, expectedNames);
        int maxLen = Math.max(actualNames.size(), expectedNames.size());
        double score = maxLen == 0 ? 1.0 : (double) lcs / maxLen;

        Set<String> actualSet = new LinkedHashSet<>(actualNames);
        Set<String> expectedSet = new LinkedHashSet<>(expectedNames);

        Set<String> redundant = new LinkedHashSet<>(actualSet);
        redundant.removeAll(expectedSet);

        Set<String> missing = new LinkedHashSet<>(expectedSet);
        missing.removeAll(actualSet);

        String reason = String.format("Order similarity: %.2f (LCS=%d/%d)", score, lcs, maxLen);

        return EvalResult.builder()
                .name(name)
                .score(score)
                .threshold(threshold)
                .reason(reason)
                .metadata(Map.of(
                        "lcsLength", lcs,
                        "redundantTools", List.copyOf(redundant),
                        "missingTools", List.copyOf(missing)))
                .build();
    }

    private EvalResult evaluateNamesAndArgs(List<ToolCall> actual, List<ToolCall> expected) {
        if (actual.isEmpty() && expected.isEmpty()) {
            return EvalResult.builder()
                    .name(name)
                    .score(1.0)
                    .threshold(threshold)
                    .reason("Both actual and expected tool calls are empty.")
                    .build();
        }

        int matched = 0;
        List<Boolean> expectedMatched = new ArrayList<>(Collections.nCopies(expected.size(), false));

        for (ToolCall actualCall : actual) {
            for (int j = 0; j < expected.size(); j++) {
                if (!expectedMatched.get(j) && toolCallsMatch(actualCall, expected.get(j))) {
                    expectedMatched.set(j, true);
                    matched++;
                    break;
                }
            }
        }

        double score = f1Score(matched, actual.size(), expected.size());
        String reason = String.format(
                "%d of %d actual and %d of %d expected tool calls matched (with arguments).",
                matched, actual.size(), matched, expected.size());

        return EvalResult.builder()
                .name(name)
                .score(score)
                .threshold(threshold)
                .reason(reason)
                .metadata(Map.of("matchedCount", matched))
                .build();
    }

    private boolean toolCallsMatch(ToolCall actual, ToolCall expected) {
        return actual.name().equals(expected.name())
                && argumentMatcher.matches(expected.arguments(), actual.arguments());
    }

    private Set<String> extractNames(List<ToolCall> calls) {
        var names = new LinkedHashSet<String>();
        for (ToolCall call : calls) {
            names.add(call.name());
        }
        return names;
    }

    private double f1Score(int truePositives, int actualSize, int expectedSize) {
        if (actualSize == 0 && expectedSize == 0) return 1.0;
        if (actualSize == 0 || expectedSize == 0) return 0.0;

        double precision = (double) truePositives / actualSize;
        double recall = (double) truePositives / expectedSize;

        if (precision + recall == 0) return 0.0;
        return 2 * precision * recall / (precision + recall);
    }

    private int longestCommonSubsequence(List<String> a, List<String> b) {
        int m = a.size();
        int n = b.size();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (a.get(i - 1).equals(b.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return dp[m][n];
    }

    /**
     * Builder for constructing the evaluator.
     */
    public static class Builder {
        private String name = "Tool Correctness";
        private double threshold = 1.0;
        private List<EvalTestCaseParam> evaluationParams = List.of();
        private String toolCallsKey = "toolCalls";
        private String expectedToolCallsKey = "toolCalls";
        private MatchMode matchMode = MatchMode.NAMES_ONLY;
        private ArgumentMatcher argumentMatcher = ArgumentMatcher.tolerant();

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
         * Sets the key used to retrieve actual tool calls from actualOutputs.
         *
         * @param toolCallsKey the key
         * @return this builder
         */
        public Builder toolCallsKey(String toolCallsKey) {
            this.toolCallsKey = toolCallsKey;
            return this;
        }

        /**
         * Sets the key used to retrieve expected tool calls from expectedOutputs.
         *
         * @param expectedToolCallsKey the key
         * @return this builder
         */
        public Builder expectedToolCallsKey(String expectedToolCallsKey) {
            this.expectedToolCallsKey = expectedToolCallsKey;
            return this;
        }

        /**
         * Sets the match mode for comparing tool calls.
         *
         * @param matchMode the match mode
         * @return this builder
         */
        public Builder matchMode(MatchMode matchMode) {
            this.matchMode = matchMode;
            return this;
        }

        /**
         * Sets the argument matcher used by {@link MatchMode#NAMES_AND_ARGS}.
         * <p>
         * Defaults to {@link ArgumentMatcher#tolerant()}, which treats numerically
         * equal values such as {@code 1} and {@code 1.0} as equal.
         *
         * @param argumentMatcher the argument matcher
         * @return this builder
         */
        public Builder argumentMatcher(ArgumentMatcher argumentMatcher) {
            this.argumentMatcher = argumentMatcher;
            return this;
        }

        /**
         * Builds the evaluator.
         *
         * @return a new evaluator
         */
        public ToolCorrectnessEvaluator build() {
            return new ToolCorrectnessEvaluator(this);
        }
    }
}
