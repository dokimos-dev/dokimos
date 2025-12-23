# Examples

This module contains various runnable examples, which aim to demonstrate the use of the evaluation framework in some
common real-world scenarios and applications.

## Examples Overview

### 1. Basic Evaluation Example

[dev.dokimos.examples.basic.BasicEvaluationExample](./src/main/java/dev/dokimos/examples/basic/BasicEvaluationExample.java)

An example that showcases a basic evaluation using the `dokimos-core` module by creating a dataset programmatically,
defining multiple LLM-based and non-LLM-based evaluators, and runs the experiments to analyze the eval results.

To see the example in action, run the following command:

```bash
mvn exec:java -pl dokimos-examples \
  -Dexec.mainClass="dev.dokimos.examples.basic.BasicEvaluationExample"
```

### 2. JUnit 5 Parameterized Testing

**Location**: `dev.dokimos.examples.junit5.QAParameterizedTest`

Shows how to integrate Dokimos with JUnit 5:

- Using `@DatasetSource` annotation to load test cases
- Running parameterized tests for each dataset example
- Using `assertEval` for test assertions

**Run**:

```bash
mvn test -pl dokimos-examples
```

Or run the specific test:

```bash
mvn test -pl dokimos-examples -Dtest=QAParameterizedTest
```

### 3. LangChain4j RAG Evaluation

**Location**: `dev.dokimos.examples.langchain4j.LangChain4jRAGExample`

Demonstrates evaluation of a RAG (Retrieval Augmented Generation) system:

- Building a RAG assistant with LangChain4j
- Using `LangChain4jSupport` utilities
- Evaluating with LLM judges
- Tracking faithfulness and accuracy metrics

**Setup**:

```bash
export OPENAI_API_KEY='your-api-key-here'
```

**Run**:

```bash
mvn exec:java -pl dokimos-examples \
  -Dexec.mainClass="dev.dokimos.examples.langchain4j.LangChain4jRAGExample"
```

## Building the Examples

Build all examples:

```bash
mvn clean install -pl dokimos-examples
```

## Project Structure

```
dokimos-examples/
├── src/
│   ├── main/
│   │   ├── java/dev/dokimos/examples/
│   │   │   ├── basic/
│   │   │   │   └── BasicEvaluationExample.java
│   │   │   ├── junit5/
│   │   │   │   └── (JUnit tests in test directory)
│   │   │   └── langchain4j/
│   │   │       └── LangChain4jRAGExample.java
│   │   └── resources/
│   │       └── datasets/
│   │           └── qa-dataset.json
│   └── test/
│       └── java/dev/dokimos/examples/
│           └── junit5/
│               └── QAParameterizedTest.java
└── pom.xml
```

## Learning Path

We recommend exploring the examples in this order:

1. **Start with BasicEvaluationExample** - Learn the core concepts
2. **Try QAParameterizedTest** - See JUnit 5 integration
3. **Explore LangChain4jRAGExample** - Advanced RAG evaluation

## Customizing Examples

Each example is designed to be self-contained and easy to modify:

- Replace the `simulateLLM()` or `callYourLLM()` methods with actual LLM API calls
- Modify datasets to match your use case
- Add new evaluators to test different quality metrics
- Experiment with different LLM models and parameters

## Common Issues

### Missing API Key

```
Error: OPENAI_API_KEY environment variable not set
```

**Solution**: Set the environment variable:

```bash
export OPENAI_API_KEY='your-api-key'
```

### Build Failures

If you encounter build issues, ensure all parent modules are installed:

```bash
mvn clean install
```

## Further Resources

- [Main Dokimos Documentation](../README.md)
- [API Documentation](https://dokimos-io.github.io/dokimos/)
- [LangChain4j Documentation](https://docs.langchain4j.dev/)

## Contributing

Found a bug or want to add a new example? Contributions are welcome! Please submit a PR to the main repository.