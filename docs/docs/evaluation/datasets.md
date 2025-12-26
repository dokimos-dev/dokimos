---
sidebar_position: 2
---

# Datasets

A **Dataset** is a collection of data points, so called **Examples**, which are used for testing the performance of your LLM application. In Dokimos, datasets can be created in various ways, including programmatically, from files, or through custom sources.

Datasets help you to structure and organize the input data that will be fed into your LLM application during evaluation. By using representative datasets, you can ensure that your evaluations are meaningful and reflect real-world scenarios, while having a single source of truth for your evaluation data.

## Creating Datasets

To create a dataset in Dokimos, you can either define it programmatically or load it from external files. Below are examples of both approaches.

### Programmatic Datasets

You can create a dataset programmatically using the `Dataset.builder()` API. Here's an example of a simple in-memory dataset:

```java
import dev.dokimos.core.Dataset;
import dev.dokimos.core.Example;

Dataset dataset = Dataset.builder()
    .name("Simple QA Dataset")
    .description("A simple dataset for question-answering tasks")
    .addExample(Example.of("What is the capital of Switzerland?", "Bern"))
    .addExample(Example.of("What is the capital of France?", "Paris"))
    .addExample(Example.of("What is the capital of Germany?", "Berlin"))
    .build();
```

The `Example.of()` method is a convenient way to create simple examples with a single input and expected output. For more complex scenarios, you can use the `Example.builder()`:

```java
Example example = Example.builder()
    .input("question", "What is AI?")
    .input("context", List.of("AI is artificial intelligence"))
    .expectedOutput("answer", "Artificial intelligence")
    .expectedOutput("confidence", 0.9)
    .metadata("source", "wikipedia")
    .build();

Dataset dataset = Dataset.builder()
    .name("QA Dataset")
    .addExample(example)
    .build();
```

## Loading Datasets from Files

Dokimos supports loading datasets from both JSON and CSV files. This is useful when you have pre-existing datasets or want to version control your evaluation data separately from your code.

### JSON Format

Datasets can be loaded from JSON files using the `Dataset.fromJson()` method. Dokimos supports two JSON formats:

#### Simple Format

The simple format uses `input` and `expectedOutput` as top-level properties:

```json
{
  "name": "refund-qa",
  "description": "Questions about refunds",
  "examples": [
    {
      "input": "What is the refund policy?",
      "expectedOutput": "30-day full refund"
    },
    {
      "input": "Can I return after 60 days?",
      "expectedOutput": "No, only within 30 days"
    }
  ]
}
```

#### Complex Format

For more advanced scenarios, you can use the complex format with `inputs`, `expectedOutputs`, and `metadata` objects:

```json
{
  "name": "complex-qa",
  "examples": [
    {
      "inputs": {
        "question": "What is AI?",
        "context": ["AI is artificial intelligence"]
      },
      "expectedOutputs": {
        "answer": "Artificial intelligence",
        "confidence": 0.9
      },
      "metadata": {
        "source": "wikipedia"
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

CSV files are also supported for simpler datasets. The CSV must have at least an `input` column, and optionally an `expectedOutput` (or `expected_output` or `output`) column. Any additional columns are treated as metadata.

#### Example CSV

```csv
input,expectedOutput,category
What is 2+2?,4,math
What is 3*3?,9,math
Hello how are you?,"I'm fine thanks",conversation
```

#### Loading CSV Files

```java
// From a file path
Dataset dataset = Dataset.fromCsv(Path.of("path/to/dataset.csv"));

// From a CSV string
String csv = """
    input,expectedOutput
    What is 2+2?,4
    What is 3*3?,9
    """;
Dataset dataset = Dataset.fromCsv(csv, "math-qa");
```

## Dataset Resolution

Dokimos provides a flexible dataset resolution system that supports multiple sources through URI schemes. This is particularly useful in testing environments.

### Supported URI Schemes

#### Classpath Resources

Load datasets from your classpath (e.g., from `src/main/resources` or `src/test/resources`):

```java
import dev.dokimos.core.DatasetResolverRegistry;

Dataset dataset = DatasetResolverRegistry.getInstance()
    .resolve("classpath:datasets/qa-dataset.json");
```

#### File System

Load datasets from the file system using either the `file:` prefix or a plain file path:

```java
// With file: prefix
Dataset dataset = DatasetResolverRegistry.getInstance()
    .resolve("file:path/to/dataset.json");

