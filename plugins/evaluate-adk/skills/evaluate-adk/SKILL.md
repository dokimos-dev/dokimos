---
name: evaluate-adk
description: Sets up evaluation of Google Agent Development Kit (ADK) agents in Java (dokimos-adk-java) or Kotlin (dokimos-adk-kotlin) using Dokimos. Use this skill when the user wants to evaluate, test, or benchmark a Google ADK LlmAgent, Runner, InMemoryRunner, BaseAgent, BaseLlm, Model, its tool calls, RAG grounding, or multi-turn conversations.
---

# Evaluate Google ADK Agents (Java & Kotlin)

Set up Dokimos evaluation for a Google ADK for Java (`com.google.adk:google-adk`) or Google ADK for Kotlin (`com.google.adk:google-adk-kotlin-core-jvm`) agent. The user will describe their agent and evaluation goals via `$ARGUMENTS`.

## Where things live

- **ADK for Java (`dokimos-adk-java`, package `dev.dokimos.adk`)**:
  - `dokimos-adk-java/src/main/java/dev/dokimos/adk/AdkSupport.java`
  - `dokimos-adk-java/src/main/java/dev/dokimos/adk/AdkTraceCollector.java`
- **ADK for Kotlin (`dokimos-adk-kotlin`, package `dev.dokimos.adk.kt`)**:
  - `dokimos-adk-kotlin/src/main/kotlin/dev/dokimos/adk/kt/AdkSupport.kt`
  - `dokimos-adk-kotlin/src/main/kotlin/dev/dokimos/adk/kt/AdkTraceCollector.kt`

Before writing code, read the relevant `AdkSupport` and `AdkTraceCollector` files to understand the available factory methods, extension functions, and conversions.

## Key capabilities in `AdkSupport` and `AdkTraceCollector`

- **Judges**:
  - Java: `AdkSupport.asJudge(BaseLlm)`, `AdkSupport.asJudge(Runner)`, `AdkSupport.asJudge(BaseAgent)`
  - Kotlin: `model.asJudge()`, `runner.asJudge()`, `agent.asJudge()`, or `AdkSupport.asJudge { prompt -> ... }` (suspend lambda)
- **Q&A and Agent Tasks**:
  - Sync: `AdkSupport.simpleTask(...)`, `AdkSupport.agentTask(...)`
  - Async (`Flowable<Event>` or `Flow<Event>` -> `CompletableFuture`): `AdkSupport.asyncTask(...)`, `AdkSupport.asyncAgentTask(...)`
  - Measured (`CallMetrics` with tokens, latency, and `PriceTable` cost): `AdkSupport.measuredTask(...)`, `AdkSupport.measuredAgentTask(...)`, `AdkSupport.measuredAsyncTask(...)`, `AdkSupport.measuredAsyncAgentTask(...)` (in Kotlin, `modelId` is automatically inferred from `LlmAgent.model.name` when omitted)
- **Tool-Call & AgentTrace Extraction**:
  - Direct: `AdkSupport.toAgentTrace(agent, input)`, `AdkSupport.toAgentTrace(runner, input)`, `AdkSupport.toAgentTrace(session)`, `AdkSupport.toAgentTrace(events)` (plus `.toAgentTrace(...)` and `suspend fun BaseAgent.runTrace(input)` in Kotlin)
  - Tool definitions (with full `Schema` -> JSON Schema conversion): `AdkSupport.toToolDefinitions(llmAgent)` / `agent.toToolDefinitions()`, `AdkSupport.toToolDefinitions(tools)`, `AdkSupport.toToolDefinitions(collector)` / `collector.toolDefinitions()`
  - Passive plugin: `AdkTraceCollector` (`BasePlugin` in Java, `com.google.adk.kt.plugins.Plugin` in Kotlin). Attach via `AdkSupport.attach(builder)` or `InMemoryRunner(agent = agent, plugins = listOf(collector))` to capture `collector.trace()`, `collector.toolDefinitions()`, `collector.trajectory()`, `collector.retrievedContexts()`, and `collector.callMetrics(...)`. Call `collector.reset()` between runs when reusing an instance.
- **RAG Evaluation**:
  - `AdkSupport.ragTask(...)`, `AdkSupport.asyncRagTask(...)`, `AdkSupport.measuredRagTask(...)`, `AdkSupport.measuredAsyncRagTask(...)`
  - `AdkSupport.extractTexts(events, retrievalToolNames...)` extracts `"context"` (`List<String>`) from `Event.groundingMetadata` chunks (`retrievedContext`, `ragChunk`, `web`) and/or retrieval tool `FunctionResponse`s (`VertexAiRagRetrieval` or custom search tools).
- **Multi-Turn Conversations**:
  - `AdkSupport.toConversationTrajectory(session, scenario)` / `session.toConversationTrajectory(scenario)` and `AdkSupport.asConversationalApp(agent)` / `agent.asConversationalApp()` for `ConversationSimulator`.

## Evaluation patterns

### Java (`dokimos-adk-java`)

```java
Runner.Builder builder = Runner.builder().agent(agent).appName("my-app");
AdkTraceCollector collector = AdkSupport.attach(builder);
Runner runner = builder.build();

AgentTrace trace = AdkSupport.toAgentTrace(runner, input);
List<ToolDefinition> tools = AdkSupport.toToolDefinitions(collector);
EvalTestCase testCase = trace.toTestCase(input, tools);

EvalResult validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
EvalResult correctness = ToolCorrectnessEvaluator.builder().build().evaluate(testCase);
```

### Kotlin (`dokimos-adk-kotlin`)

```kotlin
val collector = AdkTraceCollector()
val runner = InMemoryRunner(agent = agent, appName = "my-app", plugins = listOf(collector))

val trace = runner.toAgentTrace(input)
val tools = collector.toolDefinitions()
val testCase = trace.toTestCase(input, tools)

val validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase)
val correctness = ToolCorrectnessEvaluator.builder().build().evaluate(testCase)
```

Always construct evaluators with `XEvaluator.builder()...build()` (or use the `dokimos-kotlin` DSL inside `evaluators { ... }`).

## Dependencies

For ADK for Java:
```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-adk-java</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

For ADK for Kotlin:
```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-adk-kotlin</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

`com.google.adk:google-adk` and `com.google.adk:google-adk-kotlin-core-jvm` are `provided`-scope dependencies: the user brings their own version.

## Steps

1. Understand from `$ARGUMENTS` whether the project uses ADK for Java (`com.google.adk`) or ADK for Kotlin (`com.google.adk.kt`), which tools or RAG retrieval it uses, and what quality criteria matter.
2. Choose between single-run trace evaluation (`toAgentTrace` / `AdkTraceCollector`) or dataset-driven `Experiment` (`agentTask` / `measuredAgentTask` / `ragTask`).
3. Convert the agent's tools with `AdkSupport.toToolDefinitions(agent)` or `collector.toolDefinitions()`.
4. Score with Dokimos evaluators (`ToolCallValidityEvaluator`, `ToolCorrectnessEvaluator`, `ToolTrajectoryEvaluator`, `TaskCompletionEvaluator`, `FaithfulnessEvaluator`, etc.).
5. For the full agent evaluator set, consult the `evaluate-agent` skill.
