---
sidebar_position: 2
---

# Getting Started

This guide walks you through running the Dokimos server locally and sending your first experiment results.

## Prerequisites

- **Docker** and **Docker Compose** installed
- **Java 17+** for running experiments
- **Maven** for building the project

## Start the Server

1. Clone the repository (if you haven't already):

```bash
git clone https://github.com/dokimos-dev/dokimos.git
cd dokimos
```

2. Start the server with Docker Compose:

```bash
cd dokimos-server
docker compose up
```

This starts:
- **PostgreSQL** on port 5432
- **Dokimos Server** on port 8080

3. Wait for the server to be ready. You'll see:

```
dokimos-server  | Started DokimosServerApplication in X seconds
```

## Access the UI

Open [http://localhost:8080](http://localhost:8080) in your browser.

You'll see an empty dashboard since no experiments have been run yet.

## Run Your First Experiment

Let's run an experiment that reports results to the server.

### Option 1: Use the Example

The repository includes a ready-to-run example:

```bash
# From the repository root
cd dokimos-examples

# Set your OpenAI API key
export OPENAI_API_KEY=your-key-here

# Run the example
mvn exec:java -Dexec.mainClass="dev.dokimos.examples.basic.ServerReporterExample"
```

### Option 2: Write Your Own

Add the server client dependency:

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-server-client</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

Create an experiment with the server reporter:

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

        // Create reporter pointing to local server
        DokimosServerReporter reporter = DokimosServerReporter.builder()
            .serverUrl("http://localhost:8080")
            .projectName("my-first-project")
            .build();

        // Define your task (replace with your actual LLM call)
        Task task = example -> {
            String answer = callYourLLM(example.input());
            return Map.of("output", answer);
        };

        // Run experiment
        ExperimentResult result = Experiment.builder()
            .name("capitals-qa")
            .dataset(dataset)
            .task(task)
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

## Verify Results in the UI

After running the experiment:

1. Refresh the dashboard at [http://localhost:8080](http://localhost:8080)

2. You should see your project listed (e.g., "my-first-project")

3. Click on the project to see experiments

4. Click on an experiment to see runs with pass rate trends

5. Click on a run to see individual items and their evaluation results

## What You'll See

### Dashboard
Shows all projects with their latest run status.

### Project Page
Lists all experiments in a project with their last run date and pass rate.

### Experiment Page
Shows a trend chart of pass rates over time and a table of all runs.

### Run Page
Displays summary statistics (total items, passed, pass rate, duration) and a table of all test items. Click any item to expand and see:
- Full input text
- Expected output
- Actual output
- Detailed evaluation results with scores and reasons

## Stopping the Server

To stop the server:

```bash
# If running in foreground
Ctrl+C

# If running in background
docker compose down
```

Data is persisted in a Docker volume, so your results will still be there when you restart.

## Next Steps

- [Configuration](./configuration) - Customize server settings
- [Deployment](./deployment) - Run in production
- [Authentication](./authentication) - Secure your server
- [Client](./client) - Advanced client configuration
