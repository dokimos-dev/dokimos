---
name: evaluate-openai
description: Sets up evaluation of an OpenAI Java SDK agent using Dokimos. Use this skill when the user wants to evaluate, test, or benchmark an agent built directly on the OpenAI Java SDK that uses tools (function calling), or assess its tool call correctness, task completion, or argument hallucination. Also use when the user mentions OpenAI function calling evaluation or evaluating an OpenAI tool-calling loop with Dokimos.
---

# Evaluate an OpenAI Java SDK Agent

Set up Dokimos agent evaluation for an agent built directly on the OpenAI Java SDK (`com.openai:openai-java`). The user will describe their agent and evaluation goals via `$ARGUMENTS`.

## Where things live

- **OpenAI support utilities**: `dev.dokimos:dokimos-openai` (`dev.dokimos.openai.OpenAiSupport`)
- **Runnable example**: `dokimos-examples/src/main/java/dev/dokimos/examples/basic/OpenAIAgentEvaluationExample.java`
- **Agent data model and evaluators**: `dokimos-core/src/main/java/dev/dokimos/core/agents/` and `.../evaluators/agents/`

Add the `dev.dokimos:dokimos-openai` dependency (it brings in `dokimos-core`). It expects you to supply your own `com.openai:openai-java` version.

## The support class

`OpenAiSupport` converts the SDK's tool calls into Dokimos `ToolCall`s and tool definitions:

- **`toAgentTrace(ChatCompletionMessage message, Function<String,String> resultLookup)`**: a full trace from a message; `resultLookup` maps a tool-call id to its result. Non-function (custom) tool calls are skipped.
- **`toToolCall(ChatCompletionMessageToolCall toolCall, String result)`**: one function tool call plus the result you got from executing it.
- **`toToolCalls(ChatCompletionMessage message, Function<String,String> resultLookup)`**: all function tool calls on a message.
- **`toToolDefinitions(List<ChatCompletionTool> tools)`**: the tools the agent was given, so the validity and reliability evaluators can run.

Arguments are parsed from the model's JSON; if they cannot be parsed they default to an empty map rather than failing the trace.

## Pattern: capture the trace in your tool-calling loop

```java
AgentTrace.Builder trace = AgentTrace.builder();

for (int i = 0; i < MAX_TURNS; i++) {
    var message = client.chat().completions().create(params).choices().get(0).message();
    var toolCalls = message.toolCalls().orElse(List.of());
    if (toolCalls.isEmpty()) {
        trace.finalResponse(message.content().orElse(""));
        break;
    }
    for (var toolCall : toolCalls) {
        String result = executeTool(toolCall.asFunction().function().name());
        trace.addToolCall(OpenAiSupport.toToolCall(toolCall, result));
        // feed the result back to the model as a tool message, then continue the loop
    }
}

AgentTrace agentTrace = trace.build();
```

## Evaluate the trace

```java
List<ToolDefinition> tools = List.of(
    ToolDefinition.of("search_flights", "Search for flights", Map.of(
        "type", "object",
        "properties", Map.of("origin", Map.of("type", "string")),
        "required", List.of("origin")))
);

EvalTestCase testCase = agentTrace.toTestCase("Find flights from NYC to Paris", tools);

var validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
var correctness = ToolCorrectnessEvaluator.builder().build().evaluate(testCase);
```

## Steps

1. Understand from `$ARGUMENTS` what the agent does and which tools (functions) it exposes.
2. Add the `dev.dokimos:dokimos-openai` dependency if it is not already present.
3. In the tool-calling loop, capture each executed tool call with `OpenAiSupport.toToolCall(...)` and set the final response.
4. Define a `ToolDefinition` for each function (JSON Schema with `"type"`, `"properties"`, `"required"`) so the validity and reliability evaluators can run.
5. Build the test case with `trace.toTestCase(input, tools)` and run the agent evaluators. Start with the deterministic ones (validity, correctness), then add LLM-based ones.
6. For the full agent evaluator set and the Experiment-across-a-dataset pattern, use the `evaluate-agent` skill.
