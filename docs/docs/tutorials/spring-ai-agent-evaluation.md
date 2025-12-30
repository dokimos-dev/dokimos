---
sidebar_position: 1
---

# LLM Evaluation with Spring AI and Dokimos: Building and Evaluating an AI Agent

This tutorial walks you through building a complete AI agent with Spring AI and systematically evaluating its performance using Dokimos in Java. You will create a knowledge assistant that retrieves documents from a vector store and generates contextual answers, then evaluate it across multiple quality dimensions.

By the end of this tutorial, you will have:
- A working Spring AI agent with RAG (Retrieval-Augmented Generation) capabilities
- A multi evaluator pipeline that checks faithfulness, hallucination, and answer quality
- Insights into how your agent performs and where it can improve

## Why Evaluate Your AI Agent?

Building an AI agent is just the start. The real challenge is ensuring it performs reliably in production and meets user expectations. Traditional testing approaches fall short when dealing with LLM applications because:

**LLM Outputs are nondeterministic**: The same question can produce different (but equally valid) answers. You cannot simply assert that output will always equal some expected string.

**Quality is multidimensional**: A response might be factually correct but poorly worded, or helpful but not grounded in your documents.

**Failures are subtle**: An agent might generate confident and at a first glance convincing but factually incorrect information.

Dokimos provides a systematic framework for evaluating LLM applications. It lets you define quality criteria, run evaluations, and track performance over time.

## What We Are Building

We will build a knowledge assistant for a fictional company's documentation. The assistant will:

1. Accept user questions about products, policies, and services
2. Retrieve relevant documents from a vector store
3. Generate contextual answers based on the retrieved documents

Then we will evaluate this assistant on multiple dimensions:
- **Faithfulness**: Are the responses grounded in the retrieved documents?
- **Answer Quality**: Are the responses helpful and complete?
- **Contextual Relevance**: Is the retriever finding relevant documents?
- **Hallucination Detection**: Is the agent making things up?

## Prerequisites

Before you begin, make sure you have:
- Java 21 or later
- Maven or Gradle
- An OpenAI API key (or another supported LLM provider)
- Basic familiarity with Spring Boot and Spring AI

## Project Setup

### Dependencies

Create a new Spring Boot project and add the following dependencies:

#### Maven

