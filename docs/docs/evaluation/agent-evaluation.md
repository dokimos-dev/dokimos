---
sidebar_position: 7
---

# Agent Evaluation

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

This page shows you how to score an AI agent on the tools it called, not just its final reply.

AI agents pick tools on their own, reason through multi-step problems, and call external APIs. Checking a single response is not enough. You want to know **what tools the agent used**, **how it used them**, and **whether it finished the task**.

Dokimos gives you nine agent evaluators and a portable data model for tool calls and tool definitions. The data model works with any framework. Five evaluators are deterministic and need no LLM, so you can run them in a unit test or a CI gate with no API key.

## Quick Start

Capture the agent's tool calls, list the tools it has, then run evaluators. Copy this and adjust the tool names to your agent.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// 1. List the tools your agent can use
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

// 4. Build a test case
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

// 5. Pick the evaluators you need and run them
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

Pick from these nine. The first five need no LLM. The next two always need a judge. The last two take an optional judge.

| Evaluator                             | What it checks                                                                                  | LLM required? | Default threshold |
| ------------------------------------- | ----------------------------------------------------------------------------------------------- | :-----------: | :---------------: |
| `ToolCallValidityEvaluator`           | Tool calls match their JSON schema (names, required params, types, enums)                       |      No       |        1.0        |
| `ToolCorrectnessEvaluator`            | Agent used the expected set of tools                                                            |      No       |        1.0        |
| `ToolTrajectoryEvaluator`             | Tool-call sequence matches an expected trajectory                                               |      No       |        1.0        |
| `ToolErrorEvaluator`                  | Tool calls succeeded (no error results)                                                         |      No       |        1.0        |
| `ToolEfficiencyEvaluator`             | No redundant tool calls                                                                         |      No       |        1.0        |
| `TaskCompletionEvaluator`             | Agent completed the user's requested tasks                                                      |      Yes      |        0.5        |
| `ToolArgumentHallucinationEvaluator`  | Tool call arguments are grounded in user input                                                  |      Yes      |        0.8        |
| `ToolNameReliabilityEvaluator`        | Tool names follow naming conventions (snake_case, conciseness, clarity, ordering, intent)       |   Optional    |        0.8        |
| `ToolDescriptionReliabilityEvaluator` | Tool descriptions are well written (structure, clarity, args documented, examples, usage notes) |   Optional    |        0.8        |

### ToolCallValidityEvaluator

Checks each tool call against its JSON schema. It confirms the tool name exists, required params are present, types match, enum values are valid, and no unexpected params slip in (in strict mode, or when the schema sets `additionalProperties: false`).

Score = fraction of valid tool calls.

### ToolCorrectnessEvaluator

Compares the tools the agent used against the tools you expected. Pick one of three match modes.

| Mode                   | Comparison                                     |
| ---------------------- | ---------------------------------------------- |
| `NAMES_ONLY` (default) | Set of tool names (F1 score)                   |
| `NAMES_AND_ORDER`      | Names plus invocation order (LCS similarity)   |
| `NAMES_AND_ARGS`       | Full structural comparison including arguments |

