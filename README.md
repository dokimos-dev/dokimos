# Dokimos: Evaluation for LLM applications in Java

Dokimos is a systematic approach to evaluate LLM outputs using datasets, metrics, and experiments in Java. It integrates
seamlessly with JUnit 5 for parameterized testing, and libraries such
as [LangChain4j](https://github.com/langchain4j/langchain4j) and [Spring AI](https://spring.io/projects/spring-ai) for evaluation of sophisticated AI systems and agents.

## Features

- **Dataset-driven evaluation**: Load datasets from JSON, CSV, or custom sources
- **Built-in evaluators**: Exact match, regex, and LLM-based judges
- **JUnit 5 integration**: Parameterized tests with dataset sources
- **LangChain4j integration**: Evaluation of production-ready AI systems and agents
- **Spring AI integration**: Use Spring AI `ChatClient` and `ChatModel` as evaluation judges
- **Experiment tracking**: Aggregate eval results with pass rates and scores
- **Extensible**: Build Custom evaluators and dataset resolvers via SPI

## Installation

### Maven (Recommended)

Simply add the desired modules to your `pom.xml`:

```xml
<dependencies>
    <!-- Core evaluation framework -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-core</artifactId>
        <version>${dokimos-core.version}</version>
    </dependency>

    <!-- JUnit 5 integration -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-junit5</artifactId>
        <version>${dokimos-junit5.version}</version>
        <scope>test</scope>
    </dependency>

    <!-- LangChain4j integration -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-langchain4j</artifactId>
        <version>${dokimos-langchain4j.version}</version>
    </dependency>

    <!-- Spring AI integration -->
    <dependency>
        <groupId>dev.dokimos</groupId>
        <artifactId>dokimos-spring-ai</artifactId>
        <version>${dokimos-spring-ai.version}</version>
    </dependency>
</dependencies>
```

No additional repository configuration is needed!

---

### Gradle (Groovy DSL)

Add the desired modules to your `build.gradle`:

```groovy
dependencies {
    // Core evaluation framework
    implementation 'dev.dokimos:dokimos-core:${dokimosCoreVersion}'
    // JUnit 5 integration
    testImplementation 'dev.dokimos:dokimos-junit5:${dokimosJunit5Version}'
    // LangChain4j integration
    implementation 'dev.dokimos:dokimos-langchain4j:${dokimosLangchain4jVersion}'
    // Spring AI integration
    implementation 'dev.dokimos:dokimos-spring-ai:${dokimosSpringAiVersion}'
}
```

---

## Modules

- **dokimos-core**: Core evaluation framework with datasets, evaluators, and experiments
- **dokimos-junit5**: JUnit 5 integration for dataset-driven parameterized tests
- **dokimos-langchain4j**: LangChain4j integration for evaluation of production AI assistants and agents
- **dokimos-spring-ai**: Spring AI integration for using `ChatClient` and `ChatModel` as evaluation judges
- **dokimos-examples**: Runnable examples demonstrating evaluation patterns and custom evaluators

## Quick Start Examples

For complete, runnable examples see the [dokimos-examples](./dokimos-examples) module, which includes:
- Basic evaluation workflows
- **Custom evaluators** (extending `BaseEvaluator` or using `LLMJudgeEvaluator`)
- JUnit 5 parameterized testing
- LangChain4j RAG evaluation
- Spring AI RAG and evaluation

Run your first evaluation:

```java
Dataset dataset = Dataset.builder()
    .name("Support Questions")
    .addExample(Example.of("How do I reset my password?", "Click 'Forgot Password'..."))
    .addExample(Example.of("What's your refund policy?", "We offer 30-day refunds..."))
    .build();

ExperimentResult result = Experiment.builder()
    .dataset(dataset)
    .task(example -> Map.of("output", yourLLM.generate(example.input())))
    .evaluators(List.of(new ExactMatchEvaluator()))
    .run();

System.out.println("Pass rate: " + result.passRate());
```

## Experiment Server

The Dokimos server lets you store experiment results, track quality over time, and share findings with your team. It provides a simple web UI for viewing runs, comparing results, and debugging failures.

```bash
curl -O https://raw.githubusercontent.com/dokimos-dev/dokimos/master/docker-compose.yml
docker compose up -d
```

Open [http://localhost:8080](http://localhost:8080) to view the dashboard.

See the [server documentation](https://dokimos.dev/server/overview) for deployment options and configuration.

## Documentation

The full documentation is available
at: [https://dokimos-dev.github.io/dokimos/](https://dokimos-dev.github.io/dokimos/)

## Contributing

Contributions are welcome! Please see the [Contributing Guide](CONTRIBUTING.md) for details on how to get started.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
