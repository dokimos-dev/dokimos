---
sidebar_position: 2
---

# LangChain4j Integration

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

Dokimos works with [LangChain4j](https://github.com/langchain4j/langchain4j) so you can evaluate your AI Services and RAG pipelines.

## Why Use This Integration?

**Automatic context extraction**: LangChain4j's `Result<T>` objects already contain the retrieved documents. Dokimos extracts them automatically so you don't have to track context manually.

**Simple conversion**: Turn a `ChatModel` or AI Service into a Dokimos Task with one line of code.

**RAG evaluation ready**: Use the `FaithfulnessEvaluator` to check if answers are grounded in retrieved documents.

## Setup

Add the LangChain4j integration dependency:

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-langchain4j</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

## Basic Usage

### Evaluating a Simple ChatModel

Convert a LangChain4j `ChatModel` to a Task:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.langchain4j.LangChain4jSupport;
import dev.langchain4j.model.openai.OpenAiChatModel;

ChatModel model = OpenAiChatModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("gpt-5.2")
    .build();

// Convert to Task
Task task = LangChain4jSupport.simpleTask(model);

// Run experiment
ExperimentResult result = Experiment.builder()
    .name("ChatModel Evaluation")
    .dataset(dataset)
    .task(task)
    .evaluators(evaluators)
    .build()
    .run();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.langchain4j.LangChain4jSupport
import dev.dokimos.kotlin.dsl.experiment
import dev.langchain4j.model.openai.OpenAiChatModel

val model = OpenAiChatModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("gpt-5.2")
    .build()

// Convert to Task
val task = LangChain4jSupport.simpleTask(model)

// Run experiment
val result = experiment {
    name = "ChatModel Evaluation"
    dataset(dataset)
    task(task)
    evaluators { evaluators.forEach { evaluator(it) } }
}.run()
```

  </TabItem>
</Tabs>

### Using ChatModel as LLM Judge

Convert a `ChatModel` to a `JudgeLM` for evaluation:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.langchain4j.LangChain4jSupport;

ChatModel judgeModel = OpenAiChatModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("gpt-5.2")
    .build();

// Convert to JudgeLM
JudgeLM judge = LangChain4jSupport.asJudge(judgeModel);

// Use in evaluators
Evaluator correctness = LLMJudgeEvaluator.builder()
    .name("Answer Correctness")
    .criteria("Is the answer factually correct?")
    .judge(judge)
    .threshold(0.8)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.langchain4j.LangChain4jSupport
import dev.dokimos.kotlin.dsl.llmJudge
import dev.langchain4j.model.openai.OpenAiChatModel

val judgeModel = OpenAiChatModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("gpt-5.2")
    .build()

// Convert to JudgeLM
val judge = LangChain4jSupport.asJudge(judgeModel)

// Use in evaluators
val correctness = llmJudge(judge) {
    name = "Answer Correctness"
    criteria = "Is the answer factually correct?"
    threshold = 0.8
}
```

  </TabItem>
</Tabs>

## Evaluating RAG Systems

The main reason to use this integration is for evaluating RAG systems. Here's a complete example:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.langchain4j.LangChain4jSupport;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;

// 1. Define your AI Service interface (must return Result<String>)
interface Assistant {
    Result<String> chat(String userMessage);
}

// 2. Build your RAG pipeline
Assistant assistant = AiServices.builder(Assistant.class)
    .chatLanguageModel(chatModel)
    .contentRetriever(EmbeddingStoreContentRetriever.builder()
        .embeddingStore(embeddingStore)
        .embeddingModel(embeddingModel)
        .maxResults(3)
        .build())
    .build();

// 3. Create dataset
Dataset dataset = Dataset.builder()
    .name("customer-qa")
    .addExample(Example.of("What is the refund policy?", "30-day money-back guarantee"))
    .addExample(Example.of("How long does shipping take?", "5-7 business days"))
    .build();

// 4. Create Task (automatically extracts context from Result)
Task task = LangChain4jSupport.ragTask(assistant::chat);

// 5. Set up evaluators
JudgeLM judge = LangChain4jSupport.asJudge(judgeModel);

List<Evaluator> evaluators = List.of(
    // Check answer correctness
    LLMJudgeEvaluator.builder()
        .name("Answer Correctness")
        .criteria("Is the answer accurate and complete?")
        .judge(judge)
        .threshold(0.8)
        .build(),
    
    // Check faithfulness to retrieved context
    FaithfulnessEvaluator.builder()
        .threshold(0.7)
        .judge(judge)
        .build()
);

// 6. Run experiment
ExperimentResult result = Experiment.builder()
    .name("RAG Evaluation")
    .dataset(dataset)
    .task(task)
    .evaluators(evaluators)
    .build()
    .run();

// 7. Analyze results
System.out.println("Pass rate: " + result.passRate() * 100 + "%");
System.out.println("Faithfulness: " + result.averageScore("Faithfulness"));
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.langchain4j.LangChain4jSupport
import dev.dokimos.kotlin.dsl.dataset
import dev.dokimos.kotlin.dsl.experiment
import dev.dokimos.kotlin.dsl.faithfulness
import dev.dokimos.kotlin.dsl.llmJudge
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.Result

// 1. Define your AI Service interface (must return Result<String>)
interface Assistant {
    fun chat(userMessage: String): Result<String>
}

// 2. Build your RAG pipeline
val assistant = AiServices.builder(Assistant::class.java)
    .chatLanguageModel(chatModel)
    .contentRetriever(
        EmbeddingStoreContentRetriever.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .maxResults(3)
            .build()
    )
    .build()

// 3. Create dataset
val dataset = dataset {
    name = "customer-qa"
    example { 
        input = "What is the refund policy?"
        expected = "30-day money-back guarantee" 
    }
    example { 
        input = "How long does shipping take?"
        expected = "5-7 business days" 
    }
}

// 4. Create Task (automatically extracts context from Result)
val task = LangChain4jSupport.ragTask(assistant::chat)

// 5. Set up evaluators
val judge = LangChain4jSupport.asJudge(judgeModel)

val result = experiment {
    name = "RAG Evaluation"
    dataset(dataset)
    task(task)
    evaluators {
        llmJudge(judge) {
            name = "Answer Correctness"
            criteria = "Is the answer accurate and complete?"
            threshold = 0.8
        }
        faithfulness(judge) {
            threshold = 0.7
        }
    }
}.run()

println("Pass rate: ${result.passRate() * 100}%")
println("Faithfulness: ${result.averageScore("Faithfulness")}")
```

  </TabItem>
</Tabs>

### How It Works

The `ragTask()` method extracts the input, calls your AI Service, and automatically pulls the retrieved context from `Result.sources()`. The output includes both the answer and context:

```java
{
    "output": "We offer a 30-day money-back guarantee",
    "context": [
        "Refund policy: 30-day guarantee...",
        "Contact support to process refunds..."
    ]
}
```

This lets the `FaithfulnessEvaluator` check if the answer is grounded in what was actually retrieved.

## Advanced Usage

### Custom Dataset Keys

If your dataset uses different key names:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// Dataset with custom keys
Dataset dataset = Dataset.builder()
    .addExample(Example.builder()
        .input("question", "What is the refund policy?")
        .expectedOutput("answer", "30-day money-back guarantee")
        .build())
    .build();

// Map keys accordingly
Task task = LangChain4jSupport.ragTask(
    assistant::chat,
    "question",         // input key
    "answer",           // output key
    "retrievedContext"  // context key
);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// Dataset with custom keys
val dataset = dataset {
    example {
        input("question", "What is the refund policy?")
        expected("answer", "30-day money-back guarantee")
    }
}

// Map keys accordingly
val task = LangChain4jSupport.ragTask(
    assistant::chat,
    "question",         // input key
    "answer",           // output key
    "retrievedContext"  // context key
)
```

  </TabItem>
</Tabs>

### Tracking Extra Metrics

Use `customTask()` to track latency, source counts, or other metrics:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Task task = LangChain4jSupport.customTask(example -> {
    long start = System.currentTimeMillis();
    Result<String> result = assistant.chat(example.input());
    long latency = System.currentTimeMillis() - start;
    
    return Map.of(
        "output", result.content(),
        "context", LangChain4jSupport.extractTexts(result.sources()),
        "latencyMs", latency,
        "numSources", result.sources().size()
    );
});
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val task = LangChain4jSupport.customTask { example ->
    val start = System.currentTimeMillis()
    val result = assistant.chat(example.input())
    val latency = System.currentTimeMillis() - start

    mapOf(
        "output" to result.content(),
        "context" to LangChain4jSupport.extractTexts(result.sources()),
        "latencyMs" to latency,
        "numSources" to result.sources().size
    )
}
```

  </TabItem>
</Tabs>


### Context Extraction Utilities

Extract retrieved context in different formats:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// Simple text extraction
List<String> contextTexts = LangChain4jSupport.extractTexts(result.sources());
// ["Text from doc 1", "Text from doc 2"]

// With metadata (for source attribution)
List<Map<String, Object>> contextsWithMeta = 
    LangChain4jSupport.extractTextsWithMetadata(result.sources());
// [
//   {"text": "...", "metadata": {"source": "doc1.pdf", "page": 5}},
//   {"text": "...", "metadata": {"source": "doc2.pdf", "page": 12}}
// ]
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// Simple text extraction
val contextTexts = LangChain4jSupport.extractTexts(result.sources())
// ["Text from doc 1", "Text from doc 2"]

// With metadata (for source attribution)
val contextsWithMeta = LangChain4jSupport.extractTextsWithMetadata(result.sources())
// [
//   {"text": "...", "metadata": {"source": "doc1.pdf", "page": 5}},
//   {"text": "...", "metadata": {"source": "doc2.pdf", "page": 12}}
// ]
```

  </TabItem>
</Tabs>

## RAG-Specific Evaluators

### Faithfulness Evaluation

Verify outputs are grounded in retrieved context:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Evaluator faithfulness = FaithfulnessEvaluator.builder()
    .threshold(0.8)
    .judge(judge)
    .contextKey("context")  // Must match Task's context key
    .includeReason(true)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val faithfulness = faithfulness(judge) {
    threshold = 0.8
    contextKey = "context"  // Must match Task's context key
    includeReason = true
}
```

  </TabItem>
</Tabs>

The evaluator:
1. Extracts claims from the actual output
2. Verifies each claim against the retrieved context
3. Computes score = (supported claims) / (total claims)

### Multi-dimensional RAG Evaluation

Evaluate different quality aspects:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
List<Evaluator> evaluators = List.of(
    // Answer quality
    LLMJudgeEvaluator.builder()
        .name("Answer Quality")
        .criteria("Is the answer helpful and accurate?")
        .evaluationParams(List.of(
            EvalTestCaseParam.INPUT,
            EvalTestCaseParam.ACTUAL_OUTPUT
        ))
        .judge(judge)
        .threshold(0.8)
        .build(),
    
    // Faithfulness to sources
    FaithfulnessEvaluator.builder()
        .name("Faithfulness")
        .threshold(0.85)
        .judge(judge)
        .build(),
    
    // Context relevance
    LLMJudgeEvaluator.builder()
        .name("Context Relevance")
        .criteria("Is the retrieved context relevant to answering the question?")
        .evaluationParams(List.of(
            EvalTestCaseParam.INPUT,
            EvalTestCaseParam.METADATA  // Contains context
        ))
        .judge(judge)
        .threshold(0.75)
        .build()
);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val evaluators = evaluators {
    // Answer quality
    llmJudge(judge) {
        name = "Answer Quality"
        criteria = "Is the answer helpful and accurate?"
        params(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT)
        threshold = 0.8
    }

    // Faithfulness to sources
    faithfulness(judge) {
        name = "Faithfulness"
        threshold = 0.85
    }

    // Context relevance
    llmJudge(judge) {
        name = "Context Relevance"
        criteria = "Is the retrieved context relevant to answering the question?"
        params(EvalTestCaseParam.INPUT, EvalTestCaseParam.METADATA)
        threshold = 0.75
    }
}
```

  </TabItem>
</Tabs>

## Complete Working Example

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.*;
import dev.dokimos.langchain4j.LangChain4jSupport;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

public class RAGEvaluation {
    
    public static void main(String[] args) {
        // 1. Set up RAG components
        var embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
        var embeddingStore = new InMemoryEmbeddingStore<TextSegment>();
        
        // Ingest documents
        var documents = List.of(
            Document.from("We offer a 30-day money-back guarantee."),
            Document.from("Standard shipping takes 5-7 business days.")
        );
        
        EmbeddingStoreIngestor.builder()
            .embeddingModel(embeddingModel)
            .embeddingStore(embeddingStore)
            .build()
            .ingest(documents);
        
        // 2. Build AI Service
        interface Assistant {
            Result<String> chat(String userMessage);
        }
        
        Assistant assistant = AiServices.builder(Assistant.class)
            .chatLanguageModel(OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-5.2")
                .build())
            .contentRetriever(EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .build())
            .build();
        
        // 3. Create dataset
        Dataset dataset = Dataset.builder()
            .name("customer-qa")
            .addExample(Example.of(
                "What is the refund policy?", 
                "30-day money-back guarantee"
            ))
            .addExample(Example.of(
                "How long does shipping take?", 
                "5-7 business days"
            ))
            .build();
        
        // 4. Set up evaluation
        var judgeModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName("gpt-5.2")
            .build();
        
        JudgeLM judge = LangChain4jSupport.asJudge(judgeModel);
        
        List<Evaluator> evaluators = List.of(
            LLMJudgeEvaluator.builder()
                .name("Answer Quality")
                .criteria("Is the answer accurate?")
                .judge(judge)
                .threshold(0.8)
                .build(),
            FaithfulnessEvaluator.builder()
                .threshold(0.7)
                .judge(judge)
                .build()
        );
        
        // 5. Run experiment
        ExperimentResult result = Experiment.builder()
            .name("RAG Evaluation")
            .dataset(dataset)
            .task(LangChain4jSupport.ragTask(assistant::chat))
            .evaluators(evaluators)
            .build()
            .run();
        
        // 6. Display results
        System.out.println("Pass rate: " + 
            String.format("%.0f%%", result.passRate() * 100));
        System.out.println("Answer Quality: " + 
            String.format("%.2f", result.averageScore("Answer Quality")));
        System.out.println("Faithfulness: " + 
            String.format("%.2f", result.averageScore("Faithfulness")));
    }
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.kotlin.dsl.dataset
import dev.dokimos.kotlin.dsl.experiment
import dev.dokimos.kotlin.dsl.faithfulness
import dev.dokimos.kotlin.dsl.llmJudge
import dev.dokimos.langchain4j.LangChain4jSupport
import dev.langchain4j.data.document.Document
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.Result
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore

object RAGEvaluation {
    @JvmStatic
    fun main(args: Array<String>) {
        // 1. Set up RAG components
        val embeddingModel = BgeSmallEnV15QuantizedEmbeddingModel()
        val embeddingStore = InMemoryEmbeddingStore<TextSegment>()

        // Ingest documents
        val documents = listOf(
            Document.from("We offer a 30-day money-back guarantee."),
            Document.from("Standard shipping takes 5-7 business days.")
        )

        EmbeddingStoreIngestor.builder()
            .embeddingModel(embeddingModel)
            .embeddingStore(embeddingStore)
            .build()
            .ingest(documents)

        // 2. Build AI Service
        interface Assistant {
            fun chat(userMessage: String): Result<String>
        }

        val assistant = AiServices.builder(Assistant::class.java)
            .chatLanguageModel(
                OpenAiChatModel.builder()
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .modelName("gpt-5.2")
                    .build()
            )
            .contentRetriever(
                EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(2)
                    .build()
            )
            .build()

        // 3. Create dataset
        val dataset = dataset {
            name = "customer-qa"
            example { 
                input = "What is the refund policy?"
                expected = "30-day money-back guarantee" 
            }
            example { 
                input = "How long does shipping take?"
                expected = "5-7 business days" 
            }
        }

        // 4. Set up evaluation
        val judgeModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName("gpt-5.2")
            .build()

        val judge = LangChain4jSupport.asJudge(judgeModel)

        val result = experiment {
            name = "RAG Evaluation"
            dataset(dataset)
            task(LangChain4jSupport.ragTask(assistant::chat))
            evaluators {
                llmJudge(judge) {
                    name = "Answer Quality"
                    criteria = "Is the answer accurate?"
                    threshold = 0.8
                }
                faithfulness(judge) {
                    threshold = 0.7
                }
            }
        }.run()

        // 6. Display results
        println("Pass rate: ${"%.0f".format(result.passRate() * 100)}%")
        println("Answer Quality: ${"%.2f".format(result.averageScore("Answer Quality"))}")
        println("Faithfulness: ${"%.2f".format(result.averageScore("Faithfulness"))}")
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
@DatasetSource("classpath:datasets/rag-qa.json")
void ragSystemShouldAnswerCorrectly(Example example) {
    // Call your RAG system
    Result<String> result = assistant.chat(example.input());
    
    // Create test case with context
    Map<String, Object> outputs = Map.of(
        "output", result.content(),
        "context", LangChain4jSupport.extractTexts(result.sources())
    );
    EvalTestCase testCase = example.toTestCase(outputs);
    
    // Assert faithfulness
    Assertions.assertEval(testCase, faithfulnessEvaluator);
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.junit.DatasetSource
import org.junit.jupiter.params.ParameterizedTest

class RagJUnitTests {
    @ParameterizedTest
    @DatasetSource("classpath:datasets/rag-qa.json")
    fun ragSystemShouldAnswerCorrectly(example: Example) {
        // Call your RAG system
        val result = assistant.chat(example.input())

        // Create test case with context
        val outputs = mapOf(
            "output" to result.content(),
            "context" to LangChain4jSupport.extractTexts(result.sources())
        )
        val testCase = example.toTestCase(outputs)

        // Assert faithfulness
        Assertions.assertEval(testCase, faithfulnessEvaluator)
    }
}
```

  </TabItem>
</Tabs>

## Best Practices

**Always return `Result<String>`**: Your AI Service interface must return `Result<String>`, not just `String`. This is how LangChain4j provides the retrieved context.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// ✅ Good
interface Assistant {
    Result<String> chat(String message);
}

// ❌ Won't work (can't extract context)
interface Assistant {
    String chat(String message);
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// ✅ Good
interface Assistant {
    fun chat(message: String): Result<String>
}

// ❌ Won't work (can't extract context)
interface BadAssistant {
    fun chat(message: String): String
}
```

  </TabItem>
</Tabs>

**Use a better model for judging**: Use GPT-5.2 or similar for evaluation, even if your application uses a smaller model for generation.

**Track retrieval quality**: Monitor how many documents are retrieved and whether they're relevant. Use `customTask()` to add metrics.

**Test different retrieval settings**: Use experiments to compare different `maxResults`, embedding models, or reranking strategies.