```xml
<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Spring AI -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    </dependency>

    <!-- Dokimos Core -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-core</artifactId>
        <version>${dokimos.version}</version>
    </dependency>

    <!-- Dokimos Spring AI Integration -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-spring-ai</artifactId>
        <version>${dokimos.version}</version>
    </dependency>

    <!-- For JUnit 5 integration -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-junit5</artifactId>
        <version>${dokimos.version}</version>
        <scope>test</scope>
    </dependency>

    <!-- Spring Boot Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

#### Gradle

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.ai:spring-ai-openai-spring-boot-starter'
    implementation 'dev.dokimos:dokimos-core:${dokimosVersion}'
    implementation 'dev.dokimos:dokimos-spring-ai:${dokimosVersion}'
    testImplementation 'dev.dokimos:dokimos-junit5:${dokimosVersion}'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

### Configuration

Add your OpenAI API key and model settings to `application.properties`:

```properties
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=gpt-5-nano
spring.ai.openai.chat.options.temperature=1.0
spring.ai.openai.embedding.options.model=text-embedding-3-small
```

Note: The `gpt-5-nano` model only supports `temperature=1.0`. If using a different model like `gpt-4o-mini`, you can omit the temperature setting.

The `SimpleVectorStore` requires an embedding model to convert text to vectors. We use OpenAI's `text-embedding-3-small` which is fast and cost-effective.

## Part 1: Building the AI Agent

We start by building our knowledge assistant. This is a simple RAG pipeline that retrieves documents and generates answers.

### Setting Up the Vector Store

First, we need a vector store to hold our company documents. For this tutorial, we will use Spring AI's `SimpleVectorStore`, which stores embeddings in-memory.

```java
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();

        // Load our company documents
        List<Document> documents = List.of(
            new Document(
                "Our return policy allows customers to return any product within 30 days " +
                "of purchase for a full refund. Items must be in original condition with " +
                "tags attached. Refunds are processed within 5 business days."
            ),
            new Document(
                "Premium members receive free shipping on all orders, 20% discount on " +
                "all products, early access to new releases, and priority customer support. " +
                "Premium membership costs $99 per year."
            ),
            new Document(
                "Our customer support team is available Monday through Friday from 9 AM " +
                "to 6 PM Eastern Time. You can reach us by email at support@example.com " +
                "or by phone at 1-800-EXAMPLE."
            ),
            new Document(
                "We offer three shipping options: Standard (5-7 business days, $5.99), " +
                "Express (2-3 business days, $12.99), and Next Day ($24.99). " +
                "Orders over $50 qualify for free standard shipping."
            ),
            new Document(
                "Gift cards are available in denominations of $25, $50, $100, and $200. " +
                "Gift cards never expire and can be used for any purchase on our website. " +
                "They cannot be redeemed for cash."
            )
        );

        store.add(documents);
        return store;
    }
}
```

### Creating the Knowledge Assistant

Now we create our AI agent that combines retrieval with generation:

```java
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class KnowledgeAssistant {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public KnowledgeAssistant(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    public AssistantResponse answer(String question) {
        // Step 1: Retrieve relevant documents
        List<Document> retrievedDocs = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(question)
                .topK(3)
                .build()
        );

        // Step 2: Build context from retrieved documents
        String context = retrievedDocs.stream()
            .map(Document::getText)
            .reduce("", (a, b) -> a + "\n\n" + b);

        // Step 3: Generate response using context
        String systemPrompt = """
            You are a helpful customer service assistant. Answer the user's question
            based ONLY on the provided context. If the context does not contain
            enough information to answer the question, say so clearly.

            Context:
            %s
            """.formatted(context);

        String response = chatClient.prompt()
            .system(systemPrompt)
            .user(question)
            .call()
            .content();

        // Return both the response and retrieved context for evaluation
        return new AssistantResponse(response, retrievedDocs);
    }

    public record AssistantResponse(
        String answer,
        List<Document> retrievedDocuments
    ) {}
}
```

### Exposing the Assistant as a REST API

To make the assistant usable as a real service, expose it via a REST endpoint:

```java
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class KnowledgeAssistantController {

    private final KnowledgeAssistant assistant;

    public KnowledgeAssistantController(KnowledgeAssistant assistant) {
        this.assistant = assistant;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        var response = assistant.answer(request.question());

        List<String> sources = response.retrievedDocuments().stream()
                .map(doc -> doc.getText())
                .toList();

        return ResponseEntity.ok(new ChatResponse(response.answer(), sources));
    }

    public record ChatRequest(String question) {}

    public record ChatResponse(String answer, List<String> sources) {}
}
```

You can now interact with your assistant:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "What is your return policy?"}'
```

## Part 2: Setting Up Evaluation with Dokimos

Now that we have a working assistant, we can evaluate it systematically. We create a dataset of test questions and run them through multiple evaluators.

### Creating the Evaluation Dataset

First, create a dataset with questions and expected behaviors:

```java
import dev.dokimos.core.Dataset;
import dev.dokimos.core.Example;

Dataset dataset = Dataset.builder()
    .name("Knowledge Assistant Evaluation")
    .addExample(Example.builder()
        .input("What is your return policy?")
        .expectedOutput("30 days, full refund, original condition")
        .metadata("category", "returns")
        .build())
    .addExample(Example.builder()
        .input("How much does premium membership cost?")
        .expectedOutput("$99 per year")
        .metadata("category", "membership")
        .build())
    .addExample(Example.builder()
        .input("What are your customer support hours?")
        .expectedOutput("Monday through Friday, 9 AM to 6 PM Eastern")
        .metadata("category", "support")
        .build())
    .addExample(Example.builder()
        .input("Do gift cards expire?")
        .expectedOutput("Gift cards never expire")
        .metadata("category", "gift-cards")
        .build())
    .addExample(Example.builder()
        .input("How can I get free shipping?")
        .expectedOutput("Orders over $50 or premium membership")
        .metadata("category", "shipping")
        .build())
    .addExample(Example.builder()
        .input("What is the fastest shipping option?")
        .expectedOutput("Next Day shipping for $24.99")
        .metadata("category", "shipping")
        .build())
    .addExample(Example.builder()
        .input("Can I return a product after 60 days?")
        .expectedOutput("No, returns must be within 30 days")
        .metadata("category", "returns")
        .build())
    .addExample(Example.builder()
        .input("What benefits do premium members get?")
        .expectedOutput("Free shipping, 20% discount, early access, priority support")
        .metadata("category", "membership")
        .build())
    .build();
```

