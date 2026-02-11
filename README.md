<p align="center">
  <img src="https://raw.githubusercontent.com/dokimos-dev/dokimos/master/docs/static/img/logo.jpeg" alt="Dokimos Logo" width="150">
</p>

<h1 align="center">Dokimos</h1>

<p align="center">
  <strong>LLM Evaluation Framework for Java</strong>
</p>

<p align="center">
  <a href="https://dokimos.dev/overview">Documentation</a> •
  <a href="https://dokimos.dev/category/getting-started">Getting Started</a> •
  <a href="./dokimos-examples">Examples</a> •
  <a href="https://github.com/dokimos-dev/dokimos/issues">Issues</a>
</p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/dev.dokimos/dokimos-core"><img src="https://img.shields.io/maven-central/v/dev.dokimos/dokimos-core?label=Maven%20Central" alt="Maven Central"></a>
  <a href="https://github.com/dokimos-dev/dokimos/actions"><img src="https://img.shields.io/github/actions/workflow/status/dokimos-dev/dokimos/ci.yml?branch=master" alt="Build Status"></a>
  <a href="https://opensource.org/licenses/MIT"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License"></a>
  <a href="https://www.oracle.com/java/"><img src="https://img.shields.io/badge/Java-17%2B-orange" alt="Java 17+"></a>
</p>

---

Dokimos is an evaluation framework for LLM applications in Java and Kotlin. It helps you evaluate responses, track quality over time, and catch regressions before they reach production.

It integrates with **JUnit**, **LangChain4j**, **Spring AI** and **Koog** so you can run evaluations as part of your existing test suite and CI/CD pipeline.

## Why Dokimos?

- **JUnit integration**: Run evaluations as parameterized tests in your existing test suite.
- **Framework agnostic**: Works with LangChain4j, Spring AI, Koog or any LLM client. Powered by any LLM.
- **Built in evaluators**: Hallucination detection, faithfulness, contextual relevance, LLM as a judge, and more.
- **Custom evaluators**: Build your own metrics by extending `BaseEvaluator` or using `LLMJudgeEvaluator`.
- **Dataset support**: Load test cases from JSON, CSV, or define them programmatically.
- **CI/CD ready**: Runs locally or in any CI/CD environment. Fail builds when quality drops.
- **Kotlin as first-class citizen**: Compose all tests with a convenient Kotlin DSL.

## Quick Start



