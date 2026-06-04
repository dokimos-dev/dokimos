---
sidebar_position: 6
---

# Client

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

This page shows you how to send experiment results to a Dokimos server from your code, so your evaluation runs land in the web UI instead of staying in the console.

The `dokimos-server-client` module gives you `DokimosServerReporter`. It is a `Reporter` that batches results and POSTs them to a running server. You attach it to an experiment, run, and the results appear in the UI.

## Install

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-server-client</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

## Quick start

Build a reporter, point it at your server, and pass it to the experiment. Calling `run()` sends the results.

<Tabs groupId="language">
  <TabItem value="java" label="Java" default>

```java
import dev.dokimos.server.client.DokimosServerReporter;

// 1. Build the reporter.
DokimosServerReporter reporter = DokimosServerReporter.builder()
    .serverUrl("http://localhost:8080")
    .projectName("my-project")
    .build();

// 2. Attach it to the experiment and run.
ExperimentResult result = Experiment.builder()
    .name("my-experiment")
    .dataset(dataset)
    .task(task)
    .evaluators(evaluators)
    .reporter(reporter)
    .build()
    .run();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.kotlin.dsl.experiment
import dev.dokimos.server.client.DokimosServerReporter

// 1. Build the reporter.
val serverReporter = DokimosServerReporter.builder()
    .serverUrl("http://localhost:8080")
    .projectName("my-project")
    .build()

// 2. Attach it to the experiment and run.
val result = experiment {
    name = "my-experiment"
    dataset(dataset)
    task(task)
    evaluators { /* ... */ }
    reporter = serverReporter
}.run()
```

  </TabItem>
</Tabs>

That is the whole loop. `run()` calls `close()` for you, which flushes every pending result before returning. The rest of this page covers configuration, failure handling, and CI.

## Builder options

### Required

| Option | Description |
|--------|-------------|
| `serverUrl(String)` | Base URL of the Dokimos server (for example, `https://dokimos.example.com`) |
| `projectName(String)` | Project name that groups your experiments in the UI |

### Optional

| Option | Description | Default |
|--------|-------------|---------|
| `apiKey(String)` | Bearer API key for authentication | _(none)_ |
| `apiVersion(String)` | API version to call | `v1` |
| `onItemDeliveryFailure(Consumer<ItemDeliveryFailure>)` | Callback for batches permanently dropped after retries | _(none)_ |
| `spoolDirectory(Path)` | Append permanently failed batches to disk for later replay | _(off)_ |

### Set every option

<Tabs groupId="language">
  <TabItem value="java" label="Java" default>

```java
DokimosServerReporter reporter = DokimosServerReporter.builder()
    .serverUrl("https://dokimos.example.com")
    .projectName("my-llm-app")
    .apiKey("your-api-key")
    .apiVersion("v1")
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val reporter = DokimosServerReporter.builder()
    .serverUrl("https://dokimos.example.com")
    .projectName("my-llm-app")
    .apiKey("your-api-key")
    .apiVersion("v1")
    .build()
```

  </TabItem>
</Tabs>

## Configure with environment variables

For CI/CD and containers, read the configuration from the environment instead of hard-coding it.

| Variable | Description | Required |
|----------|-------------|----------|
| `DOKIMOS_SERVER_URL` | Server URL | Yes |
| `DOKIMOS_PROJECT_NAME` | Project name | Yes |
| `DOKIMOS_API_KEY` | API key | No |
| `DOKIMOS_API_VERSION` | API version | No |

Set the variables:

```bash
export DOKIMOS_SERVER_URL=https://dokimos.example.com
export DOKIMOS_PROJECT_NAME=my-project
export DOKIMOS_API_KEY=your-api-key
```

Then build the reporter from them:

<Tabs groupId="language">
  <TabItem value="java" label="Java" default>

```java
DokimosServerReporter reporter = DokimosServerReporter.fromEnvironment();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val reporter = DokimosServerReporter.fromEnvironment()
```

  </TabItem>
</Tabs>

`fromEnvironment()` throws `IllegalStateException` if `DOKIMOS_SERVER_URL` or `DOKIMOS_PROJECT_NAME` is missing.

## How it works

### Async processing

The client sends results in the background so it never blocks your experiment:

1. You call `reporter.reportItem()`. The item goes onto an internal queue.
2. A background thread batches queued items and POSTs them to the server.
3. Your experiment keeps running and does not wait for HTTP responses.

