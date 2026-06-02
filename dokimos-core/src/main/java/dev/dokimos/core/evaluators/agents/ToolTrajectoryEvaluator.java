package dev.dokimos.core.evaluators.agents;

import dev.dokimos.core.BaseEvaluator;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.core.evaluators.EvaluationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates an agent's tool-call trajectory against an expected trajectory using a
 * selectable match mode.
 * <p>
 * This is a deterministic, glass-box evaluator. It compares the sequence of tool
 * calls the agent made against an expected sequence, where two calls match when
 * their names are equal and their arguments match under an {@link ArgumentMatcher}.
 * Argument matching is configurable per tool via
 * {@link Builder#argumentMatcher(String, ArgumentMatcher)}; the default matcher
 * compares arguments tolerantly ({@link ArgumentMatcher#tolerant()}). Use
 * {@link Builder#argumentMatcher(ArgumentMatcher)} with
 * {@code ArgumentMatcher.of(ArgMatchMode.IGNORE)} to compare names only.
 * <p>
 * Match modes:
 * <ul>
 *   <li>{@link MatchMode#STRICT} — same length, same order, each position matches. Binary score.</li>
 *   <li>{@link MatchMode#IN_ORDER} — expected appears as an ordered subsequence; graded by
 *       longest common subsequence.</li>
 *   <li>{@link MatchMode#ANY_ORDER} — order-independent overlap; graded.</li>
 *   <li>{@link MatchMode#SUPERSET} — every expected call has a distinct match in actual
 *       (extra actual calls allowed). Binary score.</li>
 *   <li>{@link MatchMode#SUBSET} — every actual call has a distinct match in expected
 *       (missing expected calls allowed). Binary score.</li>
 *   <li>{@link MatchMode#PRECISION} — matched / number of actual calls.</li>
 *   <li>{@link MatchMode#RECALL} — matched / number of expected calls.</li>
 * </ul>
 * The unordered modes use a maximum bipartite matching, so repeated tool names are
 * counted optimally even with a stricter per-tool argument matcher. No LLM is required.
 */
public class ToolTrajectoryEvaluator extends BaseEvaluator {

    /**
     * The comparison mode for evaluating a trajectory.
     */
    public enum MatchMode {
        /** Same length, same order, each position matches. */
        STRICT,
        /** Expected is an ordered subsequence of actual; graded by LCS. */
        IN_ORDER,
        /** Order-independent overlap; graded. */
        ANY_ORDER,
        /** Every expected call has a distinct match in actual. */
        SUPERSET,
        /** Every actual call has a distinct match in expected. */
        SUBSET,
        /** Matched divided by the number of actual calls. */
        PRECISION,
        /** Matched divided by the number of expected calls. */
        RECALL
    }

    private final String toolCallsKey;
    private final String expectedToolCallsKey;
    private final MatchMode matchMode;
    private final ArgumentMatcher defaultMatcher;
    private final Map<String, ArgumentMatcher> perToolMatchers;

    private ToolTrajectoryEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        this.toolCallsKey = builder.toolCallsKey;
        this.expectedToolCallsKey = builder.expectedToolCallsKey;
        this.matchMode = builder.matchMode;
        this.defaultMatcher = builder.defaultMatcher;
        this.perToolMatchers = Map.copyOf(builder.perToolMatchers);
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
                    "ToolTrajectoryEvaluator requires '%s' in actualOutputs".formatted(toolCallsKey));
        }
        Object rawExpected = testCase.expectedOutputs().get(expectedToolCallsKey);
        if (rawExpected == null) {
            throw new EvaluationException(
                    "ToolTrajectoryEvaluator requires '%s' in expectedOutputs".formatted(expectedToolCallsKey));
        }

        List<ToolCall> actual = AgentEvalCasts.toolCalls(rawActual, toolCallsKey);
        List<ToolCall> expected = AgentEvalCasts.toolCalls(rawExpected, expectedToolCallsKey);

        if (actual.isEmpty() && expected.isEmpty()) {
            return result(1.0, "Both actual and expected trajectories are empty.", actual, expected, 0);
        }

        return switch (matchMode) {
            case STRICT -> strict(actual, expected);
            case IN_ORDER -> inOrder(actual, expected);
            case ANY_ORDER -> anyOrder(actual, expected);
            case SUPERSET -> superset(actual, expected);
            case SUBSET -> subset(actual, expected);
            case PRECISION -> precision(actual, expected);
            case RECALL -> recall(actual, expected);
        };
    }

    private EvalResult strict(List<ToolCall> actual, List<ToolCall> expected) {
        boolean ok = actual.size() == expected.size();
        if (ok) {
            for (int i = 0; i < actual.size(); i++) {
                if (!callsMatch(actual.get(i), expected.get(i))) {
                    ok = false;
                    break;
                }
            }
        }
        String reason = ok
                ? "Trajectory matches exactly (%d calls).".formatted(expected.size())
                : "Trajectory differs from expected (strict order and arguments).";
        return result(ok ? 1.0 : 0.0, reason, actual, expected, ok ? expected.size() : 0);
    }

    private EvalResult inOrder(List<ToolCall> actual, List<ToolCall> expected) {
        int lcs = longestCommonSubsequence(actual, expected);
        int maxLen = Math.max(actual.size(), expected.size());
        double score = maxLen == 0 ? 1.0 : (double) lcs / maxLen;
        String reason = "Ordered subsequence match: %d/%d (LCS).".formatted(lcs, maxLen);
        return result(score, reason, actual, expected, lcs);
    }

    private EvalResult anyOrder(List<ToolCall> actual, List<ToolCall> expected) {
        int matched = maxMatched(expected, actual);
        int maxLen = Math.max(actual.size(), expected.size());
        double score = maxLen == 0 ? 1.0 : (double) matched / maxLen;
        String reason = "Unordered match: %d/%d calls matched.".formatted(matched, maxLen);
        return result(score, reason, actual, expected, matched);
    }

    private EvalResult superset(List<ToolCall> actual, List<ToolCall> expected) {
        int matched = maxMatched(expected, actual);
        boolean ok = matched == expected.size();
        String reason = ok
                ? "Actual trajectory contains all %d expected calls.".formatted(expected.size())
                : "Actual trajectory is missing %d expected calls.".formatted(expected.size() - matched);
        return result(ok ? 1.0 : 0.0, reason, actual, expected, matched);
    }

    private EvalResult subset(List<ToolCall> actual, List<ToolCall> expected) {
        int matched = maxMatched(expected, actual);
        boolean ok = matched == actual.size();
        String reason = ok
                ? "All %d actual calls are within the expected trajectory.".formatted(actual.size())
                : "Actual trajectory has %d calls not in expected.".formatted(actual.size() - matched);
        return result(ok ? 1.0 : 0.0, reason, actual, expected, matched);
    }

    private EvalResult precision(List<ToolCall> actual, List<ToolCall> expected) {
        int matched = maxMatched(expected, actual);
        double score = actual.isEmpty() ? (expected.isEmpty() ? 1.0 : 0.0) : (double) matched / actual.size();
        return result(
                score,
                "Precision: %d/%d actual calls matched.".formatted(matched, actual.size()),
                actual,
                expected,
                matched);
    }

    private EvalResult recall(List<ToolCall> actual, List<ToolCall> expected) {
        int matched = maxMatched(expected, actual);
        double score = expected.isEmpty() ? (actual.isEmpty() ? 1.0 : 0.0) : (double) matched / expected.size();
        return result(
                score,
                "Recall: %d/%d expected calls matched.".formatted(matched, expected.size()),
                actual,
                expected,
                matched);
    }

    /**
     * Returns the maximum number of expected calls that can be paired with distinct
     * actual calls, where a pair is allowed when their names are equal and their
     * arguments match. This is a maximum bipartite matching computed with augmenting
     * paths (Kuhn's algorithm), so repeated tool names with a stricter per-tool matcher
     * are counted optimally rather than greedily.
     * <p>
     * Every call site passes {@code (expected, actual)} in this order, so the argument
     * matcher always sees the expected call as the expected side and the actual call as
     * the actual side. The maximum matching size is the same regardless of which side is
     * iterated, so this single orientation serves precision, recall, subset, superset,
     * and any-order alike.
     */
    private int maxMatched(List<ToolCall> expected, List<ToolCall> actual) {
        int[] matchedActual = new int[actual.size()];
        Arrays.fill(matchedActual, -1);
        int matched = 0;
        for (int i = 0; i < expected.size(); i++) {
            if (augment(i, expected, actual, matchedActual, new boolean[actual.size()])) {
                matched++;
            }
        }
        return matched;
    }

    private boolean augment(
            int expectedIndex, List<ToolCall> expected, List<ToolCall> actual, int[] matchedActual, boolean[] visited) {
        for (int j = 0; j < actual.size(); j++) {
            if (visited[j] || !callsMatch(actual.get(j), expected.get(expectedIndex))) {
                continue;
            }
            visited[j] = true;
            if (matchedActual[j] == -1 || augment(matchedActual[j], expected, actual, matchedActual, visited)) {
                matchedActual[j] = expectedIndex;
                return true;
            }
        }
        return false;
    }

    private int longestCommonSubsequence(List<ToolCall> actual, List<ToolCall> expected) {
        int m = actual.size();
        int n = expected.size();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (callsMatch(actual.get(i - 1), expected.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return dp[m][n];
    }

    /**
     * Two calls match when their names are equal and the actual call's arguments match
     * the expected call's arguments under the matcher for the expected call's tool
     * (per-tool override, falling back to the default matcher).
     *
     * @param actual   the actual call produced by the agent
     * @param expected the expected call
     * @return true if the calls match
     */
    private boolean callsMatch(ToolCall actual, ToolCall expected) {
        if (!actual.name().equals(expected.name())) {
            return false;
        }
        ArgumentMatcher matcher = perToolMatchers.getOrDefault(expected.name(), defaultMatcher);
        return matcher.matches(expected.arguments(), actual.arguments());
    }

    private EvalResult result(
            double score, String reason, List<ToolCall> actual, List<ToolCall> expected, int matched) {
        return EvalResult.builder()
                .name(name)
                .score(score)
                .threshold(threshold)
                .reason(reason)
                .metadata(Map.of(
                        "matchMode", matchMode.name(),
                        "matchedCount", matched,
                        "actualCount", actual.size(),
                        "expectedCount", expected.size(),
                        "actualTools", names(actual),
                        "expectedTools", names(expected)))
                .build();
    }

    private List<String> names(List<ToolCall> calls) {
        List<String> names = new ArrayList<>(calls.size());
        for (ToolCall call : calls) {
            names.add(call.name());
        }
        return names;
    }

    /**
     * Builder for constructing the evaluator.
     */
    public static class Builder {
        private String name = "Trajectory";
        private double threshold = 1.0;
        private List<EvalTestCaseParam> evaluationParams = List.of();
        private String toolCallsKey = "toolCalls";
        private String expectedToolCallsKey = "toolCalls";
        private MatchMode matchMode = MatchMode.IN_ORDER;
        private ArgumentMatcher defaultMatcher = ArgumentMatcher.tolerant();
        private final Map<String, ArgumentMatcher> perToolMatchers = new HashMap<>();

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
         * Sets the trajectory match mode.
         *
         * @param matchMode the match mode (default {@link MatchMode#IN_ORDER})
         * @return this builder
         */
        public Builder matchMode(MatchMode matchMode) {
            this.matchMode = matchMode;
            return this;
        }

        /**
         * Sets the default argument matcher applied to every tool unless overridden.
         * <p>
         * Defaults to {@link ArgumentMatcher#tolerant()}, so arguments are compared by
         * default. Supply {@code ArgumentMatcher.of(ArgMatchMode.IGNORE)} to compare tool
         * names and order only.
         *
         * @param matcher the default argument matcher
         * @return this builder
         */
        public Builder argumentMatcher(ArgumentMatcher matcher) {
            this.defaultMatcher = matcher;
            return this;
        }

        /**
         * Overrides the argument matcher for a specific tool by name.
         *
         * @param toolName the tool name
         * @param matcher  the argument matcher to use for that tool
         * @return this builder
         */
        public Builder argumentMatcher(String toolName, ArgumentMatcher matcher) {
            this.perToolMatchers.put(toolName, matcher);
            return this;
        }

        /**
         * Builds the evaluator.
         *
         * @return a new evaluator
         */
        public ToolTrajectoryEvaluator build() {
            return new ToolTrajectoryEvaluator(this);
        }
    }
}
