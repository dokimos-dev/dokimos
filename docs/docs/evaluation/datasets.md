---
sidebar_position: 2
---

# Datasets

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

A dataset is a collection of examples that represent the scenarios you want to test your LLM application against. Each example typically contains an input (like a user question or prompt) and an expected output (the correct or desired response).

Datasets let you evaluate your application systematically rather than testing with ad-hoc prompts. You can create them programmatically in your code, load them from JSON or CSV files, or fetch them from external sources.

## Creating Datasets

### Programmatic Creation

You can build datasets directly in your code using the `Dataset.builder()` API. This is useful when you want to generate test cases dynamically or keep simple datasets close to your test code.

Here's a basic example for a customer support chatbot:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.Dataset;
import dev.dokimos.core.Example;

Dataset dataset = Dataset.builder()
    .name("Customer Support FAQ")
    .description("Common questions about shipping and returns")
    .addExample(Example.of(
        "How long does shipping take?",
        "Standard shipping takes 5-7 business days"
    ))
    .addExample(Example.of(
        "What's your return policy?",
        "We accept returns within 30 days of purchase"
    ))
    .addExample(Example.of(
        "Do you ship internationally?",
        "Yes, we ship to most countries worldwide"
    ))
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.kotlin.dsl.dataset
import dev.dokimos.kotlin.dsl.example

val dataset = dataset {
    name = "Customer Support FAQ"
    description = "Common questions about shipping and returns"
    example {
        input = "How long does shipping take?"
        expected = "Standard shipping takes 5-7 business days"
    }
    example {
        input = "What's your return policy?"
        expected = "We accept returns within 30 days of purchase"
    }
    example {
        input = "Do you ship internationally?"
        expected = "Yes, we ship to most countries worldwide"
    }
}
```

  </TabItem>
</Tabs>

The `Example.of()` method is convenient for simple input-output pairs. For more complex scenarios where you need multiple inputs or outputs, use `Example.builder()`:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Example example = Example.builder()
    .input("query", "Show me a code review for this pull request")
    .input("prNumber", "1234")
    .input("repository", "acme/backend")
    .expectedOutput("summary", "The PR introduces a new authentication middleware...")
    .expectedOutput("recommendations", List.of("Add unit tests", "Update documentation"))
    .metadata("category", "code-review")
    .metadata("difficulty", "medium")
    .build();

Dataset dataset = Dataset.builder()
    .name("Code Review Assistant")
    .addExample(example)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val example = example {
    input("query", "Show me a code review for this pull request")
    input("prNumber", "1234")
    input("repository", "acme/backend")
    expected("summary", "The PR introduces a new authentication middleware...")
    expected("recommendations", listOf("Add unit tests", "Update documentation"))
    metadata("category", "code-review")
    metadata("difficulty", "medium")
}

val dataset = dataset {
    name = "Code Review Assistant"
    example(example)
}
```

  </TabItem>
</Tabs>

## Loading Datasets from Files

For most real-world use cases, you'll want to store your datasets as JSON, JSONL, or CSV files. This makes it easier to version control your test data, collaborate with team members, and separate test data from code.

### JSON Format

Dokimos supports loading datasets from JSON using `Dataset.fromJson()`. There are two formats you can use:

#### Simple Format

For straightforward input-output pairs, use this format:

```json
{
  "name": "customer-support-refunds",
  "description": "Questions about our refund policy",
  "examples": [
    {
      "input": "Can I get a refund if I'm not satisfied?",
      "expectedOutput": "Yes, we offer a 30-day money-back guarantee"
    },
    {
      "input": "How long does a refund take to process?",
      "expectedOutput": "Refunds are typically processed within 5-7 business days"
    }
  ]
}
```

#### Complex Format

When you need multiple inputs, multiple expected outputs, or metadata, use this format:

```json
{
  "name": "document-qa-with-sources",
  "examples": [
    {
      "inputs": {
        "question": "What are the system requirements?",
        "documentIds": ["doc-123", "doc-456"]
      },
      "expectedOutputs": {
        "answer": "Requires Java 21 or higher and at least 4GB RAM",
        "confidence": 0.95
      },
      "metadata": {
        "category": "technical",
        "source": "product-docs"
      }
    }
  ]
}
```

