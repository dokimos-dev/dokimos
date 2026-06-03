package dev.dokimos.examples.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import dev.dokimos.core.Assertions;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.Evaluator;
import dev.dokimos.core.Example;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.evaluators.ExactMatchEvaluator;
import dev.dokimos.core.evaluators.LLMJudgeEvaluator;
import dev.dokimos.core.evaluators.StructuralMatchEvaluator;
import dev.dokimos.core.evaluators.StructuralMatchMode;
import dev.dokimos.junit.DatasetSource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;

/**
 * Companion code for the "Evaluate LLM output in JUnit" tutorial.
 *
 * <p>Each test calls a real model through the OpenAI Java SDK Responses API and evaluates the
 * response with Dokimos. The tests are tagged {@code integration} and gated on {@code
 * OPENAI_API_KEY}, so they never run in normal CI (only with {@code mvn verify -Dgroups=integration}
 * and a key present).
 *
 * <p>The tutorial shows {@link ChatModel#GPT_5_2}; this code compiles against the openai-java SDK on
 * the classpath and only needs a key at runtime.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class EvaluateLlmOutputInJUnitTest {

    private static final OpenAIClient CLIENT = OpenAIOkHttpClient.fromEnv();
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Sends a prompt to the model and returns the concatenated output text. */
    private static String ask(String prompt) {
        Response response = CLIENT.responses()
                .create(ResponseCreateParams.builder()
                        .model(ChatModel.GPT_5_2)
                        .input(prompt)
                        .build());
        return response.output().stream()
                .filter(item -> item.isMessage())
                .flatMap(item -> item.asMessage().content().stream())
                .filter(content -> content.isOutputText())
                .map(content -> content.asOutputText().text())
                .reduce("", String::concat)
                .trim();
    }

    /** A JudgeLM backed by a cheaper model, used by {@link LLMJudgeEvaluator}. */
    private static JudgeLM judge() {
        return prompt -> CLIENT
                .responses()
                .create(ResponseCreateParams.builder()
                        .model(ChatModel.GPT_5_MINI)
                        .input(prompt)
                        .build())
                .output()
                .stream()
                .filter(item -> item.isMessage())
                .flatMap(item -> item.asMessage().content().stream())
                .filter(content -> content.isOutputText())
                .map(content -> content.asOutputText().text())
                .reduce("", String::concat);
    }

    /**
     * Deterministic check: a short factual answer should match exactly. Drives many cases from a
     * dataset resource with a single test method via {@code @DatasetSource}.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @DatasetSource("classpath:datasets/junit-tutorial-qa.json")
    void factualAnswerMatchesExactly(Example example) {
        String answer = ask(example.input());

        EvalTestCase testCase = example.toTestCase(answer);
        Evaluator exactMatch =
                ExactMatchEvaluator.builder().name("Exact Match").threshold(1.0).build();

        Assertions.assertEval(testCase, exactMatch);
    }

    /**
     * Open-ended check: exact match does not fit, so an LLM judge scores the answer against
     * natural-language criteria.
     */
    @Test
    void openEndedAnswerIsHelpful() {
        String answer = ask("In one sentence, what does an LLM evaluation framework do?");

        EvalTestCase testCase = EvalTestCase.builder()
                .input("What does an LLM evaluation framework do?")
                .actualOutput(answer)
                .build();
        Evaluator helpfulness = LLMJudgeEvaluator.builder()
                .name("Helpfulness")
                .criteria("Is the answer accurate, clear, and genuinely helpful?")
                .evaluationParams(List.of(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
                .threshold(0.7)
                .judge(judge())
                .build();

        Assertions.assertEval(testCase, helpfulness);
    }

    /** Typed view of the structured weather payload the model is asked to return. */
    record WeatherReport(String city, int temperatureCelsius, String condition) {}

    /**
     * Structured-output check: ask for JSON, compare it field-by-field with {@link
     * StructuralMatchEvaluator}, and read it back through the typed accessor.
     */
    @Test
    void structuredOutputMatchesContract() throws Exception {
        String raw = ask("Return ONLY compact JSON with keys city (string), temperatureCelsius "
                + "(integer), and condition (string) for this report: it is 21 degrees Celsius and "
                + "sunny in Paris. Do not wrap it in markdown.");

        Map<String, Object> actual = JSON.readValue(raw, new TypeReference<>() {});

        EvalTestCase testCase = EvalTestCase.builder()
                .input("weather report for Paris")
                .actualOutput("output", actual)
                .expectedOutput("output", Map.of("city", "Paris", "temperatureCelsius", 21, "condition", "sunny"))
                .build();

        Evaluator structuralMatch = StructuralMatchEvaluator.builder()
                .name("Structural Match")
                .mode(StructuralMatchMode.LENIENT)
                .threshold(1.0)
                .build();

        Assertions.assertEval(testCase, structuralMatch);

        // Typed accessor: read the same output back as a record, no manual map juggling.
        WeatherReport report = testCase.actualOutputAs(WeatherReport.class);
        assertEquals("Paris", report.city());
        assertEquals(21, report.temperatureCelsius());
    }
}
