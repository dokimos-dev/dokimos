---
sidebar_position: 7
---

# Agent Evaluation

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

AI agents autonomously use tools, reason through multi-step problems, and interact with external APIs. Evaluating them requires more than checking a single response — you need to assess **what tools they used**, **how they used them**, and **whether they accomplished the task**.

Dokimos provides a framework-agnostic agent evaluation system with six evaluators and a portable data model for tool calls and tool definitions.

## Quick Start

The simplest way to evaluate an agent: capture its tool calls, define what tools it has, and run evaluators.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// 1. Define the tools your agent can use
List<ToolDefinition> tools = List.of(
    ToolDefinition.of("search_flights", "Search for available flights", flightSchema),
    ToolDefinition.of("book_hotel", "Book a hotel room", hotelSchema)
);

// 2. Set up a judge LLM (needed for task completion and hallucination checks)
JudgeLM judge = prompt -> openAiClient.generate(prompt);

// 3. Run your agent and capture its trace
AgentTrace trace = AgentTrace.builder()
    .addToolCall(ToolCall.of("search_flights", Map.of("origin", "JFK", "destination", "CDG")))
    .addToolCall(ToolCall.of("book_hotel", Map.of("city", "Paris", "nights", 5)))
    .finalResponse("Found flights and booked your hotel in Paris.")
    .build();

// 4. Build a test case and evaluate
var testCase = EvalTestCase.builder()
    .input("Find flights from NYC to Paris and book a hotel for 5 nights")
    .actualOutput("toolCalls", trace.toolCalls())
    .actualOutput("output", trace.finalResponse())
    .expectedOutput("toolCalls", List.of(
        ToolCall.of("search_flights", Map.of()),
        ToolCall.of("book_hotel", Map.of())
    ))
    .metadata("tools", tools)
    .metadata("tasks", List.of("Search for flights", "Book a hotel"))
    .build();

// 5. Pick the evaluators you need
var results = List.of(
    ToolCallValidityEvaluator.builder().build().evaluate(testCase),
    ToolCorrectnessEvaluator.builder().build().evaluate(testCase),
    TaskCompletionEvaluator.builder().judge(judge).build().evaluate(testCase),
    ToolArgumentHallucinationEvaluator.builder().judge(judge).build().evaluate(testCase)
);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val judge = JudgeLM { prompt -> openAiClient.generate(prompt) }

val result = experiment {
    name = "Travel Agent Evaluation"
    dataset(dataset)
    task { example ->
        val trace = travelAgent.run(example.input())
        trace.toOutputMap()
    }
    evaluators {
        toolCallValidity { }
        toolCorrectness { }
        taskCompletion(judge) { }
        toolArgumentHallucination(judge) { }
    }
}.run()
```

  </TabItem>
</Tabs>

## Evaluators

| Evaluator | What it checks | LLM required? | Default threshold |
|-----------|---------------|:---:|:---:|
| `ToolCallValidityEvaluator` | Tool calls match their JSON schema (names, required params, types, enums) | No | 1.0 |
| `ToolCorrectnessEvaluator` | Agent used the expected set of tools | No | 1.0 |
| `TaskCompletionEvaluator` | Agent completed the user's requested tasks | Yes | 0.5 |
| `ToolArgumentHallucinationEvaluator` | Tool call arguments are grounded in user input | Yes | 0.8 |
| `ToolNameReliabilityEvaluator` | Tool names follow naming conventions (format, length, verb-prefix) | Optional | 0.8 |
| `ToolDescriptionReliabilityEvaluator` | Tool descriptions are well-crafted (non-empty, documented params, clarity) | Optional | 0.8 |

### ToolCallValidityEvaluator

Validates each tool call against its JSON schema. Checks: tool name exists, required params present, types match, enum values valid, no unexpected params (in strict mode or when `additionalProperties: false`).

Score = fraction of valid tool calls.

### ToolCorrectnessEvaluator

Compares actual vs expected tool usage. Three match modes:

| Mode | Comparison |
|------|-----------|
| `NAMES_ONLY` (default) | Set of tool names (F1 score) |
| `NAMES_AND_ORDER` | Names + invocation order (LCS similarity) |
| `NAMES_AND_ARGS` | Full structural comparison including arguments |

### TaskCompletionEvaluator

Sends the user-agent dialog and a task list to a judge LLM to evaluate which tasks were completed. Score = fraction of completed tasks.

Provide tasks via `metadata("tasks", List.of("Search flights", "Book hotel"))` and optional constraints via `metadata("constraints", "Budget under $500")`.

### ToolArgumentHallucinationEvaluator

Uses a judge LLM to check whether each tool call's argument values can be derived from the user's input. Score = fraction of non-hallucinated tool calls.

### ToolNameReliabilityEvaluator

Rule-based checks: `snake_case`/`camelCase` format, 2-64 char length, verb-prefixed. Optional LLM checks: no ambiguity, descriptive naming.

### ToolDescriptionReliabilityEvaluator

Rule-based checks: non-empty, 10-500 chars, required params documented, max optional args (default 3). Optional LLM check: clarity.

## Data Model

Three records in `dev.dokimos.core.agents` represent agent execution data.

### ToolCall

A single tool invocation: name, arguments, optional result, and metadata.

```java
// Quick
ToolCall call = ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "LAX"));