#### Loading JSON Files

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// From a file path
Dataset dataset = Dataset.fromJson(Path.of("path/to/dataset.json"));

// From a JSON string
String json = """
    {
      "name": "test-dataset",
      "examples": [
        {"input": "Hello", "expectedOutput": "Hi"}
      ]
    }
    """;
Dataset dataset = Dataset.fromJson(json);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// From a file path
val dataset = Dataset.fromJson(Path.of("path/to/dataset.json"))

// From a JSON string
val json = """
    {
      "name": "test-dataset",
      "examples": [
        {"input": "Hello", "expectedOutput": "Hi"}
      ]
    }
    """
val datasetFromString = Dataset.fromJson(json)
```

  </TabItem>
</Tabs>

### JSONL Format

JSONL (JSON Lines) stores one JSON object per line. This format is well-suited for large datasets because Dokimos streams it line-by-line from disk without loading the entire file into memory.

#### Simple Format

```jsonl
{"input": "Can I get a refund?", "expectedOutput": "Yes, we offer a 30-day money-back guarantee"}
{"input": "How long does a refund take?", "expectedOutput": "Refunds are processed within 5-7 business days"}
```

#### Complex Format

Each line supports the same `inputs`, `expectedOutputs`, and `metadata` structure as JSON:

```jsonl
{"inputs": {"question": "What are the system requirements?", "documentIds": ["doc-123"]}, "expectedOutputs": {"answer": "Requires Java 21 or higher", "confidence": 0.95}, "metadata": {"category": "technical"}}
{"inputs": {"question": "How do I install?", "documentIds": ["doc-456"]}, "expectedOutputs": {"answer": "Run the installer and follow the prompts", "confidence": 0.9}, "metadata": {"category": "setup"}}
```

#### Loading JSONL Files

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// From a file path (streamed line-by-line from disk)
Dataset dataset = Dataset.fromJsonl(Path.of("path/to/dataset.jsonl"));

// From a JSONL string
String jsonl = """
    {"input": "Hello", "expectedOutput": "Hi"}
    {"input": "Goodbye", "expectedOutput": "Bye"}
    """;
Dataset dataset = Dataset.fromJsonl(jsonl, "greetings");
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// From a file path (streamed line-by-line from disk)
val dataset = Dataset.fromJsonl(Path.of("path/to/dataset.jsonl"))

// From a JSONL string
val jsonl = """
    {"input": "Hello", "expectedOutput": "Hi"}
    {"input": "Goodbye", "expectedOutput": "Bye"}
    """
val datasetFromString = Dataset.fromJsonl(jsonl, "greetings")
```

  </TabItem>
</Tabs>

### CSV Format

CSV files work well for simpler datasets. You need at least an `input` column, and optionally an `expectedOutput` column (you can also use `expected_output` or `output` as the column name). Any additional columns are automatically treated as metadata.

#### Example CSV

```csv
input,expectedOutput,category,priority
How do I reset my password?,Click 'Forgot Password' on the login page,account,high
Where can I find my order history?,Go to Account > Orders,account,medium
How do I contact support?,Email us at support@example.com or use live chat,support,high
```

#### Loading CSV Files

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// From a file path
Dataset dataset = Dataset.fromCsv(Path.of("path/to/dataset.csv"));

// From a CSV string
String csv = """
    input,expectedOutput
    How do I track my package?,Check your email for the tracking number
    What payment methods do you accept?,"We accept credit cards, PayPal, and bank transfers"
    """;
Dataset dataset = Dataset.fromCsv(csv, "payment-support");
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// From a file path
val dataset = Dataset.fromCsv(Path.of("path/to/dataset.csv"))

// From a CSV string
val csv = """
    input,expectedOutput
    How do I track my package?,Check your email for the tracking number
    What payment methods do you accept?,"We accept credit cards, PayPal, and bank transfers"
    """
