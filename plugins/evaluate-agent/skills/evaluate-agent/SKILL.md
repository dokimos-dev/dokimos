---
name: evaluate-agent
description: Sets up evaluation of AI agents using Dokimos. Use this skill when the user wants to evaluate, test, or benchmark an AI agent that uses tools, or assess tool call correctness, task completion, argument hallucination, or tool definition quality. Also use when the user mentions agent evaluation, tool call validation, agent testing, or tool reliability checks.
---

# Evaluate AI Agent

Set up Dokimos agent evaluation for an AI agent that uses tools. The user will describe their agent and evaluation goals via `$ARGUMENTS`.

## Where things live

- **Agent data model**: `dokimos-core/src/main/java/dev/dokimos/core/agents/` — `ToolCall.java`, `ToolDefinition.java`, `AgentTrace.java`
- **Agent evaluators**: `dokimos-core/src/main/java/dev/dokimos/core/evaluators/agents/`
- **Kotlin DSL**: `dokimos-kotlin/src/main/kotlin/dev/dokimos/kotlin/dsl/evaluators/EvaluatorDsl.kt` and `CoreDsl.kt`
- **Example**: `dokimos-examples/src/main/java/dev/dokimos/examples/basic/AgentEvaluationExample.java`
- **Integration tests**: `dokimos-core/src/test/java/dev/dokimos/core/integration/AgentEvaluatorIT.java`
- **Maven dependency**: `dev.dokimos:dokimos-core` (agent evaluation is built in, no extra dependencies)

Before writing code, read the data model files and any relevant evaluator files to understand exact APIs.

## Available evaluators

| Evaluator | What it checks | LLM required? | Default threshold |
|-----------|---------------|:---:|:---:|
| `ToolCallValidityEvaluator` | Tool calls match JSON schema (names, required params, types, enums) | No | 1.0 |
| `ToolCorrectnessEvaluator` | Agent used the expected set of tools | No | 1.0 |
| `ToolTrajectoryEvaluator` | Tool-call sequence matches an expected trajectory (selectable match mode) | No | 1.0 |
| `ToolErrorEvaluator` | Tool calls succeeded (no error results) | No | 1.0 |
| `ToolEfficiencyEvaluator` | No redundant or duplicate tool calls | No | 1.0 |
| `TaskCompletionEvaluator` | Agent completed the user's tasks | Yes | 0.5 |
| `ToolArgumentHallucinationEvaluator` | Arguments are grounded in user input | Yes | 0.8 |
| `ToolNameReliabilityEvaluator` | Tool names follow conventions (snake_case, conciseness, clarity, ordering, intent) | Optional | 0.8 |
| `ToolDescriptionReliabilityEvaluator` | Tool descriptions are well-crafted (structure, clarity, args documented, examples, usage notes) | Optional | 0.8 |

Five of the nine are deterministic and need no LLM (`ToolCallValidity`, `ToolCorrectness`, `ToolTrajectory`, `ToolError`, `ToolEfficiency`), so they run in a unit test or CI gate with no API key.

`ToolTrajectoryEvaluator` match modes: `STRICT`, `IN_ORDER` (default, LCS), `ANY_ORDER`, `SUPERSET`, `SUBSET`, `PRECISION`, `RECALL`. Supply an `ArgumentMatcher` to also assert arguments, optionally per tool.

## Data model essentials

