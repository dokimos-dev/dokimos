---
sidebar_position: 1
---

# Dokimos Overview

Dokimos is an open-source Evaluation framework for LLM applications in Java that helps you:

1. Build load datasets programatically, file-based, or with custom sources
2. Run experiments with built-in evaluators, or your own custom evaluators
3. Run evals in a test-driven way with parameterized tests

... to provide the essential foundation for building LLM applications (in Java).

Read the **[Getting started Guide](./getting-started/installation)**.

Lean more about what you can build with `dokimos` by exploring the [examples module](https://github.com/dokimos-dev/dokimos/tree/master/dokimos-examples).

## What's Next

We're actively working on expanding Dokimos with features that make evaluation in Java easier and more powerful:

- **More built-in evaluators**: Additional evaluators for common patterns like hallucination, misuse, contextual relevance, multi-turn conversations of AI agents, and more
- **Open-source experiment server**: Run and track experiments with a web UI, compare results, and share findings with your team
- **Server-side datasets**: Store and version datasets centrally, making them easier to share across teams
- **SPI (Service Provider Interface)**: Plug in custom implementations for storage, metrics, and reporting
- **Concurrency & parallelization**: Run evaluations faster with parallel execution
- **CLI**: Command-line tools for running experiments, managing datasets, and generating reports
- **Spring integration**: Support for Spring Boot applications with auto-configuration and beans

Want to see something else? [Open an issue](https://github.com/dokimos-dev/dokimos/issues) or contribute!
