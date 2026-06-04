---
sidebar_position: 6
---

# Embabel Integration

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

This page shows you how to capture an [Embabel](https://github.com/embabel/embabel-agent) agent run as a Dokimos `AgentTrace` and score it with the agent evaluators. You register a listener, run the agent as you normally would, then read the trace out.

## What this integration gives you

**Trace capture from an event listener.** `EmbabelTraceCollector` implements Embabel's `AgenticEventListener`. It listens to the process events your agent emits and assembles an `AgentTrace` from the tool calls it observes.

**No change to how you run the agent.** You attach the collector to your `ProcessOptions` or `AgentInvocation.Builder`, run the agent, then read `collector.trace()`. The agent code stays the same.

**Straight into the agent evaluators.** The captured `AgentTrace` feeds the [agent evaluators](../evaluation/agent-evaluation) through `trace.toTestCase(input, tools)`.

## Setup

Add the integration dependency. It pulls in `dokimos-core`. You bring your own Embabel SDK version.

### Maven

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-embabel</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

You also need the Embabel agent API on your classpath:

```xml
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-api</artifactId>
    <version>0.4.0</version>
</dependency>
```

### Gradle (Groovy DSL)

```groovy
implementation 'dev.dokimos:dokimos-embabel:${dokimosVersion}'
implementation 'com.embabel.agent:embabel-agent-api:0.4.0'
```

## Capture a trace

The flow is three steps: create a collector, attach it to your run, run the agent, then read the trace.

`EmbabelSupport.attach` has two forms. One adds the collector to an existing `ProcessOptions`. The other attaches a fresh collector to an `AgentInvocation.Builder` and hands it back to you.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import com.embabel.agent.api.common.autonomy.AgentInvocation;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.embabel.EmbabelSupport;
import dev.dokimos.embabel.EmbabelTraceCollector;

// 1. Attach a collector to an invocation builder
AgentInvocation.Builder<String> builder = AgentInvocation.builder(agentPlatform)
    .options(ProcessOptions.DEFAULT);
EmbabelTraceCollector collector = EmbabelSupport.attach(builder);

// 2. Run the agent as usual
String response = builder.build(String.class).invoke(userInput);

// 3. Read the trace and the tools the agent was observed using
AgentTrace trace = collector.trace();
List<ToolDefinition> tools = EmbabelSupport.toToolDefinitions(collector);

// 4. Build a test case for the agent evaluators
EvalTestCase testCase = trace.toTestCase(userInput, tools);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import com.embabel.agent.api.common.autonomy.AgentInvocation
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.agents.AgentTrace
import dev.dokimos.core.agents.ToolDefinition
import dev.dokimos.embabel.EmbabelSupport
import dev.dokimos.embabel.EmbabelTraceCollector

// 1. Attach a collector to an invocation builder
val builder = AgentInvocation.builder(agentPlatform)
    .options(ProcessOptions.DEFAULT)
val collector: EmbabelTraceCollector = EmbabelSupport.attach(builder)

// 2. Run the agent as usual
val response = builder.build(String::class.java).invoke(userInput)

// 3. Read the trace and the tools the agent was observed using
val trace: AgentTrace = collector.trace()
val tools: List<ToolDefinition> = EmbabelSupport.toToolDefinitions(collector)

// 4. Build a test case for the agent evaluators
val testCase: EvalTestCase = trace.toTestCase(userInput, tools)
```

  </TabItem>
</Tabs>

If you already build your own `ProcessOptions`, create the collector yourself and attach it with the other overload:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import com.embabel.agent.core.ProcessOptions;
import dev.dokimos.embabel.EmbabelSupport;
import dev.dokimos.embabel.EmbabelTraceCollector;

EmbabelTraceCollector collector = new EmbabelTraceCollector();

// Returns a ProcessOptions with the collector wired in as a listener
ProcessOptions options = EmbabelSupport.attach(ProcessOptions.DEFAULT, collector);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import com.embabel.agent.core.ProcessOptions
import dev.dokimos.embabel.EmbabelSupport
import dev.dokimos.embabel.EmbabelTraceCollector

val collector = EmbabelTraceCollector()

// Returns a ProcessOptions with the collector wired in as a listener
val options: ProcessOptions = EmbabelSupport.attach(ProcessOptions.DEFAULT, collector)
```

  </TabItem>
</Tabs>

## Score the trace

`trace.toTestCase(input, tools)` builds the `EvalTestCase` the agent evaluators expect: the tool calls and final response go into the actual outputs, and the tool definitions go into metadata. Every evaluator uses `builder()`.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import dev.dokimos.core.evaluators.agents.ToolCorrectnessEvaluator;
import dev.dokimos.core.evaluators.agents.ToolEfficiencyEvaluator;
import dev.dokimos.embabel.EmbabelSupport;

AgentTrace trace = collector.trace();
List<ToolDefinition> tools = EmbabelSupport.toToolDefinitions(collector);

EvalTestCase testCase = trace.toTestCase("Find flights to Paris", tools);

// Deterministic checks, no judge needed
EvalResult validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
EvalResult efficiency = ToolEfficiencyEvaluator.builder().build().evaluate(testCase);
EvalResult correctness = ToolCorrectnessEvaluator.builder().build().evaluate(testCase);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.EvalResult
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.agents.AgentTrace
import dev.dokimos.core.agents.ToolDefinition
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator
import dev.dokimos.core.evaluators.agents.ToolCorrectnessEvaluator
import dev.dokimos.core.evaluators.agents.ToolEfficiencyEvaluator
import dev.dokimos.embabel.EmbabelSupport

val trace: AgentTrace = collector.trace()
val tools: List<ToolDefinition> = EmbabelSupport.toToolDefinitions(collector)

val testCase: EvalTestCase = trace.toTestCase("Find flights to Paris", tools)

// Deterministic checks, no judge needed
val validity: EvalResult = ToolCallValidityEvaluator.builder().build().evaluate(testCase)
val efficiency: EvalResult = ToolEfficiencyEvaluator.builder().build().evaluate(testCase)
val correctness: EvalResult = ToolCorrectnessEvaluator.builder().build().evaluate(testCase)
```

  </TabItem>
</Tabs>

For the LLM-based checks, pass a judge. See [Agent Evaluation](../evaluation/agent-evaluation) for the full list of nine evaluators and what each one checks.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.evaluators.agents.TaskCompletionEvaluator;
import dev.dokimos.core.evaluators.agents.ToolArgumentHallucinationEvaluator;

JudgeLM judge = prompt -> openAiClient.generate(prompt);

EvalTestCase testCase = trace.toTestCase(
    "Find flights to Paris",
    tools,
    List.of("Search for flights"));  // tasks, for TaskCompletionEvaluator

EvalResult completion = TaskCompletionEvaluator.builder()
    .judge(judge)
    .build()
    .evaluate(testCase);

EvalResult hallucination = ToolArgumentHallucinationEvaluator.builder()
    .judge(judge)
    .build()
    .evaluate(testCase);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.JudgeLM
import dev.dokimos.core.evaluators.agents.TaskCompletionEvaluator
import dev.dokimos.core.evaluators.agents.ToolArgumentHallucinationEvaluator

val judge = JudgeLM { prompt -> openAiClient.generate(prompt) }

val testCase: EvalTestCase = trace.toTestCase(
    "Find flights to Paris",
    tools,
    listOf("Search for flights"))  // tasks, for TaskCompletionEvaluator

val completion: EvalResult = TaskCompletionEvaluator.builder()
    .judge(judge)
    .build()
    .evaluate(testCase)

val hallucination: EvalResult = ToolArgumentHallucinationEvaluator.builder()
    .judge(judge)
    .build()
    .evaluate(testCase)
```

  </TabItem>
</Tabs>

## Inspect what was captured

Beyond `trace()`, the collector exposes the raw observations. Use these to debug or to assert directly on the calls.

- `collector.toolCalls()` returns the captured `List<ToolCall>` (name, arguments, result).
- `collector.observedToolNames()` returns the distinct tool names seen, in order.
- `collector.trace()` assembles the full `AgentTrace`.

## Limitations

Two limitations follow from how Embabel reports events. Keep them in mind when you pick evaluators.

:::warning Synthesized tool definitions

`EmbabelSupport.toToolDefinitions(collector)` builds one `ToolDefinition` per observed tool name, with an **empty input schema**. Embabel's events carry the tool names and call arguments, not the full tool contracts. So `ToolDescriptionReliabilityEvaluator` has little to score (no descriptions, no documented arguments), and its coverage is weakened. For real coverage, build the `ToolDefinition` list by hand from your actual tool contracts and pass that to `trace.toTestCase(input, tools)` instead.

:::

:::note Single-run collector

A collector captures one run. It is not thread-safe, and reusing it without clearing it appends a second run's calls onto the first. Call `collector.reset()` before reusing it, or create a fresh `EmbabelTraceCollector` per run.

```java
collector.reset(); // clears tool calls and observed names before the next run
```

:::

:::tip
The agent evaluators are framework-agnostic. Once you have an `AgentTrace`, scoring is identical across Embabel, Spring AI, LangChain4j, Koog, and OpenAI. See [Agent Evaluation](../evaluation/agent-evaluation) for the data model and every evaluator option.
:::
