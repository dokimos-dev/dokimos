# Dokimos Server Client

A lightweight client library for sending experiment results to a Dokimos server. The client handles HTTP communication asynchronously, batches results, and includes automatic retries with exponential backoff to avoid blocking the execution of experiments.

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-server-client</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

## Usage

Configure the reporter and attach it to your experiment:

```java
var reporter = DokimosServerReporter.builder()
    .serverUrl("http://localhost:8080")
    .projectName("my-project")
    .apiKey("optional-api-key")  // Only needed if the server has auth enabled
    .build();

Experiment.builder()
    .name("My Evaluation")
    .dataset(dataset)
    .task(task)
    .evaluators(evaluators)
    .reporter(reporter)
    .build()
    .run();
```

The reporter automatically:
- Creates a new run on the server when the experiment starts
- Streams item results as they complete
- Marks the run as complete when the experiment finishes

## Environment Variables

For CI/CD pipelines and containerized environments, configure via environment variables:

| Variable | Description | Required |
|----------|-------------|----------|
| `DOKIMOS_SERVER_URL` | Server URL (e.g., `https://api.my-domain.com`) | Yes |
| `DOKIMOS_PROJECT_NAME` | Project name for organizing experiments | Yes |
| `DOKIMOS_API_KEY` | API key for authentication | No |
| `DOKIMOS_API_VERSION` | API version (default: `v1`) | No |

Then use `fromEnvironment()`:

```java
var reporter = DokimosServerReporter.fromEnvironment();

Experiment.builder()
    .name("My Evaluation")
    .dataset(dataset)
    .task(task)
    .evaluators(evaluators)
    .reporter(reporter)
    .build()
    .run();
```

## How It Works

The client is designed to minimize impact on experiment execution:

1. **Async batching**: Results are queued and sent in batches of up to 10 items or every 500ms, whichever comes first. A background thread handles all HTTP communication.

2. **Automatic retries**: Failed requests are retried up to 3 times with exponential backoff.

3. **Graceful degradation**: If the server is unavailable, the client logs a warning and continues. Your experiment completes normally if reporting fails.

4. **Non-blocking**: Network latency does not block your task execution. Results are buffered and sent asynchronously.

## CI/CD Integration (GitHub Actions)

Here is an example workflow that runs evaluations and reports to a Dokimos server:

```yaml
name: LLM Evaluation

on:
  push:
    branches: [main]
  schedule:
    - cron: '0 6 * * *'  # Daily at 6 AM

jobs:
  evaluate:
    runs-on: ubuntu-latest

    env:
      DOKIMOS_SERVER_URL: ${{ secrets.DOKIMOS_SERVER_URL }}
      DOKIMOS_PROJECT_NAME: my-agentic-ai-service
      DOKIMOS_API_KEY: ${{ secrets.DOKIMOS_API_KEY }}
      OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run evaluations
        run: mvn test -Dgroups=evaluation
```

Your test class:

```java
@Tag("evaluation")
class MyEvaluationTest {

    @Test
    void runEvaluation() {
        var reporter = DokimosServerReporter.fromEnvironment();

        var result = Experiment.builder()
            .name("production-eval")
            .dataset(loadDataset())
            .task(this::callLLM)
            .evaluators(List.of(
                new ExactMatchEvaluator(),
                new SemanticSimilarityEvaluator()
            ))
            .reporter(reporter)
            .build()
            .run();

        assertThat(result.passRate()).isGreaterThan(0.8);
    }
}
```

Results are automatically sent to the server and visible in the dashboard after each CI run.

## Further Reading

- [Dokimos Server README](../dokimos-server/README.md)
- [Server Documentation](https://dokimos.dev/server/overview)