val datasetFromString = Dataset.fromCsv(csv, "payment-support")
```

  </TabItem>
</Tabs>

## Dataset Resolution

Dokimos provides a flexible way to load datasets from different sources using URI schemes. This is especially useful in testing environments where you want to load datasets from your test resources or from the file system.

### Classpath Resources

Load datasets from your classpath (like `src/main/resources` or `src/test/resources`):

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.DatasetResolverRegistry;

Dataset dataset = DatasetResolverRegistry.getInstance()
    .resolve("classpath:datasets/qa-dataset.json");
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.DatasetResolverRegistry

val dataset = DatasetResolverRegistry.getInstance()
    .resolve("classpath:datasets/qa-dataset.json")
```

  </TabItem>
</Tabs>

### File System

Load datasets from anywhere on your file system:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// With file: prefix
Dataset dataset = DatasetResolverRegistry.getInstance()
    .resolve("file:path/to/dataset.json");

// Without prefix (defaults to file system)
Dataset dataset = DatasetResolverRegistry.getInstance()
    .resolve("path/to/dataset.json");
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// With file: prefix
val dataset = DatasetResolverRegistry.getInstance()
    .resolve("file:path/to/dataset.json")

// Without prefix (defaults to file system)
val datasetFromDefault = DatasetResolverRegistry.getInstance()
    .resolve("path/to/dataset.json")
```

  </TabItem>
</Tabs>

JSON, JSONL, and CSV files are automatically detected based on the file extension.

## Using Datasets with JUnit

The `dokimos-junit` module makes it easy to use datasets with JUnit's parameterized tests through the `@DatasetSource` annotation.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.junit.DatasetSource;
import dev.dokimos.core.Example;
import org.junit.jupiter.params.ParameterizedTest;

@ParameterizedTest
@DatasetSource("classpath:datasets/qa-dataset.json")
void testQa(Example example) {
    String answer = aiService.generate(example.input());
    var testCase = example.toTestCase(answer);
    Assertions.assertEval(testCase, evaluators);
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.Example
import dev.dokimos.junit.DatasetSource
import org.junit.jupiter.params.ParameterizedTest

class DatasetTests {
    @ParameterizedTest
    @DatasetSource("classpath:datasets/qa-dataset.json")
    fun testQa(example: Example) {
        val answer = aiService.generate(example.input())
        val testCase = example.toTestCase(answer)
        Assertions.assertEval(testCase, evaluators)
    }
}
```

  </TabItem>
</Tabs>

You can also provide inline JSON or JSONL directly in the annotation:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
@ParameterizedTest
@DatasetSource(json = """
    {
      "name": "inline-test",
      "examples": [
        {"input": "test1", "expectedOutput": "result1"},
        {"input": "test2", "expectedOutput": "result2"}
      ]
    }
    """)
void testWithInlineJson(Example example) {
    // Test implementation
}

@ParameterizedTest
@DatasetSource(jsonl = """
    {"input": "test1", "expectedOutput": "result1"}
    {"input": "test2", "expectedOutput": "result2"}
    """)
