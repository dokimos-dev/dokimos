---
sidebar_position: 2 
---

# Evaluation Overview

Evaluation is an important aspect of (Gen)AI applications and usually refers to evaluation an AI application with different evaluation methods and metrics to score the performance depending on the use case.

## Why use evaluation in GenAI?

Evaluation is important for improving the performance, reliability, and safety of AI models. It helps to identify strengths and weaknesses, ensure alignment with user expectations, and mitigate risks associated with AI deployment. By systematically evaluating AI models, developers can make informed decisions about model selection, fine-tuning, and deployment strategies, ultimately leading to better user experiences and more trustworthy AI systems.

## Core Concepts in Dokimos

Dokimos provides a flexible framework for evaluating LLM applications in Java. At the moment it supports offline evaluation, which can be used for evaluating the application with a curated dataset, which is useful for benchmarking and regression testing during development. This setup can also typically be part of a CI/CD pipeline to measure the current performance of a system and to catch regressions.

The core concepts in Dokimos are:
- **Datasets**: Datasets are collections of data points used for evaluation. Dokimos supports different ways to load datasets, including programmatically, from files, or custom sources.
- **Examples**: Examples are individual data points within a dataset. Each example typically consists of an input (e.g., a prompt) and an expected output (e.g., the correct response).
- **Evaluators**: Evaluators are responsible for assessing the performance of the LLM application. Dokimos provides built-in evaluators for common evaluation tasks, but also allows you to create custom evaluators.
- **Experiments**: Experiments are the execution of evaluation tasks using datasets and evaluators. Dokimos allows you to run experiments in a test-driven way, often using parameterized tests.

## Experiments

**Experiments** are the core of any evaluation process. They are used to run evaluations using specific datasets and evaluators. In Dokimos, experiments can be defined in a way that allows for easy integration with testing frameworks like JUnit 5, enabling automated evaluation as part of your development workflow.

For good experiments, it's recommended to:
- Use representative datasets that reflect real-world scenarios.
- Choose appropriate evaluators that align with your evaluation goals.
- Analyze the results to identify areas for improvement in your LLM application.

To learn more about how to create Datasets, Evaluators, and run Experiments in Dokimos, check out the following guides:
- [See how to create a Dataset](/evaluation/datasets)
- [See how to create an Evaluator](/evaluation/evaluators)
- [See how to run Experiments](/evaluation/experiments)
