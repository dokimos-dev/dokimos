package io.dokimos.core.integration;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.OpenAIIoException;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import io.dokimos.core.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@Tag("integration")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class LLMJudgeEvaluatorIT {

    private static JudgeLM llm;

    @BeforeAll
    static void setup() {
        OpenAIClient client = OpenAIOkHttpClient.fromEnv();

        llm = prompt -> {
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .addUserMessage(prompt)
                    .model(ChatModel.GPT_5_NANO)
                    .build();

            ChatCompletion chatCompletion = client.chat().completions().create(params);
            return chatCompletion.choices().getFirst().message().content().orElse("");
        };
    }

    @Test
    void shouldEvaluateCorrectness() {
        var evaluator = LLMJudgeEvaluator.builder()
                .name("correctness")
                .criteria("Determine if the actual output conveys the same meaning as the expected output. Score 1.0 if they match semantically, 0.0 if completely different.")
                .evaluationParams(List.of(EvalTestCaseParam.ACTUAL_OUTPUT, EvalTestCaseParam.EXPECTED_OUTPUT))
                .threshold(0.7)
                .judge(llm)
                .build();

        var testCase = EvalTestCase.builder()
                .input("What is the refund policy?")
                .actualOutput("You can get a full refund within 30 days")
                .expectedOutput("We offer a 30-day full refund")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isGreaterThan(0.7);
        assertThat(result.success()).isTrue();
    }
}
