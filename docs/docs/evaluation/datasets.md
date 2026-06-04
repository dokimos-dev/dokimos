---
sidebar_position: 2
---

# Datasets

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

A dataset is your list of test cases. Each example holds an input (a user question or prompt) and the expected output (the answer you want back). You run your LLM application against every example at once instead of trying prompts by hand.

You can build a dataset in code, load it from a JSON, JSONL, or CSV file, or fetch it from a Dokimos server.

## Build one in code

Use `Dataset.builder()` when you want to keep small datasets next to your test code or generate examples on the fly.

Here is a dataset for a customer support chatbot:

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

`Example.of()` takes one input and one expected output. When you need several inputs, several expected outputs, or metadata, switch to `Example.builder()`:

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

## Load one from a file

Most of the time you store datasets as files. Files are easy to version control, share with your team, and keep apart from code. Dokimos reads JSON, JSONL, and CSV.

### JSON

Load JSON with `Dataset.fromJson()`. You can write the file in two shapes.

#### Simple shape

Use this for one input and one expected output per example:

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

#### Complex shape

Use this when you need several inputs, several expected outputs, or metadata. Note the plural keys (`inputs`, `expectedOutputs`):

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

#### Load it

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

### JSONL

JSONL (JSON Lines) puts one JSON object per line. Reach for it with large datasets. Dokimos streams the file line by line from disk, so it never loads the whole file into memory.

#### Simple shape

```jsonl
{"input": "Can I get a refund?", "expectedOutput": "Yes, we offer a 30-day money-back guarantee"}
{"input": "How long does a refund take?", "expectedOutput": "Refunds are processed within 5-7 business days"}
```

#### Complex shape

Each line takes the same `inputs`, `expectedOutputs`, and `metadata` keys as JSON:

```jsonl
{"inputs": {"question": "What are the system requirements?", "documentIds": ["doc-123"]}, "expectedOutputs": {"answer": "Requires Java 21 or higher", "confidence": 0.95}, "metadata": {"category": "technical"}}
{"inputs": {"question": "How do I install?", "documentIds": ["doc-456"]}, "expectedOutputs": {"answer": "Run the installer and follow the prompts", "confidence": 0.9}, "metadata": {"category": "setup"}}
```

#### Load it

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

### CSV

CSV fits simpler datasets. You need an `input` column. An `expectedOutput` column is optional (you can also name it `expected_output` or `output`). Every other column becomes metadata.

Parsing follows RFC 4180. A quoted field can hold the delimiter (`,`), line breaks, and doubled quotes (`""` becomes a single literal `"`). Whitespace inside quoted fields stays as is, and unquoted fields are trimmed. A leading UTF-8 byte order mark is stripped.

#### Example CSV

```csv
input,expectedOutput,category,priority
How do I reset my password?,Click 'Forgot Password' on the login page,account,high
What payment methods do you accept?,"We accept credit cards, PayPal, and bank transfers",payment,medium
How do I quote a price?,"Wrap it in double quotes like ""this""",support,low
How do I contact support?,Email us at support@example.com or use live chat,support,high
```

#### Load it

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

### Load any file with one call

If you do not want to pick a format-specific method, call `Dataset.load()`. It reads the `classpath:` and `file:` schemes, falls back to the file extension for plain paths, and then hands off to the resolver registry.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// Resolves by extension and scheme
Dataset fromJson = Dataset.load("path/to/dataset.json");
Dataset fromCsv = Dataset.load("file:path/to/dataset.csv");
Dataset fromClasspath = Dataset.load("classpath:datasets/qa-dataset.jsonl");
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// Resolves by extension and scheme
val fromJson = Dataset.load("path/to/dataset.json")
val fromCsv = Dataset.load("file:path/to/dataset.csv")
val fromClasspath = Dataset.load("classpath:datasets/qa-dataset.jsonl")
```

  </TabItem>
</Tabs>

One difference: `fromJson`, `fromCsv`, and `fromJsonl` throw a checked `IOException`, but `Dataset.load()` does not. `Dataset.load()` throws `DatasetResolutionException` when no resolver handles the argument.

## Resolve datasets by URI scheme

The resolver registry loads datasets from different sources using URI schemes. This helps in tests, where you load from test resources or from the file system.

### From the classpath

Load from your classpath, such as `src/main/resources` or `src/test/resources`:

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

### From the file system

Load from anywhere on disk:

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

The registry picks JSON, JSONL, or CSV from the file extension.

### From a Dokimos server

Add the `dokimos-server-client` dependency to your classpath, and the registry also resolves `dataset://name@version` URIs against a running Dokimos server. Now a dataset can be versioned and shared instead of living in a file. See [Server datasets](../server/datasets) for the version model, the resolver's environment variables, and its offline cache.

## Run a dataset in JUnit

The `dokimos-junit` module feeds a dataset into a JUnit parameterized test through the `@DatasetSource` annotation. Each example arrives as one `Example` parameter, so JUnit runs your test once per example.

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

You can also pass JSON or JSONL inline in the annotation:

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

For a RAG system, retrieve context first, then pass both the response and the context to your evaluators:

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

## Run a dataset against LangChain4j

The `dokimos-langchain4j` module evaluates LangChain4j AI Services and RAG pipelines. Wrap your AI Service as a `Task`, then run it across the dataset:

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

If your dataset uses other key names (say `"question"` instead of `"input"`), pass them to `ragTask`:

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

## Read an example

Every example holds inputs, expected outputs, and optional metadata. Read them the simple way for one input and one output, or read the full maps when you have several:

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

### Turn an example into a test case

Call `toTestCase()` to get an `EvalTestCase` your evaluators can score. Pass a single output, or a map when you have several:

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

## Dataset properties

A dataset exposes:

- **name**: a short name for the dataset
- **description**: an optional longer description
- **examples**: the list of examples
- **size()**: the number of examples
- **get(int index)**: the example at that index
- **Iterable**: a dataset iterates, so you can use it in a for-each loop

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

## Best practices

### Keep datasets in version control

Store datasets as files in your repository. You track changes over time and your team works on them together:

```
src/test/resources/
  datasets/
    customer-support-v1.json
    product-qa-v2.csv
    large-evaluation-set.jsonl
    code-review-examples.json
```

Files also make pull requests easy to read when someone updates test cases.

### Name and describe each dataset

Tell your team what a dataset tests:

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

Metadata helps you spot patterns in failures:

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

### Start small, grow over time

Skip the big upfront dataset. Start with 10 to 15 examples that cover the cases you care about most, then add edge cases as testing surfaces them.

### Combine sources

Load a base dataset from a file, then add programmatic examples for specific cases:

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
