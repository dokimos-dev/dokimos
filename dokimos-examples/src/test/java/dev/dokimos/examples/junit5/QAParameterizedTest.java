package dev.dokimos.examples.junit5;

import dev.dokimos.core.*;
import dev.dokimos.core.evaluators.ExactMatchEvaluator;
import dev.dokimos.junit.DatasetSource;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.List;

import static dev.dokimos.core.Assertions.assertEval;

/**
 * JUnit parameterized test example that demonstates how to:
 * - Use @DatasetSource annotation to load test cases from a JSON file
 * - Run parameterized tests for each example in the dataset
 * - Use assertEval to verify LLM outputs against expected results
 *
 * Run with: mvn test -pl dokimos-examples
 */
public class QAParameterizedTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @DatasetSource("classpath:datasets/qa-dataset.json")
    void testQA(Example example) {
        // Get actual output from your system
        String actualOutput = callYourLLM(example.input());

        // Convert to test case
        EvalTestCase testCase = example.toTestCase(actualOutput);

        // Define evaluators
        List<Evaluator> evaluators = List.of(
                ExactMatchEvaluator.builder()
                        .name("Exact Match")
                        .threshold(1.0)
                        .build());

        // Assert evaluation passes - will fail the test if evaluation fails
        assertEval(testCase, evaluators);
    }

    /**
     * Simulates an LLM call. In a real application, this would call
     * an actual LLM API (OpenAI, Anthropic, local model, etc.)
     */
    private String callYourLLM(String input) {
        // Simple rule-based responses to demonstrate the evaluation
        return switch (input) {
            case "What is 2+2?" -> "4";
            case "What is the capital of France?" -> "Paris";
            case "What is the capital of Switzerland?" -> "Bern";
            case "What is 10 + 5?" -> "15";
            default -> "I don't know";
        };
    }
}