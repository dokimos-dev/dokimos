package dev.dokimos.core.evaluators.agents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.core.BaseEvaluator;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.LlmResponseUtils;
import dev.dokimos.core.evaluators.EvaluationException;

import java.util.List;
import java.util.Map;

/**
 * Evaluates whether an AI agent completed the user's requested tasks.
 * <p>
 * This is a black-box evaluator that uses a judge LLM to analyze the dialog
 * between user and agent, comparing against a predefined task list.
 * The score is the fraction of completed tasks (0.0–1.0).
 */
public class TaskCompletionEvaluator extends BaseEvaluator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JudgeLM judge;
    private final String tasksKey;
    private final String constraintsKey;
    private final String dialogKey;

    private TaskCompletionEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        this.judge = builder.judge;
        this.tasksKey = builder.tasksKey;
        this.constraintsKey = builder.constraintsKey;
        this.dialogKey = builder.dialogKey;
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
        Object rawTasks = testCase.metadata().get(tasksKey);
        if (rawTasks == null) {
            throw new EvaluationException(
                    "TaskCompletionEvaluator requires '%s' in metadata".formatted(tasksKey));
        }

        List<String> tasks;
        if (rawTasks instanceof List<?> list) {
            tasks = list.stream().map(Object::toString).toList();
        } else {
            throw new EvaluationException("Expected a List<String> for metadata key '%s'".formatted(tasksKey));
        }

        if (tasks.isEmpty()) {
            return EvalResult.builder()
                    .name(name)
                    .score(1.0)
                    .threshold(threshold)
                    .reason("No tasks to evaluate.")
                    .build();
        }

        String dialog = resolveDialog(testCase);
        String constraints = testCase.metadata().containsKey(constraintsKey)
                ? testCase.metadata().get(constraintsKey).toString()
                : "";

        String prompt = buildPrompt(dialog, tasks, constraints);
        String response = LlmResponseUtils.stripMarkdown(judge.generate(prompt));

        return parseResponse(response, tasks.size());
    }

    private String resolveDialog(EvalTestCase testCase) {
        Object dialog = testCase.inputs().get(dialogKey);
        String userInput = dialog != null ? dialog.toString()
                : (testCase.input() != null ? testCase.input() : null);
        if (userInput == null) {
            throw new EvaluationException("TaskCompletionEvaluator requires dialog in inputs");
        }

        String agentOutput = testCase.actualOutput();
        if (agentOutput != null && !agentOutput.isBlank()) {
            return "User: " + userInput + "\n\nAgent: " + agentOutput;
        }
        return userInput;
    }

    private String buildPrompt(String dialog, List<String> tasks, String constraints) {
        var sb = new StringBuilder();
        sb.append("You are evaluating whether an AI agent completed the user's requested tasks.\n\n");
        sb.append("DIALOG:\n").append(dialog).append("\n\n");
        sb.append("TASKS TO EVALUATE:\n");
        for (int i = 0; i < tasks.size(); i++) {
            sb.append(i + 1).append(". ").append(tasks.get(i)).append("\n");
        }
        if (!constraints.isEmpty()) {
            sb.append("\nCONSTRAINTS:\n").append(constraints).append("\n");
        }
        sb.append("\nFor each task, determine whether it was completed based on the dialog.\n");
        sb.append("Respond ONLY as JSON in this format (no markdown):\n");
        sb.append("{\"tasks\": [{\"task\": \"...\", \"completed\": true/false, \"reason\": \"...\"}]}\n");
        return sb.toString();
    }

    private EvalResult parseResponse(String response, int totalTasks) {
        try {
            String json = extractJsonObject(response);
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> taskResults = (List<Map<String, Object>>) parsed.get("tasks");

            if (taskResults == null) {
                return EvalResult.builder()
                        .name(name)
                        .score(0.0)
                        .threshold(threshold)
                        .reason("Judge response missing 'tasks' field.")
                        .build();
            }

            long completed = taskResults.stream()
                    .filter(t -> Boolean.TRUE.equals(t.get("completed")))
                    .count();

            double score = Math.min(1.0, (double) completed / totalTasks);
            String reason = String.format("%d/%d tasks completed.", completed, totalTasks);

            return EvalResult.builder()
                    .name(name)
                    .score(score)
                    .threshold(threshold)
                    .reason(reason)
                    .metadata(Map.of("taskResults", taskResults))
                    .build();
        } catch (Exception e) {
            return EvalResult.builder()
                    .name(name)
                    .score(0.0)
                    .threshold(threshold)
                    .reason("Failed to parse judge response: " + e.getMessage())
                    .build();
        }
    }

    private static String extractJsonObject(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    /**
     * Builder for constructing the evaluator.
     */
    public static class Builder {
        private String name = "Task Completion";
        private double threshold = 0.5;
        private List<EvalTestCaseParam> evaluationParams = List.of(EvalTestCaseParam.INPUT);
        private JudgeLM judge;
        private String tasksKey = "tasks";
        private String constraintsKey = "constraints";
        private String dialogKey = "input";

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
         * Sets the judge LLM for evaluating task completion.
         *
         * @param judge the judge LLM
         * @return this builder
         */
        public Builder judge(JudgeLM judge) {
            this.judge = judge;
            return this;
        }

        /**
         * Sets the metadata key for the task list.
         *
         * @param tasksKey the key
         * @return this builder
         */
        public Builder tasksKey(String tasksKey) {
            this.tasksKey = tasksKey;
            return this;
        }

        /**
         * Sets the metadata key for product constraints.
         *
         * @param constraintsKey the key
         * @return this builder
         */
        public Builder constraintsKey(String constraintsKey) {
            this.constraintsKey = constraintsKey;
            return this;
        }

        /**
         * Sets the inputs key for the dialog.
         *
         * @param dialogKey the key
         * @return this builder
         */
        public Builder dialogKey(String dialogKey) {
            this.dialogKey = dialogKey;
            return this;
        }

        /**
         * Builds the evaluator.
         *
         * @return a new evaluator
         * @throws IllegalStateException if judge is not set
         */
        public TaskCompletionEvaluator build() {
            if (judge == null) {
                throw new IllegalStateException("JudgeLM is required for TaskCompletionEvaluator");
            }
            return new TaskCompletionEvaluator(this);
        }
    }
}
