---
sidebar_position: 6
---

# Client

The `dokimos-server-client` module provides `DokimosServerReporter`, a client that sends experiment results to a Dokimos server.

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-server-client</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

## Basic Usage

```java
import dev.dokimos.server.client.DokimosServerReporter;

// Create reporter
DokimosServerReporter reporter = DokimosServerReporter.builder()
    .serverUrl("http://localhost:8080")
    .projectName("my-project")
    .build();

// Attach to experiment
ExperimentResult result = Experiment.builder()
    .name("my-experiment")
    .dataset(dataset)
    .task(task)
    .evaluators(evaluators)
    .reporter(reporter)
    .build()
    .run();
```

## Builder Options

### Required Options

| Option | Description |
|--------|-------------|
| `serverUrl(String)` | Base URL of the Dokimos server (e.g., `https://dokimos.example.com`) |
| `projectName(String)` | Project name for organizing experiments |

### Optional Options

| Option | Description | Default |
|--------|-------------|---------|
| `apiKey(String)` | API key for authentication | _(none)_ |
| `apiVersion(String)` | API version to use | `v1` |

### Example with All Options

```java
DokimosServerReporter reporter = DokimosServerReporter.builder()
    .serverUrl("https://dokimos.example.com")
    .projectName("my-llm-app")
    .apiKey("your-api-key")
    .apiVersion("v1")
    .build();
```

## Environment Variable Configuration

For CI/CD and containerized environments, configure via environment variables:

| Variable | Description | Required |
|----------|-------------|----------|
| `DOKIMOS_SERVER_URL` | Server URL | Yes |
| `DOKIMOS_PROJECT_NAME` | Project name | Yes |
| `DOKIMOS_API_KEY` | API key | No |
| `DOKIMOS_API_VERSION` | API version | No |

```bash
export DOKIMOS_SERVER_URL=https://dokimos.example.com
export DOKIMOS_PROJECT_NAME=my-project
export DOKIMOS_API_KEY=your-api-key
```

```java
DokimosServerReporter reporter = DokimosServerReporter.fromEnvironment();
```

This throws `IllegalStateException` if required variables are missing.

## How It Works

### Async Processing

The client processes results asynchronously to avoid blocking experiment execution:

1. When you call `reporter.reportItem()`, items are added to an internal queue
2. A background thread batches items and sends them to the server
3. Your experiment continues without waiting for HTTP responses

### Batching

Items are sent in batches to reduce HTTP overhead:

- **Batch size**: Up to 10 items per request
- **Batch timeout**: 500ms maximum wait time

Whichever threshold is reached first triggers a batch send.

### Retry Logic

Failed requests are retried with exponential backoff:

- **Max retries**: 3 attempts
- **Initial backoff**: 100ms
- **Backoff multiplier**: 2x (100ms → 200ms → 400ms)

Client errors (4xx) are not retried. Server errors (5xx) and network failures are retried.

### Graceful Degradation

If the server is unavailable:

1. The client logs a warning
2. The experiment continues normally
3. Results are lost (not persisted locally)

This ensures network issues don't break your evaluation pipeline.

## Error Handling

### Server Unavailable at Start

If the server is unavailable when starting a run:

```java
RunHandle handle = reporter.startRun("experiment", metadata);
// handle.runId() will be "local-<timestamp>" if server unavailable
```

The experiment runs normally but results won't be stored.

### Server Unavailable During Run

If the server becomes unavailable during a run:

- Items in the queue are retried
- After max retries, items are dropped with a warning log
- The experiment continues
- The run may be incomplete on the server

### Authentication Errors

If API key authentication fails:

