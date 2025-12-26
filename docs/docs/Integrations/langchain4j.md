---
sidebar_position: 2
---

# LangChain4j Integration

Dokimos provides seamless integration with [LangChain4j](https://github.com/langchain4j/langchain4j), making it easy to evaluate AI Services, RAG pipelines, and chat models built with LangChain4j.

## Motivation

### Native RAG Support
Automatically extract and evaluate retrieved context from LangChain4j's `Result<T>` objects, so no manual context tracking is needed.

### Drop-in Compatibility
Convert LangChain4j components (`ChatModel`, AI Services) directly into Dokimos [Tasks](../evaluation/experiments#understanding-the-task-interface) and [Evaluators](../evaluation/evaluators).

### Evaluation
Easily evaluate whether your RAG system's outputs are grounded in the retrieved context using built-in and custom evaluators.

## Setup

Add the LangChain4j integration dependency:

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-langchain4j</artifactId>
    <version>0.2.0</version>
</dependency>
```

## Basic Usage

### Evaluating a Simple ChatModel

Convert a LangChain4j `ChatModel` to a Task:

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

### Using ChatModel as LLM Judge

Convert a `ChatModel` to a `JudgeLM` for evaluation:

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

## RAG Evaluation of AI Services and Agents

The primary use case for LangChain4j integration is evaluating RAG (Retrieval-Augmented Generation) systems.

### Complete RAG Example

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

### How RAG Task Works

The `ragTask()` method automatically:

1. **Extracts the input** from the example
2. **Calls your AI Service** and gets a `Result<String>`
3. **Extracts retrieved context** from `result.sources()`
4. **Returns outputs** with both the answer and context

Output map structure:
```java
{
    "output": "30-day money-back guarantee",
    "context": [
        "We offer a 30-day money-back guarantee...",
        "To request a refund, contact our support..."
    ]
}
```

This enables [FaithfulnessEvaluator](../evaluation/evaluators#faithfulnessevaluator) to verify the answer is grounded in the retrieved context.

## Advanced Configuration

### Custom Key Mapping

If your [dataset](../evaluation/datasets) uses different keys:

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

### Custom Task with Additional Tracking

Track extra metrics like latency or source count:

```java
Task task = LangChain4jSupport.customTask(example -> {
    String query = example.input();
    
    // Track latency
    long start = System.currentTimeMillis();
    Result<String> result = assistant.chat(query);
    long duration = System.currentTimeMillis() - start;
    
    // Extract context with metadata
    var contextsWithMetadata = LangChain4jSupport.extractTextsWithMetadata(result.sources());
    
    return Map.of(
        "output", result.content(),
        "context", LangChain4jSupport.extractTexts(result.sources()),
        "latencyMs", duration,
        "sourceCount", result.sources().size(),
        "sourcesWithMetadata", contextsWithMetadata
    );
});
```

### Context Extraction Utilities

Extract retrieved context in different formats:

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

## RAG-Specific Evaluators

### Faithfulness Evaluation

Verify outputs are grounded in retrieved context:

```java
Evaluator faithfulness = FaithfulnessEvaluator.builder()
    .threshold(0.8)
    .judge(judge)
    .contextKey("context")  // Must match Task's context key
    .includeReason(true)
    .build();
```

The evaluator:
1. Extracts claims from the actual output
2. Verifies each claim against the retrieved context
3. Computes score = (supported claims) / (total claims)

### Multi-dimensional RAG Evaluation

Evaluate different quality aspects:

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

## Complete Working Example

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

## JUnit 5 Integration

Combine with [JUnit 5](./junit5) for testing:

```java
import dev.dokimos.junit5.DatasetSource;
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

## Best Practices

### 1. Return `Result<String>` from AI Services

Always use `Result<String>` return type to enable context extraction:

```java
interface Assistant {
    Result<String> chat(String message);
}

// Without `Result<String>`, we can't extract the entire context
interface Assistant {
    String chat(String message);
}
```

## API Reference

### LangChain4jSupport Methods

```java
// Convert ChatModel to JudgeLM
JudgeLM asJudge(ChatModel model)

// Simple Q&A task
Task simpleTask(ChatModel model)

// RAG task with default keys
Task ragTask(Function<String, Result<String>> assistantCall)

// RAG task with custom keys
Task ragTask(
    Function<String, Result<String>> assistantCall,
    String inputKey,
    String outputKey,
    String contextKey
)

// Custom task builder
Task customTask(Task taskFunction)

// Extract context text only
List<String> extractTexts(List<Content> contents)

// Extract context with metadata
List<Map<String, Object>> extractTextsWithMetadata(List<Content> contents)
```
