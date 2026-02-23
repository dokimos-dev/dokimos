---
name: evaluate-agent
description: Sets up evaluation of AI agents using Dokimos. Use this skill when the user wants to evaluate, test, or benchmark an AI agent that uses tools, or assess tool call correctness, task completion, argument hallucination, or tool definition quality. Also use when the user mentions agent evaluation, tool call validation, agent testing, or tool reliability checks.
---

# Evaluate AI Agent

Set up Dokimos agent evaluation for an AI agent that uses tools. The user will describe their agent and evaluation goals via `$ARGUMENTS`.

## Where things live

- **Agent data model**: `dokimos-core/src/main/java/dev/dokimos/core/agents/`
  - `ToolCall.java` — represents a single tool invocation
  - `ToolDefinition.java` — describes a tool's contract (name, description, JSON schema)
  - `AgentTrace.java` — wraps a complete agent execution trace
- **Agent evaluators**: `dokimos-core/src/main/java/dev/dokimos/core/evaluators/agents/`
- **Example**: `dokimos-examples/src/main/java/dev/dokimos/examples/basic/AgentEvaluationExample.java`
- **Maven dependency**: `dev.dokimos:dokimos-core`

Before writing code, read the data model files (`ToolCall.java`, `ToolDefinition.java`, `AgentTrace.java`) and any relevant evaluator files.

## Data model

### ToolCall

Represents a single tool invocation made by an agent:

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

### ToolDefinition

Describes a tool's contract with JSON schema for arguments:

```java
ToolDefinition tool = ToolDefinition.of("search_flights", "Search for available flights", Map.of(
    "type", "object",
    "properties", Map.of(
        "origin", Map.of("type", "string", "description", "Origin airport code"),
        "destination", Map.of("type", "string", "description", "Destination airport code")
    ),
    "required", List.of("origin", "destination")
));

tool.requiredParameters();  // ["origin", "destination"]
tool.parameterNames();      // {"origin", "destination"}
```

### AgentTrace

Wraps a complete agent execution. Use `toOutputMap()` to convert to the format evaluators expect:

```java
AgentTrace trace = AgentTrace.builder()
    .addReasoningStep("User wants flights to Paris, searching...")
    .addToolCall(ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "CDG")))
    .addReasoningStep("Found flights. Now booking hotel.")
    .addToolCall(ToolCall.of("book_hotel", Map.of("city", "Paris", "nights", 3)))
    .finalResponse("I've found flights and booked your hotel in Paris.")
    .build();

// In a Task:
Task agentTask = example -> {
    AgentTrace trace = runAgent(example.input());
    return trace.toOutputMap(); // Returns map with "output", "toolCalls", "reasoningSteps"
};
```

## Available evaluators

### Rule-based (no LLM required)

| Evaluator | What it checks | Default threshold |
|-----------|---------------|-------------------|
| `ToolCallValidityEvaluator` | Tool calls are syntactically correct per JSON schema | 1.0 |
| `ToolCorrectnessEvaluator` | Agent used the expected set of tools | 1.0 |
| `ToolNameReliabilityEvaluator` | Tool names follow naming conventions | 0.8 |
| `ToolDescriptionReliabilityEvaluator` | Tool descriptions are well-crafted | 0.8 |

### LLM-based (require JudgeLM)

| Evaluator | What it checks | Default threshold |
|-----------|---------------|-------------------|
| `TaskCompletionEvaluator` | Agent completed the user's requested tasks | 0.5 |
| `ToolArgumentHallucinationEvaluator` | Tool call arguments are grounded in user input | 0.8 |

Note: `ToolNameReliabilityEvaluator` and `ToolDescriptionReliabilityEvaluator` also accept an optional `JudgeLM` for semantic checks (ambiguity, clarity).

## Evaluation patterns

### Tool call validation (rule-based)

```java
List<ToolDefinition> tools = List.of(
    ToolDefinition.of("search_flights", "Search for flights", flightSchema),
    ToolDefinition.of("book_hotel", "Book a hotel room", hotelSchema)
);

var evaluator = ToolCallValidityEvaluator.builder()
    .strictMode(false) // Only enforce no-extra-params when schema says so
    .threshold(1.0)
    .build();

var testCase = EvalTestCase.builder()
    .actualOutput("toolCalls", agentTrace.toolCalls())
    .metadata("tools", tools)
    .build();

EvalResult result = evaluator.evaluate(testCase);
```