- **`ToolCall`**: A tool invocation record (name, arguments map, optional result string, optional metadata). Create with `ToolCall.of(name, args)` or `ToolCall.builder()`.
- **`ToolDefinition`**: A tool's contract (name, description, JSON Schema map for arguments). Create with `ToolDefinition.of(name, desc, schema)`. The schema must follow JSON Schema format with `"type": "object"`, `"properties"`, and optional `"required"`.
- **`AgentTrace`**: Wraps a full agent execution (tool calls, reasoning steps, final response). Build with `AgentTrace.builder().addToolCall(...).finalResponse(...).build()`. Call `toOutputMap()` to get a map with keys `"output"`, `"toolCalls"`, `"reasoningSteps"` for use in `EvalTestCase`. `trace.toTestCase(input, tools, tasks)` is a shortcut that builds a ready-to-use `EvalTestCase` (tools and tasks are optional overloads).
- **Multi-turn (`ConversationTrajectory` in `dev.dokimos.core.conversation`)**: when tools are called across a conversation, attach them to each assistant turn with `Message.assistant(content, toolCalls)` (or the builder's `assistantMessage(content, toolCalls)`). Score per turn with `trajectory.toolCallsByTurn()` (one `List<ToolCall>` per assistant turn) plus the deterministic evaluators, or build a whole-conversation case with `toTestCase(tools)` (deterministic, input is the last user turn) / `toTestCase(tools, tasks)` (judge, input is the full transcript). `TrajectoryEvaluator.includeToolCalls(true)` renders the calls into the judge prompt.

## Extracting traces from a framework

Do not hand-build `AgentTrace` if the agent runs on a supported framework. Each extractor turns a framework run into a trace:

- **LangChain4j** (`dokimos-langchain4j`): `LangChain4jSupport.toAgentTrace(result)` from an `AiServices` `Result<T>`; `toToolDefinitions(specs)` for the tools.
- **Spring AI** (`dokimos-spring-ai`): `SpringAiSupport.toAgentTrace(assistantMessage, toolResponseMessages)` (results matched by tool-call id); `toToolDefinitions(defs)`.
- **Koog** (`dokimos-koog`): install a `KoogTraceCollector` via `collectAgentTrace(collector)` in the event handler, run the agent, then `collector.toAgentTrace(response)`.
- **OpenAI Java SDK**: copy the `OpenAiAgentTraces` bridge from `dokimos-examples` (not a published module) and build the trace as the tool-calling loop runs.

See the agent-evaluation guide for the full extractor reference: https://dokimos.dev/evaluation/agent-evaluation

## EvalTestCase key conventions

Evaluators read from specific keys in `EvalTestCase` maps:

| Map | Key | Type | Used by |
|-----|-----|------|---------|
| `actualOutputs` | `"toolCalls"` | `List<ToolCall>` | Validity, Correctness, Trajectory, Tool Error, Tool Efficiency, Hallucination |
| `actualOutputs` | `"output"` | `String` | Task Completion |
| `expectedOutputs` | `"toolCalls"` | `List<ToolCall>` | Correctness, Trajectory |
| `metadata` | `"tools"` | `List<ToolDefinition>` | Validity, Name Reliability, Description Reliability |
| `metadata` | `"tasks"` | `List<String>` | Task Completion |
| `metadata` | `"constraints"` | `String` | Task Completion |

**IMPORTANT**: In an Experiment, evaluators read metadata from the `Example`, NOT from the `Experiment`. Put `"tools"` and `"tasks"` in each Example's metadata (in the dataset), not on the Experiment builder.

## Evaluator configuration

```java
// Rule-based — just use defaults or set threshold/strictMode
ToolCallValidityEvaluator.builder().strictMode(true).threshold(1.0).build();
ToolCorrectnessEvaluator.builder().matchMode(ToolCorrectnessEvaluator.MatchMode.NAMES_ONLY).build();

// LLM-based — require a JudgeLM
JudgeLM judge = prompt -> openAiClient.generate(prompt);
TaskCompletionEvaluator.builder().judge(judge).threshold(0.5).build();
ToolArgumentHallucinationEvaluator.builder().judge(judge).threshold(0.8).build();

// Tool reliability — optional JudgeLM for semantic checks
ToolNameReliabilityEvaluator.builder().judge(judge).threshold(0.8).build();
ToolDescriptionReliabilityEvaluator.builder().maxInputArgs(5).maxOptionalArgs(3).judge(judge).build();
```

`ToolCorrectnessEvaluator` match modes: `NAMES_ONLY` (default, F1 score), `NAMES_AND_ORDER` (LCS similarity), `NAMES_AND_ARGS` (full structural comparison).

## Minimal pattern — single test case

```java
List<ToolDefinition> tools = List.of(
    ToolDefinition.of("search_flights", "Search for flights", Map.of(
        "type", "object",
        "properties", Map.of(
            "origin", Map.of("type", "string", "description", "Origin airport code"),
            "destination", Map.of("type", "string", "description", "Destination airport code")
        ),
        "required", List.of("origin", "destination")
    ))
);

AgentTrace trace = AgentTrace.builder()
    .addToolCall(ToolCall.of("search_flights", Map.of("origin", "JFK", "destination", "CDG")))
    .finalResponse("Found flights to Paris.")
    .build();

var testCase = EvalTestCase.builder()
    .input("Find flights from NYC to Paris")
    .actualOutput("toolCalls", trace.toolCalls())
    .actualOutput("output", trace.finalResponse())
    .metadata("tools", tools)
    .build();

var result = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
```

## Experiment pattern — across a dataset

```java
JudgeLM judge = prompt -> openAiClient.generate(prompt);

// Tools and tasks go in each Example's metadata
Dataset dataset = Dataset.builder()
    .name("Agent Evaluation")
    .addExample(Example.builder()
        .input("input", "Find flights to Paris and book a hotel")
        .expectedOutput("toolCalls", List.of(
            ToolCall.of("search_flights", Map.of()),
            ToolCall.of("book_hotel", Map.of())
        ))
        .metadata("tools", tools)
        .metadata("tasks", List.of("Search flights", "Book hotel"))
        .build())
    .build();

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
    .build()
    .run();
```

## Kotlin DSL

```kotlin
val judge = JudgeLM { prompt -> openAiClient.generate(prompt) }

// Standalone evaluator
val validity = toolCallValidity { threshold = 1.0 }

// In an experiment
evaluators {
    toolCallValidity { strictMode = true }
    toolCorrectness { matchMode = ToolCorrectnessEvaluator.MatchMode.NAMES_ONLY }
    toolTrajectory { matchMode = ToolTrajectoryEvaluator.MatchMode.IN_ORDER }
    toolError { }
    toolEfficiency { }
    taskCompletion(judge) { threshold = 0.5 }
    toolArgumentHallucination(judge) { threshold = 0.8 }
    toolNameReliability { judge = judgeLM }
    toolDescriptionReliability { maxInputArgs = 5; maxOptionalArgs = 3 }
}
```

## Steps

1. Understand from `$ARGUMENTS` what the agent does, what tools it uses, and the evaluation goals
2. Read the data model files (`ToolCall.java`, `ToolDefinition.java`, `AgentTrace.java`) and any relevant evaluator source files
3. Determine which evaluators are needed based on the table above
4. Define `ToolDefinition` objects for each tool the agent can use (with JSON Schema for arguments including `"type"`, `"properties"`, `"required"`)
5. Create a dataset with examples — each Example should include `metadata("tools", tools)` and optionally `metadata("tasks", taskList)` and `expectedOutput("toolCalls", expectedCalls)`
6. Build the `Task` using `AgentTrace.toOutputMap()` to capture tool calls and reasoning
7. Wire evaluators and run the experiment
8. Start with rule-based evaluators (`ToolCallValidityEvaluator`, `ToolCorrectnessEvaluator`) first — they don't need an LLM and give fast deterministic feedback. Add LLM-based evaluators once basics pass.
