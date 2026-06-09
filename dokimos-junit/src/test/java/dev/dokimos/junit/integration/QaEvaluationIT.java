package dev.dokimos.junit.integration;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import dev.dokimos.core.*;
import dev.dokimos.core.evaluators.LLMJudgeEvaluator;
import dev.dokimos.junit.DatasetSource;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;

@Tag("integration")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class QaEvaluationIT {

    private static List<Evaluator> evaluators;

    @BeforeAll
    static void setup() {
        OpenAIClient client = OpenAIOkHttpClient.fromEnv();

        JudgeLM llm = prompt -> {
            var params = ChatCompletionCreateParams.builder()
                    .addUserMessage(prompt)
                    .model(ChatModel.GPT_5_NANO)
                    .build();
            return client.chat()
                    .completions()
                    .create(params)
                    .choices()
                    .get(0)
                    .message()
                    .content()
                    .orElse("");
        };

        evaluators = List.of(LLMJudgeEvaluator.builder()
                .name("answer-correctness")
                .criteria("Does the answer correctly answer the question? Treat it as correct when it"
                        + " conveys the expected answer's meaning, even if it adds further accurate detail"
                        + " or uses different wording.")
                .evaluationParams(List.of(
                        EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT, EvalTestCaseParam.EXPECTED_OUTPUT))
                .threshold(0.5)
                .judge(llm)
                .build());
    }

    @ParameterizedTest
    @DatasetSource("classpath:datasets/refund-qa.json")
    void shouldAnswerRefundQuestionsCorrectly(Example example) {
        String aiResponse = simulateAssistant(example.input());

        var testCase = example.toTestCase(aiResponse);
        Assertions.assertEval(testCase, evaluators);
    }

    private String simulateAssistant(String question) {
        return "You can get a full refund within 30 days of purchase. No return is allowed after 30 days.";
    }
}
