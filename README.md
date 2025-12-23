# Dokimos: Evaluation for LLM applications in Java

Dokimos is a systematic approach to evaluate LLM outputs using datasets, metrics, and experiments in Java. It integrates
seamlessly with JUnit 5 for parameterized testing, and libraries, such
as [LangChain4j](https://github.com/langchain4j/langchain4j) for evaluation of sophisticated AI systems and agents.

## Features

- **Dataset-driven evaluation**: Load datasets from JSON, CSV, or custom sources
- **Built-in evaluators**: Exact match, regex, and LLM-based judges
- **JUnit 5 integration**: Parameterized tests with dataset sources
- **LangChain4j integration**: Evaluation of production-ready AI systems and agents
- **Experiment tracking**: Aggregate eval results with pass rates and scores
- **Extensible**: Build Custom evaluators and dataset resolvers via SPI

## Requirements

- Java 17 or higher
- Maven 3.6 or higher

## Installation

Add the desired modules to your `pom.xml`:

```xml

<dependencies>
    ...
    <!-- Core evaluation framework -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-core</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>

    <!-- JUnit 5 integration -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-junit5</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>

    <!-- LangChain4j integration -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-langchain4j</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>
    ...
</dependencies>
```

## Modules

- **dokimos-core**: Core evaluation framework with datasets, evaluators, and experiments
- **dokimos-junit5**: JUnit 5 integration for dataset-driven parameterized tests
- **dokimos-langchain4j**: LangChain4j integration for evaluation of production AI assistants and agents

## Examples

### dokimos-core: Basic Evaluation

```java
import dev.dokimos.core.*;

import java.util.List;
import java.util.Map;

public class BasicEvaluation {
    public static void main(String[] args) {
        // Create a dataset
        Dataset dataset = Dataset.builder()
                .name("QA Dataset")
                .addExample(Example.of("What is 2+2?", "4"))
                .addExample(Example.of("What is the capital of France?", "Paris"))
                .build();

        // Define evaluators
        List<Evaluator> evaluators = List.of(
                ExactMatchEvaluator.builder().build(),
                RegexEvaluator.builder()
                        .pattern("\\d+|[A-Z][a-z]+")
                        .build()
        );

        // Create a task for your LLM or system under test
        Task task = example -> {
            String answer = callYourLLM(example.input());
            return Map.of("output", answer);
        };

        // Run experiment
        ExperimentResult result = Experiment.builder()
                .name("QA Evaluation")
                .dataset(dataset)
                .task(task)
                .evaluators(evaluators)
                .build()
                .run();

        // Check results
        System.out.println("Pass rate: " + result.passRate());
        System.out.println("Average score: " + result.averageScore());
    }

    private static String callYourLLM(String input) {
        // Your LLM implementation
        return "Hi, how can I help you today?";
    }
}
```

### dokimos-junit5: Parameterized Testing

```java
import dev.dokimos.core.*;
import dev.dokimos.junit5.DatasetSource;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.List;

import static dev.dokimos.core.Assertions.assertEval;

public class QATest {

    @ParameterizedTest
    @DatasetSource("classpath:qa-dataset.json")
    void testQA(Example example) {
        // Get actual output from your system
        String actualOutput = callYourLLM(example.input());

        // Convert to test case
        EvalTestCase testCase = example.toTestCase(actualOutput);

        // Define evaluators
        List<Evaluator> evaluators = List.of(
                ExactMatchEvaluator.builder()
                        .threshold(1.0)
                        .build()
        );

        // Assert evaluation passes
        assertEval(testCase, evaluators);
    }

    private String callYourLLM(String input) {
        // Your LLM implementation
        return "answer";
    }
}
```

Dataset file (`src/test/resources/qa-dataset.json`):

```json
{
  "name": "QA Dataset",
  "description": "Question answering test cases",
  "examples": [
    {
      "inputs": {
        "input": "What is 2+2?"
      },
      "expectedOutputs": {
        "output": "4"
      }
    },
    {
      "inputs": {
        "input": "What is the capital of Switzerland?"
      },
      "expectedOutputs": {
        "output": "Bern"
      }
    }
  ]
}
```

### dokimos-langchain4j: Evaluation of production-ready AI assistants and agents

```java
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.dokimos.core.*;
import dev.dokimos.langchain4j.LangChain4jSupport;

import java.util.List;

public class RAGEvaluation {

    interface Assistant {
        Result<String> chat(String userMessage);
    }

    public static void main(String[] args) {
        // Build a RAG assistant
        ChatModel chatModel = createYourChatModel();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .retrievalAugmentor(createYourRetrievalAugmentor())
                .build();

        // Create a RAG task
        Task task = LangChain4jSupport.ragTask(assistant::chat);

        // Load the dataset
        Dataset dataset = Dataset.fromJson("path/to/dataset.json");

        // Define evaluators
        ChatModel judge = createYourJudgeModel();
        List<Evaluator> evaluators = List.of(
                LLMJudgeEvaluator.builder()
                        .name("Faithfulness")
                        .judge(LangChain4jSupport.asJudge(judge))
                        .criteria("Is the answer faithful to the retrieved context?")
                        .requiredInputs(List.of(
                                EvalTestCaseParam.ACTUAL_OUTPUT,
                                EvalTestCaseParam.INPUT
                        ))
                        .build(),
                LLMJudgeEvaluator.builder()
                        .name("Context Relevancy")
                        .judge(LangChain4jSupport.asJudge(judge))
                        .criteria("Is the retrieved context relevant to the question?")
                        .requiredInputs(List.of(
                                EvalTestCaseParam.INPUT
                        ))
                        .build()
        );

        // Run experiment
        ExperimentResult result = Experiment.builder()
                .name("Evaluation")
                .dataset(dataset)
                .task(task)
                .evaluators(evaluators)
                .build()
                .run();

        System.out.println("Pass rate: " + result.passRate());
    }

    private static ChatModel createYourChatModel() {
        // Your ChatModel implementation
        return null;
    }

    private static ChatModel createYourJudgeModel() {
        // Your judge model implementation
        return null;
    }

    private static Object createYourRetrievalAugmentor() {
        // Your retrieval augmentor implementation
        return null;
    }
}
```

## Documentation

Full API documentation is available
at: [https://dokimos-io.github.io/dokimos/](https://dokimos-io.github.io/dokimos/)

## Contributing

Contributions are welcome! Please feel free to submit a pull request or open an issue.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