In `NAMES_AND_ARGS` mode, arguments use a tolerant matcher by default, so numerically equal values like `1` and `1.0` count as equal. See [Argument Matching](#argument-matching) below.

### ToolTrajectoryEvaluator

Scores the agent's tool-call _sequence_ against an expected one. Deterministic, no LLM. Use it to assert how an agent should move through a task, and choose how strict the order and arguments need to be.

| Mode        | Meaning                                              | Score        |
| ----------- | ---------------------------------------------------- | ------------ |
| `STRICT`    | Same calls, same order, arguments match              | 0 or 1       |
| `IN_ORDER`  | Expected appears as an ordered subsequence           | graded (LCS) |
| `ANY_ORDER` | Same calls in any order                              | graded       |
| `SUPERSET`  | Actual contains every expected call (extras allowed) | 0 or 1       |
| `SUBSET`    | Every actual call is in expected (omissions allowed) | 0 or 1       |
| `PRECISION` | Matched / number of actual calls                     | graded       |
| `RECALL`    | Matched / number of expected calls                   | graded       |

It reads `toolCalls` from `actualOutputs` and `expectedOutputs`. The unordered modes use maximum bipartite matching, so repeated tool names are counted in the best possible way.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
ToolTrajectoryEvaluator trajectory = ToolTrajectoryEvaluator.builder()
    .matchMode(ToolTrajectoryEvaluator.MatchMode.IN_ORDER)
    .build();

var testCase = EvalTestCase.builder()
    .actualOutput("toolCalls", trace.toolCalls())
    .expectedOutput("toolCalls", List.of(
        ToolCall.of("search_flights", Map.of()),
        ToolCall.of("book_hotel", Map.of())
    ))
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val trajectory = toolTrajectory {
    matchMode = ToolTrajectoryEvaluator.MatchMode.IN_ORDER
}
```

  </TabItem>
</Tabs>

By default arguments use a tolerant matcher, so numerically equal values like `1` and `1.0` match. To compare tool names and order only, pass `ArgumentMatcher.of(ArgMatchMode.IGNORE)`. You can also override the matcher for one tool.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
ToolTrajectoryEvaluator trajectory = ToolTrajectoryEvaluator.builder()
    .matchMode(ToolTrajectoryEvaluator.MatchMode.ANY_ORDER)
    .argumentMatcher(ArgumentMatcher.tolerant())                            // default for every tool
    .argumentMatcher("book_hotel", ArgumentMatcher.of(ArgMatchMode.SUBSET)) // override one tool
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val trajectory = toolTrajectory {
    matchMode = ToolTrajectoryEvaluator.MatchMode.ANY_ORDER
    argumentMatcher = ArgumentMatcher.tolerant()                  // default for every tool
    argumentMatcher("book_hotel", ArgumentMatcher.of(ArgMatchMode.SUBSET)) // override one tool
}
```

  </TabItem>
</Tabs>

### ToolErrorEvaluator

Looks at each tool call's result and scores the fraction that succeeded. Deterministic, no LLM. A call counts as failed when its result is null or blank, when it is a JSON object with a top-level `error` field, or when it matches a custom predicate you supply.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
ToolErrorEvaluator toolError = ToolErrorEvaluator.builder()
    .errorDetector(result -> result.contains("HTTP 500")) // optional, on top of the defaults
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val toolError = toolError {
    errorDetector = { it.contains("HTTP 500") } // optional, on top of the defaults
}
```

  </TabItem>
</Tabs>

### ToolEfficiencyEvaluator

Finds redundant tool calls. The score is the ratio of distinct calls to total calls, so `1.0` means no redundancy. Two calls are redundant when they share a name and matching arguments. Consecutive duplicates also show up in the result metadata as a loop signal. Deterministic, no LLM.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
ToolEfficiencyEvaluator efficiency = ToolEfficiencyEvaluator.builder().build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val efficiency = toolEfficiency { }
```

  </TabItem>
</Tabs>

Treat efficiency as a signal, not a hard gate. A legitimately repeated call (a retry, say) lowers the score, so tune the threshold to your case.

### TaskCompletionEvaluator

Sends the user-agent dialog and a task list to a judge LLM, which decides which tasks were completed. Score = fraction of completed tasks.

Provide tasks with `metadata("tasks", List.of("Search flights", "Book hotel"))` and optional constraints with `metadata("constraints", "Budget under $500")`.

### ToolArgumentHallucinationEvaluator

Uses a judge LLM to check whether each tool call's argument values can be derived from the user's input. Score = fraction of non-hallucinated tool calls.

### ToolNameReliabilityEvaluator

Checks tool names with 5 checks. Rule-based checks always run: `snakecase_format` (strict snake_case), `conciseness` (7 segments or fewer), `intent_over_implementation` (blocklist for patterns like `_with_llm`, `_via_api`). LLM checks need a judge: `clarity` (purpose clear from the name alone), `name_order` (follows operation_system_entity_data ordering), plus a semantic `intent_over_implementation`.

Without a judge, only the 3 rule-based checks run. The score is based on the checks that actually ran.

### ToolDescriptionReliabilityEvaluator

Checks tool descriptions with 13 checks. Rule-based checks always run: `input_arguments_clarity` (params have descriptions), `input_arguments_types` (params have types), `max_num_input_arguments` (5 or fewer by default), `max_optional_input_arguments` (3 or fewer by default). LLM checks need a judge: `general_structure`, `has_examples`, `has_usage_notes`, `intent_over_implementation`, `clarity`, `redundancy`, `input_arguments_enum`, `input_arguments_format`, `return_statement_quality`.

Without a judge, only the 4 rule-based checks run. The score is based on the checks that actually ran.

## Argument Matching

`ToolTrajectoryEvaluator` and `ToolCorrectnessEvaluator` (in `NAMES_AND_ARGS` mode) compare arguments through an `ArgumentMatcher`. The default, `TolerantArgumentMatcher`, compares structurally with a few deliberate tolerances.

- **Numbers** compare by value, so `1`, `1.0`, and `1L` are equal. This is always on. Treating `1` and `1.0` as different is a JSON number-widening artifact, not a real difference.
- **Strings** compare exactly by default. Whitespace trimming and case-insensitivity are opt-in, so turning them on never silently changes existing pass/fail outcomes.
- **Maps and lists** compare recursively with the same rules.

`ArgMatchMode` sets how the key sets are compared.

| Mode       | Actual arguments must...                            |
| ---------- | --------------------------------------------------- |
| `EXACT`    | have the same keys as expected, all values matching |
| `SUBSET`   | contain every expected entry (extra keys allowed)   |
| `SUPERSET` | be contained in expected (omissions allowed)        |
| `IGNORE`   | not be compared at all                              |

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
ArgumentMatcher matcher = TolerantArgumentMatcher.builder()
    .mode(ArgMatchMode.SUBSET)   // only the expected arguments must be present and correct
    .trimStrings(true)
    .caseInsensitive(true)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val matcher = TolerantArgumentMatcher.builder()
    .mode(ArgMatchMode.SUBSET)   // only the expected arguments must be present and correct
    .trimStrings(true)
    .caseInsensitive(true)
    .build()
```

  </TabItem>
</Tabs>

Shortcuts: `ArgumentMatcher.tolerant()` gives the default `EXACT` matcher, and `ArgumentMatcher.of(mode)` gives a tolerant matcher in another mode. For anything custom, pass a lambda: `(expected, actual) -> ...`.

## Data Model

Three records in `dev.dokimos.core.agents` hold agent execution data.

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

The `result` is a single string. `result(String)` stores whatever you pass, exactly as is. Use it when your tool already produced a string. When the tool produced a structured value (a record, POJO, map, or list), use `resultJson(Object)` instead. It serializes the value to a compact, single-line JSON string and stores it in the same `result` component, so you stop hand-escaping JSON. A `null` value serializes to the JSON literal `null`.

```java
record Confirmation(String confirmation, double total) {}

// Before: hand-escaped JSON, easy to get wrong
ToolCall.builder()
    .name("book_hotel")
    .result("{\"confirmation\": \"ABC123\", \"total\": 540.0}")
    .build();

// After: serialize the value, no escaping
ToolCall.builder()
    .name("book_hotel")
    .resultJson(new Confirmation("ABC123", 540.0))
    .build();
```

Read a structured result back, type-safe, with `resultAs(Class<T>)` or `resultAs(OutputType<T>)`, the counterpart of `resultJson`. This is what makes a sequential agent's `output -> input -> output` chain assertable: capture each step's structured result, then read it back as a real object. Tool-call arguments read back the same way with `argumentsAs(Class<T>)` and `argumentsAs(OutputType<T>)`. This is one stop on Dokimos's typed-data pipeline. See the [Structured & Typed Data](./structured-typed-data.md) hub for how it connects to typed task outputs, structural matching, and the typed accessors on `EvalTestCase`.

```java
ToolCall call = ToolCall.builder()
    .name("book_hotel")
    .resultJson(new Confirmation("ABC123", 540.0))
    .build();

Confirmation booked = call.resultAs(Confirmation.class);   // back to a typed object
List<Confirmation> many =
    call.resultAs(new OutputType<List<Confirmation>>() {}); // generics via OutputType
```

:::note
Both writers set the same `result` field, so downstream evaluators (`ToolErrorEvaluator`, the hallucination judge, and anything reading `ToolCall.result()`) see an identical string either way. `resultAs` parses that string as JSON (the form `resultJson` produces). A `null` or blank result returns `null`, and a raw non-JSON string from `result(String)` is not parseable, so use `result()` for that.
:::

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

When you evaluate a single trace directly, `toTestCase()` is a shortcut that builds a ready-to-use `EvalTestCase`. The tool calls, final response, and reasoning steps go into the actual outputs, and the tool definitions and tasks go into metadata. Use it so the validity and completion evaluators don't fail just because the `tools` or `tasks` entries were left out.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
EvalTestCase testCase = trace.toTestCase(
    "Find flights from NYC to Paris", // user input
    tools,                            // List<ToolDefinition>, optional
    List.of("Search flights"));       // tasks, optional

// Shorter overloads when you don't need every part:
EvalTestCase justInput = trace.toTestCase("Find flights from NYC to Paris");
EvalTestCase withTools = trace.toTestCase("Find flights from NYC to Paris", tools);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val testCase = trace.toTestCase(
    "Find flights from NYC to Paris", // user input
    tools,                            // List<ToolDefinition>, optional
    listOf("Search flights"))         // tasks, optional

// Shorter overloads when you don't need every part:
val justInput = trace.toTestCase("Find flights from NYC to Paris")
val withTools = trace.toTestCase("Find flights from NYC to Paris", tools)
```

  </TabItem>
</Tabs>

## Extracting Traces from Your Framework

The examples above assume you already have an `AgentTrace`. In practice your agent runs on a framework, and Dokimos ships extractors that turn a framework's own run result into an `AgentTrace`, so you don't hand-write the mapping. Each extractor captures the tool calls (name, parsed arguments, and result) and the final response.

<Tabs groupId="framework" defaultValue="langchain4j">
  <TabItem value="langchain4j" label="LangChain4j">

`AiServices` methods that return `Result<T>` carry the tool executions for a run. Pass the result to `LangChain4jSupport.toAgentTrace`, and convert the tool specifications with `toToolDefinitions` so the validity and reliability evaluators can see the tools the agent was given.

```java
import dev.dokimos.langchain4j.LangChain4jSupport;

Result<String> result = assistant.chat(userMessage);

AgentTrace trace = LangChain4jSupport.toAgentTrace(result);
List<ToolDefinition> tools = LangChain4jSupport.toToolDefinitions(toolSpecifications);

EvalTestCase testCase = trace.toTestCase(userMessage, tools);
```

  </TabItem>
  <TabItem value="spring-ai" label="Spring AI">

An `AssistantMessage` carries the tool calls the model made. The results come back in the `ToolResponseMessage`s. Pass both so the trace carries the calls and what the tools returned (matched by tool-call id).

```java
import dev.dokimos.springai.SpringAiSupport;

AgentTrace trace = SpringAiSupport.toAgentTrace(assistantMessage, toolResponseMessages);
List<ToolDefinition> tools = SpringAiSupport.toToolDefinitions(toolDefinitions);

EvalTestCase testCase = trace.toTestCase(userMessage, tools);
```

  </TabItem>
  <TabItem value="koog" label="Koog">

Koog reports tool calls through its event handler. Install a `KoogTraceCollector` with `collectAgentTrace`, run the agent, then read the trace.

```kotlin
import dev.dokimos.koog.KoogTraceCollector
import dev.dokimos.koog.collectAgentTrace

val collector = KoogTraceCollector()
val agent = AIAgent(/* ... */) {
    install(EventHandler) { collectAgentTrace(collector) }
}

val response = agent.run(userInput)
val testCase = collector.toAgentTrace(response).toTestCase(userInput, tools)
```

The collector tolerates framework versions: it reads the completion context reflectively, so one build works across Koog 0.6.4 through 1.0.0.

  </TabItem>
  <TabItem value="embabel" label="Embabel">

Embabel reports tool calls through its `AgenticEventListener`. Attach an `EmbabelTraceCollector` to your run with `EmbabelSupport.attach`, run the agent, then read the trace. The tool definitions are synthesized from the observed tool names with an empty schema, so build them by hand for full `ToolDescriptionReliabilityEvaluator` coverage.

```java
import dev.dokimos.embabel.EmbabelSupport;
import dev.dokimos.embabel.EmbabelTraceCollector;

EmbabelTraceCollector collector = EmbabelSupport.attach(invocationBuilder);

String response = invocationBuilder.build(String.class).invoke(userInput);

AgentTrace trace = collector.trace();
List<ToolDefinition> tools = EmbabelSupport.toToolDefinitions(collector);

EvalTestCase testCase = trace.toTestCase(userInput, tools);
```

See the [Embabel integration](../integrations/embabel) for the full flow and limitations.

  </TabItem>
  <TabItem value="spring-ai-alibaba" label="Spring AI Alibaba">

For Spring AI Alibaba Graph agents, `SpringAiAlibabaSupport.toAgentTrace` reads the run's `OverAllState` and windows over its `messages` list to recover the tool calls per turn. Convert the tool callbacks the agent was given with `toToolDefinitions`.

```java
import dev.dokimos.springai.alibaba.SpringAiAlibabaSupport;

AgentTrace trace = SpringAiAlibabaSupport.toAgentTrace(state);
List<ToolDefinition> tools = SpringAiAlibabaSupport.toToolDefinitions(toolCallbacks);

EvalTestCase testCase = trace.toTestCase(userInput, tools);
```

  </TabItem>
  <TabItem value="openai" label="OpenAI">

The OpenAI Java SDK has no published Dokimos module, so a small reusable bridge lives in the examples module (copy it into your project). It turns the SDK's tool calls into Dokimos `ToolCall`s as your tool-calling loop runs.

```java
AgentTrace.Builder trace = AgentTrace.builder();
for (var toolCall : message.toolCalls().orElse(List.of())) {
    String result = myApp.execute(toolCall);
    trace.addToolCall(OpenAiAgentTraces.toToolCall(toolCall, result));
}
trace.finalResponse(finalMessage.content().orElse(""));

EvalTestCase testCase = trace.build().toTestCase(userMessage, tools);
```

  </TabItem>
</Tabs>

## EvalTestCase Keys

Agent evaluators read these keys from `EvalTestCase`.

| Map               | Key             | Type                   | Used by                                                                       |
| ----------------- | --------------- | ---------------------- | ----------------------------------------------------------------------------- |
| `actualOutputs`   | `"toolCalls"`   | `List<ToolCall>`       | Validity, Correctness, Trajectory, Tool Error, Tool Efficiency, Hallucination |
| `actualOutputs`   | `"output"`      | `String`               | Task Completion                                                               |
| `expectedOutputs` | `"toolCalls"`   | `List<ToolCall>`       | Correctness, Trajectory                                                       |
| `metadata`        | `"tools"`       | `List<ToolDefinition>` | Validity, Name Reliability, Description Reliability                           |
| `metadata`        | `"tasks"`       | `List<String>`         | Task Completion                                                               |
| `metadata`        | `"constraints"` | `String`               | Task Completion                                                               |

## Evaluator Configuration

Every evaluator uses the builder pattern. Common options:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// Rule-based: just set threshold
ToolCallValidityEvaluator.builder()
    .strictMode(true)       // Fail on any unexpected param
    .threshold(1.0)
    .build();

ToolCorrectnessEvaluator.builder()
    .matchMode(ToolCorrectnessEvaluator.MatchMode.NAMES_AND_ORDER)
    .build();

// LLM-based: provide a judge
TaskCompletionEvaluator.builder()
    .judge(judgeLM)
    .threshold(0.5)
    .build();

// Tool reliability: optional judge for semantic checks
ToolNameReliabilityEvaluator.builder()
    .judge(judgeLM)  // optional
    .threshold(0.8)
    .build();

ToolDescriptionReliabilityEvaluator.builder()
    .maxInputArgs(5)    // default 5
    .maxOptionalArgs(3) // default 3
    .judge(judgeLM)     // optional, enables 9 additional LLM checks
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
    toolDescriptionReliability { maxInputArgs = 5; maxOptionalArgs = 3; judge = judgeLM }
}
```

  </TabItem>
</Tabs>

## Running as an Experiment

To evaluate an agent across a dataset, put tool definitions and task lists in each **Example's metadata**. That is where evaluators look for them at runtime.

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

## OpenAI Integration

Here is a full example that captures tool calls from an OpenAI agent and evaluates them. There are three bridge points:

1. Convert your `ToolDefinition` to OpenAI's `ChatCompletionTool` format.
2. Extract tool call names and arguments from the OpenAI response.
3. Build an `AgentTrace` from the captured execution.

```java
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.*;
import com.openai.models.chat.completions.*;
import dev.dokimos.core.agents.*;

