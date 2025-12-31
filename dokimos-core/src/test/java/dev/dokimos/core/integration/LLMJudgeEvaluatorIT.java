package dev.dokimos.core.integration;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.evaluators.LLMJudgeEvaluator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link LLMJudgeEvaluator} across multiple OpenAI
 * models.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class LLMJudgeEvaluatorIT {

    private static OpenAIClient client;

    @BeforeAll
    static void setup() {
        client = OpenAIOkHttpClient.fromEnv();
    }

    static Stream<Arguments> models() {
        return Stream.of(
                Arguments.of(ChatModel.GPT_4O_MINI),
                Arguments.of(ChatModel.GPT_5_NANO),
                Arguments.of(ChatModel.GPT_5_MINI));
    }

    private JudgeLM createJudge(ChatModel model) {
        return prompt -> {
            var params = ChatCompletionCreateParams.builder()
                    .addUserMessage(prompt)
                    .model(model)
                    .build();
            return client.chat().completions().create(params)
                    .choices().get(0).message().content().orElse("");
        };
    }

    @ParameterizedTest(name = "shouldEvaluateCorrectness [{0}]")
    @MethodSource("models")
    void shouldEvaluateCorrectness(ChatModel model) {
        var evaluator = LLMJudgeEvaluator.builder()
                .name("correctness")
                .criteria(
                        "Determine if the actual output conveys the same meaning as the expected output. " +
                                "Score 1.0 if they match semantically, 0.0 if completely different.")
                .evaluationParams(List.of(EvalTestCaseParam.ACTUAL_OUTPUT, EvalTestCaseParam.EXPECTED_OUTPUT))
                .threshold(0.7)
                .judge(createJudge(model))
                .build();

        var testCase = EvalTestCase.builder()
                .input("What is the refund policy?")
                .actualOutput("You can get a full refund within 30 days")
                .expectedOutput("We offer a 30-day full refund")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score())
                .as("Score from model %s should indicate semantic match", model)
                .isBetween(0.7, 1.0);
        assertThat(result.success()).isTrue();
        assertThat(result.reason()).isNotBlank();
    }

    @ParameterizedTest(name = "shouldDetectSemanticMismatch [{0}]")
    @MethodSource("models")
    void shouldDetectSemanticMismatch(ChatModel model) {
        var evaluator = LLMJudgeEvaluator.builder()
                .name("correctness")
                .criteria(
                        "Determine if the actual output conveys the same meaning as the expected output. " +
                                "Score 1.0 if they match semantically, 0.0 if completely different.")
                .evaluationParams(List.of(EvalTestCaseParam.ACTUAL_OUTPUT, EvalTestCaseParam.EXPECTED_OUTPUT))
                .threshold(0.7)
                .judge(createJudge(model))
                .build();

        var testCase = EvalTestCase.builder()
                .input("What is the refund policy?")
                .actualOutput("Our store is located in New York City")
                .expectedOutput("We offer a 30-day full refund")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score())
                .as("Score from model %s should indicate semantic mismatch", model)
                .isLessThan(0.5);
        assertThat(result.success()).isFalse();
        assertThat(result.reason()).isNotBlank();
    }

    @ParameterizedTest(name = "shouldReturnValidEvalResult [{0}]")
    @MethodSource("models")
    void shouldReturnValidEvalResult(ChatModel model) {
        var evaluator = LLMJudgeEvaluator.builder()
                .name("quality-check")
                .criteria("Rate the quality and relevance of the response to the input question.")
                .evaluationParams(List.of(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
                .threshold(0.5)
                .judge(createJudge(model))
                .build();

        var testCase = EvalTestCase.builder()
                .input("What is the capital of France?")
                .actualOutput("The capital of France is Paris.")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.name()).isEqualTo("quality-check");
        assertThat(result.score())
                .as("Score from model %s should be within valid range", model)
                .isBetween(0.0, 1.0);
        assertThat(result.reason())
                .as("Model %s should provide a reason", model)
                .isNotBlank();
    }
}
