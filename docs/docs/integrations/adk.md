---
sidebar_position: 8
---

# Google ADK Integration

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

This page shows you how to evaluate agents built with the [Google Agent Development Kit (ADK) for Java](https://github.com/google/adk-java) (`com.google.adk:google-adk`) and [Google ADK for Kotlin](https://github.com/google/adk-kotlin) (`com.google.adk:google-adk-kotlin-core-jvm`) using Dokimos.

Dokimos ships two dedicated modules:
- **`dokimos-adk-java`** (`dev.dokimos.adk`): for `com.google.adk:google-adk` (RxJava3 `Flowable<Event>` streams, `BaseLlm`, `BasePlugin`).
- **`dokimos-adk-kotlin`** (`dev.dokimos.adk.kt`): for `com.google.adk:google-adk-kotlin-core-jvm` (Kotlin Coroutines `Flow<Event>`, `Model`, `Plugin`, suspend functions, and idiomatic Kotlin extension functions).

## What you get

- **One-line judge**: turn an ADK model (`Gemini` / `BaseLlm` / `Model`), `Runner`, `BaseAgent` (`LlmAgent`), or Kotlin `suspend (String) -> String` lambda into a Dokimos `JudgeLM` with `AdkSupport.asJudge(...)` (or `.asJudge()` in Kotlin).
- **Sync, async, and measured tasks**: wrap any ADK model, `Runner`, or `BaseAgent` into a Dokimos `Task`, `AsyncTask`, or `MeasuredTask` (with token usage, latency, and `PriceTable` cost).
- **Agent trace & tool-call capture**: fold ADK `Event` streams or `Session` histories into an `AgentTrace` with `AdkSupport.toAgentTrace(...)` (or `.toAgentTrace(...)` / `suspend fun BaseAgent.runTrace(...)` in Kotlin), or attach the passive `AdkTraceCollector` plugin to your `Runner` / `InMemoryRunner` / `App` to capture `ToolCall`s and full `ToolDefinition` schemas automatically.
- **RAG context extraction**: `AdkSupport.ragTask(...)` and `AdkSupport.extractTexts(...)` pull retrieved context from Gemini `GroundingMetadata` chunks (`retrievedContext`, `ragChunk`, `web`) and retrieval tool `FunctionResponse`s (`VertexAiRagRetrieval` or custom search tools).
- **Multi-turn conversations & simulation**: convert ADK `Session`s into `ConversationTrajectory` instances and adapt any `Runner` or `BaseAgent` as a `ConversationalApplication` for `ConversationSimulator`.

## Setup

Add `dokimos-adk-java` (for ADK Java) or `dokimos-adk-kotlin` (for ADK Kotlin) to your project. The ADK artifacts are `provided` dependencies so you bring the ADK version your application already uses.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java (ADK for Java)">

### Maven

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-adk-java</artifactId>
    <version>${dokimos.version}</version>
</dependency>
<dependency>
    <groupId>com.google.adk</groupId>
    <artifactId>google-adk</artifactId>
    <version>1.12.0</version>
</dependency>
```

### Gradle

```kotlin
implementation("dev.dokimos:dokimos-adk-java:${dokimosVersion}")
implementation("com.google.adk:google-adk:1.12.0")
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin (ADK for Kotlin)">

### Maven

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-adk-kotlin</artifactId>
    <version>${dokimos.version}</version>
</dependency>
<dependency>
    <groupId>com.google.adk</groupId>
    <artifactId>google-adk-kotlin-core-jvm</artifactId>
    <version>1.4.0</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
implementation("dev.dokimos:dokimos-adk-kotlin:${dokimosVersion}")
implementation("com.google.adk:google-adk-kotlin-core-jvm:1.4.0")
```

  </TabItem>
</Tabs>

## Use an ADK model or agent as a judge

Pass a `BaseLlm` / `Model` (e.g., `Gemini`), `Runner`, or `BaseAgent` to `AdkSupport.asJudge(...)` (or use `.asJudge()` in Kotlin):

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import com.google.adk.models.Gemini;
import dev.dokimos.adk.AdkSupport;
import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.Evaluator;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.evaluators.LLMJudgeEvaluator;
import java.util.List;

Gemini gemini = Gemini.builder()
    .modelName("gemini-3.8-flash")
    .build();

JudgeLM judge = AdkSupport.asJudge(gemini);

Evaluator correctness = LLMJudgeEvaluator.builder()
    .name("Answer Correctness")
    .criteria("Is the answer factually accurate and helpful?")
    .evaluationParams(List.of(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
    .judge(judge)
    .threshold(0.8)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import com.google.adk.kt.models.Gemini
import dev.dokimos.adk.kt.asJudge
import dev.dokimos.kotlin.dsl.llmJudge

val gemini = Gemini(name = "gemini-3.8-flash")
val judge = gemini.asJudge()

val correctness = llmJudge(judge) {
    name = "Answer Correctness"
    criteria = "Is the answer factually accurate and helpful?"
    threshold = 0.8
}
```

  </TabItem>
</Tabs>

## Evaluate Q&A and agent tasks

Use `simpleTask` when you only need the final text response under `"output"`, or `agentTask` when your evaluators inspect `"toolCalls"` and `"reasoningSteps"` alongside `"output"`. Each example executes in an isolated session.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import com.google.adk.agents.LlmAgent;
import dev.dokimos.adk.AdkSupport;
import dev.dokimos.core.*;

LlmAgent agent = LlmAgent.builder()
    .name("support_agent")
    .model("gemini-3.8-flash")
    .instruction("Answer customer questions concisely.")
    .build();

// Simple text output under "output"
Task qaTask = AdkSupport.simpleTask(agent);

// Full AgentTrace output map ("output", "toolCalls", "reasoningSteps")
Task traceTask = AdkSupport.agentTask(agent);

// Reactive async variants backed by Runner.runAsync
AsyncTask asyncQaTask = AdkSupport.asyncTask(agent);
AsyncTask asyncTraceTask = AdkSupport.asyncAgentTask(agent);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.Gemini
import dev.dokimos.adk.kt.AdkSupport

val agent = LlmAgent(
    name = "support_agent",
    model = Gemini(name = "gemini-3.8-flash"),
    instruction = Instruction("Answer customer questions concisely."),
)

// Simple text output under "output"
val qaTask = AdkSupport.simpleTask(agent)

// Full AgentTrace output map ("output", "toolCalls", "reasoningSteps")
val traceTask = AdkSupport.agentTask(agent)

// Coroutine-backed async variants bridging Flow<Event> to CompletableFuture
val asyncQaTask = AdkSupport.asyncTask(agent)
val asyncTraceTask = AdkSupport.asyncAgentTask(agent)
```

  </TabItem>
</Tabs>

## Evaluate tool-calling agents

You can capture an `AgentTrace` from an ADK agent in two ways:

1. **Direct extraction (`AdkSupport.toAgentTrace` / `.toAgentTrace(...)` / `suspend fun runTrace(...)`)**: run a `Runner` or `BaseAgent` (or pass a `Session` or `List<Event>`) and convert its events into an `AgentTrace`.
2. **Passive plugin (`AdkTraceCollector`)**: register `AdkTraceCollector` on your `Runner` / `InMemoryRunner` / `App`, run your agent as usual, and read `collector.trace()` and `collector.toolDefinitions()` afterward.

### Option 1: Direct trace extraction

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.FunctionTool;
import dev.dokimos.adk.AdkSupport;
import dev.dokimos.core.*;
import dev.dokimos.core.agents.*;
import dev.dokimos.core.evaluators.agents.*;
import java.util.List;

FunctionTool weatherTool = FunctionTool.create(WeatherTools.class, "getWeather");
LlmAgent agent = LlmAgent.builder()
    .name("weather_agent")
    .model("gemini-3.8-flash")
    .instruction("Use get_weather to answer weather questions.")
    .tools(weatherTool)
    .build();

// Run the agent in an isolated session and extract the trace
AgentTrace trace = AdkSupport.toAgentTrace(agent, "What is the weather in Paris?");

// Extract ToolDefinitions (with full JSON Schema) from the agent's tools
List<ToolDefinition> tools = AdkSupport.toToolDefinitions(agent);

EvalTestCase testCase = trace.toTestCase("What is the weather in Paris?", tools);

EvalResult validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
EvalResult efficiency = ToolEfficiencyEvaluator.builder().build().evaluate(testCase);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.Gemini
import dev.dokimos.adk.kt.toAgentTrace
import dev.dokimos.adk.kt.toToolDefinitions
import dev.dokimos.core.evaluators.agents.ToolCallValidityEvaluator
import dev.dokimos.core.evaluators.agents.ToolEfficiencyEvaluator

val agent = LlmAgent(
    name = "weather_agent",
    model = Gemini(name = "gemini-3.8-flash"),
    instruction = Instruction("Use get_weather to answer weather questions."),
    tools = listOf(weatherTool),
)

// Blocking helper (or use suspend `agent.runTrace(...)` inside a coroutine)
val trace = agent.toAgentTrace("What is the weather in Paris?")
val tools = agent.toToolDefinitions()
val testCase = trace.toTestCase("What is the weather in Paris?", tools)

val validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase)
val efficiency = ToolEfficiencyEvaluator.builder().build().evaluate(testCase)
```

  </TabItem>
</Tabs>

### Option 2: Passive trace collection with `AdkTraceCollector`

`AdkTraceCollector` implements ADK's plugin interface (`BasePlugin` in Java, `com.google.adk.kt.plugins.Plugin` in Kotlin). Because ADK passes the live `BaseTool` instance to tool callbacks, `collector.toolDefinitions()` preserves each observed tool's full description and parameter JSON schema automatically.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import com.google.adk.agents.RunConfig;
import com.google.adk.runner.Runner;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import dev.dokimos.adk.AdkSupport;
import dev.dokimos.adk.AdkTraceCollector;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.core.agents.ToolDefinition;
import java.util.List;

// 1. Attach AdkTraceCollector to your Runner.Builder
Runner.Builder builder = Runner.builder().agent(agent).appName("weather-app");
AdkTraceCollector collector = AdkSupport.attach(builder);
Runner runner = builder.build();

// 2. Run the agent as usual
String userQuery = "What is the weather in Paris?";
runner.runAsync(
        "user-1",
        "session-1",
        Content.fromParts(Part.fromText(userQuery)),
        RunConfig.builder().autoCreateSession(true).build())
    .toList()
    .blockingGet();

// 3. Read the trace and full tool definitions from the collector
AgentTrace trace = collector.trace();
List<ToolDefinition> tools = AdkSupport.toToolDefinitions(collector);
EvalTestCase testCase = trace.toTestCase(userQuery, tools);

// Call reset() before reusing the collector for another independent run
collector.reset();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Role
import dev.dokimos.adk.kt.AdkTraceCollector
import kotlinx.coroutines.flow.collect

// 1. Register AdkTraceCollector on InMemoryRunner (or via AdkSupport.attach(builder))
val collector = AdkTraceCollector()
val runner = InMemoryRunner(
    agent = agent,
    appName = "weather-app",
    plugins = listOf(collector),
)

// 2. Run the agent as usual in a coroutine
val userQuery = "What is the weather in Paris?"
runner.runAsync(
    userId = "user-1",
    sessionId = "session-1",
    newMessage = Content.fromText(Role.USER, userQuery),
).collect()

// 3. Read the trace and full tool definitions from the collector
val trace = collector.trace()
val tools = collector.toolDefinitions()
val testCase = trace.toTestCase(userQuery, tools)

// Call reset() before reusing the collector for another independent run
collector.reset()
```

  </TabItem>
</Tabs>

## Evaluate RAG pipelines

ADK agents retrieve grounding context in two ways:
1. **Built-in Gemini Grounding / Vertex AI RAG**, which populates `Event.groundingMetadata` (`GroundingChunk` -> `retrievedContext.text`, `ragChunk.text`, or `web`).
2. **Retrieval tools** such as `VertexAiRagRetrieval` or custom search `FunctionTool`s, which return chunks in `FunctionResponse` maps (under keys like `"response"`, `"context"`, `"documents"`, `"chunks"`, or `"results"`).

`AdkSupport.ragTask(...)` (and `asyncRagTask` / `measuredRagTask`) extracts context from both sources and writes it under `"context"` alongside `"output"`, ready for `FaithfulnessEvaluator` and `ContextualRelevancyEvaluator`.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.adk.AdkSupport;
import dev.dokimos.core.*;
import dev.dokimos.core.evaluators.ContextualRelevancyEvaluator;
import dev.dokimos.core.evaluators.FaithfulnessEvaluator;
import java.util.List;

// Extracts context from GroundingMetadata and/or retrieval tool responses
Task ragTask = AdkSupport.ragTask(ragAgent);

// Or restrict FunctionResponse context extraction to specific retrieval tool names
Task filteredRagTask = AdkSupport.ragTask(ragAgent, List.of("search_policy"));

JudgeLM judge = AdkSupport.asJudge(gemini);

ExperimentResult result = Experiment.builder()
    .name("ADK RAG Evaluation")
    .dataset(dataset)
    .task(ragTask)
    .evaluators(List.of(
        FaithfulnessEvaluator.builder().judge(judge).threshold(0.8).build(),
        ContextualRelevancyEvaluator.builder().judge(judge).threshold(0.7).build()
    ))
    .build()
    .run();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.adk.kt.AdkSupport
import dev.dokimos.adk.kt.asJudge
import dev.dokimos.kotlin.dsl.experiment
import dev.dokimos.kotlin.dsl.faithfulness

val ragTask = AdkSupport.ragTask(ragAgent)
val judge = gemini.asJudge()

val result = experiment {
    name = "ADK Kotlin RAG Evaluation"
    dataset(dataset)
    task(ragTask)
    evaluators {
        faithfulness(judge) { threshold = 0.8 }
    }
}.run()
```

  </TabItem>
</Tabs>

## Multi-turn conversations and simulation

Convert an ADK `Session` (or `List<Event>`) into a `ConversationTrajectory` with `AdkSupport.toConversationTrajectory(...)` (or `session.toConversationTrajectory(...)` in Kotlin), or adapt a `Runner` / `BaseAgent` as a `ConversationalApplication` to run multi-turn persona simulations with `ConversationSimulator`:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.adk.AdkSupport;
import dev.dokimos.core.conversation.*;

// Adapt an ADK agent for multi-turn simulation
ConversationalApplication app = AdkSupport.asConversationalApp(agent);

SimulatedUser user = LLMSimulatedUser.builder()
    .judge(judge)
    .persona("An impatient traveler asking about flight delays")
    .scenario("Resolve a delayed flight rebooking")
    .build();

ConversationTrajectory trajectory = ConversationSimulator.builder()
    .application(app)
    .simulatedUser(user)
    .maxTurns(4)
    .build()
    .simulate();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.adk.kt.asConversationalApp
import dev.dokimos.core.conversation.ConversationSimulator
import dev.dokimos.core.conversation.LLMSimulatedUser

val app = agent.asConversationalApp()

val user = LLMSimulatedUser.builder()
    .judge(judge)
    .persona("An impatient traveler asking about flight delays")
    .scenario("Resolve a delayed flight rebooking")
    .build()

val trajectory = ConversationSimulator.builder()
    .application(app)
    .simulatedUser(user)
    .maxTurns(4)
    .build()
    .simulate()
```

  </TabItem>
</Tabs>

## Cost, tokens, and latency

ADK attaches `UsageMetadata` (`promptTokenCount` and `candidatesTokenCount`) to each model `Event`. `AdkSupport.measuredTask`, `measuredAgentTask`, `measuredRagTask`, and `AdkTraceCollector.callMetrics` sum token counts across all non-partial events in a run, measure wall-clock latency, and compute USD cost via a `PriceTable`.

In `dokimos-adk-kotlin`, `LlmAgent.model.name` is exposed publicly so `modelId` is automatically inferred when omitted!

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.adk.AdkSupport;
import dev.dokimos.core.MeasuredTask;
import dev.dokimos.core.PriceTable;

PriceTable prices = (model, tokensIn, tokensOut) ->
    (tokensIn * 0.00015 / 1000.0) + (tokensOut * 0.00060 / 1000.0);

MeasuredTask task = AdkSupport.measuredAgentTask(agent, "gemini-3.8-flash", prices);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.adk.kt.AdkSupport
import dev.dokimos.core.PriceTable

val prices = PriceTable { _, tokensIn, tokensOut ->
    (tokensIn * 0.00015 / 1000.0) + (tokensOut * 0.00060 / 1000.0)
}

// modelId is inferred automatically from agent.model.name ("gemini-3.8-flash")
val task = AdkSupport.measuredAgentTask(agent, priceTable = prices)
```

  </TabItem>
</Tabs>

See [Cost and Pricing](../evaluation/cost-and-pricing) for details on `PriceTable` and `CallMetrics`.

## Complete examples

See [`AdkAgentEvaluationExample.java`](https://github.com/dokimos-dev/dokimos/blob/master/dokimos-examples/src/main/java/dev/dokimos/examples/adk/AdkAgentEvaluationExample.java) and [`AdkKotlinAgentEvaluationExample.kt`](https://github.com/dokimos-dev/dokimos/blob/master/dokimos-examples/src/main/kotlin/dev/dokimos/examples/adk/AdkKotlinAgentEvaluationExample.kt) in `dokimos-examples` for self-contained runnable examples demonstrating `AdkTraceCollector`, tool-call evaluation, and `measuredAgentTask` / `measuredAsyncAgentTask`.
