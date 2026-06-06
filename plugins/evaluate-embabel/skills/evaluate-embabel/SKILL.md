---
name: evaluate-embabel
description: Sets up evaluation of Embabel agents using Dokimos. Use this skill when the user wants to evaluate, test, or benchmark an Embabel agent, its tool calls, or its execution trace. Also use when the user mentions Embabel evaluation or integrating Dokimos with an Embabel project.
---

# Evaluate Embabel

Set up Dokimos evaluation for an Embabel agent. The user will describe their agent and evaluation goals via `$ARGUMENTS`.

Requires Java 21 or later: Embabel's published artifacts are built for Java 21. The rest of Dokimos stays on Java 17.

## Where things live

- **Embabel support**: `dokimos-embabel/src/main/java/dev/dokimos/embabel/EmbabelSupport.java`
- **Trace collector**: `dokimos-embabel/src/main/java/dev/dokimos/embabel/EmbabelTraceCollector.java`
- **Maven dependency**: `dev.dokimos:dokimos-embabel`

Before writing code, read `EmbabelTraceCollector.java` to understand how events map to a trace.

## How it works

Embabel reports tool calls through per-event `AgenticEventListener` callbacks during a run, not as a return value. `EmbabelTraceCollector` implements that listener and assembles an `AgentTrace` from the `ToolCallResponseEvent`s it observes.

- **`EmbabelSupport.attach(ProcessOptions, collector)`** — registers the collector on `ProcessOptions`, returning new options to run with.
- **`EmbabelSupport.attach(AgentInvocation.Builder)`** — registers a fresh collector on an invocation builder and returns it.
- **`collector.trace()`** — materializes the `AgentTrace` after the run.
- **`EmbabelSupport.toToolDefinitions(collector)`** — synthesizes `ToolDefinition`s from the observed tool names. These carry an empty input schema, so `ToolDescriptionReliabilityEvaluator` coverage is weakened; build the definitions by hand if you need full schema coverage.

The collector is single-run and not thread-safe. Reuse one instance only after calling `reset()`.

## Evaluation pattern

```java
EmbabelTraceCollector collector = new EmbabelTraceCollector();
ProcessOptions options = EmbabelSupport.attach(ProcessOptions.builder().build(), collector);

AgentInvocation<String> invocation =
        AgentInvocation.builder(platform).options(options).build();
invocation.invoke(input);

AgentTrace trace = collector.trace();
List<ToolDefinition> tools = EmbabelSupport.toToolDefinitions(collector);
EvalTestCase testCase = trace.toTestCase(input, tools);

var validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
var correctness = ToolCorrectnessEvaluator.builder().build().evaluate(testCase);
```

Always construct evaluators with `XEvaluator.builder()...build()`; they have private constructors.

## Reading tool results and arguments back typed

A captured `ToolCall` keeps its arguments as a `Map` and its result as the string Embabel returned. Read them typed with `call.argumentsAs(MyArgs.class)` and, when the result is JSON, `call.resultAs(MyResult.class)` (or `OutputType` for generics).

## Dependencies

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-embabel</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

Embabel itself is a provided-scope dependency: the user brings their own version (`com.embabel.agent:embabel-agent-api`).

## Steps

1. Understand from `$ARGUMENTS` what the Embabel agent does and which tools it calls
2. Confirm the project builds on Java 21
3. Attach an `EmbabelTraceCollector` to the run and capture `collector.trace()`
4. Convert to an `EvalTestCase` with `trace.toTestCase(input, tools)`
5. Score with the agent evaluators (prefer deterministic ones for CI)
6. For the full agent evaluator set, use the `evaluate-agent` skill