Alternatively, you can also load datasets from JSON files for easier maintenance:

```json
{
  "name": "Knowledge Assistant Evaluation",
  "examples": [
    {
      "input": "What is your return policy?",
      "expectedOutput": "30 days, full refund, original condition",
      "metadata": { "category": "returns" }
    },
    {
      "input": "How much does premium membership cost?",
      "expectedOutput": "$99 per year",
      "metadata": { "category": "membership" }
    }
  ]
}
```

```java
Dataset dataset = Dataset.fromJson(Paths.get("src/test/resources/datasets/qa-dataset.json"));
```

### Defining the Evaluation Task

The Task interface bridges your application with Dokimos. It takes an example and returns the outputs that evaluators will check:

```java
import dev.dokimos.core.Task;
import org.springframework.ai.document.Document;

Task evaluationTask = example -> {
    // Run our assistant
    var response = assistant.answer(example.input());

    // Extract context texts for evaluation
    List<String> contextTexts = response.retrievedDocuments().stream()
        .map(Document::getText)
        .toList();

    // Return outputs for evaluators to check
    return Map.of(
        "output", response.answer(),
        "context", contextTexts
    );
};
```

The key insight here is that we return both the answer and the retrieved context. This allows evaluators to check not just what the agent said, but whether it was grounded in the documents it retrieved.

### Setting Up the LLM Judge

Dokimos uses the LLM as Judge pattern for semantic evaluation. We will use Spring AI's `ChatModel` as our judge:

```java
import dev.dokimos.core.JudgeLM;
import dev.dokimos.springai.SpringAiSupport;
import org.springframework.ai.chat.model.ChatModel;

@Autowired
private ChatModel chatModel;

// Convert Spring AI ChatModel to Dokimos JudgeLM
JudgeLM judge = SpringAiSupport.asJudge(chatModel);
```

:::tip Using a Different Model for Judging

For better evaluation quality, consider using a more capable model as your judge. You can configure a separate ChatModel bean specifically for evaluation:

```java
@Bean
@Qualifier("judgeModel")
public ChatModel judgeModel() {
    return OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .model("gpt-5.2")
        .build();
}
```

:::

## Part 3: Configuring Multiple Evaluators

Next, set up evaluators to check different quality dimensions. Dokimos provides several built-in evaluators, and you can create custom ones for specific needs.

:::caution API Costs

LLM based evaluators (FaithfulnessEvaluator, HallucinationEvaluator, LLMJudgeEvaluator, ContextualRelevanceEvaluator) make API calls to your judge model for each test case. Running evaluations on large datasets can incur significant costs. Start with a small dataset (10 to 20 examples) during development, and consider using a more cost effective model for judging later.

:::

### Faithfulness Evaluator

The FaithfulnessEvaluator checks whether the response is grounded in the retrieved context. This is crucial for RAG systems where you want to ensure the agent is not making things up.

```java
import dev.dokimos.core.evaluators.FaithfulnessEvaluator;

Evaluator faithfulness = FaithfulnessEvaluator.builder()
    .threshold(0.8)
    .judge(judge)
    .contextKey("context")  // Key where we stored retrieved documents
    .includeReason(true)    // Get explanation for the score
    .build();
```

The evaluator works by:
1. Breaking down the response into individual claims
2. Checking each claim against the retrieved context
3. Calculating score = (supported claims) / (total claims)

A score of 0.8 means 80% of the claims in the response are supported by the context.

### Hallucination Evaluator

While faithfulness measures how much is grounded, the HallucinationEvaluator specifically measures the proportion of fabricated content:

