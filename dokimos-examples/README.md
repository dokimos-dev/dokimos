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

### 2. Custom Evaluators

[dev.dokimos.examples.basic.CustomEvaluatorExample](./src/main/java/dev/dokimos/examples/basic/CustomEvaluatorExample.java)

Demonstrates how to create custom evaluators in two ways:

- **Building a custom keyword evaluator** by extending `BaseEvaluator`
- **Using LLMJudgeEvaluator** for semantic evaluation with LLM judges

This example is perfect for to implementing domain-specific or any custom evaluation logic.

To run the example:

```bash
mvn exec:java -pl dokimos-examples \
  -Dexec.mainClass="dev.dokimos.examples.basic.CustomEvaluatorExample"
```

### 3. JUnit 5 Parameterized Testing

**Location**: `dev.dokimos.examples.junit5.QAParameterizedTest`

Shows how to integrate `dokimos` with JUnit 5:

- Using `@DatasetSource` annotation to load test cases
- Running parameterized tests for each dataset example
- Using `assertEval` for test assertions

**Run**:

To run the test:

```bash
mvn test -pl dokimos-examples -Dtest=QAParameterizedTest
```

### 4. LangChain4j RAG Evaluation

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

### 5. Spring AI Evaluation Example

**Location**: `dev.dokimos.examples.springai.SpringAiEvaluationExample`

Shows how to use Spring AI with Dokimos for evaluating AI applications:

- Converting Spring AI `ChatModel` to a `JudgeLM` judge
- Evaluating customer support responses

**Setup**:

```bash
export OPENAI_API_KEY='your-api-key-here'
```

**Run**:

```bash
mvn exec:java -pl dokimos-examples \
  -Dexec.mainClass="dev.dokimos.examples.springai.SpringAiEvaluationExample"
```

### 6. Spring AI RAG Evaluation

**Location**: `dev.dokimos.examples.springai.SpringAiRAGExample`

This example showcases the evaluation of a basic RAG system built with Spring AI:

- Building a RAG system with Spring AI's `SimpleVectorStore`
- Using `SpringAiSupport` for conversion utilities
- Evaluating faithfulness of responses to retrieved context
- Tracking retrieval and generation quality

**Setup**:

```bash
export OPENAI_API_KEY='your-api-key-here'
```

**Run**:

```bash
mvn exec:java -pl dokimos-examples \
  -Dexec.mainClass="dev.dokimos.examples.springai.SpringAiRAGExample"
```

## Building the Examples

Build all examples:

```bash
mvn clean install -pl dokimos-examples
```

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
- [API Documentation](https://dokimos-dev.github.io/dokimos/)
