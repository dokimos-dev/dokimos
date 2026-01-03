package dev.dokimos.examples.junit;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import dev.dokimos.core.*;
import dev.dokimos.core.evaluators.FaithfulnessEvaluator;
import dev.dokimos.core.evaluators.LLMJudgeEvaluator;
import dev.dokimos.junit.DatasetSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.List;
import java.util.Map;

@Tag("example")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class QuestionAnsweringTest {

        private static List<Evaluator> evaluators;
        private static OpenAIClient client;

        @BeforeAll
        static void setup() {
                client = OpenAIOkHttpClient.fromEnv();

                JudgeLM judge = QuestionAnsweringTest::generate;

                // Define the list of evaluators we want to run
                evaluators = List.of(
                                LLMJudgeEvaluator.builder()
                                                .name("answer-correctness")
                                                .criteria("Does the actual output answer the user's question correctly?")
                                                .evaluationParams(List.of(EvalTestCaseParam.INPUT,
                                                                EvalTestCaseParam.EXPECTED_OUTPUT,
                                                                EvalTestCaseParam.ACTUAL_OUTPUT))
                                                .threshold(0.8)
                                                .judge(judge)
                                                .build(),
                                FaithfulnessEvaluator.builder()
                                                .judge(judge)
                                                .threshold(0.5)
                                                .contextKey("retrievedContext")
                                                .includeReason(false)
                                                .build());
        }

        private static String generate(String prompt) {
                var params = ChatCompletionCreateParams.builder()
                                .addUserMessage(prompt)
                                .model(ChatModel.GPT_5_NANO)
                                .build();

                return client.chat().completions().create(params)
                                .choices().get(0).message().content().orElse("");
        }

        @ParameterizedTest
        @DatasetSource("classpath:datasets/qa-dataset.json")
        void shouldPassEvaluators(Example example) {
                // This is just an example context, that could be retrieved from
                // a vector store, for example
                List<String> retrievedContext = List.of(
                                "1. Bern is the capital city of Switzerland",
                                "2. Paris is the capital of France");
                var response = generate(example.input());

                // In this case, we use a `Map` to provide both, the generated answer,
                // and the retrieved context to the outputs
                var testCase = example.toTestCase(Map.of(
                                "output", response,
                                "retrievedContext", retrievedContext));

                // Run the evaluation for the test case
                Assertions.assertEval(testCase, evaluators);
        }
}