OpenAIClient client = OpenAIOkHttpClient.fromEnv();

// Define tools once, use them for both OpenAI and evaluation
List<ToolDefinition> tools = List.of(
    ToolDefinition.of("search_flights", "Search for flights", flightSchema),
    ToolDefinition.of("book_hotel", "Book a hotel room", hotelSchema)
);

// Convert to OpenAI format
ChatCompletionTool toOpenAITool(ToolDefinition def) {
    var params = FunctionParameters.builder();
    for (var entry : def.inputSchema().entrySet()) {
        params.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
    }
    return ChatCompletionTool.ofFunction(
        ChatCompletionFunctionTool.builder()
            .function(FunctionDefinition.builder()
                .name(def.name())
                .description(def.description())
                .parameters(params.build())
                .build())
            .build());
}

// Run the tool-calling loop
var traceBuilder = AgentTrace.builder();
var paramsBuilder = ChatCompletionCreateParams.builder()
    .model(ChatModel.GPT_5_NANO)
    .addUserMessage("Find flights to Paris and book a hotel for 5 nights");
tools.forEach(t -> paramsBuilder.addTool(toOpenAITool(t)));

for (int i = 0; i < 10; i++) {
    var completion = client.chat().completions().create(paramsBuilder.build());
    var message = completion.choices().get(0).message();
    paramsBuilder.addMessage(message);

    var toolCalls = message.toolCalls().orElse(List.of());
    if (toolCalls.isEmpty()) {
        traceBuilder.finalResponse(message.content().orElse(""));
        break;
    }

    for (var toolCall : toolCalls) {
        var func = toolCall.asFunction();
        var function = func.function();
        String result = yourApp.executeTool(function.name(), function.arguments(Map.class));

        traceBuilder.addToolCall(ToolCall.builder()
            .name(function.name())
            .arguments(function.arguments(Map.class))
            .result(result)
            .build());

        paramsBuilder.addMessage(ChatCompletionToolMessageParam.builder()
            .toolCallId(func.id())
            .content(result)
            .build());
    }
}

