---
sidebar_position: 7
---

# Agent Evaluation

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

AI agents autonomously use tools, reason through multi-step problems, and interact with external APIs. Evaluating them requires more than checking a single response — you need to assess **what tools they used**, **how they used them**, and **whether they accomplished the task**.

Dokimos provides a framework-agnostic agent evaluation system inspired by [Booking.com's AI agent evaluation research](https://booking.ai/ai-agent-evaluation-82e781439d97). It works with any agent framework (LangChain4j, Spring AI, Koog, or custom) by providing a portable data model for tool calls and tool definitions.

## Evaluation Categories

Agent evaluators fall into two categories:

| Category | What it checks | Evaluators |
|----------|---------------|------------|
| **Black box** | Did the agent accomplish the task? | `TaskCompletionEvaluator` |
| **Glass box** | Did the agent use tools correctly? Are tool definitions well-crafted? | `ToolCallValidityEvaluator`, `ToolCorrectnessEvaluator`, `ToolArgumentHallucinationEvaluator`, `ToolNameReliabilityEvaluator`, `ToolDescriptionReliabilityEvaluator` |

## Data Model

Three records in `dev.dokimos.core.agents` represent agent execution data.

### ToolCall

A single tool invocation made by an agent.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// Quick creation
ToolCall call = ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "LAX"));

// Full builder
ToolCall call = ToolCall.builder()
    .name("book_hotel")
    .argument("city", "Paris")
    .argument("nights", 3)
    .result("{\"confirmation\": \"ABC123\"}")
    .metadata("latencyMs", 150)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val call = ToolCall.of("search_flights", mapOf("origin" to "NYC", "destination" to "LAX"))

val call = ToolCall.builder()
    .name("book_hotel")
    .argument("city", "Paris")
    .argument("nights", 3)
    .result("""{"confirmation": "ABC123"}""")
    .metadata("latencyMs", 150)
    .build()
```

  </TabItem>
</Tabs>

### ToolDefinition

Describes a tool's contract: name, description, and JSON schema for arguments.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
ToolDefinition tool = ToolDefinition.of("search_flights", "Search for available flights", Map.of(
    "type", "object",
    "properties", Map.of(
        "origin", Map.of("type", "string", "description", "Origin airport code"),
        "destination", Map.of("type", "string", "description", "Destination airport code")
    ),
    "required", List.of("origin", "destination")
));

// Helper methods
tool.requiredParameters();  // ["origin", "destination"]
tool.parameterNames();      // {"origin", "destination"}
tool.parameterSchema("origin"); // {"type": "string", "description": "Origin airport code"}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val tool = ToolDefinition.of("search_flights", "Search for available flights", mapOf(
    "type" to "object",
    "properties" to mapOf(
        "origin" to mapOf("type" to "string", "description" to "Origin airport code"),
        "destination" to mapOf("type" to "string", "description" to "Destination airport code")
    ),
    "required" to listOf("origin", "destination")
))

tool.requiredParameters()  // ["origin", "destination"]
tool.parameterNames()      // {"origin", "destination"}
```

  </TabItem>
</Tabs>

### AgentTrace

Wraps a complete agent execution trace. Use `toOutputMap()` to convert it into a format suitable for `EvalTestCase`.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
AgentTrace trace = AgentTrace.builder()
    .addReasoningStep("User wants flights to Paris, searching...")
    .addToolCall(ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "CDG")))
    .addReasoningStep("Found flights. Now booking hotel.")
    .addToolCall(ToolCall.of("book_hotel", Map.of("city", "Paris", "nights", 3)))
    .finalResponse("I've found flights and booked your hotel in Paris.")
    .metadata("totalLatencyMs", 2500)
    .build();

// Use in an experiment Task
Task agentTask = example -> {
    AgentTrace trace = runAgent(example.input());
    return trace.toOutputMap(); // Returns map with "output", "toolCalls", "reasoningSteps"
};
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val trace = AgentTrace.builder()
    .addReasoningStep("User wants flights to Paris, searching...")
    .addToolCall(ToolCall.of("search_flights", mapOf("origin" to "NYC", "destination" to "CDG")))
    .addReasoningStep("Found flights. Now booking hotel.")
    .addToolCall(ToolCall.of("book_hotel", mapOf("city" to "Paris", "nights" to 3)))
    .finalResponse("I've found flights and booked your hotel in Paris.")
    .metadata("totalLatencyMs", 2500)
    .build()

