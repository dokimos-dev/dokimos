---
sidebar_position: 1
---

import AgentPrompt from '@site/src/components/AgentPrompt';
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Dokimos Overview

Dokimos lets you score, track, and regression-test the responses of your LLM application in Java and Kotlin, so you know when a prompt or model change made things better or worse.

It is an open-source evaluation framework. It works with Spring AI, Spring AI Alibaba, LangChain4j, Koog, Embabel, or plain Java, and it helps you:

1. Build and manage datasets in code, from files, or with custom sources
2. Run experiments with built-in evaluators, or your own custom evaluators
3. Evaluate AI agents, including their tool calls and execution traces
4. Work with typed, structured data end to end, from task output to evaluator
5. Run evals in a test-driven way with JUnit parameterized tests
6. Track experiment results over time with an optional server and web UI

Dokimos is framework agnostic. The core depends on no AI framework, so it works with any LLM client, or none at all. The Spring AI, Spring AI Alibaba, LangChain4j, Koog, and Embabel modules are thin, optional bridges that capture a run in one line. You never need them to use Dokimos.

Dokimos brings the evaluation tooling that Python developers have to the Java ecosystem.

## See it run

Here is a complete experiment. It runs your LLM application against three examples, scores each answer with an LLM judge, and prints the pass rate.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.*;
import dev.dokimos.core.evaluators.LLMJudgeEvaluator;
import java.util.List;
import java.util.Map;

// 1. Build a dataset.
Dataset dataset = Dataset.builder()
    .name("Product Support Questions")
    .addExample(Example.of(
        "How do I reset my password?",
        "Click 'Forgot Password' on the login page and follow the email instructions"
    ))
    .addExample(Example.of(
        "Where can I track my order?",
        "Go to your account dashboard and click on 'Order History'"
    ))
    .addExample(Example.of(
        "What payment methods do you accept?",
        "We accept credit cards, PayPal, and bank transfers"
    ))
    .build();

// 2. Define the task that calls your application.
Task task = example -> {
    String answer = customerSupportBot.generateAnswer(example.input());
    return Map.of("output", answer);
};

// 3. Pick evaluators.
List<Evaluator> evaluators = List.of(
    LLMJudgeEvaluator.builder()
        .name("Answer Quality")
        .criteria("Is the answer helpful and accurate?")
        .judge(judge)
        .threshold(0.8)
        .build()
);

// 4. Run the experiment.
ExperimentResult result = Experiment.builder()
    .name("QA Evaluation")
    .dataset(dataset)
    .task(task)
    .evaluators(evaluators)
    .build()
    .run();

// 5. Read the results.
System.out.println("Pass rate: " + String.format("%.2f%%", result.passRate() * 100));
System.out.println("Total examples: " + result.totalCount());
System.out.println("Passed: " + result.passCount());
System.out.println("Failed: " + result.failCount());
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.kotlin.dsl.dataset
import dev.dokimos.kotlin.dsl.experiment
import dev.dokimos.kotlin.dsl.llmJudge
import dev.dokimos.kotlin.dsl.task

// 1. Build a dataset.
val dataset = dataset {
    name = "Product Support Questions"
    example {
        input = "How do I reset my password?"
        expected = "Click 'Forgot Password' on the login page and follow the email instructions"
    }
    example {
        input = "Where can I track my order?"
        expected = "Go to your account dashboard and click on 'Order History'"
    }
    example {
        input = "What payment methods do you accept?"
        expected = "We accept credit cards, PayPal, and bank transfers"
    }
}

// 2. Define the task that calls your application.
val task = task { example ->
    val answer = customerSupportBot.generateAnswer(example.input())
    mapOf("output" to answer)
}

// 3. Run the experiment with an LLM judge.
val result = experiment {
    name = "QA Evaluation"
    dataset(dataset)
    task(task)
    evaluators {
        llmJudge(judge) {
            name = "Answer Quality"
            criteria = "Is the answer helpful and accurate?"
            threshold = 0.8
        }
    }
}.run()

// 4. Read the results.
println("Pass rate: %.2f%%".format(result.passRate() * 100))
println("Total examples: ${result.totalCount()}")
println("Passed: ${result.passCount()}")
println("Failed: ${result.failCount()}")
```

  </TabItem>
</Tabs>

Want the full walkthrough, from adding the dependency to running this in a test? Read the **[Getting started Guide](./getting-started/installation)**.

To see what you can build, explore the [examples module](https://github.com/dokimos-dev/dokimos/tree/master/dokimos-examples).

## Using a coding agent?

Paste this prompt to get a first eval written against your own code.

<AgentPrompt />

## Structured and typed data

A task can return a real domain object, such as a record, a POJO, or a list. Dokimos compares it structurally, so numbers compare by value and formatting and key order do not count. You read it back type-safely in custom evaluators, LLM judges, tool-call results, and metadata. See the **[Structured and Typed Data](./evaluation/structured-typed-data.md)** guide for the whole pipeline in one place.

## The production eval loop

The optional **[server](./server/overview)** closes the loop from a single run to a system that holds quality steady over time. You can:

- Hold datasets on the server and pin tests to a version with [server datasets](./server/datasets).
- Fail a build when a run regresses against its baseline with the [CI regression gate](./server/ci-gate).
- Score runs and traces with the [server LLM judge](./server/llm-judge).
- Evaluate [production traces](./server/traces) online as they arrive.
- Get a webhook on a quality drop with [regression alerting](./server/alerting).
- Turn the items evaluators got wrong into new dataset versions through [review and curation](./server/curation).

See the [server overview](./server/overview) for how the pieces fit together.

## For AI agents

Point a coding agent at the machine-readable docs. [llms.txt](https://dokimos.dev/llms.txt) indexes the documentation, and [llms-full.txt](https://dokimos.dev/llms-full.txt) is the whole thing in one file. Every page also has a markdown version and an "Open in ChatGPT / Claude" action in its footer.

## What's next

We are expanding Dokimos with features that make evaluation in Java easier:

- **More built-in evaluators**: Additional evaluators for common patterns like misuse detection and more.
- **Test Data Generation**: Use LLMs to generate synthetic test datasets for evaluation.
- **SPI (Service Provider Interface)**: Plug in custom implementations for storage, metrics, and reporting.
- **CLI**: Command-line tools for running experiments, managing datasets, and generating reports.

Want to see something else? [Open an issue](https://github.com/dokimos-dev/dokimos/issues) or contribute!