```java
import dev.dokimos.core.evaluators.HallucinationEvaluator;

Evaluator hallucination = HallucinationEvaluator.builder()
    .threshold(0.2)  // Allow at most 20% hallucinated content
    .judge(judge)
    .contextKey("context")
    .includeReason(true)
    .build();
```

**Important:** For this evaluator, lower scores are better. A score of 0.0 means no hallucinations were detected. The evaluator passes when `score <= threshold`.

### Answer Quality Evaluator

The LLMJudgeEvaluator lets you define custom evaluation criteria in natural language:

```java
import dev.dokimos.core.evaluators.LLMJudgeEvaluator;
import dev.dokimos.core.EvalTestCaseParam;

Evaluator answerQuality = LLMJudgeEvaluator.builder()
    .name("Answer Quality")
    .criteria("""
        Evaluate the answer based on these criteria:
        1. Does it directly address the user's question?
        2. Is it clear and easy to understand?
        3. Does it provide specific, actionable information?
        4. Is it appropriately concise without missing key details?
        """)
    .evaluationParams(List.of(
        EvalTestCaseParam.INPUT,
        EvalTestCaseParam.ACTUAL_OUTPUT
    ))
    .threshold(0.7)
    .judge(judge)
    .build();
```

### Contextual Relevance Evaluator

This evaluator checks whether the retriever is finding relevant documents for each query:

```java
import dev.dokimos.core.evaluators.ContextualRelevanceEvaluator;

Evaluator contextRelevance = ContextualRelevanceEvaluator.builder()
    .threshold(0.6)
    .judge(judge)
    .retrievalContextKey("context")
    .includeReason(true)
    .build();
```

The evaluator scores each retrieved chunk independently and calculates the mean. This helps identify when your retriever is returning irrelevant documents that could confuse the LLM.

### Combining All Evaluators

Put all evaluators together:

```java
List<Evaluator> evaluators = List.of(
    // Check if response is grounded in context
    FaithfulnessEvaluator.builder()
        .threshold(0.8)
        .judge(judge)
        .contextKey("context")
        .includeReason(true)
        .build(),

    // Check for hallucinated content
    HallucinationEvaluator.builder()
        .threshold(0.2)
        .judge(judge)
        .contextKey("context")
        .includeReason(true)
        .build(),

    // Check answer quality
    LLMJudgeEvaluator.builder()
        .name("Answer Quality")
        .criteria("Is the answer helpful, clear, and directly addresses the question?")
        .evaluationParams(List.of(
            EvalTestCaseParam.INPUT,
            EvalTestCaseParam.ACTUAL_OUTPUT
        ))
        .threshold(0.7)
        .judge(judge)
        .build(),

    // Check retrieval quality
    ContextualRelevanceEvaluator.builder()
        .threshold(0.6)
        .judge(judge)
        .retrievalContextKey("context")
        .includeReason(true)
        .build()
);
```

## Part 4: Running the Evaluation Experiment

With our dataset, task, and evaluators ready, we can run a full evaluation experiment:

```java
import dev.dokimos.core.Experiment;
import dev.dokimos.core.ExperimentResult;

ExperimentResult result = Experiment.builder()
    .name("Knowledge Assistant v1.0 Evaluation")
    .description("Evaluating the RAG based knowledge assistant")
    .dataset(dataset)
    .task(evaluationTask)
    .evaluators(evaluators)
    .metadata("model", "gpt-5-nano")
    .metadata("retrievalTopK", 3)
    .metadata("timestamp", Instant.now().toString())
    .build()
    .run();
```

### Analyzing Results

The experiment returns aggregate metrics and detailed per example results:

```java
// Overall metrics
System.out.println("=== Experiment Results ===");
System.out.println("Name: " + result.name());
System.out.println("Total examples: " + result.totalCount());
System.out.println("Passed: " + result.passCount());
System.out.println("Failed: " + result.failCount());
System.out.println("Pass rate: " + String.format("%.1f%%", result.passRate() * 100));

// Per evaluator metrics
System.out.println("\n=== Average Scores by Evaluator ===");
System.out.println("Faithfulness: " + String.format("%.2f", result.averageScore("Faithfulness")));
System.out.println("Hallucination: " + String.format("%.2f", result.averageScore("Hallucination")));
System.out.println("Answer Quality: " + String.format("%.2f", result.averageScore("Answer Quality")));
System.out.println("Contextual Relevance: " + String.format("%.2f", result.averageScore("ContextualRelevance")));
```

