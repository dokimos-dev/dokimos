package dev.dokimos.server.judge;

import dev.dokimos.core.EvalTestCaseParam;
import java.util.List;

/**
 * Builds the judge prompt in the same shape the core LLM judge evaluator produces, so server-side and
 * in-process scoring stay aligned. The criteria, the selected parameters in their given order, the
 * score range, and the JSON response instruction match the core format exactly.
 */
final class JudgePromptBuilder {

    private JudgePromptBuilder() {}

    static String build(
            String criteria,
            List<EvalTestCaseParam> params,
            double minScore,
            double maxScore,
            String input,
            String expectedOutput,
            String actualOutput) {
        StringBuilder sb = new StringBuilder();
        sb.append("Evaluate the following based on this criteria: ")
                .append(criteria)
                .append("\n\n");

        for (EvalTestCaseParam param : params) {
            switch (param) {
                case INPUT -> sb.append("Input: ").append(input).append("\n");
                case ACTUAL_OUTPUT ->
                    sb.append("Actual Output: ").append(actualOutput).append("\n");
                case EXPECTED_OUTPUT ->
                    sb.append("Expected Output: ").append(expectedOutput).append("\n");
            }
        }

        sb.append("Provide a score between ")
                .append(minScore)
                .append(" and ")
                .append(maxScore)
                .append(", and a brief reasoning.");
        sb.append("Respond in JSON format: {\"score\": <number>, \"reason\": \"<explanation>\"}");
        return sb.toString();
    }
}