// Full builder
ToolCall call = ToolCall.builder()
    .name("book_hotel")
    .argument("city", "Paris")
    .argument("nights", 3)
    .result("{\"confirmation\": \"ABC123\"}")
    .build();
```

### ToolDefinition

A tool's contract: name, description, and JSON schema for arguments.

```java
ToolDefinition tool = ToolDefinition.of("search_flights", "Search for available flights", Map.of(
    "type", "object",
    "properties", Map.of(
        "origin", Map.of("type", "string", "description", "Origin airport code"),
        "destination", Map.of("type", "string", "description", "Destination airport code")
    ),
    "required", List.of("origin", "destination")
));
```

### AgentTrace

Wraps a complete agent execution. Use `toOutputMap()` to produce the map format that evaluators expect (`"output"`, `"toolCalls"`, `"reasoningSteps"`).

```java
Task agentTask = example -> {
    AgentTrace trace = runAgent(example.input());
    return trace.toOutputMap();
};
```

## EvalTestCase Keys

Agent evaluators use these keys in `EvalTestCase`:

| Map | Key | Type | Used by |
|-----|-----|------|---------|
| `actualOutputs` | `"toolCalls"` | `List<ToolCall>` | Validity, Correctness, Hallucination |
| `actualOutputs` | `"output"` | `String` | Task Completion |
| `expectedOutputs` | `"toolCalls"` | `List<ToolCall>` | Correctness |
| `metadata` | `"tools"` | `List<ToolDefinition>` | Validity, Name Reliability, Description Reliability |
| `metadata` | `"tasks"` | `List<String>` | Task Completion |
| `metadata` | `"constraints"` | `String` | Task Completion |

## Evaluator Configuration

All evaluators use the builder pattern. Common options:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// Rule-based — just set threshold
ToolCallValidityEvaluator.builder()
    .strictMode(true)       // Fail on any unexpected param
    .threshold(1.0)
    .build();

ToolCorrectnessEvaluator.builder()
    .matchMode(ToolCorrectnessEvaluator.MatchMode.NAMES_AND_ORDER)
    .build();

// LLM-based — provide a judge
TaskCompletionEvaluator.builder()
    .judge(judgeLM)
    .threshold(0.5)
    .build();

// Tool reliability — optional judge for semantic checks
ToolNameReliabilityEvaluator.builder()
    .judge(judgeLM)  // optional
    .threshold(0.8)
    .build();

ToolDescriptionReliabilityEvaluator.builder()
    .maxOptionalArgs(3)
    .judge(judgeLM)  // optional
    .threshold(0.8)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
evaluators {
    toolCallValidity { strictMode = true }
    toolCorrectness { matchMode = ToolCorrectnessEvaluator.MatchMode.NAMES_AND_ORDER }
    taskCompletion(judge) { threshold = 0.5 }
    toolArgumentHallucination(judge) { threshold = 0.8 }
    toolNameReliability { judge = judgeLM }
    toolDescriptionReliability { maxOptionalArgs = 3; judge = judgeLM }
}
```

  </TabItem>