AgentTrace trace = traceBuilder.build();

// Evaluate
var testCase = EvalTestCase.builder()
    .input("Find flights to Paris and book a hotel for 5 nights")
    .actualOutput("toolCalls", trace.toolCalls())
    .actualOutput("output", trace.finalResponse())
    .metadata("tools", tools)
    .metadata("tasks", List.of("Search for flights", "Book a hotel"))
    .build();

var result = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
```

The loop runs up to 10 iterations because the model may call tools across several turns. It might search first, then book based on those results. Each iteration is one API round-trip, and the loop exits when the model returns a final text response instead of tool calls.

> See [`OpenAIAgentEvaluationExample.java`](https://github.com/dokimos-dev/dokimos/blob/master/dokimos-examples/src/main/java/dev/dokimos/examples/basic/OpenAIAgentEvaluationExample.java) for a complete runnable example.

## Best Practices

- **Start with rule-based evaluators.** `ToolCallValidityEvaluator` and `ToolCorrectnessEvaluator` need no LLM and give fast, deterministic feedback. Add LLM-based evaluators once the basics pass.
- **Evaluate tool definitions in CI.** Use `ToolNameReliabilityEvaluator` and `ToolDescriptionReliabilityEvaluator` to catch tool definition quality issues before they change agent behavior.
- **Use AgentTrace for consistent data flow.** Build `AgentTrace` objects in your `Task` and call `toOutputMap()` to produce the standard format every evaluator expects.
- **Combine with standard evaluators.** Use `LLMJudgeEvaluator` to check the quality of the agent's final response alongside the tool-level checks.
