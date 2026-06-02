---
sidebar_position: 1
---

import AgentPrompt from '@site/src/components/AgentPrompt';

# Dokimos Overview

Dokimos is an open-source Evaluation Framework for LLM applications in Java and Kotlin. It works with AI frameworks (Spring AI, LangChain4j, Koog, or plain Java) and helps you:

1. Build and manage datasets programatically, from files, or with custom sources
2. Run experiments with built-in evaluators, or your own custom evaluators
3. Evaluate AI agents, including their tool calls and execution traces
4. Run evals in a test-driven way with JUnit parameterized tests
5. Track experiment results over time with an optional server and web UI

Dokimos aims to bring the evaluation tooling that Python developers have to the Java ecosystem.

Using a coding agent? Paste this to get a first eval written against your own code.

<AgentPrompt />

Read the **[Getting started Guide](./getting-started/installation)**.

Lean more about what you can build with `dokimos` by exploring the [examples module](https://github.com/dokimos-dev/dokimos/tree/master/dokimos-examples).

## The production eval loop

The optional **[server](./server/overview)** closes the loop from a single run to a system that holds quality steady over time. You hold datasets on the server and pin tests to a version with [server datasets](./server/datasets), fail a build when a run regresses against its baseline with the [CI regression gate](./server/ci-gate), score runs and traces with the [server LLM judge](./server/llm-judge), evaluate [production traces](./server/traces) online as they arrive, get a webhook on a quality drop with [regression alerting](./server/alerting), and turn the items evaluators got wrong into new dataset versions through [review and curation](./server/curation). See the [server overview](./server/overview) for how the pieces fit together.

## For AI agents

Point a coding agent at the machine-readable docs: [llms.txt](https://dokimos.dev/llms.txt) indexes the documentation, and [llms-full.txt](https://dokimos.dev/llms-full.txt) is the whole thing in one file. Every page also has a markdown version and an "Open in ChatGPT / Claude" action in its footer.

## What's Next

We're actively working on expanding Dokimos with features that make evaluation in Java easier and more powerful:

- **More built-in evaluators**: Additional evaluators for common patterns like misuse detection and more
- **Test Data Generation**: Use LLMs to generate synthetic test datasets for evaluation
- **SPI (Service Provider Interface)**: Plug in custom implementations for storage, metrics, and reporting
- **CLI**: Command-line tools for running experiments, managing datasets, and generating reports

Want to see something else? [Open an issue](https://github.com/dokimos-dev/dokimos/issues) or contribute!