### Tool correctness (rule-based)

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

Match modes: `NAMES_ONLY` (default), `NAMES_AND_ORDER`, `NAMES_AND_ARGS`.

### Task completion (LLM-based)

```java
JudgeLM judge = prompt -> openAiClient.generate(prompt);

var evaluator = TaskCompletionEvaluator.builder()
    .judge(judge)
    .threshold(0.5)
    .build();

var testCase = EvalTestCase.builder()
    .input("Find flights to Paris and book a hotel")
    .metadata("tasks", List.of("Search for flights", "Book a hotel"))
    .metadata("constraints", "Budget under $500")
    .build();

EvalResult result = evaluator.evaluate(testCase);
```

### Argument hallucination detection (LLM-based)

```java
var evaluator = ToolArgumentHallucinationEvaluator.builder()
    .judge(judge)
    .threshold(0.8)
    .build();

var testCase = EvalTestCase.builder()
    .input("Find flights from NYC to Paris on March 15")
    .actualOutput("toolCalls", List.of(
        ToolCall.of("search_flights", Map.of("origin", "NYC", "destination", "CDG", "date", "2026-03-15"))
    ))
    .build();
```

### Tool definition quality (rule-based + optional LLM)

```java
// Tool naming quality
var nameEvaluator = ToolNameReliabilityEvaluator.builder()
    .judge(judge) // optional, for semantic checks
    .threshold(0.8)
    .build();

// Tool description quality
var descEvaluator = ToolDescriptionReliabilityEvaluator.builder()
    .maxOptionalArgs(3)
    .judge(judge) // optional, for clarity checks
    .threshold(0.8)
    .build();

var testCase = EvalTestCase.builder()
    .metadata("tools", availableTools)
    .build();
```

### Full experiment with AgentTrace

```java
JudgeLM judge = prompt -> openAiClient.generate(prompt);

List<ToolDefinition> tools = List.of(
    ToolDefinition.of("search_flights", "Search for flights", flightSchema),
    ToolDefinition.of("book_hotel", "Book a hotel room", hotelSchema)
);

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
        ToolArgumentHallucinationEvaluator.builder().judge(judge).build(),
        ToolNameReliabilityEvaluator.builder().build(),
        ToolDescriptionReliabilityEvaluator.builder().build()
    ))
    .metadata(Map.of("tools", tools))
    .build()
    .run();
```

### Kotlin DSL

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

## EvalTestCase key conventions

Agent evaluators use these keys:

| Map | Key | Type | Description |
|-----|-----|------|-------------|
| `actualOutputs` | `"toolCalls"` | `List<ToolCall>` | Tool calls the agent made |
| `actualOutputs` | `"output"` | `String` | Agent's final text response |
| `expectedOutputs` | `"toolCalls"` | `List<ToolCall>` | Expected tool calls (for correctness) |
| `metadata` | `"tools"` | `List<ToolDefinition>` | Available tool definitions |
| `metadata` | `"tasks"` | `List<String>` | Task list (for completion eval) |
| `metadata` | `"constraints"` | `String` | Product constraints (for completion eval) |

## Dependencies

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-core</artifactId>
    <version>${dokimos.version}</version>
</dependency>

<!-- Optional: Kotlin DSL -->
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-kotlin</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

No additional dependencies needed — agent evaluation is built into `dokimos-core`.

## Steps

1. Understand from `$ARGUMENTS` what the agent does, what tools it uses, and the evaluation goals
2. Determine which evaluators are needed:
   - **Tool proficiency**: `ToolCallValidityEvaluator`, `ToolCorrectnessEvaluator`, `ToolArgumentHallucinationEvaluator`
   - **Task completion**: `TaskCompletionEvaluator`
   - **Tool definition quality**: `ToolNameReliabilityEvaluator`, `ToolDescriptionReliabilityEvaluator`
3. Define `ToolDefinition` objects for each tool the agent can use
4. Create a dataset with examples (input queries and optionally expected tool calls)
5. Build the `Task` using `AgentTrace.toOutputMap()` to capture tool calls and reasoning
6. Wire evaluators and run the experiment
7. Start with rule-based evaluators first, add LLM-based ones once basics pass
