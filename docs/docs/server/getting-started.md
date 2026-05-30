---
sidebar_position: 2
---

# Getting Started

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

Get the Dokimos server running in under a minute. No building, no cloning, just Docker.

## Start the Server

Run these two commands:

```bash
# Download the compose file
curl -O https://raw.githubusercontent.com/dokimos-dev/dokimos/master/docker-compose.yml

# Start the server
docker compose up -d
```

The server will now be running at [http://localhost:8080](http://localhost:8080).

:::tip No Docker?
If you don't have Docker installed, get it from [docker.com](https://docs.docker.com/get-docker/).
:::

## Send Your First Results

Add the client dependency to your project:

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-server-client</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

Create an experiment that reports to the server:

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

## View Results in the UI

After running your experiment:

1. Open [http://localhost:8080](http://localhost:8080)
2. Click on your project "my-first-project"
3. Click on the experiment to see pass rates
4. Click on a run to see individual test cases and evaluation details

## Managing the Server

```bash
# View logs
docker compose logs -f server

# Stop the server 
docker compose down

# Stop and remove all data
docker compose down -v
```

## Next Steps

You have reported one run. The server is built to close the loop around it, so quality holds steady as your app changes:

- [Server datasets](./datasets): hold this dataset on the server and pin the test to an exact version
- [CI regression gate](./ci-gate): fail the build when a run regresses against its baseline
- [LLM judge](./llm-judge): score runs and traces on the server with an LLM as judge
- [Production traces](./traces): ingest OTLP traces from your running app and evaluate them online
- [Review and curation](./curation): turn the items evaluators got wrong into the next dataset version

Operating the server:

- [Configuration](./configuration): Customize settings and environment variables
- [Deployment](./deployment): Share with your team or run in production
- [Authentication](./authentication): Secure write operations and scope API keys by role
- [Client](./client): Advanced reporter configuration

---

## Building from Source (Development)

If you're contributing to Dokimos and need to build the server locally:

```bash
# Clone the repository
git clone https://github.com/dokimos-dev/dokimos.git
cd dokimos

# Use the development compose file
cd dokimos-server
docker compose -f docker-compose.dev.yml up --build
```

See the [Server README](https://github.com/dokimos-dev/dokimos/blob/master/dokimos-server/README.md) for more details.