### Batching

Items ship in batches to cut HTTP overhead:

- **Batch size**: up to 10 items per request.
- **Batch timeout**: 500ms maximum wait.

Whichever limit is hit first triggers a send.

### Retries

A failed send retries up to 3 times with exponential backoff, starting at 100ms. Every batch POST carries an `Idempotency-Key` that is reused across retries, so a successful retry of an already recorded request deduplicates on the server.

Which status codes get retried:

- **`429 Too Many Requests`**: treated as transient and retried. If the response includes a `Retry-After` header (delay in seconds), that delay overrides the backoff for the next attempt.
- **`5xx`**: retried with backoff.
- **Other `4xx`**: terminal. The batch is not retried.

## Error handling

### Server unavailable at start

If the server is down when you start a run, the run still proceeds. The handle gets a local ID instead of a server ID:

```java
RunHandle handle = reporter.startRun("experiment", metadata);
// handle.runId() is "local-<timestamp>" when the server is unavailable.
```

The experiment runs normally, but its results are not stored.

### Authentication errors

If the API key check fails:

- The server returns `401 Unauthorized`.
- The client logs a warning like `Client error 401 for POST ...`.

### Permanently dropped items

If a batch still fails after every retry, those items are dropped and never recorded. By default this only writes an error log, which can leave CI green while data is lost. Two opt-in mechanisms make dropped batches visible.

#### getFailedItemCount()

`getFailedItemCount()` returns the total number of items dropped after retries. Check it after the run and fail the build if anything was lost:

```java
reporter.close();  // Flushes and drains all pending batches.

if (reporter.getFailedItemCount() > 0) {
    throw new IllegalStateException(
        reporter.getFailedItemCount() + " items were not recorded by the server");
}
```

#### onItemDeliveryFailure callback

Register a callback to react to each dropped batch as it happens. It receives an `ItemDeliveryFailure` record with `runId()`, `itemCount()`, and the dropped `items()`:

```java
DokimosServerReporter reporter = DokimosServerReporter.builder()
    .serverUrl("https://dokimos.example.com")
    .projectName("my-project")
    .onItemDeliveryFailure(failure ->
        log.error("Dropped {} items for run {}", failure.itemCount(), failure.runId()))
    .build();
```

The callback runs on the reporter's background worker thread, so keep it lightweight. Do not call `flush()`, `close()`, or `reportItem()` on the same reporter from inside it.

#### Durable spooling

Set `spoolDirectory(Path)` to write permanently failed batches to disk instead of losing them. Each dropped batch is appended as one JSON line to `failed-items.ndjson` in that directory, so an outage that outlasts every retry leaves a replayable record. Spooling is off by default.

```java
DokimosServerReporter reporter = DokimosServerReporter.builder()
    .serverUrl("https://dokimos.example.com")
    .projectName("my-project")
    .spoolDirectory(Path.of("target/dokimos-spool"))
    .build();
```

## Lifecycle methods

### flush()

Force every queued item to send and block until it is done:

```java
reporter.reportItem(handle, item1);
reporter.reportItem(handle, item2);
reporter.flush();  // Blocks until all items are sent.
```

Use this when you need items persisted before moving on.

### close()

Shut the reporter down cleanly:

```java
reporter.close();  // Flushes remaining items and stops the background thread.
```

`Experiment.run()` calls `close()` for you when the experiment finishes.

## Testing

### Mock the reporter

For unit tests, implement `Reporter` with a no-op stub that records what it received:

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
        // No-op.
    }

    @Override
    public void flush() {
        // No-op.
    }

    @Override
    public void close() {
        // No-op.
    }
}

// In the test:
MockReporter mockReporter = new MockReporter();
Experiment.builder()
    .reporter(mockReporter)
    // ...
    .build()
    .run();

assertThat(mockReporter.reportedItems).hasSize(expectedCount);
```

## CI/CD integration

Run evaluations on every push (and on a schedule) and report straight to your server. Store the server URL and API key as secrets, set the project name inline.

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

Pass `serverUrl()` to the builder, or set the `DOKIMOS_SERVER_URL` environment variable.

### "401 Unauthorized" errors

The server has API key authentication on, but one of these is true:

- No API key was provided, or
- The wrong API key was provided.

Make sure your `DOKIMOS_API_KEY` matches the server-side `DOKIMOS_API_KEY` environment variable.
