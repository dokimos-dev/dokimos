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

## Error Handling

### Server Unavailable at Start

If the server is unavailable when starting a run:

```java
RunHandle handle = reporter.startRun("experiment", metadata);
// handle.runId() will be "local-<timestamp>" if the server is unavailable
```

The experiment runs normally but results won't be stored.

### Authentication Errors

If API key authentication fails:

- Server returns `401 Unauthorized`
- Client logs warning: `Client error 401 for POST ...`

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

Make sure that your `DOKIMOS_API_KEY` matches the server-side `DOKIMOS_API_KEY` environment variable.