### Investigating Failures

When tests fail, you can dig into the details:

```java
System.out.println("\n=== Failed Cases ===");
for (ItemResult item : result.itemResults()) {
    if (!item.success()) {
        System.out.println("\nQuestion: " + item.example().input());
        System.out.println("Expected: " + item.example().expectedOutput());
        System.out.println("Actual: " + item.actualOutputs().get("output"));

        System.out.println("Evaluator Results:");
        for (EvalResult eval : item.evalResults()) {
            String status = eval.success() ? "PASS" : "FAIL";
            System.out.println("  " + eval.name() + ": " + status +
                " (score: " + String.format("%.2f", eval.score()) + ")");
            if (!eval.success() && eval.reason() != null) {
                System.out.println("    Reason: " + eval.reason());
            }
        }
    }
}
```

## Part 5: Integrating with JUnit 5

For continuous integration, you can run evaluations as part of your test suite using the Dokimos JUnit 5 integration.

### Organizing Evaluators

In a real application, you will want to organize your evaluators in a reusable way. Create a factory class that encapsulates your evaluation configuration:

```java
package com.example.evaluation;

import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.Evaluator;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.evaluators.*;

import java.util.List;

public final class QAEvaluators {

    public static final String CONTEXT_KEY = "context";

    private QAEvaluators() {}

    public static List<Evaluator> standard(JudgeLM judge) {
        return List.of(
                faithfulness(judge),
                hallucination(judge),
                answerQuality(judge),
                contextualRelevance(judge)
        );
    }

    public static Evaluator faithfulness(JudgeLM judge) {
        return FaithfulnessEvaluator.builder()
                .threshold(0.8)
                .judge(judge)
                .contextKey(CONTEXT_KEY)
                .includeReason(true)
                .build();
    }

    public static Evaluator hallucination(JudgeLM judge) {
        return HallucinationEvaluator.builder()
                .threshold(0.2)
                .judge(judge)
                .contextKey(CONTEXT_KEY)
                .includeReason(true)
                .build();
    }

    public static Evaluator answerQuality(JudgeLM judge) {
        return LLMJudgeEvaluator.builder()
                .name("Answer Quality")
                .criteria("""
                        Evaluate the answer based on:
                        1. Does it directly address the user's question?
                        2. Is it clear and easy to understand?
                        3. Does it provide specific, actionable information?
                        4. Is it appropriately concise?
                        """)
                .evaluationParams(List.of(
                        EvalTestCaseParam.INPUT,
                        EvalTestCaseParam.ACTUAL_OUTPUT
                ))
                .threshold(0.7)
                .judge(judge)
                .build();
    }

    public static Evaluator contextualRelevance(JudgeLM judge) {
        return ContextualRelevanceEvaluator.builder()
                .threshold(0.6)
                .judge(judge)
                .retrievalContextKey(CONTEXT_KEY)
                .includeReason(true)
                .build();
    }
}
```

This approach keeps your evaluation logic separate from your application code and makes it easy to reuse across different tests.

### Writing the Evaluation Test

Now write a clean test that uses the evaluator factory:

```java
import dev.dokimos.core.Assertions;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.Evaluator;
import dev.dokimos.core.Example;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.junit5.DatasetSource;
import dev.dokimos.springai.SpringAiSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class KnowledgeAssistantEvaluationTest {

    @Autowired
    private KnowledgeAssistant assistant;

    @Autowired
    private ChatModel chatModel;

    private List<Evaluator> evaluators;

    @BeforeEach
    void setup() {
        JudgeLM judge = SpringAiSupport.asJudge(chatModel);
        evaluators = QAEvaluators.standard(judge);
    }

    @ParameterizedTest
    @DatasetSource("classpath:datasets/qa-dataset.json")
    void shouldProvideQualityAnswers(Example example) {
        var response = assistant.answer(example.input());

        List<String> contextTexts = response.retrievedDocuments().stream()
                .map(Document::getText)
                .toList();

        EvalTestCase testCase = EvalTestCase.builder()
                .input(example.input())
                .actualOutput(response.answer())
                .actualOutput(QAEvaluators.CONTEXT_KEY, contextTexts)
                .expectedOutput(example.expectedOutput())
                .build();

        Assertions.assertEval(testCase, evaluators);
    }
}
```

