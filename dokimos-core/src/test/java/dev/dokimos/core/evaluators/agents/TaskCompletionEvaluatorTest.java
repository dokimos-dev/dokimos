package dev.dokimos.core.evaluators.agents;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.evaluators.EvaluationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskCompletionEvaluatorTest {

    @Test
    void shouldReturnFullScoreWhenAllTasksCompleted() {
        JudgeLM mockJudge = prompt -> """
                {"tasks": [
                    {"task": "Book hotel", "completed": true, "reason": "Hotel booked in Paris"},
                    {"task": "Search flights", "completed": true, "reason": "Flights found to Paris"}
                ]}
                """;

        var evaluator = TaskCompletionEvaluator.builder().judge(mockJudge).build();

        var testCase = EvalTestCase.builder()
                .input("I need to book a hotel and find flights to Paris")
                .metadata("tasks", List.of("Book hotel", "Search flights"))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
        assertThat(result.reason()).contains("2/2");
    }

    @Test
    void shouldReturnPartialScoreWhenSomeTasksIncomplete() {
        JudgeLM mockJudge = prompt -> """
                {"tasks": [
                    {"task": "Book hotel", "completed": true, "reason": "Hotel booked"},
                    {"task": "Search flights", "completed": false, "reason": "No flights searched"}
                ]}
                """;

        var evaluator = TaskCompletionEvaluator.builder().judge(mockJudge).build();

        var testCase = EvalTestCase.builder()
                .input("Book hotel and search flights")
                .metadata("tasks", List.of("Book hotel", "Search flights"))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.5);
    }

    @Test
    void shouldReturnZeroWhenNoTasksCompleted() {
        JudgeLM mockJudge = prompt -> """
                {"tasks": [
                    {"task": "Book hotel", "completed": false, "reason": "Not done"},
                    {"task": "Search flights", "completed": false, "reason": "Not done"}
                ]}
                """;

        var evaluator = TaskCompletionEvaluator.builder().judge(mockJudge).build();

        var testCase = EvalTestCase.builder()
                .input("Book hotel and search flights")
                .metadata("tasks", List.of("Book hotel", "Search flights"))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.success()).isFalse();
    }

    @Test
    void shouldReturnFullScoreForEmptyTaskList() {
        JudgeLM mockJudge = prompt -> "{}";

        var evaluator = TaskCompletionEvaluator.builder().judge(mockJudge).build();

        var testCase = EvalTestCase.builder()
                .input("Hello")
                .metadata("tasks", List.of())
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldHandleConstraints() {
        JudgeLM mockJudge = prompt -> {
            assertThat(prompt).contains("budget under $200");
            return """
                    {"tasks": [
                        {"task": "Book hotel", "completed": true, "reason": "Within budget"}
                    ]}
                    """;
        };

        var evaluator = TaskCompletionEvaluator.builder().judge(mockJudge).build();

        var testCase = EvalTestCase.builder()
                .input("Book a hotel in Paris")
                .metadata("tasks", List.of("Book hotel"))
                .metadata("constraints", "budget under $200")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldHandleMalformedJudgeResponse() {
        JudgeLM mockJudge = prompt -> "Not valid JSON at all";

        var evaluator = TaskCompletionEvaluator.builder().judge(mockJudge).build();

        var testCase = EvalTestCase.builder()
                .input("Book a hotel")
                .metadata("tasks", List.of("Book hotel"))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.success()).isFalse();
        assertThat(result.reason()).contains("Failed to parse");
    }

    @Test
    void shouldThrowWhenTasksMissing() {
        JudgeLM mockJudge = prompt -> "{}";

        var evaluator = TaskCompletionEvaluator.builder().judge(mockJudge).build();

        var testCase = EvalTestCase.builder().input("Book a hotel").build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(EvaluationException.class)
                .hasMessageContaining("tasks");
    }

    @Test
    void shouldThrowWhenJudgeNotSet() {
        assertThatThrownBy(() -> TaskCompletionEvaluator.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JudgeLM");
    }

    @Test
    void shouldRespectCustomThreshold() {
        JudgeLM mockJudge = prompt -> """
                {"tasks": [
                    {"task": "Book hotel", "completed": true, "reason": "Done"},
                    {"task": "Search flights", "completed": false, "reason": "Not done"}
                ]}
                """;

        var evaluator = TaskCompletionEvaluator.builder()
                .judge(mockJudge)
                .threshold(0.3)
                .build();

        var testCase = EvalTestCase.builder()
                .input("Do stuff")
                .metadata("tasks", List.of("Book hotel", "Search flights"))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.success()).isTrue(); // 0.5 >= 0.3
    }

    @Test
    void shouldHandleMarkdownWrappedResponse() {
        JudgeLM mockJudge = prompt -> """
                ```json
                {"tasks": [
                    {"task": "Book hotel", "completed": true, "reason": "Done"}
                ]}
                ```
                """;

        var evaluator = TaskCompletionEvaluator.builder().judge(mockJudge).build();

        var testCase = EvalTestCase.builder()
                .input("Book a hotel")
                .metadata("tasks", List.of("Book hotel"))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }
}