// Without prefix (defaults to file system)
Dataset dataset = DatasetResolverRegistry.getInstance()
    .resolve("path/to/dataset.json");
```

Both JSON and CSV files are automatically detected based on the file extension (`.json` or `.csv`).

## Using Datasets with JUnit 5

The `dokimos-junit5` module provides seamless integration with JUnit 5's parameterized tests through the `@DatasetSource` annotation.

### Basic Usage

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

### Inline JSON

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

### Advanced Example with Context

The `@DatasetSource` annotation works well with complex evaluation scenarios:

```java
@ParameterizedTest
@DatasetSource("classpath:datasets/qa-dataset.json")
void shouldPassEvaluators(Example example) {
    // Retrieve context (e.g., from a vector store)
    List<String> retrievedContext = List.of(
        "Bern is the capital city of Switzerland",
        "Paris is the capital of France"
    );
    
    String response = aiService.generate(example.input());
    
    // Provide both the response and context to evaluators
    var testCase = example.toTestCase(Map.of(
        "output", response,
        "retrievedContext", retrievedContext
    ));
    
    Assertions.assertEval(testCase, evaluators);
}
```

## Using Datasets with LangChain4j

The `dokimos-langchain4j` module provides special support for LangChain4j applications, making it easy to evaluate RAG (Retrieval-Augmented Generation) pipelines and AI Services.

### Basic Integration

```java
import dev.dokimos.core.Dataset;
import dev.dokimos.langchain4j.LangChain4jSupport;

Dataset dataset = Dataset.builder()
    .name("customer-qa")
    .addExample(Example.of("What is the refund policy?", "30-day money-back guarantee"))
    .addExample(Example.of("How long does shipping take?", "5-7 business days"))
    .build();

// Create your LangChain4j AI Service
Assistant assistant = AiServices.builder(Assistant.class)
    .chatLanguageModel(chatModel)
    .retrievalAugmentor(retrievalAugmentor)
    .build();

// Use LangChain4jSupport to wrap your AI Service
Task task = LangChain4jSupport.taskOf(assistant, Assistant::chat);

// Run the experiment
ExperimentResult result = Experiment.builder()
    .name("RAG Evaluation")
    .dataset(dataset)
    .task(task)
    .evaluators(evaluators)
    .build()
    .run();
```

### Custom Key Mapping

By default, LangChain4jSupport expects datasets to use `input` as the input key. If your dataset uses different keys (e.g., `question`), you can specify custom key mappings:

```java
// Dataset uses "question" instead of "input"
Task task = LangChain4jSupport.taskOf(
    assistant, 
    Assistant::chat,
    "question"  // custom input key
);
```

## Working with Examples

The `Example` class represents a single data point in your dataset. Each example contains:

- **inputs**: A map of input values (e.g., question, context)
- **expectedOutputs**: A map of expected output values (e.g., answer, confidence)
- **metadata**: Additional information about the example (e.g., source, category)

### Accessing Example Data

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

### Converting to Test Cases

Examples can be easily converted to test cases for evaluation:

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

### 1. Version Control Your Datasets

Store datasets as JSON or CSV files in your repository to track changes over time:

```
src/
  main/
    resources/
      datasets/
        qa-dataset.json
        customer-support.csv
        technical-docs.json
```

### 2. Use Descriptive Names

Give your datasets meaningful names that describe their purpose:

```java
Dataset.builder()
    .name("customer-support-refund-policy")
    .description("Questions and answers about refund policies")
    // ...
```

### 3. Include Metadata

Use metadata to categorize and filter examples:

```java
Example.builder()
    .input("input", "What is the return policy?")
    .expectedOutput("output", "30 days")
    .metadata("category", "refunds")
    .metadata("difficulty", "easy")
    .metadata("source", "customer-faq")
    .build();
```

### 4. Start Small and Iterate

Begin with a small, high-quality dataset and expand based on your evaluation results:

```java
// Start with 10-20 representative examples
Dataset smallDataset = Dataset.builder()
    .name("initial-qa-test")
    .addExample(/* ... */)
    .build();

// Expand as you identify edge cases
```

### 5. Mix Data Sources

Combine programmatic and file-based approaches as needed:

```java
Dataset fileDataset = Dataset.fromJson(Path.of("base-dataset.json"));
Dataset combinedDataset = Dataset.builder()
    .name("combined-dataset")
    .addExamples(fileDataset.examples())
    .addExample(Example.of("New test case", "Expected result"))
    .build();
```
