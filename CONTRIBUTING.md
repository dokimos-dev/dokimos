# Contributing to Dokimos

Thanks for your interest in contributing to Dokimos! This guide will help you get started with development, testing, and
submitting your contributions.

## Prerequisites

- **JDK 17 or higher** installed (the project supports Java 17, 21, and 25)
- **Maven 3.6+** (the project uses it for builds)

Recommended:

- An IDE like IntelliJ IDEA or VS Code with Java support

## Getting Started

1. Clone the repo:

    ```bash
    git clone https://github.com/dokimos-dev/dokimos.git
    ```

2. Build all modules from the root of the repo:

    ```bash
    mvn clean install
    ```

This compiles the code, runs tests, and installs artifacts to your local Maven repository. The build includes:

- **dokimos-core**: Core evaluation framework
- **dokimos-junit**: JUnit integration
- **dokimos-langchain4j**: LangChain4j integration
- **dokimos-examples**: Example implementations

To skip tests during build:

```bash
mvn clean install -DskipTests
```

## Project Structure

```
dokimos/
├── dokimos-core/          # Core evaluation framework
│   ├── src/main/java/     # Source code
│   └── src/test/java/     # Unit and integration tests
├── dokimos-junit/         # JUnit integration
├── dokimos-langchain4j/   # LangChain4j integration
├── dokimos-examples/      # Runnable examples
└── docs/                  # Documentation site (Docusaurus)
```

## Running Tests

### Unit Tests

There are several ways to run the tests, e.g. in IntelliJ you can use the Run widget or the structured tool window.
If you want to run them via your Terminal, use the following command to run all unit tests:

```bash
mvn test
```

To run tests for a specific module, e.g. `dokimos-core`:

```bash
mvn test -pl dokimos-core -am
```

Run a specific test class:

```bash
mvn test -pl dokimos-core -Dtest=ExactMatchEvaluatorTest -am
```

### Integration Tests

Integration tests are marked with `@Tag("integration")` and excluded from regular builds. They typically require
external API keys, such as from OpenAI.

Run integration tests:

```bash
mvn verify -Dgroups=integration
```

Some integration tests need an OpenAI API key:

```bash
export OPENAI_API_KEY='your-api-key-here'
mvn verify -Dgroups=integration
```

### Testing Strategy

- Unit tests use JUnit and AssertJ for assertions
- Tag expensive tests requiring external APIs with `@Tag("integration")`

## Some General Coding Guidelines

- **Java version**: Write code compatible with Java 17, which is the minimum supported version
- **Javadoc**: Public APIs should have Javadoc comments explaining purpose, parameters, return values, and exceptions

## Working on Documentation

The docs site uses Docusaurus. To preview changes:

```bash
cd docs
npm install
npm start
```

This starts a local server at `http://localhost:3000`.

## Running the Experiment Server

To run the experiment server locally for development or testing:

```bash
cd dokimos-server
docker compose up
```

This starts the server at `http://localhost:8080` with a PostgreSQL database.

For configuration options and deployment details, see the [dokimos-server README](./dokimos-server/README.md).

> **Note**: A devcontainer configuration is planned to simplify container-based development workflows.

## Getting Help

- **Questions**: Open a GitHub Discussion
- **Bugs**: Create an issue with reproduction steps
- **Feature requests**: Open an issue describing the use case