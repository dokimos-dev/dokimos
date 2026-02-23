---
name: evaluate-agent
description: Sets up evaluation of AI agents using Dokimos. Use this skill when the user wants to evaluate, test, or benchmark an AI agent that uses tools, or assess tool call correctness, task completion, argument hallucination, or tool definition quality. Also use when the user mentions agent evaluation, tool call validation, agent testing, or tool reliability checks.
---

# Evaluate AI Agent

Set up Dokimos agent evaluation for an AI agent that uses tools. The user will describe their agent and evaluation goals via `$ARGUMENTS`.

## Where things live

- **Agent data model**: `dokimos-core/src/main/java/dev/dokimos/core/agents/` — `ToolCall.java`, `ToolDefinition.java`, `AgentTrace.java`
- **Agent evaluators**: `dokimos-core/src/main/java/dev/dokimos/core/evaluators/agents/`
- **Example**: `dokimos-examples/src/main/java/dev/dokimos/examples/basic/AgentEvaluationExample.java`
- **Maven dependency**: `dev.dokimos:dokimos-core`

Before writing code, read the data model files and any relevant evaluator files.

## Available evaluators

| Evaluator | What it checks | LLM required? | Default threshold |
|-----------|---------------|:---:|:---:|
| `ToolCallValidityEvaluator` | Tool calls match JSON schema (names, required params, types) | No | 1.0 |
| `ToolCorrectnessEvaluator` | Agent used the expected set of tools | No | 1.0 |
| `TaskCompletionEvaluator` | Agent completed the user's tasks | Yes | 0.5 |
| `ToolArgumentHallucinationEvaluator` | Arguments are grounded in user input | Yes | 0.8 |
| `ToolNameReliabilityEvaluator` | Tool names follow conventions | Optional | 0.8 |
| `ToolDescriptionReliabilityEvaluator` | Tool descriptions are well-crafted | Optional | 0.8 |

## EvalTestCase key conventions

| Map | Key | Type | Used by |
|-----|-----|------|---------|
| `actualOutputs` | `"toolCalls"` | `List<ToolCall>` | Validity, Correctness, Hallucination |
| `actualOutputs` | `"output"` | `String` | Task Completion |
| `expectedOutputs` | `"toolCalls"` | `List<ToolCall>` | Correctness |
| `metadata` | `"tools"` | `List<ToolDefinition>` | Validity, Name Reliability, Description Reliability |
| `metadata` | `"tasks"` | `List<String>` | Task Completion |
| `metadata` | `"constraints"` | `String` | Task Completion |

## Minimal pattern

```java
// 1. Define tools
List<ToolDefinition> tools = List.of(
    ToolDefinition.of("search_flights", "Search for flights", flightSchema),
    ToolDefinition.of("book_hotel", "Book a hotel room", hotelSchema)
);

// 2. Capture agent trace
AgentTrace trace = AgentTrace.builder()
    .addToolCall(ToolCall.of("search_flights", Map.of("origin", "JFK", "destination", "CDG")))
    .addToolCall(ToolCall.of("book_hotel", Map.of("city", "Paris", "nights", 5)))
    .finalResponse("Found flights and booked your hotel.")
    .build();

// 3. Build test case and evaluate
var testCase = EvalTestCase.builder()
    .input("Find flights to Paris and book a hotel for 5 nights")
    .actualOutput("toolCalls", trace.toolCalls())
    .metadata("tools", tools)
    .build();

var result = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
```

## As an experiment

```java
JudgeLM judge = prompt -> openAiClient.generate(prompt);

ExperimentResult result = Experiment.builder()
    .name("Agent Evaluation")
    .dataset(dataset)
    .task(example -> {
        AgentTrace trace = myAgent.run(example.input());
        return trace.toOutputMap();
    })
    .evaluators(List.of(
        ToolCallValidityEvaluator.builder().build(),
        ToolCorrectnessEvaluator.builder().build(),
        TaskCompletionEvaluator.builder().judge(judge).build(),
        ToolArgumentHallucinationEvaluator.builder().judge(judge).build()
    ))
    .metadata(Map.of("tools", tools))
    .build()
    .run();
```

## Steps

1. Understand from `$ARGUMENTS` what the agent does, what tools it uses, and the evaluation goals
2. Determine which evaluators are needed based on the table above
3. Define `ToolDefinition` objects for each tool the agent can use
4. Create a dataset with examples (input queries and optionally expected tool calls)
5. Build the `Task` using `AgentTrace.toOutputMap()` to capture tool calls
6. Wire evaluators and run the experiment
7. Start with rule-based evaluators first, add LLM-based ones once basics pass
