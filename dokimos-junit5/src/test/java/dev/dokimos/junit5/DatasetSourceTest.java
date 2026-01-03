package dev.dokimos.junit5;

import dev.dokimos.core.*;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DatasetSourceTest {

  private final Evaluator passingEvaluator = new Evaluator() {
    @Override
    public EvalResult evaluate(EvalTestCase testCase) {
      return EvalResult.success("mockEval", 1.0, "Looks good!");
    }

    @Override
    public String name() {
      return "mockEvaluator";
    }

    @Override
    public double threshold() {
      return 0.5;
    }
  };

  @ParameterizedTest
  @DatasetSource("classpath:datasets/sample.json")
  void shouldLoadFromClasspath(Example example) {
    assertThat(example.input()).isNotBlank();
    assertThat(example.expectedOutput()).isNotBlank();
  }

  @ParameterizedTest
  @DatasetSource(json = """
      {
        "examples": [
          {"input": "What is 2+2?", "expectedOutput": "4"},
          {"input": "What is 3*3?", "expectedOutput": "9"}
        ]
      }
      """)
  void shouldLoadFromInlineJson(Example example) {
    assertThat(example.input()).isNotBlank();
  }

  @ParameterizedTest
  @DatasetSource(json = """
      {
        "examples": [
          {"input": "Hello", "expectedOutput": "Hi"}
        ]
      }
      """)
  void shouldSupportEvaluators(Example example) {
    String actualOutput = "Hi, how can I help you today?";
    var testCase = example.toTestCase(actualOutput);

    Assertions.assertEval(testCase, List.of(passingEvaluator));
  }

  @ParameterizedTest
  @DatasetSource(json = """
      {
        "examples": [
          {"input": "Capital of France?", "expectedOutput": "Paris"},
          {"input": "Capital of Germany?", "expectedOutput": "Berlin"},
          {"input": "Capital of Italy?", "expectedOutput": "Rome"}
        ]
      }
      """)
  void shouldRunMultipleExamples(Example example) {
    // This will run 3 times, once for each example
    assertThat(example.input()).contains("Capital");
  }

}
