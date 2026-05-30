---
sidebar_position: 1
---

# Server Overview

The Dokimos server is the production eval loop around your experiments. It stores run results and provides a web UI to view, compare, and track quality over time, and it closes the loop: hold datasets centrally and pin tests to a version, fail a build when a run regresses, score runs and production traces with an LLM judge, and turn evaluator misses back into new dataset versions.

The loop, end to end: pin a test to a [server dataset](./datasets) version, report the run, [gate it](./ci-gate) against its baseline in CI, [score](./llm-judge) runs and [production traces](./traces) with a judge, get [alerted](./alerting) on a regression, then [review and curate](./curation) the misses into the next dataset version.

## How It Works

**Simple setup.** Two commands get you running:

```bash
curl -O https://raw.githubusercontent.com/dokimos-dev/dokimos/master/docker-compose.yml
docker compose up -d
```

**Your infrastructure.** The server runs entirely on your machines.

**Just Docker.** The pre-built image from GitHub Container Registry includes everything, so no local building and no dependencies are needed to install.

**Persistent storage.** Results are stored in PostgreSQL.

## Why Use the Server?

**Centralized results**: All experiment data lives in one database and can be shared across your team.

**Web UI**: Browse experiments, view individual runs, and drill into specific test cases.

**Trend tracking**: See how your pass rates change over time and catch regressions before they reach production.

**Team collaboration**: Share results with teammates. Everyone sees the same data without passing files around.

**CI/CD integration**: Run evaluations in your pipeline and automatically report results to the server.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Your Infrastructure                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐    │
│   │   Local Dev  │     │   CI/CD      │     │  Production  │    │
│   │  Experiments │     │  Pipeline    │     │   Tests      │    │
│   └──────┬───────┘     └──────┬───────┘     └──────┬───────┘    │
│          │                    │                    │            │
│          └────────────────────┼────────────────────┘            │
│                               │                                 │
│                               ▼                                 │
│                    ┌──────────────────┐                         │
│                    │  DokimosServer   │                         │
│                    │    Reporter      │                         │
│                    └────────┬─────────┘                         │
│                             │ HTTP/JSON                         │
│                             ▼                                   │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │                    Dokimos Server                       │   │
│   │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │   │
│   │  │   REST API  │  │   Web UI    │  │   Background    │  │   │
│   │  │  /api/v1/*  │  │   React     │  │   Processing    │  │   │
│   │  └──────┬──────┘  └──────┬──────┘  └────────┬────────┘  │   │
│   │         │                │                  │           │   │
│   │         └────────────────┼──────────────────┘           │   │
│   │                          │                              │   │
│   │                          ▼                              │   │
│   │              ┌───────────────────────┐                  │   │
│   │              │     PostgreSQL        │                  │   │
│   │              │  Projects, Runs,      │                  │   │
│   │              │  Items, Eval Results  │                  │   │
│   │              └───────────────────────┘                  │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │                      Browser                            │   │
│   │  ┌─────────────────────────────────────────────────┐    │   │
│   │  │  Dashboard  │  Experiments  │  Runs  │  Items   │    │   │
│   │  └─────────────────────────────────────────────────┘    │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Data Model

The server organizes data hierarchically:

- **Project**: Top-level container (e.g., "my-llm-app")
  - **Experiment**: A named evaluation scenario (e.g., "customer-support-qa")
    - **Run**: A single execution of an experiment with timestamp and metadata
      - **Item**: Individual test case with input, output, and eval results

## Key Features

### Dashboard
See all your projects at one place with their latest runs.

### Experiment View
View all runs for an experiment with pass rate trends over time.

### Run Details
Drill into specific runs to see individual test cases, scores, and evaluation reasons.

### Expandable Items
Click on any item to see full input/output text and detailed evaluation results.

### Server datasets
Hold your datasets on the server, versioned and shared, and reference a specific version from code by URI. See [Server datasets](./datasets).

### Review and curation
Review the items evaluators got wrong, annotate them, and promote them into a new dataset version. See [Review and curation](./curation).

### Run comparison
Compare two runs item by item to see exactly what a change moved. See [Comparing runs](./diff).

### LLM judge
Score runs and traces on the server with an LLM as judge, using a stored connection that speaks the Open Responses or Chat Completions API. See [LLM judge](./llm-judge).

### Production traces
Ingest OTLP traces from your running app and evaluate them online as they arrive. See [Production traces](./traces).

### Regression alerting
Get a webhook when a run regresses against its baseline. See [Regression alerting](./alerting).

## Quick Start

```bash
curl -O https://raw.githubusercontent.com/dokimos-dev/dokimos/master/docker-compose.yml
docker compose up -d
```

Open [http://localhost:8080](http://localhost:8080). See [Getting Started](./getting-started) for a complete walkthrough.

## Next Steps

- [Getting Started](./getting-started): Run your first experiment with server reporting
- [Configuration](./configuration): Environment variables and settings
- [Deployment](./deployment): Share with your team or run in production
- [Authentication](./authentication): Secure write operations and scope API keys by role
- [Client](./client): Reporter client configuration
- [Server datasets](./datasets): Hold datasets on the server and reference them by URI
- [Review and curation](./curation): Turn evaluator misses into new dataset versions
- [Comparing runs](./diff): Diff two runs item by item
- [LLM judge](./llm-judge): Score runs and traces with an LLM as judge
- [Production traces](./traces): Ingest and evaluate production traffic
- [Regression alerting](./alerting): Webhook on a quality drop
  