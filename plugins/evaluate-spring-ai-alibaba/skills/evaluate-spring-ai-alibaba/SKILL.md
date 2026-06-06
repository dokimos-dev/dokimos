---
name: evaluate-spring-ai-alibaba
description: Sets up evaluation of Spring AI Alibaba graph agents using Dokimos. Use this skill when the user wants to evaluate, test, or benchmark a Spring AI Alibaba ReactAgent or graph run, its tool calls, or its execution trace. Also use when the user mentions Spring AI Alibaba evaluation or integrating Dokimos with a spring-ai-alibaba project.
---

# Evaluate Spring AI Alibaba

Set up Dokimos evaluation for a Spring AI Alibaba graph agent. The user will describe their agent and evaluation goals via `$ARGUMENTS`.

## Where things live

- **Support class**: `dokimos-spring-ai-alibaba/src/main/java/dev/dokimos/springai/alibaba/SpringAiAlibabaSupport.java`
- **Maven dependency**: `dev.dokimos:dokimos-spring-ai-alibaba` (pulls in `dokimos-spring-ai`)

Before writing code, read `SpringAiAlibabaSupport.java`. It reuses `SpringAiSupport` for the actual message-to-`ToolCall` extraction, so it stays thin.

## How it works

A Spring AI Alibaba graph run carries its whole conversation as standard Spring AI message types (`AssistantMessage`, `ToolResponseMessage`) under the `OverAllState` key `"messages"`. The support class folds that list into one `AgentTrace`, matching each tool call to its result with per-turn windowing (so a reused tool-call id across turns does not bind to the wrong result).

- **`SpringAiAlibabaSupport.toAgentTrace(OverAllState)`** — fold a finished run's state into an `AgentTrace`.
- **`toAgentTrace(Optional<OverAllState>)`** — same, for `CompiledGraph.invoke(...)` which returns an `Optional`.
- **`toAgentTrace(ReactAgent, Map<String,Object> inputs, RunnableConfig)`** — the one-liner: run the agent's compiled graph with full fidelity and fold.
- **`toToolDefinitions(List<ToolCallback>)`** — map the tool callbacks the agent was built with to `ToolDefinition`s.

Use `getCompiledGraph().invoke(...)`, not `call(...)`: the latter is lossy and drops intermediate tool calls.

There is no `asJudge` or `asyncTask` here. Spring AI Alibaba runs on a standard Spring AI `ChatModel`, so use `SpringAiSupport.asJudge(...)` and `SpringAiSupport.asyncTask(...)` for those.

## Evaluation pattern

```java
OverAllState state = agent.getCompiledGraph().invoke(inputs, config).orElseThrow();
AgentTrace trace = SpringAiAlibabaSupport.toAgentTrace(state);
// or the one-liner:
AgentTrace trace = SpringAiAlibabaSupport.toAgentTrace(agent, inputs, config);

List<ToolDefinition> tools = SpringAiAlibabaSupport.toToolDefinitions(toolCallbacks);
EvalTestCase testCase = trace.toTestCase(userInput, tools);

var validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
var correctness = ToolCorrectnessEvaluator.builder().build().evaluate(testCase);
```

Always construct evaluators with `XEvaluator.builder()...build()`; they have private constructors.

## Reading tool results and arguments back typed

A captured `ToolCall` keeps its arguments as a `Map` and its result as the string the tool returned. Read them typed with `call.argumentsAs(MyArgs.class)` and, when the result is JSON, `call.resultAs(MyResult.class)` (or `OutputType` for generics).

## Dependencies

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-spring-ai-alibaba</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

Spring AI Alibaba itself is a provided-scope dependency: the user brings their own version (`com.alibaba.cloud.ai:spring-ai-alibaba-graph-core`).

## Steps

1. Understand from `$ARGUMENTS` what the graph agent does and which tools it calls
2. Run the agent through `getCompiledGraph().invoke(...)` and fold the state into an `AgentTrace`
3. Convert to an `EvalTestCase` with `trace.toTestCase(input, tools)`
4. Score with the agent evaluators (prefer deterministic ones for CI)
5. For the full agent evaluator set, use the `evaluate-agent` skill
