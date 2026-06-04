---
sidebar_position: 5
---

# Spring AI Alibaba Integration

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

This page shows you how to evaluate a [Spring AI Alibaba](https://github.com/alibaba/spring-ai-alibaba) graph or agent run with Dokimos. Spring AI Alibaba's graph runtime carries its whole conversation as standard Spring AI message types, so Dokimos folds a run's `OverAllState` straight into an `AgentTrace` and reuses the same message extraction as the [Spring AI integration](./spring-ai).

## What you get

- **Graph state to trace**: fold a graph run's `OverAllState` `"messages"` list into a single `AgentTrace` with `SpringAiAlibabaSupport.toAgentTrace(...)`.
- **Reuses Spring AI**: tool-call and tool-definition conversion delegate to `SpringAiSupport`, so the same `AssistantMessage`/`ToolResponseMessage` handling applies.
- **Per-turn correlation**: tool calls are matched to their results turn by turn, so a tool-call id reused across turns never binds to the wrong result.

## Step 1: Add the dependency

This module pulls `dokimos-core` and `dokimos-spring-ai`. You bring the Spring AI Alibaba SDK (`spring-ai-alibaba-graph-core:1.0.0.2`) yourself.

### Maven

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-spring-ai-alibaba</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

### Gradle (Groovy DSL)

```groovy
implementation 'dev.dokimos:dokimos-spring-ai-alibaba:${dokimosVersion}'
```

## Step 2: Fold a graph run into a trace

A Spring AI Alibaba `ReactAgent` runs on a compiled graph. The graph keeps every intermediate tool call in its `OverAllState`, under the `"messages"` key. `SpringAiAlibabaSupport.toAgentTrace(state)` reads that list and builds one `AgentTrace`: the tool calls come from the assistant messages, and the final response is the text of the last assistant message.

If you already have the state, pass it directly:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import com.alibaba.cloud.ai.graph.OverAllState;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.springai.alibaba.SpringAiAlibabaSupport;

// The OverAllState from a graph run
OverAllState state = /* ... */;

AgentTrace trace = SpringAiAlibabaSupport.toAgentTrace(state);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import com.alibaba.cloud.ai.graph.OverAllState
import dev.dokimos.core.agents.AgentTrace
import dev.dokimos.springai.alibaba.SpringAiAlibabaSupport

// The OverAllState from a graph run
val state: OverAllState = /* ... */

val trace: AgentTrace = SpringAiAlibabaSupport.toAgentTrace(state)
```

  </TabItem>
</Tabs>

## Step 3: Run the agent and read the state

The compiled graph is the full-fidelity entry point. Call `getCompiledGraph().invoke(...)`, which returns an `Optional<OverAllState>` carrying the whole run. The one-liner `toAgentTrace(agent, inputs, config)` does this for you: it invokes the agent's compiled graph and folds the returned state.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.springai.alibaba.SpringAiAlibabaSupport;
import org.springframework.ai.chat.messages.UserMessage;

// Build a ReactAgent on your Spring AI ChatClient
ReactAgent agent = ReactAgent.builder()
    .name("assistant")
    .chatClient(chatClient)
    .tools(toolCallbacks)
    .build();

// Inputs go in under the "messages" key
Map<String, Object> inputs = Map.of(
    "messages", List.of(new UserMessage("What's the weather in Paris?"))
);

// One-liner: invoke the compiled graph and fold the state
AgentTrace trace = SpringAiAlibabaSupport.toAgentTrace(agent, inputs, RunnableConfig.builder().build());
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import com.alibaba.cloud.ai.graph.RunnableConfig
import com.alibaba.cloud.ai.graph.agent.ReactAgent
import dev.dokimos.core.agents.AgentTrace
import dev.dokimos.springai.alibaba.SpringAiAlibabaSupport
import org.springframework.ai.chat.messages.UserMessage

// Build a ReactAgent on your Spring AI ChatClient
val agent = ReactAgent.builder()
    .name("assistant")
    .chatClient(chatClient)
    .tools(toolCallbacks)
    .build()

// Inputs go in under the "messages" key
val inputs = mapOf(
    "messages" to listOf(UserMessage("What's the weather in Paris?"))
)

// One-liner: invoke the compiled graph and fold the state
val trace: AgentTrace = SpringAiAlibabaSupport.toAgentTrace(agent, inputs, RunnableConfig.builder().build())
```

  </TabItem>
</Tabs>

If you manage the graph yourself, invoke it and fold the `Optional` it returns:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import com.alibaba.cloud.ai.graph.OverAllState;
import dev.dokimos.springai.alibaba.SpringAiAlibabaSupport;

Optional<OverAllState> state = agent.getCompiledGraph().invoke(inputs);

AgentTrace trace = SpringAiAlibabaSupport.toAgentTrace(state);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import com.alibaba.cloud.ai.graph.OverAllState
import dev.dokimos.springai.alibaba.SpringAiAlibabaSupport
import java.util.Optional

val state: Optional<OverAllState> = agent.compiledGraph.invoke(inputs)

val trace = SpringAiAlibabaSupport.toAgentTrace(state)
```

  </TabItem>
</Tabs>

:::note

Use `getCompiledGraph().invoke(...)` rather than a single-shot call. The compiled graph preserves every intermediate tool call across turns; a single-shot call would lose them.

:::

## Per-turn windowing

A graph run can span several turns, and a sub-agent or loop may reuse a tool-call id across them. To keep results correlated, `toToolCalls(state)` windows the messages: each `AssistantMessage` that issues tool calls is matched only against the `ToolResponseMessage`s that follow it, up to the next `AssistantMessage`. A call with no matching response in its window has a `null` result. This is what `toAgentTrace` uses, so multi-turn runs score correctly without any extra wiring.

If you want the raw calls without building a trace, read them directly:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.agents.ToolCall;
import dev.dokimos.springai.alibaba.SpringAiAlibabaSupport;

List<ToolCall> toolCalls = SpringAiAlibabaSupport.toToolCalls(state);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.agents.ToolCall
import dev.dokimos.springai.alibaba.SpringAiAlibabaSupport

val toolCalls: List<ToolCall> = SpringAiAlibabaSupport.toToolCalls(state)
```

  </TabItem>
</Tabs>

## Step 4: Score with the agent evaluators

Convert the tool callbacks the agent was built with into `ToolDefinition`s, build an `EvalTestCase` with `trace.toTestCase(input, tools)`, and run any of the [agent evaluators](../evaluation/agent-evaluation). Use the `builder()` form for every agent evaluator.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolDefinition;
import dev.dokimos.core.evaluators.agents.ToolCorrectnessEvaluator;
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator;
import dev.dokimos.springai.alibaba.SpringAiAlibabaSupport;

// Run the agent and fold its state
AgentTrace trace = SpringAiAlibabaSupport.toAgentTrace(agent, inputs, RunnableConfig.builder().build());

// Convert the tools the agent was given
List<ToolDefinition> tools = SpringAiAlibabaSupport.toToolDefinitions(toolCallbacks);

// Build the test case the agent evaluators expect
EvalTestCase testCase = trace.toTestCase("What's the weather in Paris?", tools);

// Evaluate
EvalResult validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
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
import dev.dokimos.springai.alibaba.SpringAiAlibabaSupport

// Run the agent and fold its state
val trace: AgentTrace = SpringAiAlibabaSupport.toAgentTrace(agent, inputs, RunnableConfig.builder().build())

// Convert the tools the agent was given
val tools: List<ToolDefinition> = SpringAiAlibabaSupport.toToolDefinitions(toolCallbacks)

// Build the test case the agent evaluators expect
val testCase: EvalTestCase = trace.toTestCase("What's the weather in Paris?", tools)

// Evaluate
val validity: EvalResult = ToolCallValidityEvaluator.builder().build().evaluate(testCase)
val correctness: EvalResult = ToolCorrectnessEvaluator.builder().build().evaluate(testCase)
```

  </TabItem>
</Tabs>

:::tip

See [Agent Evaluation](../evaluation/agent-evaluation) for the full set of agent evaluators and the `EvalTestCase` keys they read.

:::

## Judges and async tasks

This module deliberately does not add `asJudge` or `asyncTask`. Spring AI Alibaba agents run on a standard Spring AI `ChatModel` or `ChatClient`, so use `SpringAiSupport.asJudge(...)` and `SpringAiSupport.asyncTask(...)` from the [Spring AI integration](./spring-ai) directly.

## Coopetition note

Spring AI Alibaba ships its own admin console that shows runs after the fact. That is useful for inspecting what happened. Dokimos is the gate that runs before: it scores a run's tool calls against the tools the agent was given and fails the build when the agent picks the wrong tool, hallucinates arguments, or misses the task. Use the admin console to look; use Dokimos in CI to block.
