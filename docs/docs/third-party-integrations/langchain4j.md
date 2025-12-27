---
sidebar_position: 2
---

# LangChain4j Integration

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

## Evaluating RAG Systems

The main reason to use this integration is for evaluating RAG systems. Here's a complete example:

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

### Tracking Extra Metrics

Use `customTask()` to track latency, source counts, or other metrics:

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

**Always return `Result<String>`**: Your AI Service interface must return `Result<String>`, not just `String`. This is how LangChain4j provides the retrieved context.

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

**Use a better model for judging**: Use GPT-5.2 or similar for evaluation, even if your application uses a smaller model for generation.

**Track retrieval quality**: Monitor how many documents are retrieved and whether they're relevant. Use `customTask()` to add metrics.

**Test different retrieval settings**: Use experiments to compare different `maxResults`, embedding models, or reranking strategies.