Add the dependency to your `pom.xml` (check [Maven Central](https://central.sonatype.com/artifact/dev.dokimos/dokimos-core) for the latest version):

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-core</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

### Run a standalone evaluator

Evaluate a single response directly:

#### Java

```java
Evaluator evaluator = ExactMatchEvaluator.builder()
    .name("Exact Match")
    .threshold(1.0)
    .build();

EvalTestCase testCase = EvalTestCase.of("What is 2+2?", "4", "4");
EvalResult result = evaluator.evaluate(testCase);

System.out.println("Passed: " + result.success());  // true
System.out.println("Score: " + result.score());     // 1.0
```

#### Kotlin

```kotlin
val evaluator = exactMatch {
    name = "Exact Match"
    threshold = 1.0
}

val testCase = EvalTestCase.of("What is 2+2?", "4", "4")
val result = evaluator.evaluate(testCase)

println("Passed: ${result.success()}")  // true
println("Score: ${result.score()}")     // 1.0
```

### Write a JUnit test

Use `@DatasetSource` to run evaluations as parameterized tests:

#### Java

```java
JudgeLM judgeLM = prompt -> openAiClient.generate(prompt);

Evaluator correctnessEvaluator = LLMJudgeEvaluator.builder()
    .name("Correctness")
    .criteria("Is the answer correct and complete?")
    .evaluationParams(List.of(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
    .judge(judgeLM)
    .build();

@ParameterizedTest
@DatasetSource("classpath:datasets/qa.json")
void testQAResponses(Example example) {
    String response = assistant.chat(example.input());
    EvalTestCase testCase = example.toTestCase(response);

    Assertions.assertEval(testCase, correctnessEvaluator);
}
```

#### Kotlin

```kotlin
val judgeLM = JudgeLM { prompt -> openAiClient.generate(prompt) }

val correctnessEvaluator = llmJudge(judgeLM) {
    name = "Correctness"
    criteria = "Is the answer correct and complete?"
    params(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT)
}

class QaTests {
    @ParameterizedTest
    @DatasetSource("classpath:datasets/qa.json")
    fun testQAResponses(example: Example) {
        val response = assistant.chat(example.input())
        val testCase = example.toTestCase(response)

        Assertions.assertEval(testCase, correctnessEvaluator)
    }
}
```

### Evaluate a dataset in bulk

Run experiments across entire datasets with aggregated metrics:

#### Java

```java
JudgeLM judgeLM = prompt -> openAiClient.generate(prompt);

Evaluator correctnessEvaluator = LLMJudgeEvaluator.builder()
    .name("Correctness")
    .criteria("Is the answer correct?")
    .evaluationParams(List.of(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
    .judge(judgeLM)
    .build();

Dataset dataset = Dataset.builder()
    .name("QA Dataset")
    .addExample(Example.of("What is 2+2?", "4"))
    .addExample(Example.of("Capital of France?", "Paris"))
    .build();

ExperimentResult result = Experiment.builder()
    .name("QA Evaluation")
    .dataset(dataset)
    .task(example -> Map.of("output", yourLLM.generate(example.input())))
    .evaluators(List.of(correctnessEvaluator))
    .build()
    .run();

// Check results
System.out.println("Pass rate: " + result.passRate());
System.out.println("Correctness avg: " + result.averageScore("Correctness"));

// Export to multiple formats
result.exportHtml(Path.of("report.html"));
result.exportJson(Path.of("results.json"));
```

#### Kotlin

```kotlin
val judgeLM = JudgeLM { prompt -> openAiClient.generate(prompt) }

val result = experiment {
    name = "QA Evaluation"
    dataset {
        name = "QA Dataset"
        example {
            input = "What is 2+2?"
            expected = "4"
        }
        example {
            input = "Capital of France?"
            expected = "Paris"
        }
    }

    task { example ->
        mapOf("output" to yourLLM.generate(example.input()))
    }

    evaluators {
        llmJudge(judgeLM) {
            name = "Correctness"
            criteria = "Is the answer correct?"
            params(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT)
        }
    }
}.run()

println("Pass rate: ${result.passRate()}")
println("Correctness avg: ${result.averageScore("Correctness")}")

result.exportHtml(Path.of("report.html"))
result.exportJson(Path.of("results.json"))
```

See more patterns in the [dokimos-examples](./dokimos-examples) module.

## Features

**Dataset driven evaluation**
Load test cases from JSON, CSV, or build them programmatically. Version your datasets alongside your code.

**Built in evaluators**
Ready to use evaluators for hallucination detection, faithfulness, contextual relevance, and LLM as a judge patterns.

**Experiment tracking**
Aggregate results across runs, calculate pass rates, and export to JSON, HTML, Markdown, or CSV.

**Extensible**
Build custom evaluators by extending `BaseEvaluator`, or use `LLMJudgeEvaluator` with your own criteria for quick semantic checks.

## Modules

| Module                  | Description                                                          |
|-------------------------|----------------------------------------------------------------------|
| `dokimos-core`          | Core framework with datasets, evaluators, and experiments (required) |
| `dokimos-kotlin`        | Convenient Kotlin DSL for all core building blocks.                  |
| `dokimos-junit`         | JUnit integration with `@DatasetSource` for parameterized tests      |
| `dokimos-langchain4j`   | LangChain4j support for evaluating RAG systems and agents            |
| `dokimos-spring-ai`     | Spring AI integration using `ChatClient` and `ChatModel` as judges   |
| `dokimos-koog`          | Koog integration using `AIAgent` as judge.                           |
| `dokimos-server`        | Optional API and web UI for tracking experiments over time           |
| `dokimos-server-client` | Client library for reporting to the Dokimos server                   |

## Installation

### Maven

Add the modules you need (check [Maven Central](https://central.sonatype.com/artifact/dev.dokimos/dokimos-core) for the latest version):

```xml
<dependencies>
    <!-- Core framework (required) -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-core</artifactId>
        <version>${dokimos.version}</version>
    </dependency>

    <!-- JUnit integration -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-junit</artifactId>
        <version>${dokimos.version}</version>
        <scope>test</scope>
    </dependency>

    <!-- LangChain4j integration -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-langchain4j</artifactId>
        <version>${dokimos.version}</version>
    </dependency>

    <!-- Spring AI integration -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-spring-ai</artifactId>
        <version>${dokimos.version}</version>
    </dependency>

    <!-- Koog integration -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-koog</artifactId>
        <version>${dokimos.version}</version>
    </dependency>

    <!-- Kotlin integration, applicable to all modules -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-kotlin</artifactId>
        <version>${dokimos.version}</version>
    </dependency>

</dependencies>
```

<details>
<summary>Gradle</summary>

```groovy
dependencies {
    implementation 'dev.dokimos:dokimos-core:$dokimosVersion'
    testImplementation 'dev.dokimos:dokimos-junit:$dokimosVersion'
    implementation 'dev.dokimos:dokimos-langchain4j:$dokimosVersion'
    implementation 'dev.dokimos:dokimos-spring-ai:$dokimosVersion'
    implementation 'dev.dokimos:dokimos-koog:$dokimosVersion'
    implementation 'dev.dokimos:dokimos-kotlin:$dokimosVersion'
}
```

</details>

No additional repository configuration needed.

## Integrations

### JUnit

Use `@DatasetSource` to load test cases and `LLMJudgeEvaluator` with custom criteria:

#### Java

```java
// Create a judge from any LLM client
JudgeLM judgeLM = prompt -> openAiClient.generate(prompt);

@ParameterizedTest
@DatasetSource("classpath:support-tickets.json")
void testSupportResponses(Example example) {
    String response = supportBot.answer(example.input());
    EvalTestCase testCase = example.toTestCase(response);

    Evaluator evaluator = LLMJudgeEvaluator.builder()
        .name("Helpfulness")
        .criteria("Is the response helpful and addresses the customer's issue?")
        .evaluationParams(List.of(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
        .judge(judgeLM)
        .threshold(0.7)
        .build();

    Assertions.assertEval(testCase, evaluator);
}
```

#### Kotlin

```kotlin
val judgeLM = JudgeLM { prompt -> openAiClient.generate(prompt) }

class SupportTests {
    @ParameterizedTest
    @DatasetSource("classpath:support-tickets.json")
    fun testSupportResponses(example: Example) {
        val response = supportBot.answer(example.input())
        val testCase = example.toTestCase(response)

        val evaluator = llmJudge(judgeLM) {
            name = "Helpfulness"
            criteria = "Is the response helpful and addresses the customer's issue?"
            params(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT)
            threshold = 0.7
        }

        Assertions.assertEval(testCase, evaluator)
    }
}
```

### LangChain4j

Evaluate RAG pipelines and AI assistants built with LangChain4j:

#### Java

```java
// Create a judge from any LLM client
JudgeLM judgeLM = prompt -> chatLanguageModel.generate(prompt);

Evaluator faithfulness = FaithfulnessEvaluator.builder()
    .judge(judgeLM)
    .contextKey("retrievedContext")
    .threshold(0.8)
    .build();

Experiment.builder()
    .dataset(dataset)
    .task(example -> {
        Result<String> result = assistant.chat(example.input());
        return Map.of(
            "output", result.content(),
            "retrievedContext", result.sources()
        );
    })
    .evaluators(List.of(faithfulness))
    .build()
    .run();
```

#### Kotlin

```kotlin
val judgeLM = JudgeLM { prompt -> chatLanguageModel.generate(prompt) }

val result = experiment {
    dataset(dataset)
    task { example ->
        val result = assistant.chat(example.input())
        mapOf(
            "output" to result.content(),
            "retrievedContext" to result.sources()
        )
    }
    evaluators {
        faithfulness(judgeLM) {
            contextKey = "retrievedContext"
            threshold = 0.8
        }
    }
}.run()
```

### Spring AI

Use Spring AI's `ChatModel` as an evaluation judge:

#### Java

```java
JudgeLM judge = SpringAiSupport.asJudge(chatModel);
 
Evaluator evaluator = LLMJudgeEvaluator.builder()
    .name("Accuracy")
    .criteria("Is the response factually accurate?")
    .evaluationParams(List.of(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
    .judge(judge)
    .threshold(0.8)
    .build();
```

#### Kotlin

```kotlin
val judge = SpringAiSupport.asJudge(chatModel)

val evaluator = llmJudge(judge) {
    name = "Accuracy"
    criteria = "Is the response factually accurate?"
    params(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT)
    threshold = 0.8
}
```

### Koog (Kotlin only)

```kotlin
// Koog agent as judge
val judge = asJudge(aiAgent::run)

val correctness = llmJudge(judge) {
    name = "Correctness"
    criteria = "Is the response correct and concise?"
    params(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT)
    threshold = 0.8
}

val result = experiment {
    name = "Koog QA Evaluation"
    dataset {
        name = "Koog QA"
        example {
            input = "What is 2+2?"
            expected = "4"
        }
    }
    task { example -> mapOf("output" to aiAgent.runBlocking(example.input())) }
    evaluators { evaluator(correctness) }
}.run()

println("Pass rate: ${result.passRate()}")
```

## Experiment Server

The Dokimos server is an optional component for tracking experiment results over time. It provides a web UI for viewing runs, comparing results, and debugging failures.

```bash
curl -O https://raw.githubusercontent.com/dokimos-dev/dokimos/master/docker-compose.yml
docker compose up -d
```

Open [http://localhost:8080](http://localhost:8080) to view the dashboard.

See the [server documentation](https://dokimos.dev/server/overview) for deployment options.

## Roadmap

- More built in evaluators: multi turn conversations, agent tool use, misuse detection
- CLI for running evaluations outside of tests
- Server-side Dataset versioning and management

See the [full roadmap](https://dokimos.dev/overview/#whats-next) on the docs site.

## Get Help

- **Questions**: [GitHub Discussions](https://github.com/dokimos-dev/dokimos/discussions)
- **Bugs**: [GitHub Issues](https://github.com/dokimos-dev/dokimos/issues)
- **Contributing**: See [CONTRIBUTING.md](./CONTRIBUTING.md)

## License

MIT License. See [LICENSE](./LICENSE) for details.

---

<p align="center">
  <a href="https://dokimos.dev/overview">Documentation</a> •
  <a href="https://github.com/dokimos-dev/dokimos">GitHub</a>
</p>
