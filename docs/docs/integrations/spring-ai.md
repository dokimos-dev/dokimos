---
sidebar_position: 3
---

# Spring AI Integration

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

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

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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
    .evaluationParams(List.of(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
    .judge(judge)
    .threshold(0.8)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.kotlin.dsl.llmJudge
import dev.dokimos.springai.SpringAiSupport
import org.springframework.ai.chat.client.ChatClient

val clientBuilder: ChatClient.Builder = ChatClient.builder(chatModel)

// Convert to JudgeLM
val judge = SpringAiSupport.asJudge(clientBuilder)

// Use in evaluators
val correctness = llmJudge(judge) {
    name = "Answer Correctness"
    criteria = "Is the answer factually correct?"
    threshold = 0.8
}
```

  </TabItem>
</Tabs>

### Using ChatModel as LLM Judge

You can also convert a `ChatModel` directly:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.kotlin.dsl.faithfulness
import dev.dokimos.springai.SpringAiSupport
import org.springframework.ai.openai.OpenAiChatModel

val chatModel = OpenAiChatModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .model("gpt-5.2")
    .build()

// Convert to JudgeLM
val judge = SpringAiSupport.asJudge(chatModel)

// Use in evaluators
val faithfulness = faithfulness(judge) {
    threshold = 0.7
}
```

  </TabItem>
</Tabs>

## Evaluating Spring AI Applications

### Converting Between Spring AI and Dokimos

Convert Spring AI `EvaluationRequest` to Dokimos `EvalTestCase`:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.EvalResult
import dev.dokimos.core.EvalTestCase
import dev.dokimos.springai.SpringAiSupport
import org.springframework.ai.document.Document
import org.springframework.ai.evaluation.EvaluationRequest

// Create Spring AI EvaluationRequest
val retrievedDocs = listOf(
    Document("30-day money-back guarantee"),
    Document("Contact support for refunds")
)

val request = EvaluationRequest(
    "What is the refund policy?",   // user text
    retrievedDocs,                   // retrieved documents
    "We offer a 30-day refund policy." // response content
)

// Convert to Dokimos EvalTestCase
val testCase: EvalTestCase = SpringAiSupport.toTestCase(request)

// Run evaluation
val result: EvalResult = faithfulnessEvaluator.evaluate(testCase)

// Convert back to Spring AI EvaluationResponse
val response = SpringAiSupport.toEvaluationResponse(result)

// Check results
println("Passed: ${response.isPass}")
println("Score: ${response.metadata["score"]}")
println("Feedback: ${response.feedback}")
```

  </TabItem>
</Tabs>

### Complete Evaluation Example

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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
                .evaluationParams(List.of(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.kotlin.dsl.dataset
import dev.dokimos.kotlin.dsl.experiment
import dev.dokimos.kotlin.dsl.llmJudge
import dev.dokimos.core.evaluators.ExactMatchEvaluator
import dev.dokimos.springai.SpringAiSupport
import org.springframework.ai.openai.OpenAiChatModel

object SpringAiEvaluation {
    @JvmStatic
    fun main(args: Array<String>) {
        // 1. Set up ChatModel
        val chatModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .model("gpt-5.2")
            .build()

        // 2. Create a dataset
        val dataset = dataset {
            name = "customer-qa"
            example {
                input = "What is your return policy?"
                expected = "30-day money-back guarantee"
            }
            example {
                input = "How can I contact support?"
                expected = "Email support@example.com"
            }
        }

        // 3. Create Task
        val task = task { example ->
            val response = chatModel.call(example.input())
            mapOf("output" to response)
        }

        // 4. Set up evaluators with Spring AI judge
        val judgeModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName("gpt-4o")
            .build()

        val judge = SpringAiSupport.asJudge(judgeModel)

        val result = experiment {
            name = "Spring AI Evaluation"
            dataset(dataset)
            task(task)
            evaluators {
                llmJudge(judge) {
                    name = "Answer Quality"
                    criteria = "Is the answer helpful and accurate?"
                    threshold = 0.8
                }
                evaluator(ExactMatchEvaluator())
            }
        }.run()

        // 6. Display results
        println("Pass rate: ${"%.0f".format(result.passRate() * 100)}%")
        println("Answer Quality: ${"%.2f".format(result.averageScore("Answer Quality"))}")
    }
}
```

  </TabItem>
</Tabs>

:::tip

Also see the [Datasets](../evaluation/datasets.md) and [Evaluators](../evaluation/evaluators) documentation for more details on creating and loading datasets, and using evaluators.

:::

## Async Tasks

For datasets where each example is an independent, blocking `ChatClient` call, `asyncTask` lets the experiment keep many calls in flight instead of blocking one thread per example. Wire it with `Experiment.builder().asyncTask(...)` and bound concurrency with `parallelism(...)`.

`SpringAiSupport.asyncTask(client)` reads the example input as the user message and writes the response under the default `"output"` key. The blocking `ChatClient` call is dispatched on the common `ForkJoinPool` via `CompletableFuture.supplyAsync(...)`.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.*;
import dev.dokimos.springai.SpringAiSupport;
import org.springframework.ai.chat.client.ChatClient;

ChatClient client = ChatClient.builder(chatModel).build();
AsyncTask task = SpringAiSupport.asyncTask(client);

ExperimentResult result = Experiment.builder()
    .name("Spring AI Async")
    .dataset(dataset)
    .asyncTask(task)
    .parallelism(8)
    .evaluators(evaluators)
    .build()
    .run();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.AsyncTask
import dev.dokimos.kotlin.dsl.experiment
import dev.dokimos.springai.SpringAiSupport
import org.springframework.ai.chat.client.ChatClient

val client = ChatClient.builder(chatModel).build()
val task: AsyncTask = SpringAiSupport.asyncTask(client)

val result = experiment {
    name = "Spring AI Async"
    dataset(dataset)
    asyncTask(task)
    parallelism = 8
    evaluators { evaluators.forEach { evaluator(it) } }
}.run()
```

  </TabItem>
</Tabs>

To read and write different keys, use `asyncTask(client, inputKey, outputKey)`.

:::note

The common pool is shared process-wide and its effective parallelism is roughly one less than the CPU count, so it caps how many blocking calls actually run at once even when `parallelism` is higher. For controlled, isolated concurrency, pass an `Executor` sized to your target throughput — `asyncTask(client, executor)` or the four-arg `asyncTask(client, inputKey, outputKey, executor)`.

:::

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

// A pool sized to match your desired concurrency
Executor executor = Executors.newFixedThreadPool(16);

AsyncTask task = SpringAiSupport.asyncTask(client, executor);

Experiment.builder()
    .dataset(dataset)
    .asyncTask(task)
    .parallelism(16)
    .evaluators(evaluators)
    .build()
    .run();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import java.util.concurrent.Executors

// A pool sized to match your desired concurrency
val executor = Executors.newFixedThreadPool(16)

val task = SpringAiSupport.asyncTask(client, executor)

experiment {
    dataset(dataset)
    asyncTask(task)
    parallelism = 16
    evaluators { evaluators.forEach { evaluator(it) } }
}.run()
```

  </TabItem>
</Tabs>

### Reactive Tasks

If your pipeline is already reactive (`Mono`), bridge it directly instead of blocking on a pool. `reactiveStringTask` wraps a `Mono<String>` response under the default `"output"` key; `reactiveTask` adapts a `Mono<TaskResult>` when you want full control over the output map. Each `Mono` is converted to a `CompletableFuture` via `Mono.toFuture()`.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.*;
import dev.dokimos.springai.SpringAiSupport;

// Mono<String> -> output
AsyncTask stringTask = SpringAiSupport.reactiveStringTask(example ->
    reactiveChatClient.prompt()
        .user(example.input())
        .stream()
        .content()
        .collectList()
        .map(parts -> String.join("", parts)));

// Mono<TaskResult> -> full control over the output map
AsyncTask resultTask = SpringAiSupport.reactiveTask(example ->
    reactiveChatClient.prompt()
        .user(example.input())
        .stream()
        .content()
        .collectList()
        .map(parts -> TaskResult.of(Map.of("output", String.join("", parts)))));
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.AsyncTask
import dev.dokimos.core.TaskResult
import dev.dokimos.springai.SpringAiSupport

// Mono<String> -> output
val stringTask: AsyncTask = SpringAiSupport.reactiveStringTask { example ->
    reactiveChatClient.prompt()
        .user(example.input())
        .stream()
        .content()
        .collectList()
        .map { parts -> parts.joinToString("") }
}

// Mono<TaskResult> -> full control over the output map
val resultTask: AsyncTask = SpringAiSupport.reactiveTask { example ->
    reactiveChatClient.prompt()
        .user(example.input())
        .stream()
        .content()
        .collectList()
        .map { parts -> TaskResult.of(mapOf("output" to parts.joinToString(""))) }
}
```

  </TabItem>
</Tabs>

## Evaluating Tool-Calling Agents

When your Spring AI agent calls tools, `toAgentTrace` turns an `AssistantMessage` (and its `ToolResponseMessage`s) into an `AgentTrace` you can feed straight into the [agent evaluators](../evaluation/agent-evaluation). Tool calls are matched to their results by tool-call id, and `toToolDefinitions` converts the Spring AI tool definitions the agent was given so calls can be checked against them.

`AgentTrace.toTestCase(userMessage, tools)` produces the `EvalTestCase` the agent evaluators expect.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.*;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.ToolCorrectnessEvaluator;
import dev.dokimos.springai.SpringAiSupport;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

// From your agent run: the assistant message and the tool responses produced for it
AssistantMessage assistantMessage = /* ... */;
List<ToolResponseMessage> toolResponses = /* ... */;

// Convert the tools the agent was given
List<ToolDefinition> tools = SpringAiSupport.toToolDefinitions(springAiToolDefinitions);

// Build a trace (tool calls matched to results by id) and a test case
AgentTrace trace = SpringAiSupport.toAgentTrace(assistantMessage, toolResponses);
EvalTestCase testCase = trace.toTestCase("What's the weather in Paris?", tools);

// Evaluate with an agent evaluator
EvalResult result = new ToolCorrectnessEvaluator().evaluate(testCase);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.EvalResult
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.agents.AgentTrace
import dev.dokimos.core.evaluators.agents.ToolCorrectnessEvaluator
import dev.dokimos.springai.SpringAiSupport
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.ToolResponseMessage

// From your agent run
val assistantMessage: AssistantMessage = /* ... */
val toolResponses: List<ToolResponseMessage> = /* ... */

// Convert the tools the agent was given
val tools = SpringAiSupport.toToolDefinitions(springAiToolDefinitions)

// Build a trace (tool calls matched to results by id) and a test case
val trace: AgentTrace = SpringAiSupport.toAgentTrace(assistantMessage, toolResponses)
val testCase: EvalTestCase = trace.toTestCase("What's the weather in Paris?", tools)

// Evaluate with an agent evaluator
val result: EvalResult = ToolCorrectnessEvaluator().evaluate(testCase)
```

  </TabItem>
</Tabs>

:::note

`toAgentTrace(message)` (without tool responses) builds a trace from the tool calls alone — use it when you only need to evaluate which tools the agent chose, not their results.

:::

## Bridging Spring AI Evaluators

If you're using Spring AI's built-in evaluators and want to integrate with Dokimos:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.*;
import dev.dokimos.core.evaluators.*;
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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.BaseEvaluator
import dev.dokimos.core.EvalResult
import dev.dokimos.core.EvalTestCase
import dev.dokimos.springai.SpringAiSupport
import org.springframework.ai.evaluation.RelevancyEvaluator

// Spring AI evaluator
val springAiEvaluator = RelevancyEvaluator(ChatClient.builder(chatModel))

// Create Spring AI EvaluationRequest
val request = EvaluationRequest(
    userQuestion,
    retrievedDocuments,
    generatedResponse
)

// Evaluate with Spring AI
val springAiResponse = springAiEvaluator.evaluate(request)

// Convert to Dokimos for tracking in experiments
val testCase: EvalTestCase = SpringAiSupport.toTestCase(request)

// Custom Dokimos evaluator wrapping Spring AI evaluator
val dokimosEvaluator = object : BaseEvaluator("relevancy", 1.0, listOf()) {
    override fun runEvaluation(testCase: EvalTestCase): EvalResult {
        // Convert Dokimos -> Spring AI -> evaluate -> convert back
        val req: EvaluationRequest = /* build from testCase */ request
        val resp: EvaluationResponse = springAiEvaluator.evaluate(req)

        return EvalResult(
             name = name(),
             score = resp.metadata["score"] as Double,
             success = resp.isPass,
             reason = resp.feedback
        )
    }
}
```

  </TabItem>
</Tabs>

## Working with RAG in Spring AI

When evaluating RAG systems built with Spring AI:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.kotlin.dsl.faithfulness
import dev.dokimos.kotlin.dsl.task
import dev.dokimos.springai.SpringAiSupport
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.VectorStore

// Your RAG setup
val vectorStore: VectorStore = /* your vector store */
val chatClient: ChatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults()))
    .build()

// Create evaluation task
val ragTask = task { example ->
    val query = example.input()

    // Retrieve documents
    val retrieved: List<Document> = vectorStore.similaritySearch(
        SearchRequest.query(query).withTopK(3)
    )

    // Generate response
    val response = chatClient.prompt()
        .user(query)
        .call()
        .content()

    val context = retrieved.map { it.text }

    mapOf(
        "output" to response,
        "context" to context
    )
}

// Evaluate faithfulness
val judge = SpringAiSupport.asJudge(chatModel)

val result = experiment {
    dataset(dataset)
    task(ragTask)
    evaluators {
        faithfulness(judge) {
            threshold = 0.8
        }
    }
}.run()
```

  </TabItem>
</Tabs>

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

<Tabs groupId="lang" defaultValue="java">
<TabItem value="java" label="Java">

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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.kotlin.dsl.experiment
import dev.dokimos.kotlin.dsl.faithfulness
import dev.dokimos.springai.SpringAiSupport
import org.springframework.ai.chat.model.ChatModel
import org.springframework.stereotype.Component

@Component
class AiEvaluationService(private val chatModel: ChatModel) {

    fun evaluate(dataset: Dataset, task: Task): ExperimentResult {
        val judge = SpringAiSupport.asJudge(chatModel)

        return experiment {
            dataset(dataset)
            task(task)
            evaluators {
                faithfulness(judge) {
                    
                }
            }
        }.run()
    }
}
```

  </TabItem>
</Tabs>

## JUnit Integration

Combine with [JUnit](./junit) for testing:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.junit.DatasetSource;
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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.junit.DatasetSource
import org.junit.jupiter.params.ParameterizedTest

class ChatAccuracyTests {
    @ParameterizedTest
    @DatasetSource("classpath:datasets/qa-dataset-v1.json")
    fun chatResponseShouldBeAccurate(example: Example) {
        // Generate response with Spring AI
        val response = chatClient.prompt()
            .user(example.input())
            .call()
            .content()

        // Create test case
        val testCase = EvalTestCase(
            input = example.input(),
            actualOutput = response,
            expectedOutput = example.expectedOutput()
        )

        // Assert with evaluator
        Assertions.assertEval(testCase, exactMatchEvaluator)
    }
}
```

  </TabItem>
</Tabs>

### Threshold-Based Quality Assertions

The parameterized test above fails if *any single example* fails evaluation. For many use cases, you may want to assert that the *average score* across all examples meets a quality threshold instead. This is useful when:

- Individual examples may occasionally score below the threshold, but overall quality should be high
- You want to set different thresholds for different evaluators
- You're running quality gates in CI/CD pipelines

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

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
            .evaluationParams(List.of(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
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

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.ExperimentResult
import dev.dokimos.core.JudgeLM
import dev.dokimos.core.evaluators.ContextualRelevanceEvaluator
import dev.dokimos.kotlin.dsl.experiment
import dev.dokimos.kotlin.dsl.faithfulness
import dev.dokimos.kotlin.dsl.llmJudge
import dev.dokimos.springai.SpringAiSupport
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ThresholdAssertions {

    @Test
    fun experimentMeetsQualityThresholds() {
        val dataset = DatasetResolverRegistry.getInstance()
            .resolve("classpath:datasets/qa-dataset.json")

        val judge: JudgeLM = SpringAiSupport.asJudge(chatModel)

        val result: ExperimentResult = experiment {
            name = "Agent Evaluation"
            dataset(dataset)
            task(task)
            evaluators {
                faithfulness(judge) {
                    contextKey = "context"
                }
                contextualRelevance(judge) {
                    retrievalContextKey = "context"
                }
                llmJudge(judge) {
                    name = "Answer Quality"
                    criteria = "Is the answer helpful, clear, and accurate?"
                }
            }
        }.run()

        assertTrue(result.averageScore("Answer Quality") >= 0.7)
        assertTrue(result.averageScore("Faithfulness") >= 0.75)
    }
}
```

  </TabItem>
</Tabs>

:::tip

Use `assertAll` to run all assertions and report all failures at once, rather than stopping at the first failure. This gives you a complete picture of which quality thresholds are not being met.

:::

## Integration with Spring AI Testing

You can use Dokimos evaluators alongside Spring AI's testing utilities to create comprehensive test suites for your AI applications.
