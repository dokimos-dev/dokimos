---
sidebar_position: 1
---

# LLM Evaluation with Spring AI and Dokimos: Building and Evaluating an AI Agent

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

This page shows you how to build a RAG agent with Spring AI and score its answers with Dokimos, in Java and Kotlin. You build a knowledge assistant that retrieves documents and writes answers, then you measure how good those answers are.

By the end you will have:

- A working Spring AI agent with RAG (Retrieval-Augmented Generation).
- An evaluator pipeline that checks faithfulness, hallucination, and answer quality.
- A clear read on how the agent performs and where it falls short.

Want to run the finished code first? Clone the [tutorial example](https://github.com/dokimos-dev/dokimos/tree/master/dokimos-examples/src/main/java/dev/dokimos/examples/springai/tutorial) and come back. Everything below builds it step by step.

## Why Evaluate Your AI Agent?

Shipping an agent is the easy part. Knowing it stays correct in production is the hard part. Normal tests do not fit LLM apps for three reasons:

**LLM outputs change run to run.** The same question can return different answers that are both fine. You cannot assert that output equals one fixed string.

**Quality has many dimensions.** An answer can be correct but unclear, or helpful but not backed by your documents.

**Failures hide.** An agent can sound confident and still state something false.

Dokimos gives you a repeatable way to check LLM apps. You define quality criteria, run them, and watch the scores over time.

## What We Are Building

We build a knowledge assistant for a fictional company's docs. The assistant will:

1. Take user questions about products, policies, and services.
2. Retrieve matching documents from a vector store.
3. Write an answer based on those documents.

Then we measure the assistant on four dimensions:

- **Faithfulness**: Are the answers backed by the retrieved documents?
- **Answer Quality**: Are the answers helpful and complete?
- **Contextual Relevance**: Is the retriever finding the right documents?
- **Hallucination Detection**: Is the agent making things up?

## Prerequisites

Before you start, make sure you have:

- Java 21 or later
- Maven or Gradle
- An OpenAI API key (or another supported LLM provider)
- Basic familiarity with Spring Boot and Spring AI

## Project Setup

### Dependencies

Create a Spring Boot project. Then add these dependencies.

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

    <!-- Dokimos Kotlin Integration (Optional) -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-kotlin</artifactId>
        <version>${dokimos.version}</version>
    </dependency>

    <!-- For JUnit integration -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-junit</artifactId>
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
    implementation 'dev.dokimos:dokimos-kotlin:${dokimosVersion}' //optional for Kotlin projects
    testImplementation 'dev.dokimos:dokimos-junit:${dokimosVersion}'
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

Note: The `gpt-5-nano` model only supports `temperature=1.0`. If you use a different model like `gpt-4o-mini`, you can drop the temperature setting.

The `SimpleVectorStore` needs an embedding model to turn text into vectors. We use OpenAI's `text-embedding-3-small`, which is fast and cheap.

## Part 1: Building the AI Agent

We start with the assistant. It is a small RAG pipeline: retrieve documents, then write an answer.

### Setting Up the Vector Store

First, we need a store for the company documents. We use Spring AI's `SimpleVectorStore`, which keeps embeddings in memory.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.SimpleVectorStore
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class VectorStoreConfig {

    @Bean
    fun vectorStore(embeddingModel: EmbeddingModel): VectorStore {
        val store = SimpleVectorStore.builder(embeddingModel).build()

        // Load our company documents
        val documents = listOf(
            Document(
                "Our return policy allows customers to return any product within 30 days " +
                "of purchase for a full refund. Items must be in original condition with " +
                "tags attached. Refunds are processed within 5 business days."
            ),
            Document(
                "Premium members receive free shipping on all orders, 20% discount on " +
                "all products, early access to new releases, and priority customer support. " +
                "Premium membership costs $99 per year."
            ),
            Document(
                "Our customer support team is available Monday through Friday from 9 AM " +
                "to 6 PM Eastern Time. You can reach us by email at support@example.com " +
                "or by phone at 1-800-EXAMPLE."
            ),
            Document(
                "We offer three shipping options: Standard (5-7 business days, $5.99), " +
                "Express (2-3 business days, $12.99), and Next Day ($24.99). " +
                "Orders over $50 qualify for free standard shipping."
            ),
            Document(
                "Gift cards are available in denominations of $25, $50, $100, and $200. " +
                "Gift cards never expire and can be used for any purchase on our website. " +
                "They cannot be redeemed for cash."
            )
        )

        store.add(documents)
        return store
    }
}
```

  </TabItem>
</Tabs>

### Creating the Knowledge Assistant

Now create the agent. It retrieves documents, then generates an answer from them.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service

@Service
class KnowledgeAssistant(
    chatClientBuilder: ChatClient.Builder,
    private val vectorStore: VectorStore
) {
    private val chatClient: ChatClient = chatClientBuilder.build()

    fun answer(question: String): AssistantResponse {
        // Step 1: Retrieve relevant documents
        val retrievedDocs: List<Document> = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(question)
                .topK(3)
                .build()
        )

        // Step 2: Build context from retrieved documents
        val context = retrievedDocs.joinToString(separator = "\n\n") { it.text }

        // Step 3: Generate response using context
        val systemPrompt = """
            You are a helpful customer service assistant. Answer the user's question
            based ONLY on the provided context. If the context does not contain
            enough information to answer the question, say so clearly.

            Context:
            %s
        """.trimIndent().format(context)

        val response = chatClient.prompt()
            .system(systemPrompt)
            .user(question)
            .call()
            .content()

        return AssistantResponse(response, retrievedDocs)
    }

    data class AssistantResponse(
        val answer: String,
        val retrievedDocuments: List<Document>
    )
}
```

  </TabItem>
</Tabs>

The assistant returns both the answer and the retrieved documents. Keep both around. The evaluators need the documents to check whether the answer is grounded.

### Exposing the Assistant as a REST API

Wrap the assistant in a REST endpoint so you can call it as a service.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class KnowledgeAssistantController(
    private val assistant: KnowledgeAssistant
) {

    @PostMapping("/chat")
    fun chat(@RequestBody request: ChatRequest): ResponseEntity<ChatResponse> {
        val response = assistant.answer(request.question)
        val sources = response.retrievedDocuments.map { it.text }
        return ResponseEntity.ok(ChatResponse(response.answer, sources))
    }

    data class ChatRequest(val question: String)
    data class ChatResponse(val answer: String, val sources: List<String>)
}
```

  </TabItem>
</Tabs>

Start the app, then call it:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "What is your return policy?"}'
```

## Part 2: Setting Up Evaluation with Dokimos

The assistant works. Now we score it. We build a dataset of test questions and run each one through the evaluators.

### Creating the Evaluation Dataset

Build a dataset of questions and the answers you expect.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.kotlin.dsl.dataset
import dev.dokimos.kotlin.dsl.example

val dataset = dataset {
    name = "Knowledge Assistant Evaluation"
    example {
        input = "What is your return policy?"
        expected = "30 days, full refund, original condition"
        metadata("category", "returns")
    }
    example {
        input = "How much does premium membership cost?"
        expected = "$99 per year"
        metadata("category", "membership")
    }
    example {
        input = "What are your customer support hours?"
        expected = "Monday through Friday, 9 AM to 6 PM Eastern"
        metadata("category", "support")
    }
    example {
        input = "Do gift cards expire?"
        expected = "Gift cards never expire"
        metadata("category", "gift-cards")
    }
    example {
        input = "How can I get free shipping?"
        expected = "Orders over $50 or premium membership"
        metadata("category", "shipping")
    }
    example {
        input = "What is the fastest shipping option?"
        expected = "Next Day shipping for $24.99"
        metadata("category", "shipping")
    }
    example {
        input = "Can I return a product after 60 days?"
        expected = "No, returns must be within 30 days"
        metadata("category", "returns")
    }
    example {
        input = "What benefits do premium members get?"
        expected = "Free shipping, 20% discount, early access, priority support"
        metadata("category", "membership")
    }
}
```

  </TabItem>
</Tabs>

You can also load a dataset from a JSON file. This keeps the examples out of your code and easier to edit.

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

Load it with one call:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Dataset dataset = Dataset.fromJson(Paths.get("src/test/resources/datasets/qa-dataset.json"));
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import java.nio.file.Paths

val dataset = Dataset.fromJson(Paths.get("src/test/resources/datasets/qa-dataset.json"))
```

  </TabItem>
</Tabs>

### Defining the Evaluation Task

The `Task` connects your app to Dokimos. It takes one example, runs your assistant, and returns the outputs the evaluators will check.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.kotlin.dsl.task

val evaluationTask = task { example ->
    // Run our assistant
    val response = assistant.answer(example.input())

    // Extract context texts for evaluation
    val contextTexts = response.retrievedDocuments.map { it.text }

    // Return outputs for evaluators to check
    mapOf(
        "output" to response.answer,
        "context" to contextTexts
    )
}
```

  </TabItem>
</Tabs>

The task returns the answer under `"output"` and the retrieved documents under `"context"`. With both in hand, the evaluators can check not only what the agent said, but whether the documents back it up.

### Setting Up the LLM Judge

Several evaluators use an LLM as a judge to score answers. We wrap Spring AI's `ChatModel` as a Dokimos `JudgeLM`.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.JudgeLM;
import dev.dokimos.springai.SpringAiSupport;
import org.springframework.ai.chat.model.ChatModel;

@Autowired
private ChatModel chatModel;

// Convert Spring AI ChatModel to Dokimos JudgeLM
JudgeLM judge = SpringAiSupport.asJudge(chatModel);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.JudgeLM
import dev.dokimos.springai.SpringAiSupport
import org.springframework.ai.chat.model.ChatModel
import org.springframework.beans.factory.annotation.Autowired

@Autowired
val chatModel: ChatModel

val judge: JudgeLM = SpringAiSupport.asJudge(chatModel)
```

  </TabItem>
</Tabs>

:::tip Using a Different Model for Judging

A stronger model makes a better judge. Define a separate `ChatModel` bean just for evaluation:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
@Bean
@Qualifier("judgeModel")
fun judgeModel(): ChatModel = OpenAiChatModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .model("gpt-5.2")
    .build()
```

  </TabItem>
</Tabs>

:::

## Part 3: Configuring Multiple Evaluators

Now set up the evaluators, one per quality dimension. Dokimos ships several built-in evaluators, and you can write your own.

:::caution API Costs

The LLM based evaluators (`FaithfulnessEvaluator`, `HallucinationEvaluator`, `LLMJudgeEvaluator`, `ContextualRelevanceEvaluator`) call your judge model once per test case. Large datasets cost real money. Start with 10 to 20 examples while you build, and pick a cheaper judge model when you scale up.

:::

### Faithfulness Evaluator

The `FaithfulnessEvaluator` checks that the answer is backed by the retrieved context. This is the core check for RAG: it catches answers that drift away from the documents.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.evaluators.FaithfulnessEvaluator;

Evaluator faithfulness = FaithfulnessEvaluator.builder()
    .threshold(0.8)
    .judge(judge)
    .contextKey("context")  // Key where we stored retrieved documents
    .includeReason(true)    // Get explanation for the score
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val faithfulness = faithfulness(judge) {
    threshold = 0.8
    contextKey = "context"  // Key where we stored retrieved documents
    includeReason = true     // Get explanation for the score
}
```

  </TabItem>
</Tabs>

Here is how it scores:

1. It splits the answer into individual claims.
2. It checks each claim against the retrieved context.
3. It computes score = (supported claims) / (total claims).

A score of 0.8 means 80% of the claims in the answer are backed by the context.

### Hallucination Evaluator

Faithfulness measures how much is grounded. The `HallucinationEvaluator` measures the opposite: how much is made up.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.evaluators.HallucinationEvaluator;

Evaluator hallucination = HallucinationEvaluator.builder()
    .threshold(0.2)  // Allow at most 20% hallucinated content
    .judge(judge)
    .contextKey("context")
    .includeReason(true)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val hallucination = hallucination(judge) {
    threshold = 0.2  // Allow at most 20% hallucinated content
    contextKey = "context"
    includeReason = true
}
```

  </TabItem>
</Tabs>

**Important:** For this evaluator, lower is better. A score of 0.0 means no hallucinations. It passes when `score <= threshold`.

### Answer Quality Evaluator

The `LLMJudgeEvaluator` lets you write your own criteria in plain English.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val answerQuality = llmJudge(judge) {
    name = "Answer Quality"
    criteria = """
        Evaluate the answer based on these criteria:
        1. Does it directly address the user's question?
        2. Is it clear and easy to understand?
        3. Does it provide specific, actionable information?
        4. Is it appropriately concise without missing key details?
    """.trimIndent()
    params(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT)
    threshold = 0.7
}
```

  </TabItem>
</Tabs>

### Contextual Relevance Evaluator

This evaluator checks whether the retriever pulled the right documents for each question.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.evaluators.ContextualRelevanceEvaluator;

Evaluator contextRelevance = ContextualRelevanceEvaluator.builder()
    .threshold(0.6)
    .judge(judge)
    .retrievalContextKey("context")
    .includeReason(true)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val contextRelevance = contextualRelevance(judge) {
    threshold = 0.6
    retrievalContextKey = "context"
    includeReason = true
}
```

  </TabItem>
</Tabs>

It scores each retrieved chunk on its own, then takes the mean. Use it to spot a retriever that returns junk documents and confuses the LLM.

### Combining All Evaluators

Put the four evaluators into one list.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val evaluators = evaluators {
    // Check if response is grounded in context
    faithfulness(judge) {
        threshold = 0.8
        contextKey = "context"
        includeReason = true
    }

    // Check for hallucinated content
    hallucination(judge) {
        threshold = 0.2
        contextKey = "context"
        includeReason = true
    }

    // Check answer quality
    llmJudge(judge) {
        name = "Answer Quality"
        criteria = "Is the answer helpful, clear, and directly addresses the question?"
        params(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT)
        threshold = 0.7
    }

    // Check retrieval quality
    contextualRelevance(judge) {
        threshold = 0.6
        retrievalContextKey = "context"
        includeReason = true
    }
}
```

  </TabItem>
</Tabs>

## Part 4: Running the Evaluation Experiment

Dataset, task, and evaluators are ready. Run the full experiment.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.kotlin.dsl.experiment

val result = experiment {
    name = "Knowledge Assistant v1.0 Evaluation"
    description = "Evaluating the RAG based knowledge assistant"
    dataset(dataset)
    task(evaluationTask)
    evaluators(evaluators)
    metadata("model", "gpt-5-nano")
    metadata("retrievalTopK", 3)
    metadata("timestamp", Instant.now().toString())
}.run()
```

  </TabItem>
</Tabs>

### Analyzing Results

The result holds both the totals and the per example detail. Print the totals first.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// Overall metrics
println("=== Experiment Results ===")
println("Name: ${result.name()}")
println("Total examples: ${result.totalCount()}")
println("Passed: ${result.passCount()}")
println("Failed: ${result.failCount()}")
println("Pass rate: ${"%.1f".format(result.passRate() * 100)}%")

// Per evaluator metrics
println("\n=== Average Scores by Evaluator ===")
println("Faithfulness: ${"%.2f".format(result.averageScore("Faithfulness"))}")
println("Hallucination: ${"%.2f".format(result.averageScore("Hallucination"))}")
println("Answer Quality: ${"%.2f".format(result.averageScore("Answer Quality"))}")
println("Contextual Relevance: ${"%.2f".format(result.averageScore("ContextualRelevance"))}")
```

  </TabItem>
</Tabs>

### Investigating Failures

When a case fails, open it up. Print the question, the expected and actual answers, and each evaluator's score with its reason.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
println("\n=== Failed Cases ===")
result.itemResults().forEach { item ->
    if (!item.success()) {
        println("\nQuestion: ${item.example().input()}")
        println("Expected: ${item.example().expectedOutput()}")
        println("Actual: ${item.actualOutputs()["output"]}")

        println("Evaluator Results:")
        item.evalResults().forEach { eval ->
            val status = if (eval.success()) "PASS" else "FAIL"
            println("  ${eval.name()}: $status (score: ${"%.2f".format(eval.score())})")
            if (!eval.success() && eval.reason() != null) {
                println("    Reason: ${eval.reason()}")
            }
        }
    }
}
```

  </TabItem>
</Tabs>

## Part 5: Integrating with JUnit

Run the same evaluations from your test suite so they fire in CI. Use the Dokimos JUnit integration.

### Organizing Evaluators

Pull the evaluator setup into a factory class. This keeps the config in one place and lets every test reuse it.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
package com.example.evaluation

import dev.dokimos.core.Evaluator
import dev.dokimos.core.JudgeLM
import dev.dokimos.core.EvalTestCaseParam
import dev.dokimos.kotlin.dsl.contextualRelevance
import dev.dokimos.kotlin.dsl.faithfulness
import dev.dokimos.kotlin.dsl.hallucination
import dev.dokimos.kotlin.dsl.llmJudge

object QAEvaluators {
    const val CONTEXT_KEY = "context"

    fun standard(judge: JudgeLM): List<Evaluator> = listOf(
        faithfulness(judge) {
            threshold = 0.8
            contextKey = CONTEXT_KEY
            includeReason = true
        },
        hallucination(judge) {
            threshold = 0.2
            contextKey = CONTEXT_KEY
            includeReason = true
        },
        llmJudge(judge) {
            name = "Answer Quality"
            criteria = "Is the answer helpful, clear, and directly addresses the question?"
            params(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT)
            threshold = 0.7
        },
        contextualRelevance(judge) {
            threshold = 0.6
            retrievalContextKey = CONTEXT_KEY
            includeReason = true
        }
    )
}
```

  </TabItem>
</Tabs>

The factory keeps evaluation config out of your app code and lets every test reuse the same setup.

### Writing the Evaluation Test

Now write a short test that calls the factory.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.Assertions;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.Evaluator;
import dev.dokimos.core.Example;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.junit.DatasetSource;
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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.Assertions
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.Evaluator
import dev.dokimos.core.Example
import dev.dokimos.core.JudgeLM
import dev.dokimos.junit.DatasetSource
import dev.dokimos.kotlin.core.EvalTestCase
import dev.dokimos.springai.SpringAiSupport
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.springframework.ai.chat.model.ChatModel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class KnowledgeAssistantEvaluationTest {

    @Autowired
    private lateinit var assistant: KnowledgeAssistant

    @Autowired
    private lateinit var chatModel: ChatModel

    private lateinit var evaluators: List<Evaluator>

    @BeforeEach
    fun setup() {
        val judge: JudgeLM = SpringAiSupport.asJudge(chatModel)
        evaluators = QAEvaluators.standard(judge)
    }

    @ParameterizedTest
    @DatasetSource("classpath:datasets/qa-dataset.json")
    fun shouldProvideQualityAnswers(example: Example) {
        val response = assistant.answer(example.input())

        val contextTexts = response.retrievedDocuments.map { it.text }

        val testCase = EvalTestCase(
            input = example.input(),
            actualOutput = response.answer,
            actualOutputs = mapOf(QAEvaluators.CONTEXT_KEY to contextTexts),
            expectedOutputs = mapOf("output" to (example.expectedOutput() ?: ""))
        )

        Assertions.assertEval(testCase, evaluators)
    }
}
```

  </TabItem>
</Tabs>

### Running in CI/CD

Add a job to your GitHub Actions workflow.

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

In production you want the scores plotted over time, not just printed once. The Dokimos Server gives you a web UI for trends and run comparisons.

### Starting the Server

Download the Docker Compose file and start the server.

```bash
curl -O https://raw.githubusercontent.com/dokimos-dev/dokimos/master/docker-compose.yml
docker compose up -d
```

The server runs at `http://localhost:8080`.

### Sending Results to the Server

Add a `DokimosServerReporter` to the experiment. It ships your results to the server.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.kotlin.dsl.experiment
import dev.dokimos.server.client.DokimosServerReporter

val reporter = DokimosServerReporter.builder()
    .serverUrl("http://localhost:8080")
    .projectName("knowledge-assistant")
    .build()

val result = experiment {
    name = "Knowledge Assistant v1.0"
    dataset(dataset)
    task(evaluationTask)
    evaluators(evaluators)
    reporter(reporter)
}.run()
```

  </TabItem>
</Tabs>

The reporter batches results and sends them while the experiment runs. When it finishes, open the web UI.

On the server you can:

- See pass rates and scores over time.
- Compare different model setups.
- Drill into specific failures.
- Share results with your team.

## Part 7: Creating Custom Evaluators

When the built-in evaluators do not fit, write your own by extending `BaseEvaluator`. Put it in the evaluation package next to `QAEvaluators`.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
package com.example.evaluation

import dev.dokimos.core.BaseEvaluator
import dev.dokimos.core.EvalResult
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.EvalTestCaseParam

/**
 * Custom evaluator that checks if the response length is within acceptable bounds.
 * This demonstrates a deterministic evaluator that does not require an LLM judge.
 */
class ResponseLengthEvaluator(
    private val minWords: Int,
    private val maxWords: Int
) : BaseEvaluator("Response Length", 1.0, listOf(EvalTestCaseParam.ACTUAL_OUTPUT)) {

    override fun runEvaluation(testCase: EvalTestCase): EvalResult {
        val output = testCase.actualOutput()
        val wordCount = output.split("\s+".toRegex()).size

        val withinBounds = wordCount in minWords..maxWords
        val score = if (withinBounds) 1.0 else 0.0
        val reason = "Response has $wordCount words (expected $minWords-$maxWords)"

        return EvalResult(
            name = name(),
            score = score,
            threshold = threshold(),
            reason = reason)
    }
}
```

  </TabItem>
</Tabs>

This one is deterministic, so it needs no LLM judge. Now wire it into the factory.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// In QAEvaluators.java
public static Evaluator responseLength(int minWords, int maxWords) {
    return new ResponseLengthEvaluator(minWords, maxWords);
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// In QAEvaluators.kt
fun responseLength(minWords: Int, maxWords: Int): Evaluator = ResponseLengthEvaluator(minWords, maxWords)
```

  </TabItem>
</Tabs>

## Part 8: Advanced Evaluation Patterns

### Evaluating Precision and Recall

When you have ground truth labels for the relevant documents, you can measure classic IR (Information Retrieval) metrics: precision and recall.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.evaluators.MatchingStrategy
import dev.dokimos.kotlin.dsl.precision
import dev.dokimos.kotlin.dsl.recall

val example = example {
    input = "What is your return policy?"
    expected("relevantDocs", listOf("doc-returns-1", "doc-returns-2"))
}

val taskWithDocIds = task { ex ->
    val response = assistant.answer(ex.input())
    val retrievedIds = response.retrievedDocuments.map { it.metadata["id"].toString() }

    mapOf(
        "output" to response.answer,
        "retrievedDocs" to retrievedIds
    )
}

val precision: Evaluator = precision {
    name = "Retrieval Precision"
    retrievedKey = "retrievedDocs"
    expectedKey = "relevantDocs"
    matchingStrategy = MatchingStrategy.byEquality()
    threshold = 0.8
}

val recall: Evaluator = recall {
    name = "Retrieval Recall"
    retrievedKey = "retrievedDocs"
    expectedKey = "relevantDocs"
    matchingStrategy = MatchingStrategy.byEquality()
    threshold = 0.8
}
```

  </TabItem>
</Tabs>

### Flexible Matching Strategies

A `MatchingStrategy` decides when a retrieved item counts as a match. Pick the one that fits your data.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// Case insensitive matching
MatchingStrategy.caseInsensitive()

// Match by a specific field in objects
MatchingStrategy.byField("id")

// Match by multiple fields
MatchingStrategy.byFields("subject", "predicate", "object")

// Substring containment
MatchingStrategy.byContainment(normalize = true)

// LLM based semantic matching (most flexible)
MatchingStrategy.llmBased(judge)

// Combine strategies
MatchingStrategy.anyOf(strategy1, strategy2)  // OR
MatchingStrategy.allOf(strategy1, strategy2)  // AND
```

  </TabItem>
</Tabs>

### Typed Tool-Call Results

When you grow the assistant into a tool-using agent, a tool often returns structured data, not a string. Capture it with `resultJson(...)`, which serializes the value to JSON so you stop hand-escaping. Read it back type-safely with `resultAs(Class<T>)`. This keeps a sequential agent's `output -> input -> output` chain assertable.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.agents.ToolCall;

record Booking(String confirmation, double total) {}

// Build a tool call whose result is a structured value
ToolCall call = ToolCall.builder()
    .name("book_hotel")
    .argument("city", "Paris")
    .argument("nights", 5)
    .resultJson(new Booking("ABC123", 540.0))  // serialized to JSON, no escaping
    .build();

// Read the structured result back as a real object
Booking booked = call.resultAs(Booking.class);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.agents.ToolCall

data class Booking(val confirmation: String, val total: Double)

// Build a tool call whose result is a structured value
val call = ToolCall.builder()
    .name("book_hotel")
    .argument("city", "Paris")
    .argument("nights", 5)
    .resultJson(Booking("ABC123", 540.0))  // serialized to JSON, no escaping
    .build()

// Read the structured result back as a real object
val booked = call.resultAs(Booking::class.java)
```

  </TabItem>
</Tabs>

For the whole typed-data pipeline, see the [Structured & Typed Data](../evaluation/structured-typed-data) hub. For the full agent data model, see [Agent Evaluation](../evaluation/agent-evaluation).

### Async Evaluation

On large datasets, run evaluations off the main thread.

<Tabs groupId="lang" defaultValue="java">
<TabItem value="java" label="Java">

```java
// Single evaluator async
CompletableFuture<EvalResult> future = evaluator.evaluateAsync(testCase);

// With custom executor for parallel evaluation
ExecutorService executor = Executors.newFixedThreadPool(4);
CompletableFuture<EvalResult> future = evaluator.evaluateAsync(testCase, executor);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// Single evaluator async
val evalResult: EvalResult = evaluator.evaluateAsync(testCase).await()

// With custom executor for parallel evaluation
val executor = Executors.newFixedThreadPool(4)
val evalResult2: EvalResult = evaluator.evaluateAsync(testCase, executor).await()
```

  </TabItem>
</Tabs>

## Best Practices

### Start with a Small, High-Quality Dataset

Do not build a huge dataset on day one. Start with 10 to 20 examples that cover your main cases. Add more as you find edge cases and failures.

### Use Multiple Evaluators

Each evaluator catches a different problem:

- **Faithfulness** catches answers that stray from the context.
- **Hallucination** quantifies made-up content.
- **Answer Quality** catches unhelpful or unclear answers.
- **Contextual Relevance** flags retrieval problems.

### Set Realistic Thresholds

Do not demand perfection at the start. Begin around 0.7 and raise it as the system improves. A threshold of 1.0 fails on any flaw.

### Run Evaluations Regularly

Put evaluations in CI/CD. Run a small dataset on every PR, and a larger one nightly or weekly.

## Conclusion

Evaluating agents is how you keep them reliable. In this tutorial you learned how to:

1. Build a RAG knowledge assistant with Spring AI and expose it as a REST API.
2. Create evaluation datasets with examples and expected outputs.
3. Organize evaluators in a reusable factory class.
4. Configure several evaluators for different quality dimensions.
5. Run evaluations from JUnit for CI/CD.
6. Track results over time with the Dokimos Server.
7. Write custom evaluators for your own needs.

Spring AI builds the agent. Dokimos measures it. Together they cover building and shipping reliable AI apps in Java.

## Next Steps

- Explore the [Evaluators documentation](../evaluation/evaluators) for all available evaluators
- Learn about [Datasets](../evaluation/datasets) for advanced dataset management
- Set up the [Dokimos Server](../server/overview) for result tracking
- Check out the [JUnit integration](../integrations/junit) for test driven evaluation

## Resources

- [Tutorial Example Code](https://github.com/dokimos-dev/dokimos/tree/master/dokimos-examples/src/main/java/dev/dokimos/examples/springai/tutorial) - The complete working code from this tutorial
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Dokimos GitHub Repository](https://github.com/dokimos-dev/dokimos)

---

If you found this tutorial helpful, please consider giving the repository a star on GitHub. It helps others discover the project and keeps us motivated to improve it ⭐.
