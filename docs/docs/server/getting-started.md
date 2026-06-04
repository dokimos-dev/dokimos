---
sidebar_position: 2
---

# Getting Started

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

This page gets the Dokimos server running locally and sends it your first evaluation results, so you can see pass rates in a web UI. No cloning, no building, just Docker.

## Start the server

Run these two commands:

```bash
# Download the compose file
curl -O https://raw.githubusercontent.com/dokimos-dev/dokimos/master/docker-compose.yml

# Start the server
docker compose up -d
```

The server is now running at [http://localhost:8080](http://localhost:8080). Open it in your browser to confirm.

:::tip No Docker?
If you don't have Docker installed, get it from [docker.com](https://docs.docker.com/get-docker/).
:::

## Send your first results

First, add the client dependency to your project:

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-server-client</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

Next, run an experiment that reports to the server. Copy this in, swap `callYourLLM` for your own LLM call, and run it:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.*;
import dev.dokimos.server.client.DokimosServerReporter;

public class MyFirstServerExperiment {
    public static void main(String[] args) {
        // Create dataset
        Dataset dataset = Dataset.builder()
            .name("Capital Cities")
            .addExample(Example.of("What is the capital of France?", "Paris"))
            .addExample(Example.of("What is the capital of Japan?", "Tokyo"))
            .build();

        // Connect to the local server
        DokimosServerReporter reporter = DokimosServerReporter.builder()
            .serverUrl("http://localhost:8080")
            .projectName("my-first-project")
            .build();

        // Run experiment
        ExperimentResult result = Experiment.builder()
            .name("capitals-qa")
            .dataset(dataset)
            .task(example -> {
                String answer = callYourLLM(example.input());
                return Map.of("output", answer);
            })
            .evaluators(List.of(
                ExactMatchEvaluator.builder()
                    .name("exact-match")
                    .threshold(1.0)
                    .build()
            ))
            .reporter(reporter)
            .build()
            .run();

        System.out.println("Pass rate: " + result.passRate());
    }
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.kotlin.dsl.dataset
import dev.dokimos.kotlin.dsl.exactMatch
import dev.dokimos.kotlin.dsl.experiment
import dev.dokimos.kotlin.dsl.task
import dev.dokimos.server.client.DokimosServerReporter

fun main() {
    // Create dataset
    val dataset = dataset {
        name = "Capital Cities"
        example {
            input = "What is the capital of France?"
            expected = "Paris"
        }
        example {
            input = "What is the capital of Japan?"
            expected = "Tokyo"
        }
    }

    // Connect to the local server
    val reporter = DokimosServerReporter.builder()
        .serverUrl("http://localhost:8080")
        .projectName("my-first-project")
        .build()

    // Run experiment
    val result = experiment {
        name = "capitals-qa"
        dataset(dataset)
        task {
            val answer = callYourLLM(input())
            mapOf("output" to answer)
        }
        evaluators {
            exactMatch {
                name = "exact-match"
                threshold = 1.0
            }
        }
        reporter(reporter)
    }.run()

    println("Pass rate: ${result.passRate()}")
}
```

  </TabItem>
</Tabs>

The `reporter` sends every run to the server. The `projectName` groups runs together in the UI.

## View results in the UI

After the experiment runs, follow these steps:

1. Open [http://localhost:8080](http://localhost:8080)
2. Click your project "my-first-project"
3. Click the experiment to see pass rates
4. Click a run to see individual test cases and evaluation details

## Manage the server

Use these commands to watch logs, stop the server, or wipe its data:

```bash
# View logs
docker compose logs -f server

# Stop the server
docker compose down

# Stop and remove all data
docker compose down -v
```

## Next steps

You have reported one run. The server is built to close the loop around it, so quality holds steady as your app changes:

- [Server datasets](./datasets): hold this dataset on the server and pin the test to an exact version
- [CI regression gate](./ci-gate): fail the build when a run regresses against its baseline
- [LLM judge](./llm-judge): score runs and traces on the server with an LLM as judge
- [Production traces](./traces): ingest OTLP traces from your running app and evaluate them online
- [Review and curation](./curation): turn the items evaluators got wrong into the next dataset version

To operate the server, read these:

- [Configuration](./configuration): Customize settings and environment variables
- [Deployment](./deployment): Share with your team or run in production
- [Authentication](./authentication): Secure write operations and scope API keys by role
- [Client](./client): Advanced reporter configuration

---

## Build from source (development)

Building from source is only for contributing to Dokimos. To use the server, the [steps above](#start-the-server) are all you need.

To build the server locally:

```bash
# Clone the repository
git clone https://github.com/dokimos-dev/dokimos.git
cd dokimos

# Use the development compose file
cd dokimos-server
docker compose -f docker-compose.dev.yml up --build
```

See the [Server README](https://github.com/dokimos-dev/dokimos/blob/master/dokimos-server/README.md) for more details.