// Use in an experiment Task
val agentTask = Task { example ->
    val trace = runAgent(example.input())
    trace.toOutputMap() // Returns map with "output", "toolCalls", "reasoningSteps"
}
```

  </TabItem>
</Tabs>

## Evaluators

### ToolCallValidityEvaluator

Validates that tool calls are syntactically correct per their JSON schema definitions. **No LLM required.**

Checks per tool call:
1. Tool name exists in available tools
2. All required parameters are present
3. No unexpected parameters (if strict mode or `additionalProperties: false`)
4. Parameter types match schema types
5. Enum values match if specified

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
var evaluator = ToolCallValidityEvaluator.builder()
    .strictMode(false) // Only enforce no-extra-params when schema says so
    .threshold(1.0)
    .build();

var testCase = EvalTestCase.builder()
    .actualOutput("toolCalls", agentTrace.toolCalls())
    .metadata("tools", availableTools)
    .build();

EvalResult result = evaluator.evaluate(testCase);
// result.metadata().get("validationResults") → per-call details
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
evaluators {
    toolCallValidity {
        strictMode = false
        threshold = 1.0
    }
}
```

  </TabItem>
</Tabs>

**Default threshold:** 1.0 | **Score:** Fraction of valid tool calls

### ToolCorrectnessEvaluator

Checks whether the agent used the expected set of tools. **No LLM required.**

Supports three match modes:

| Mode | What it compares |
|------|-----------------|
| `NAMES_ONLY` (default) | Set of tool names used vs expected |
| `NAMES_AND_ORDER` | Tool names + invocation order (LCS similarity) |
| `NAMES_AND_ARGS` | Full structural comparison including arguments |

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
var evaluator = ToolCorrectnessEvaluator.builder()
    .matchMode(ToolCorrectnessEvaluator.MatchMode.NAMES_ONLY)
    .build();

var testCase = EvalTestCase.builder()
    .actualOutput("toolCalls", agentTrace.toolCalls())
    .expectedOutput("toolCalls", List.of(
        ToolCall.of("search_flights", Map.of()),
        ToolCall.of("book_hotel", Map.of())
    ))
    .build();

EvalResult result = evaluator.evaluate(testCase);
// result.metadata() contains: correctTools, redundantTools, missingTools
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
evaluators {
    toolCorrectness {
        matchMode = ToolCorrectnessEvaluator.MatchMode.NAMES_ONLY
    }
}
```

  </TabItem>
</Tabs>

**Default threshold:** 1.0 | **Score:** F1-score of tool name sets

### TaskCompletionEvaluator

Evaluates whether the agent completed the user's requested tasks using a judge LLM. **Requires JudgeLM.**

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
var evaluator = TaskCompletionEvaluator.builder()
    .judge(judgeLM)
    .threshold(0.5)
    .build();

var testCase = EvalTestCase.builder()
    .input("Find flights to Paris and book a hotel")
    .metadata("tasks", List.of("Search for flights", "Book a hotel"))
    .metadata("constraints", "Budget under $500")
    .build();

EvalResult result = evaluator.evaluate(testCase);
// result.metadata().get("taskResults") → per-task completion verdicts
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
evaluators {
    taskCompletion(judgeLM) {
        threshold = 0.5
        tasksKey = "tasks"
        constraintsKey = "constraints"
    }
}
```

  </TabItem>
</Tabs>

**Default threshold:** 0.5 | **Score:** Fraction of completed tasks

### ToolArgumentHallucinationEvaluator

Assesses whether tool call argument values are factually grounded in the user's input. **Requires JudgeLM.**

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
var evaluator = ToolArgumentHallucinationEvaluator.builder()
    .judge(judgeLM)
    .threshold(0.8)
    .build();

var testCase = EvalTestCase.builder()
    .input("Find flights from NYC to Paris on March 15")
    .actualOutput("toolCalls", List.of(
        ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "CDG", "date", "2026-03-15"))
    ))
    .build();

EvalResult result = evaluator.evaluate(testCase);
// result.metadata().get("verdicts") → per-call grounding verdicts
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
evaluators {
    toolArgumentHallucination(judgeLM) {
        threshold = 0.8
    }
}
```

  </TabItem>
</Tabs>

**Default threshold:** 0.8 | **Score:** Fraction of non-hallucinated tool calls

### ToolNameReliabilityEvaluator

Evaluates tool naming quality with rule-based checks and optional LLM checks.

Checks:
1. **Format:** Uses `snake_case` or `camelCase` consistently
2. **Length:** Name between 2-64 characters
3. **Verb-prefixed:** Starts with an action verb (get, search, create, update, delete, etc.)
4. **No ambiguity:** Does not use generic names (e.g., `process`, `handle`) — LLM-assisted if judge provided
5. **Descriptive:** Name clearly indicates purpose — LLM-assisted if judge provided

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// Rule-based only (no LLM needed)
var evaluator = ToolNameReliabilityEvaluator.builder()
    .threshold(0.8)
    .build();

// With LLM for semantic checks
var evaluator = ToolNameReliabilityEvaluator.builder()
    .judge(judgeLM)
    .threshold(0.8)
    .build();

var testCase = EvalTestCase.builder()
    .metadata("tools", availableTools)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
evaluators {
    toolNameReliability {
        threshold = 0.8
        judge = judgeLM // optional
    }
}
```

  </TabItem>
