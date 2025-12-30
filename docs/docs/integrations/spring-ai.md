---
sidebar_position: 3
---

# Spring AI Integration

Dokimos works with [Spring AI](https://spring.io/projects/spring-ai) so you can evaluate your AI applications using Spring AI's `ChatClient` and `ChatModel`.

## Why Use This Integration?

**Simple conversion**: Turn a Spring AI `ChatClient` or `ChatModel` into a Dokimos-compatible `JudgeLM` for evaluation with one line of code.

**Spring AI compatibility**: Use your existing Spring AI infrastructure for evaluation without additional setup.

**Flexible evaluation**: Bridge between Spring AI's `EvaluationRequest`/`EvaluationResponse` and the Dokimos evaluation framework.

## Setup

Add the Spring AI integration dependency:

### Maven

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-spring-ai</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

### Gradle (Groovy DSL)

```groovy
implementation 'dev.dokimos:dokimos-spring-ai:${dokimosVersion}'
```

## Basic Usage

### Using ChatClient as LLM Judge

Convert a Spring AI `ChatClient` to a `JudgeLM` for evaluation:

```java
import dev.dokimos.core.*;
import dev.dokimos.core.evaluators.*;
import dev.dokimos.springai.SpringAiSupport;
import org.springframework.ai.chat.client.ChatClient;

ChatClient.Builder clientBuilder = ChatClient.builder(chatModel);

// Convert to JudgeLM
JudgeLM judge = SpringAiSupport.asJudge(clientBuilder);

// Use in evaluators
Evaluator correctness = LLMJudgeEvaluator.builder()
    .name("Answer Correctness")
    .criteria("Is the answer factually correct?")
    .judge(judge)
    .threshold(0.8)
    .build();
```

### Using ChatModel as LLM Judge

You can also convert a `ChatModel` directly:

```java
import dev.dokimos.core.*;
import dev.dokimos.core.evaluators.*;
import dev.dokimos.springai.SpringAiSupport;
import org.springframework.ai.openai.OpenAiChatModel;

ChatModel chatModel = OpenAiChatModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .model("gpt-5.2")
    .build();

// Convert to JudgeLM
JudgeLM judge = SpringAiSupport.asJudge(chatModel);

// Use in evaluators
Evaluator faithfulness = FaithfulnessEvaluator.builder()
    .threshold(0.7)
    .judge(judge)
    .build();
```

## Evaluating Spring AI Applications

### Converting Between Spring AI and Dokimos

Convert Spring AI `EvaluationRequest` to Dokimos `EvalTestCase`:

```java
import dev.dokimos.core.*;
import dev.dokimos.springai.SpringAiSupport;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.document.Document;

// Create Spring AI EvaluationRequest
List<Document> retrievedDocs = List.of(
    new Document("30-day money-back guarantee"),
    new Document("Contact support for refunds")
);

EvaluationRequest request = new EvaluationRequest(
    "What is the refund policy?",           // user text
    retrievedDocs,                           // retrieved documents
    "We offer a 30-day refund policy."      // response content
);

// Convert to Dokimos EvalTestCase
EvalTestCase testCase = SpringAiSupport.toTestCase(request);

// Run evaluation
EvalResult result = faithfulnessEvaluator.evaluate(testCase);

// Convert back to Spring AI EvaluationResponse
EvaluationResponse response = SpringAiSupport.toEvaluationResponse(result);

// Check results
System.out.println("Passed: " + response.isPass());
System.out.println("Score: " + response.getMetadata().get("score"));
System.out.println("Feedback: " + response.getFeedback());
```

### Complete Evaluation Example

```java
import dev.dokimos.core.*;
import dev.dokimos.core.evaluators.*;
import dev.dokimos.springai.SpringAiSupport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;

public class SpringAiEvaluation {

    public static void main(String[] args) {
        // 1. Set up ChatModel
        ChatModel chatModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .model("gpt-5.2")
            .build();

        // 2. Create a dataset
        Dataset dataset = Dataset.builder()
            .name("customer-qa")
            .addExample(Example.of(
                "What is your return policy?",
                "30-day money-back guarantee"
            ))
            .addExample(Example.of(
                "How can I contact support?",
                "Email support@example.com"
            ))
            .build();

        // 3. Create Task
        Task task = example -> {
            String response = chatModel.call(example.input());
            return Map.of("output", response);
        };

        // 4. Set up evaluators with Spring AI judge
        ChatModel judgeModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName("gpt-4o")
            .build();

        JudgeLM judge = SpringAiSupport.asJudge(judgeModel);

        List<Evaluator> evaluators = List.of(
            LLMJudgeEvaluator.builder()
                .name("Answer Quality")
                .criteria("Is the answer helpful and accurate?")
                .judge(judge)
                .threshold(0.8)
                .build(),
            new ExactMatchEvaluator()
        );

        // 5. Run experiment
        ExperimentResult result = Experiment.builder()
            .name("Spring AI Evaluation")
            .dataset(dataset)
            .task(task)
            .evaluators(evaluators)
            .build()
            .run();

        // 6. Display results
        System.out.println("Pass rate: " +
            String.format("%.0f%%", result.passRate() * 100));
        System.out.println("Answer Quality: " +
            String.format("%.2f", result.averageScore("Answer Quality")));
    }
}
```

:::tip

Also see the [Datasets](../evaluation/datasets.md) and [Evaluators](../evaluation/evaluators) documentation for more details on creating and loading datasets, and using evaluators.

:::

## Bridging Spring AI Evaluators

If you're using Spring AI's built-in evaluators and want to integrate with Dokimos:

```java
import dev.cokimos.core.*;
import dev.cokimos.core.evaluators.*;
import dev.dokimos.springai.SpringAiSupport;
import org.springframework.ai.evaluation.RelevancyEvaluator;

// Spring AI evaluator
RelevancyEvaluator springAiEvaluator = new RelevancyEvaluator(
    ChatClient.builder(chatModel)
);

// Create Spring AI EvaluationRequest
EvaluationRequest request = new EvaluationRequest(
    userQuestion,
    retrievedDocuments,
    generatedResponse
);

// Evaluate with Spring AI
EvaluationResponse springAiResponse = springAiEvaluator.evaluate(request);

// Convert to Dokimos for tracking in experiments
EvalTestCase testCase = SpringAiSupport.toTestCase(request);

// You can also create a custom Dokimos evaluator that wraps Spring AI evaluators
Evaluator dokimosEvaluator = new BaseEvaluator("relevancy") {
    @Override
    protected EvalResult doEvaluate(EvalTestCase testCase) {
        // Convert Dokimos -> Spring AI -> evaluate -> convert back
        EvaluationRequest req = /* build from testCase */;
        EvaluationResponse resp = springAiEvaluator.evaluate(req);

        return EvalResult.builder()
            .name(name())
            .score(resp.getMetadata().get("score"))
            .success(resp.isPass())
            .reason(resp.getFeedback())
            .build();
    }
};
```

## Working with RAG in Spring AI

When evaluating RAG systems built with Spring AI:

```java
import dev.dokimos.core.*;
import dev.dokimos.core.evaluators.FaithfulnessEvaluator;
import dev.dokimos.springai.SpringAiSupport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

// Your RAG setup
VectorStore vectorStore = /* your vector store */;
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(
        new QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults())
    )
    .build();

// Create evaluation task
Task ragTask = example -> {
    String query = example.input();

    // Retrieve documents
    List<Document> retrieved = vectorStore.similaritySearch(
        SearchRequest.query(query).withTopK(3)
    );

    // Generate response
    String response = chatClient.prompt()
        .user(query)
        .call()
        .content();

    // Extract the context texts
    List<String> context = retrieved.stream()
        .map(Document::getText)
        .toList();

    return Map.of(
        "output", response,
        "context", context
    );
};

// Evaluate faithfulness
JudgeLM judge = SpringAiSupport.asJudge(chatModel);

Evaluator faithfulness = FaithfulnessEvaluator.builder()
    .threshold(0.8)
    .judge(judge)
    .build();

ExperimentResult result = Experiment.builder()
    .dataset(dataset)
    .task(ragTask)
    .evaluators(List.of(faithfulness))
    .build()
    .run();
```

## Field Mappings

### EvaluationRequest -> EvalTestCase

When converting from Spring AI to Dokimos:

| Spring AI | Dokimos |
|-----------|---------|
| `getUserText()` | `inputs["input"]` |
| `getResponseContent()` | `actualOutputs["output"]` |
| `getDataList()` | `actualOutputs["context"]` (as `List<String>`) |

### EvalResult -> EvaluationResponse

When converting from Dokimos back to Spring AI:

| Dokimos | Spring AI |
|---------|-----------|
| `success()` | `isPass()` |
| `score()` | `metadata["score"]` |
| `reason()` | `getFeedback()` |
| `metadata()` | `getMetadata()` (merged with score) |

## Best Practices

**Combine with Spring Boot**: In a Spring Boot application, you can inject your ChatModel beans and use them directly for evaluation:

```java
@Component
public class AiEvaluationService {

    private final ChatModel chatModel;

    public AiEvaluationService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public ExperimentResult evaluate(Dataset dataset, Task task) {
        JudgeLM judge = SpringAiSupport.asJudge(chatModel);

        return Experiment.builder()
            .dataset(dataset)
            .task(task)
            .evaluators(List.of(
                FaithfulnessEvaluator.builder()
                    .judge(judge)
                    .build()
            ))
            .build()
            .run();
    }
}
```

## JUnit 5 Integration

Combine with [JUnit 5](./junit5) for testing:

```java
import dev.dokimos.junit5.DatasetSource;
import org.junit.jupiter.params.ParameterizedTest;

@ParameterizedTest
@DatasetSource("classpath:datasets/qa-dataset-v1.json")
void chatResponseShouldBeAccurate(Example example) {
    // Generate response with Spring AI
    String response = chatClient.prompt()
        .user(example.input())
        .call()
        .content();

    // Create test case
    EvalTestCase testCase = EvalTestCase.of(
        example.input(),
        response,
        example.expectedOutput()
    );

    // Assert with evaluator
    Assertions.assertEval(testCase, exactMatchEvaluator);
}
```

### Threshold-Based Quality Assertions

The parameterized test above fails if *any single example* fails evaluation. For many use cases, you may want to assert that the *average score* across all examples meets a quality threshold instead. This is useful when:

- Individual examples may occasionally score below the threshold, but overall quality should be high
- You want to set different thresholds for different evaluators
- You're running quality gates in CI/CD pipelines

```java
import dev.dokimos.core.*;
import dev.dokimos.core.evaluators.*;
import dev.dokimos.springai.SpringAiSupport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Test
void experimentMeetsQualityThresholds() {
    Dataset dataset = DatasetResolverRegistry.getInstance()
        .resolve("classpath:datasets/qa-dataset.json");

    JudgeLM judge = SpringAiSupport.asJudge(chatModel);

    List<Evaluator> evaluators = List.of(
        FaithfulnessEvaluator.builder()
            .judge(judge)
            .contextKey("context")
            .build(),
        ContextualRelevanceEvaluator.builder()
            .judge(judge)
            .retrievalContextKey("context")
            .build(),
        LLMJudgeEvaluator.builder()
            .name("Answer Quality")
            .criteria("Is the answer helpful, clear, and accurate?")
            .judge(judge)
            .build()
    );

    ExperimentResult result = Experiment.builder()
        .name("Agent Evaluation")
        .dataset(dataset)
        .task(task)
        .evaluators(evaluators)
        .build()
        .run();

    // Assert each evaluator's average meets 0.8
    assertAll(
        () -> assertTrue(result.averageScore("Faithfulness") >= 0.8,
            "Faithfulness: " + result.averageScore("Faithfulness")),
        () -> assertTrue(result.averageScore("ContextualRelevance") >= 0.8,
            "ContextualRelevance: " + result.averageScore("ContextualRelevance")),
        () -> assertTrue(result.averageScore("Answer Quality") >= 0.8,
            "Answer Quality: " + result.averageScore("Answer Quality"))
    );
}
```

:::tip

Use `assertAll` to run all assertions and report all failures at once, rather than stopping at the first failure. This gives you a complete picture of which quality thresholds are not being met.

:::

## Integration with Spring AI Testing

You can use Dokimos evaluators alongside Spring AI's testing utilities to create comprehensive test suites for your AI applications.
