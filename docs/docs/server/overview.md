---
sidebar_position: 1
---

# Server Overview

The Dokimos server stores your experiment results and provides a web UI to view, compare, and track quality over time.

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
- [Authentication](./authentication): Secure write operations
- [Client](./client): Reporter client configuration
  