---
sidebar_position: 1
---

# Server Overview

The Dokimos server provides centralized storage and visualization for your experiment results. Instead of tracking evaluations in local files or logs, you can store everything in one place where your team can view runs, compare results, and track quality trends over time.

## Why Use the Server?

**Centralized results**: All experiment data lives in one database. No more scattered CSV files or log outputs.

**Web UI**: Browse experiments, view individual runs, and drill into specific test cases without writing code.

**Trend tracking**: See how your pass rates change over time. Catch regressions before they reach production.

**Team collaboration**: Share results with teammates. Everyone sees the same data without passing files around.

**CI/CD integration**: Run evaluations in your pipeline and automatically report results to the server.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Your Infrastructure                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐    │
│   │   Local Dev  │     │   CI/CD      │     │  Production  │    │
│   │  Experiments │     │  Pipeline    │     │   Tests      │    │
│   └──────┬───────┘     └──────┬───────┘     └──────┬───────┘    │
│          │                    │                    │             │
│          └────────────────────┼────────────────────┘             │
│                               │                                  │
│                               ▼                                  │
│                    ┌──────────────────┐                          │
│                    │  DokimosServer   │                          │
│                    │    Reporter      │                          │
│                    └────────┬─────────┘                          │
│                             │ HTTP/JSON                          │
│                             ▼                                    │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │                    Dokimos Server                        │   │
│   │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │   │
│   │  │   REST API  │  │   Web UI    │  │   Background    │  │   │
│   │  │  /api/v1/*  │  │   React     │  │   Processing    │  │   │
│   │  └──────┬──────┘  └──────┬──────┘  └────────┬────────┘  │   │
│   │         │                │                  │            │   │
│   │         └────────────────┼──────────────────┘            │   │
│   │                          │                               │   │
│   │                          ▼                               │   │
│   │              ┌───────────────────────┐                   │   │
│   │              │     PostgreSQL        │                   │   │
│   │              │  Projects, Runs,      │                   │   │
│   │              │  Items, Eval Results  │                   │   │
│   │              └───────────────────────┘                   │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │                      Browser                             │   │
│   │  ┌─────────────────────────────────────────────────┐    │   │
│   │  │  Dashboard  │  Experiments  │  Runs  │  Items   │    │   │
│   │  └─────────────────────────────────────────────────┘    │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
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
See all your projects at a glance with their latest run status.

### Experiment View
View all runs for an experiment with pass rate trends over time.

### Run Details
Drill into specific runs to see individual test cases, scores, and evaluation reasons.

### Expandable Items
Click on any item to see full input/output text and detailed evaluation results.

## Quick Start

Get started in 30 seconds:

```bash
cd dokimos-server
docker compose up
```

Then open [http://localhost:8080](http://localhost:8080).

See the [Getting Started](./getting-started) guide for a complete walkthrough.

## Next Steps

- [Getting Started](./getting-started) - Run your first experiment with server reporting
- [Configuration](./configuration) - Environment variables and settings
- [Deployment](./deployment) - Production deployment options
- [Authentication](./authentication) - Securing your server
- [Client](./client) - Using the reporter client in your code
