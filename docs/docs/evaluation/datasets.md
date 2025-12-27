---
sidebar_position: 2
---

# Datasets

A dataset is a collection of examples that represent the scenarios you want to test your LLM application against. Each example typically contains an input (like a user question or prompt) and an expected output (the correct or desired response).

Datasets let you evaluate your application systematically rather than testing with ad-hoc prompts. You can create them programmatically in your code, load them from JSON or CSV files, or fetch them from external sources.

## Creating Datasets

### Programmatic Creation

You can build datasets directly in your code using the `Dataset.builder()` API. This is useful when you want to generate test cases dynamically or keep simple datasets close to your test code.

Here's a basic example for a customer support chatbot:

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

The `Example.of()` method is convenient for simple input-output pairs. For more complex scenarios where you need multiple inputs or outputs, use `Example.builder()`:

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

## Loading Datasets from Files

For most real-world use cases, you'll want to store your datasets as JSON or CSV files. This makes it easier to version control your test data, collaborate with team members, and separate test data from code.

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

## Dataset Resolution

Dokimos provides a flexible way to load datasets from different sources using URI schemes. This is especially useful in testing environments where you want to load datasets from your test resources or from the file system.

### Classpath Resources

Load datasets from your classpath (like `src/main/resources` or `src/test/resources`):

```java
import dev.dokimos.core.DatasetResolverRegistry;

Dataset dataset = DatasetResolverRegistry.getInstance()
    .resolve("classpath:datasets/qa-dataset.json");
```

### File System

Load datasets from anywhere on your file system:

```java
// With file: prefix
Dataset dataset = DatasetResolverRegistry.getInstance()
    .resolve("file:path/to/dataset.json");

// Without prefix (defaults to file system)
Dataset dataset = DatasetResolverRegistry.getInstance()
    .resolve("path/to/dataset.json");
```

Both JSON and CSV files are automatically detected based on the file extension.

## Using Datasets with JUnit 5

The `dokimos-junit5` module makes it easy to use datasets with JUnit 5's parameterized tests through the `@DatasetSource` annotation.

```java
import dev.dokimos.junit5.DatasetSource;
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

You can also provide inline JSON directly in the annotation:

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
void testWithInlineData(Example example) {
    // Test implementation
}
```

For more complex evaluation scenarios with RAG systems:

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

## Using Datasets with LangChain4j

The `dokimos-langchain4j` module provides utilities for evaluating LangChain4j AI Services and RAG pipelines.

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

If your dataset uses custom key names (like `"question"` instead of `"input"`), specify them explicitly:

```java
// Dataset uses "question" instead of "input"
Task task = LangChain4jSupport.ragTask(
    assistant::chat,
    "question",  // custom input key
    "answer",    // custom output key
    "context"    // custom context key
);
```

## Working with Examples

Each example in a dataset contains inputs, expected outputs, and optional metadata. You can access this data in different ways depending on your needs:

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

### Converting Examples to Test Cases

You can easily convert examples to test cases for evaluation:

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

## Dataset Properties

Datasets have the following properties:

- **name**: A descriptive name for the dataset
- **description**: An optional detailed description
- **examples**: The list of examples in the dataset
- **size()**: Returns the number of examples
- **get(int index)**: Retrieves an example by index
- **Iterable**: Datasets are iterable, so you can use them in for-each loops

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

## Best Practices

### Version control your datasets

Keep datasets as files in your repository so you can track changes over time and collaborate with your team:

```
src/test/resources/
  datasets/
    customer-support-v1.json
    product-qa-v2.csv
    code-review-examples.json
```

This also makes it easier to review changes when someone updates test cases.

### Use meaningful names and descriptions

Help your team understand what each dataset tests:

```java
Dataset.builder()
    .name("edge-cases-numeric-inputs")
    .description("Tests handling of unusual numeric inputs like negative numbers, decimals, and scientific notation")
    // ...
```

### Add metadata for filtering and analysis

Metadata helps you understand patterns in failures:

```java
Example.builder()
    .input("userMessage", "Cancel my subscription")
    .expectedOutput("response", "I can help you cancel your subscription...")
    .metadata("category", "account-management")
    .metadata("complexity", "medium")
    .metadata("requires-auth", true)
    .build();
```

### Start small, grow organically

Don't try to build a huge dataset upfront. Start with 10-15 examples covering the most important scenarios, then add edge cases as you discover them through testing.

### Combine different sources

Load a base dataset from a file and add programmatic examples for specific test scenarios:

```java
Dataset baseDataset = Dataset.fromJson(Path.of("datasets/base-qa.json"));

Dataset testDataset = Dataset.builder()
    .name("qa-with-edge-cases")
    .addExamples(baseDataset.examples())
    .addExample(Example.of("", "Please provide a question"))  // empty input
    .addExample(Example.of("a".repeat(1000), "..."))  // very long input
    .build();
```