</Tabs>

## Running as an Experiment

To evaluate an agent across a dataset, put tool definitions and task lists in each **Example's metadata** — this is where evaluators look for them at runtime.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
JudgeLM judge = prompt -> openAiClient.generate(prompt);

List<ToolDefinition> tools = List.of(
    ToolDefinition.of("search_flights", "Search for flights", flightSchema),
    ToolDefinition.of("book_hotel", "Book a hotel room", hotelSchema)
);

// Tools and tasks go in each Example's metadata
Dataset dataset = Dataset.builder()
    .name("Travel Agent")
    .addExample(Example.builder()
        .input("input", "Find flights to Paris and book a hotel for 5 nights")
        .expectedOutput("toolCalls", List.of(
            ToolCall.of("search_flights", Map.of()),
            ToolCall.of("book_hotel", Map.of())
        ))
        .metadata("tools", tools)
        .metadata("tasks", List.of("Search flights", "Book hotel"))
        .build())
    .build();

ExperimentResult result = Experiment.builder()
    .name("Travel Agent Evaluation")
    .dataset(dataset)
    .task(example -> {
        AgentTrace trace = travelAgent.run(example.input());
        return trace.toOutputMap();
    })
    .evaluators(List.of(
        ToolCallValidityEvaluator.builder().build(),
        ToolCorrectnessEvaluator.builder().build(),
        TaskCompletionEvaluator.builder().judge(judge).build(),
        ToolArgumentHallucinationEvaluator.builder().judge(judge).build()
    ))
    .build()
    .run();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val judge = JudgeLM { prompt -> openAiClient.generate(prompt) }

val tools = listOf(
    ToolDefinition.of("search_flights", "Search for flights", flightSchema),
    ToolDefinition.of("book_hotel", "Book a hotel room", hotelSchema)
)

// Tools and tasks go in each Example's metadata
val dataset = Dataset.builder()
    .name("Travel Agent")
    .addExample(Example.builder()
        .input("input", "Find flights to Paris and book a hotel for 5 nights")
        .expectedOutput("toolCalls", listOf(
            ToolCall.of("search_flights", mapOf()),
            ToolCall.of("book_hotel", mapOf())
        ))
        .metadata("tools", tools)
        .metadata("tasks", listOf("Search flights", "Book hotel"))
        .build())
    .build()

val result = experiment {
    name = "Travel Agent Evaluation"
    dataset(dataset)
    task { example ->
        val trace = travelAgent.run(example.input())
        trace.toOutputMap()
    }
    evaluators {
        toolCallValidity { }
        toolCorrectness { }
        taskCompletion(judge) { }
        toolArgumentHallucination(judge) { }
    }
}.run()
```

  </TabItem>
</Tabs>

## Best Practices

- **Start with rule-based evaluators.** `ToolCallValidityEvaluator` and `ToolCorrectnessEvaluator` don't need an LLM and give fast, deterministic feedback. Add LLM-based evaluators once the basics pass.
- **Evaluate tool definitions in CI.** Use `ToolNameReliabilityEvaluator` and `ToolDescriptionReliabilityEvaluator` to catch tool definition quality issues before they affect agent behavior.
- **Use AgentTrace for consistent data flow.** Build `AgentTrace` objects in your `Task` and use `toOutputMap()` to produce the standard format all evaluators expect.
- **Combine with standard evaluators.** Use `LLMJudgeEvaluator` to check the quality of the agent's final response alongside tool-level checks.