void testWithInlineJsonl(Example example) {
    // Test implementation
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
@ParameterizedTest
@DatasetSource(json = """
    {
      "name": "inline-test",
      "examples": [
        {"input": "test1", "expectedOutput": "result1"},
        {"input": "test2", "expectedOutput": "result2"}
      ]
    }
    """)
fun testWithInlineJson(example: Example) {
    // Test implementation
}

@ParameterizedTest
@DatasetSource(jsonl = """
    {"input": "test1", "expectedOutput": "result1"}
    {"input": "test2", "expectedOutput": "result2"}
    """)
fun testWithInlineJsonl(example: Example) {
    // Test implementation
}
```

  </TabItem>
</Tabs>

For more complex evaluation scenarios with RAG systems:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
@ParameterizedTest
@DatasetSource("classpath:datasets/qa-dataset.json")
void shouldPassEvaluators(Example example) {
    // Retrieve relevant documents from your vector store
    List<String> retrievedContext = vectorStore.search(example.input(), topK = 3);
    
    // Generate response using the retrieved context
    String response = ragService.generate(example.input(), retrievedContext);
    
    // Provide both the response and context to evaluators
    var testCase = example.toTestCase(Map.of(
        "output", response,
        "retrievedContext", retrievedContext
    ));
    
    Assertions.assertEval(testCase, evaluators);
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
@ParameterizedTest
@DatasetSource("classpath:datasets/qa-dataset.json")
fun shouldPassEvaluators(example: Example) {
    // Retrieve relevant documents from your vector store
    val retrievedContext = vectorStore.search(example.input(), topK = 3)

    // Generate response using the retrieved context
    val response = ragService.generate(example.input(), retrievedContext)

    // Provide both the response and context to evaluators
    val testCase = example.toTestCase(
        mapOf(
            "output" to response,
            "retrievedContext" to retrievedContext
        )
    )

    Assertions.assertEval(testCase, evaluators)
}
```

  </TabItem>
</Tabs>

## Using Datasets with LangChain4j

The `dokimos-langchain4j` module provides utilities for evaluating LangChain4j AI Services and RAG pipelines.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.Dataset;
import dev.dokimos.langchain4j.LangChain4jSupport;

Dataset dataset = Dataset.builder()
    .name("customer-support")
    .addExample(Example.of(
        "What's your refund policy?",
        "We offer a 30-day money-back guarantee"
    ))
    .addExample(Example.of(
        "How long does shipping take?",
        "Standard shipping takes 5-7 business days"
    ))
    .build();

// Create your LangChain4j AI Service that returns Result<String>
interface Assistant {
    Result<String> chat(String userMessage);
}

Assistant assistant = AiServices.builder(Assistant.class)
    .chatLanguageModel(chatModel)
    .retrievalAugmentor(retrievalAugmentor)
    .build();

// Wrap it as a Task (automatically extracts context from Result.sources())
Task task = LangChain4jSupport.ragTask(assistant::chat);

// Run the experiment
ExperimentResult result = Experiment.builder()
    .name("RAG Evaluation")
    .dataset(dataset)
    .task(task)
    .evaluators(evaluators)
    .build()
    .run();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.Dataset
import dev.dokimos.core.Example
import dev.dokimos.core.ExperimentResult
import dev.dokimos.langchain4j.LangChain4jSupport
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.Result

val dataset = dataset {
    name = "customer-support"
    example {
        input = "What's your refund policy?"
        expected = "We offer a 30-day money-back guarantee"
    }
    example {
        input = "How long does shipping take?"
        expected = "Standard shipping takes 5-7 business days"
    }
}

// Create your LangChain4j AI Service that returns Result<String>
interface Assistant {
    fun chat(userMessage: String): Result<String>
}

val assistant = AiServices.builder(Assistant::class.java)
    .chatLanguageModel(chatModel)
    .retrievalAugmentor(retrievalAugmentor)
    .build()

// Wrap it as a Task (automatically extracts context from Result.sources())
val task = LangChain4jSupport.ragTask(assistant::chat)

// Run the experiment
val result: ExperimentResult = experiment {
    name = "RAG Evaluation"
    dataset(dataset)
    task(task)
    evaluators(evaluators)
}.run()
```

  </TabItem>
</Tabs>

If your dataset uses custom key names (like `"question"` instead of `"input"`), specify them explicitly:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// Dataset uses "question" instead of "input"
Task task = LangChain4jSupport.ragTask(
    assistant::chat,
    "question",  // custom input key
    "answer",    // custom output key
    "context"    // custom context key
);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// Dataset uses "question" instead of "input"
val task = LangChain4jSupport.ragTask(
    assistant::chat,
    "question",  // custom input key
    "answer",    // custom output key
    "context"    // custom context key
)
```

  </TabItem>
</Tabs>

## Working with Examples

Each example in a dataset contains inputs, expected outputs, and optional metadata. You can access this data in different ways depending on your needs:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Example example = dataset.get(0);

// Simple access for single input/output
String input = example.input();
String expectedOutput = example.expectedOutput();

// Access to all inputs, outputs, and metadata
Map<String, Object> inputs = example.inputs();
Map<String, Object> expectedOutputs = example.expectedOutputs();
Map<String, Object> metadata = example.metadata();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val example = dataset[0]

// Simple access for single input/output
val input = example.input()
val expectedOutput = example.expectedOutput()

// Access to all inputs, outputs, and metadata
val inputs = example.inputs()
val expectedOutputs = example.expectedOutputs()
val metadata = example.metadata()
```

  </TabItem>
</Tabs>

### Converting Examples to Test Cases

You can easily convert examples to test cases for evaluation:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// With a single output
String actualAnswer = aiService.generate(example.input());
EvalTestCase testCase = example.toTestCase(actualAnswer);

// With multiple outputs
Map<String, Object> actualOutputs = Map.of(
    "output", actualAnswer,
    "retrievedContext", context,
    "confidence", 0.95
);
EvalTestCase testCase = example.toTestCase(actualOutputs);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// With a single output
val actualAnswer = aiService.generate(example.input())
val testCase = example.toTestCase(actualAnswer)

// With multiple outputs
val actualOutputs = mapOf(
    "output" to actualAnswer,
    "retrievedContext" to context,
    "confidence" to 0.95
)
val multiOutputTestCase = example.toTestCase(actualOutputs)
```

  </TabItem>
</Tabs>

## Dataset Properties

Datasets have the following properties:

- **name**: A descriptive name for the dataset
- **description**: An optional detailed description
- **examples**: The list of examples in the dataset
- **size()**: Returns the number of examples
- **get(int index)**: Retrieves an example by index
- **Iterable**: Datasets are iterable, so you can use them in for-each loops

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Dataset dataset = // ... load or create dataset

System.out.println("Dataset: " + dataset.name());
System.out.println("Description: " + dataset.description());
System.out.println("Number of examples: " + dataset.size());

// Iterate over examples
for (Example example : dataset) {
    System.out.println("Input: " + example.input());
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val dataset = /* ... load or create dataset ... */

println("Dataset: ${dataset.name()}")
println("Description: ${dataset.description()}")
println("Number of examples: ${dataset.size()}")

// Iterate over examples
dataset.forEach { example ->
    println("Input: ${example.input()}")
}
```

  </TabItem>
</Tabs>

## Best Practices

### Version control your datasets

Keep datasets as files in your repository so you can track changes over time and collaborate with your team:

```
src/test/resources/
  datasets/
    customer-support-v1.json
    product-qa-v2.csv
    large-evaluation-set.jsonl
    code-review-examples.json
```

This also makes it easier to review changes when someone updates test cases.

### Use meaningful names and descriptions

Help your team understand what each dataset tests:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Dataset.builder()
    .name("edge-cases-numeric-inputs")
    .description("Tests handling of unusual numeric inputs like negative numbers, decimals, and scientific notation")
    // ...
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
dataset {
    name = "edge-cases-numeric-inputs"
    description = "Tests handling of unusual numeric inputs like negative numbers, decimals, and scientific notation"
    // ...
}
```

  </TabItem>
</Tabs>

### Add metadata for filtering and analysis

Metadata helps you understand patterns in failures:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Example.builder()
    .input("userMessage", "Cancel my subscription")
    .expectedOutput("response", "I can help you cancel your subscription...")
    .metadata("category", "account-management")
    .metadata("complexity", "medium")
    .metadata("requires-auth", true)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
example {
    input("userMessage", "Cancel my subscription")
    expected("response", "I can help you cancel your subscription...")
    metadata("category", "account-management")
    metadata("complexity", "medium")
    metadata("requires-auth", true)
}
```

  </TabItem>
</Tabs>

### Start small, grow organically

Don't try to build a huge dataset upfront. Start with 10-15 examples covering the most important scenarios, then add edge cases as you discover them through testing.

### Combine different sources

Load a base dataset from a file and add programmatic examples for specific test scenarios:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Dataset baseDataset = Dataset.fromJson(Path.of("datasets/base-qa.json"));

Dataset testDataset = Dataset.builder()
    .name("qa-with-edge-cases")
    .addExamples(baseDataset.examples())
    .addExample(Example.of("", "Please provide a question"))  // empty input
    .addExample(Example.of("a".repeat(1000), "..."))  // very long input
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val baseDataset = Dataset.fromJson(Path.of("datasets/base-qa.json"))

val testDataset = dataset {
    name = "qa-with-edge-cases"
    examples(baseDataset.examples())
    example {
        input = ""
        expected = "Please provide a question"
    }
    example {
        input = "a".repeat(1000)
        expected = "..."
    }
}
```

  </TabItem>
</Tabs>