</Tabs>

**Default threshold:** 0.8 | **Score:** Average fraction of checks passed across all tools

### ToolDescriptionReliabilityEvaluator

Evaluates tool description quality with rule-based checks and optional LLM checks.

Checks:
1. **Non-empty:** Description is not blank
2. **Length:** Description between 10-500 characters
3. **Input args documented:** All required parameters have descriptions in the schema
4. **Max optional args:** No more than N optional parameters (default: 3)
5. **Clarity:** Description is clear and unambiguous — LLM-assisted if judge provided

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
var evaluator = ToolDescriptionReliabilityEvaluator.builder()
    .maxOptionalArgs(3)
    .threshold(0.8)
    .build();

var testCase = EvalTestCase.builder()
    .metadata("tools", availableTools)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
evaluators {
    toolDescriptionReliability {
        maxOptionalArgs = 3
        threshold = 0.8
        judge = judgeLM // optional
    }
}
```

  </TabItem>
</Tabs>

**Default threshold:** 0.8 | **Score:** Average fraction of checks passed across all tools

## EvalTestCase Convention

Agent evaluators use these keys in `EvalTestCase` maps:

| Map | Key | Type | Description |
|-----|-----|------|-------------|
| `actualOutputs` | `"toolCalls"` | `List<ToolCall>` | Tool calls the agent made |
| `actualOutputs` | `"output"` | `String` | Agent's final text response |
| `expectedOutputs` | `"toolCalls"` | `List<ToolCall>` | Expected tool calls (for correctness) |
| `metadata` | `"tools"` | `List<ToolDefinition>` | Available tool definitions |
| `metadata` | `"tasks"` | `List<String>` | Task list (for completion eval) |
| `metadata` | `"constraints"` | `String` | Product constraints (for completion eval) |

## Full Example

Here's a complete example evaluating a travel agent:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// Define available tools
List<ToolDefinition> tools = List.of(
    ToolDefinition.of("search_flights", "Search for available flights", flightSchema),
    ToolDefinition.of("book_hotel", "Book a hotel room", hotelSchema)
);

// Expected tool usage
List<ToolCall> expectedCalls = List.of(
    ToolCall.of("search_flights", Map.of()),
    ToolCall.of("book_hotel", Map.of())
);

// Define evaluators
JudgeLM judge = prompt -> openAiClient.generate(prompt);

List<Evaluator> evaluators = List.of(
    ToolCallValidityEvaluator.builder().build(),
    ToolCorrectnessEvaluator.builder().build(),
    TaskCompletionEvaluator.builder().judge(judge).build(),
    ToolArgumentHallucinationEvaluator.builder().judge(judge).build(),
    ToolNameReliabilityEvaluator.builder().build(),
    ToolDescriptionReliabilityEvaluator.builder().build()
);

// Run experiment
ExperimentResult result = Experiment.builder()
    .name("Travel Agent Evaluation")
    .dataset(dataset)
    .task(example -> {
        AgentTrace trace = travelAgent.run(example.input());
        Map<String, Object> outputs = new HashMap<>(trace.toOutputMap());
        return outputs;
    })
    .evaluators(evaluators)
    .metadata(Map.of("tools", tools))
    .build()
    .run();
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
        toolNameReliability { }
        toolDescriptionReliability { }
    }
}.run()
```

  </TabItem>
</Tabs>

## Best Practices

### Start with rule-based evaluators
`ToolCallValidityEvaluator` and `ToolCorrectnessEvaluator` don't need an LLM and give you fast, deterministic feedback. Add LLM-based evaluators once the basics pass.

### Evaluate tool definitions separately
Use `ToolNameReliabilityEvaluator` and `ToolDescriptionReliabilityEvaluator` in your CI pipeline to catch tool definition quality issues before they affect agent behavior.

### Use AgentTrace for consistent data flow
Build `AgentTrace` objects in your `Task` implementation and use `toOutputMap()` to produce the standard map format that all agent evaluators expect.

### Combine with standard evaluators
Agent evaluators complement existing evaluators. Use `LLMJudgeEvaluator` to check the quality of the agent's final response alongside tool-level checks.