### Running in CI/CD

Add this to your GitHub Actions workflow:

```yaml
name: AI Agent Evaluation

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  evaluate:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run Evaluations
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
        run: mvn test -Dtest=KnowledgeAssistantEvaluationTest
```

## Part 6: Tracking Results Over Time

For production applications, you want to track evaluation results over time. The Dokimos Server provides a web UI for visualizing trends and comparing experiments.

### Starting the Server

Download the Docker Compose file and start the server:

```bash
curl -O https://raw.githubusercontent.com/dokimos-dev/dokimos/main/docker-compose.yml
docker compose up -d
```

The server will be available at `http://localhost:8080`.

### Sending Results to the Server

Use `DokimosServerReporter` to send experiment results to the server:

```java
import dev.dokimos.server.client.DokimosServerReporter;

var reporter = DokimosServerReporter.builder()
    .serverUrl("http://localhost:8080")
    .projectName("knowledge-assistant")
    .build();

ExperimentResult result = Experiment.builder()
    .name("Knowledge Assistant v1.0")
    .dataset(dataset)
    .task(evaluationTask)
    .evaluators(evaluators)
    .reporter(reporter)
    .build()
    .run();
```

The reporter batches results and sends them to the server during experiment execution. After the experiment completes, you can view the results in the web UI.

The server stores results and lets you:
- View pass rates and scores over time
- Compare different model configurations
- Drill down into specific failures
- Share results with your team

## Part 7: Creating Custom Evaluators

Sometimes the built in evaluators do not cover your specific needs. You can create custom evaluators by extending `BaseEvaluator`. Place these in your evaluation package alongside `QAEvaluators`:

```java
package com.example.evaluation;

import dev.dokimos.core.BaseEvaluator;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;

import java.util.List;

/**
 * Custom evaluator that checks if the response length is within acceptable bounds.
 * This demonstrates a deterministic evaluator that does not require an LLM judge.
 */
public class ResponseLengthEvaluator extends BaseEvaluator {

    private final int minWords;
    private final int maxWords;

    public ResponseLengthEvaluator(int minWords, int maxWords) {
        super("Response Length", 1.0, List.of(EvalTestCaseParam.ACTUAL_OUTPUT));
        this.minWords = minWords;
        this.maxWords = maxWords;
    }

    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        String output = testCase.actualOutput();
        int wordCount = output.split("\\s+").length;

        boolean withinBounds = wordCount >= minWords && wordCount <= maxWords;
        double score = withinBounds ? 1.0 : 0.0;
        String reason = String.format(
            "Response has %d words (expected %d-%d)",
            wordCount, minWords, maxWords);

        return EvalResult.builder()
            .name(name())
            .score(score)
            .threshold(threshold())
            .reason(reason)
            .build();
    }
}
```

You can then add it to your evaluator factory:

```java
// In QAEvaluators.java
public static Evaluator responseLength(int minWords, int maxWords) {
    return new ResponseLengthEvaluator(minWords, maxWords);
}
```

## Part 8: Advanced Evaluation Patterns

### Evaluating Precision and Recall

For RAG systems where you have ground truth labels for relevant documents, you can measure traditional IR (Information Retrieval) metrics, such as precision and recall:

```java
import dev.dokimos.core.evaluators.PrecisionEvaluator;
import dev.dokimos.core.evaluators.RecallEvaluator;
import dev.dokimos.core.evaluators.MatchingStrategy;

// Example with document IDs
var example = Example.builder()
    .input("What is your return policy?")
    .expectedOutput("relevantDocs", List.of("doc-returns-1", "doc-returns-2"))
    .build();

Task taskWithDocIds = example -> {
    var response = assistant.answer(example.input());

    List<String> retrievedIds = response.retrievedDocuments().stream()
        .map(doc -> doc.getMetadata().get("id").toString())
        .toList();

    return Map.of(
        "output", response.answer(),
        "retrievedDocs", retrievedIds
    );
};

Evaluator precision = PrecisionEvaluator.builder()
    .name("Retrieval Precision")
    .retrievedKey("retrievedDocs")
    .expectedKey("relevantDocs")
    .matchingStrategy(MatchingStrategy.byEquality())
    .threshold(0.8)
    .build();

Evaluator recall = RecallEvaluator.builder()
    .name("Retrieval Recall")
    .retrievedKey("retrievedDocs")
    .expectedKey("relevantDocs")
    .matchingStrategy(MatchingStrategy.byEquality())
    .threshold(0.8)
    .build();
```

### Flexible Matching Strategies

Dokimos provides various matching strategies for comparing retrieved items:

```java
// Case insensitive matching
MatchingStrategy.caseInsensitive()

// Match by a specific field in objects
MatchingStrategy.byField("id")

// Match by multiple fields
MatchingStrategy.byFields("subject", "predicate", "object")

// Substring containment
MatchingStrategy.byContainment(true)

// LLM based semantic matching (most flexible)
MatchingStrategy.llmBased(judge)

// Combine strategies
MatchingStrategy.anyOf(strategy1, strategy2)  // OR
MatchingStrategy.allOf(strategy1, strategy2)  // AND
```

### Async Evaluation

For large datasets, you can run evaluations asynchronously:

```java
// Single evaluator async
CompletableFuture<EvalResult> future = evaluator.evaluateAsync(testCase);

// With custom executor for parallel evaluation
ExecutorService executor = Executors.newFixedThreadPool(4);
CompletableFuture<EvalResult> future = evaluator.evaluateAsync(testCase, executor);
```

## Best Practices

### Start with a Small, High Quality Dataset

Do not try to build a huge dataset upfront. Start with 10 to 20 carefully crafted examples that cover your main use cases. Add more examples as you discover edge cases and failures.

### Use Multiple Evaluators

Different evaluators catch different problems:
- **Faithfulness** catches responses that stray from the context
- **Hallucination** quantifies fabricated content
- **Answer Quality** catches unhelpful or unclear responses
- **Contextual Relevance** identifies retrieval issues

### Set Realistic Thresholds

Do not expect perfection right away. Start with achievable thresholds (around 0.7) and increase them as you improve your system. A threshold of 1.0 means any imperfection fails.

### Run Evaluations Regularly

Integrate evaluations into your CI/CD pipeline. Run quick evaluations on every PR with a small dataset, and full evaluations nightly or weekly with larger datasets.

## Conclusion

Evaluating AI agents is essential for building reliable applications. In this tutorial, you learned how to:

1. Build a RAG based knowledge assistant with Spring AI and expose it as a REST API
2. Create evaluation datasets with examples and expected outputs
3. Organize evaluators in a reusable factory class
4. Configure multiple evaluators for different quality dimensions
5. Integrate evaluations with JUnit 5 for CI/CD
6. Track results over time with the Dokimos Server
7. Create custom evaluators for domain specific needs

The structure we built separates concerns cleanly: the application code handles user requests, while the evaluation code lives in its own package and can be run independently via tests. This mirrors how you would structure a production application.

The combination of Spring AI for building and Dokimos for evaluating provides a complete toolkit for developing production ready AI applications in Java.

## Next Steps

- Explore the [Evaluators documentation](/evaluation/evaluators) for all available evaluators
- Learn about [Datasets](/evaluation/datasets) for advanced dataset management
- Set up the [Dokimos Server](/server/overview) for result tracking
- Check out the [JUnit 5 integration](/integrations/junit5) for test driven evaluation

## Resources

- [Tutorial Example Code](https://github.com/dokimos-dev/dokimos/tree/main/dokimos-examples/src/main/java/dev/dokimos/examples/springai/tutorial) - The complete working code from this tutorial
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Dokimos GitHub Repository](https://github.com/dokimos-dev/dokimos)

---

If you found this tutorial helpful, please consider giving the repository a star on GitHub. It helps others discover the project and keeps us motivated to improve it ⭐.