- Server returns `401 Unauthorized`
- Client logs warning: `Client error 401 for POST ...`
- Items are not retried (client errors don't retry)
- Experiment continues but results aren't stored

## Lifecycle Methods

### flush()

Force all queued items to be sent:

```java
reporter.reportItem(handle, item1);
reporter.reportItem(handle, item2);
reporter.flush();  // Blocks until all items are sent
```

Useful when you need to ensure items are persisted before proceeding.

### close()

Shut down the reporter cleanly:

```java
reporter.close();  // Flushes remaining items and stops background thread
```

The `Experiment.run()` method calls `close()` automatically after completing.

## Performance Characteristics

### Throughput

The client can handle high throughput experiments:

- Background thread processes items independently
- Batching reduces HTTP overhead
- Non-blocking design doesn't slow down task execution

Typical overhead: < 1ms per item added to queue.

### Memory Usage

Items are held in memory until sent:

- Each item in queue: ~1-10KB depending on content size
- Queue is unbounded (could grow large if server is slow)
- Items are removed after successful send or max retries

### Network Usage

Per batch of 10 items:

- One HTTP POST request
- Request size: depends on item content (typically 10-100KB)
- Response size: minimal (~50 bytes)

## Testing

### Mocking the Reporter

For unit tests, create a mock reporter:

```java
class MockReporter implements Reporter {
    List<ItemResult> reportedItems = new ArrayList<>();

    @Override
    public RunHandle startRun(String name, Map<String, Object> metadata) {
        return new RunHandle("mock-run-id");
    }

    @Override
    public void reportItem(RunHandle handle, ItemResult result) {
        reportedItems.add(result);
    }

    @Override
    public void completeRun(RunHandle handle, RunStatus status) {
        // No-op
    }

    @Override
    public void flush() {
        // No-op
    }

    @Override
    public void close() {
        // No-op
    }
}

// In test
MockReporter mockReporter = new MockReporter();
Experiment.builder()
    .reporter(mockReporter)
    // ...
    .build()
    .run();

assertThat(mockReporter.reportedItems).hasSize(expectedCount);
```

### Integration Tests

Test against a real server in integration tests:

```java
@Tag("integration")
class ServerIntegrationTest {

    private DokimosServerReporter reporter;

    @BeforeEach
    void setUp() {
        reporter = DokimosServerReporter.builder()
            .serverUrl("http://localhost:8080")
            .projectName("integration-tests")
            .build();
    }

    @AfterEach
    void tearDown() {
        reporter.close();
    }

    @Test
    void shouldReportResults() {
        ExperimentResult result = Experiment.builder()
            .name("integration-test-" + System.currentTimeMillis())
            .dataset(testDataset)
            .task(testTask)
            .evaluators(testEvaluators)
            .reporter(reporter)
            .build()
            .run();

        assertThat(result.passRate()).isGreaterThanOrEqualTo(0.0);
        // Results should be visible in server UI
    }
}
```

### Test Containers

Use Testcontainers for isolated integration tests:

```java
@Testcontainers
class ServerContainerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> server = new GenericContainer<>("dokimos-server:latest")
        .withExposedPorts(8080)
        .withEnv("DB_HOST", postgres.getHost())
        .withEnv("DB_PORT", postgres.getFirstMappedPort().toString())
        .dependsOn(postgres);

    @Test
    void shouldReportToContainerizedServer() {
        String serverUrl = "http://" + server.getHost() + ":" + server.getFirstMappedPort();

        DokimosServerReporter reporter = DokimosServerReporter.builder()
            .serverUrl(serverUrl)
            .projectName("container-test")
            .build();

        // Run experiment...
    }
}
```

## CI/CD Integration

### GitHub Actions

```yaml
name: Evaluation

on:
  push:
    branches: [main]
  schedule:
    - cron: '0 6 * * *'

jobs:
  evaluate:
    runs-on: ubuntu-latest
    env:
      DOKIMOS_SERVER_URL: ${{ secrets.DOKIMOS_SERVER_URL }}
      DOKIMOS_PROJECT_NAME: my-app
      DOKIMOS_API_KEY: ${{ secrets.DOKIMOS_API_KEY }}

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run evaluations
        run: mvn test -Dgroups=evaluation
```

### GitLab CI

```yaml
evaluation:
  stage: test
  image: maven:3.9-eclipse-temurin-21
  variables:
    DOKIMOS_SERVER_URL: $DOKIMOS_SERVER_URL
    DOKIMOS_PROJECT_NAME: my-app
    DOKIMOS_API_KEY: $DOKIMOS_API_KEY
  script:
    - mvn test -Dgroups=evaluation
  only:
    - main
    - schedules
```

### Jenkins

```groovy
pipeline {
    agent any

    environment {
        DOKIMOS_SERVER_URL = credentials('dokimos-server-url')
        DOKIMOS_PROJECT_NAME = 'my-app'
        DOKIMOS_API_KEY = credentials('dokimos-api-key')
    }

    stages {
        stage('Evaluate') {
            steps {
                sh 'mvn test -Dgroups=evaluation'
            }
        }
    }
}
```

## Troubleshooting

### "serverUrl is required"

```
IllegalStateException: serverUrl is required
```

Either pass `serverUrl()` to the builder or set `DOKIMOS_SERVER_URL` environment variable.

### "401 Unauthorized" Errors

The server has API key authentication enabled but:
- No API key provided, or
- Wrong API key provided

Check your `DOKIMOS_API_KEY` matches the server's `DOKIMOS_API_KEY`.

### Results Not Appearing

1. Check server is running: `curl http://localhost:8080/actuator/health`
2. Check for client-side errors in logs
3. Verify project name matches what you expect
4. Wait a few seconds for async processing to complete

### High Memory Usage

If processing very large experiments:
- Items queue in memory until sent
- Consider smaller batch sizes or more frequent flushes
- Monitor queue size in logs
