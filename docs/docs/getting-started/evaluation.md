---
sidebar_position: 2 
---

# Evaluation Overview

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

This page shows you how Dokimos scores the output of your LLM application, so you can measure quality, catch regressions, and compare changes with numbers instead of guesses.

## Run your first evaluation

Here is a full, runnable example. It builds a small dataset, runs your application against it, scores the answers with an LLM judge, and prints a pass rate. Copy it, swap in your own `customerSupportBot` and `judge`, and run it.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.*;
import dev.dokimos.core.evaluators.LLMJudgeEvaluator;
import java.util.List;
import java.util.Map;

// 1. Build a dataset: inputs paired with the expected answers.
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
    .build();

// 2. Define the task: this calls your application for each example.
Task task = example -> {
    String answer = customerSupportBot.generateAnswer(example.input());
    return Map.of("output", answer);
};

// 3. Pick an evaluator to score each output.
List<Evaluator> evaluators = List.of(
    LLMJudgeEvaluator.builder()
        .name("Answer Quality")
        .criteria("Is the answer helpful and accurate?")
        .judge(judge)
        .threshold(0.8)
        .build()
);

// 4. Run the experiment and read the results.
ExperimentResult result = Experiment.builder()
    .name("QA Evaluation")
    .dataset(dataset)
    .task(task)
    .evaluators(evaluators)
    .build()
    .run();

System.out.println("Pass rate: " + String.format("%.2f%%", result.passRate() * 100));
System.out.println("Passed: " + result.passCount());
System.out.println("Failed: " + result.failCount());
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.kotlin.dsl.dataset
import dev.dokimos.kotlin.dsl.evaluators
import dev.dokimos.kotlin.dsl.experiment
import dev.dokimos.kotlin.dsl.llmJudge
import dev.dokimos.kotlin.dsl.task

// 1. Build a dataset: inputs paired with the expected answers.
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
}

// 2. Define the task: this calls your application for each example.
val task = task { example ->
    val answer = customerSupportBot.generateAnswer(example.input())
    mapOf("output" to answer)
}

// 3 and 4. Add an evaluator, run the experiment, read the results.
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

println("Pass rate: %.2f%%".format(result.passRate() * 100))
println("Passed: ${result.passCount()}")
println("Failed: ${result.failCount()}")
```

  </TabItem>
</Tabs>

That is the whole loop: a dataset goes in, a scored result comes out. The rest of this page explains the pieces.

## What evaluation gives you

Evaluation scores the responses of an AI application against metrics that fit your use case. You run it to:

- Find where your application is strong and where it is weak.
- Check that outputs match what users expect.
- Reduce the risk of shipping bad or unsafe responses.
- Decide which model, prompt, or retrieval setup to ship.

Scores turn "this feels better" into a number you can track over time.

## Core concepts

Dokimos evaluates LLM applications in Java and Kotlin. It runs offline evaluation: you score your application against a curated dataset. This fits benchmarking and regression testing during development, and it runs well inside a CI/CD pipeline to measure current performance and catch regressions.

Four concepts make up the framework:

- **Datasets**: A collection of data points used for evaluation. Load them programmatically, from files, or from a custom source. (In the example above, that is `Dataset.builder()`.)
- **Examples**: One data point in a dataset. Each example holds an input (such as a prompt) and an expected output (the correct response). (That is `Example.of(...)`.)
- **Evaluators**: The code that scores how well your application did. Dokimos ships built-in evaluators for common tasks, and you can write your own. (That is `LLMJudgeEvaluator` above.)
- **Experiments**: One run of an evaluation: a dataset plus a task plus evaluators. You can run experiments test-driven, often with parameterized tests. (That is `Experiment.builder()`.)

## Experiments

An experiment is the unit you run. It ties a dataset to a task and a set of evaluators, then produces scored results. Experiments plug into testing frameworks like JUnit, so you can run evaluation as part of your normal development workflow.

For useful experiments:

- Use datasets that reflect real-world inputs.
- Pick evaluators that match what you care about (accuracy, helpfulness, format, and so on).
- Read the results to find what to improve.

## Next steps

Now go deeper on each piece:

- [Create a Dataset](../evaluation/datasets)
- [Create an Evaluator](../evaluation/evaluators)
- [Run Experiments](../evaluation/experiments)
