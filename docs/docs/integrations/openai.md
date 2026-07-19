---
sidebar_position: 7
---

# OpenAI Java SDK Integration

Evaluate an agent built directly on the [OpenAI Java SDK](https://github.com/openai/openai-java) (`com.openai:openai-java`) with Dokimos. This integration captures the tool calls the model makes into a Dokimos `AgentTrace` so the agent evaluators can score them, and turns an `OpenAIClient` into an LLM judge.

Reach for this when you call OpenAI's SDK directly, without LangChain4j or Spring AI.

## What this integration gives you

**Tool-call capture.** Turn an OpenAI chat-completion response into a Dokimos `AgentTrace`, `ToolCall`, and `ToolDefinition` with `OpenAiSupport`, so the [agent evaluators](../evaluation/agent-evaluation) (tool correctness, trajectory, task completion, and so on) run on your real agent runs.

**One-line judge.** Turn an `OpenAIClient` into a Dokimos `JudgeLM` with `asJudge` for the LLM-based evaluators.

**Bring your own SDK version.** `openai-java` is a `provided` dependency, so Dokimos uses whatever version you already ship.

## Setup

Add the OpenAI integration dependency. It brings in `dokimos-core`; you supply your own `com.openai:openai-java` version.

Maven:

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-openai</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

Gradle (Kotlin DSL):

```kotlin
implementation("dev.dokimos:dokimos-openai:${dokimosVersion}")
```

## Use an OpenAI client as a judge

`asJudge` wraps an `OpenAIClient` as a `JudgeLM` for the LLM-based evaluators. You pass the model the judge should use:

```java
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.openai.OpenAiSupport;

OpenAIClient client = OpenAIOkHttpClient.fromEnv(); // reads OPENAI_API_KEY

JudgeLM judge = OpenAiSupport.asJudge(client, ChatModel.GPT_5_NANO);
```

## Capture tool calls into a trace

Run your tool-calling loop as usual. Each time the model calls a tool, capture it with `OpenAiSupport.toToolCall(toolCall, result)` and add it to an `AgentTrace`:

```java
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import dev.dokimos.core.agents.AgentTrace;
import dev.dokimos.openai.OpenAiSupport;

var traceBuilder = AgentTrace.builder();
var params = ChatCompletionCreateParams.builder()
    .model(ChatModel.GPT_5_NANO)
    .addUserMessage("Find flights to Paris and book a hotel for 5 nights");
tools.forEach(params::addTool); // your List<ChatCompletionTool>

for (int i = 0; i < 10; i++) {
    var message = client.chat().completions().create(params.build()).choices().get(0).message();
    params.addMessage(message);

    var toolCalls = message.toolCalls().orElse(List.of());
    if (toolCalls.isEmpty()) {
        traceBuilder.finalResponse(message.content().orElse(""));
        break;
    }

    for (var toolCall : toolCalls) {
        var func = toolCall.asFunction();
        String result = yourApp.executeTool(func.function().name(), func.function().arguments(Map.class));

        traceBuilder.addToolCall(OpenAiSupport.toToolCall(toolCall, result));

        params.addMessage(ChatCompletionToolMessageParam.builder()
            .toolCallId(func.id())
            .content(result)
            .build());
    }
}

AgentTrace trace = traceBuilder.build();
```

For a single response, capture the whole thing at once with `OpenAiSupport.toAgentTrace(message, id -> resultFor(id))`, which pulls the final text and every function tool call. A `resultLookup` that returns `null` is fine when you have not executed the tools.

## Convert the tool definitions

To run the validity and reliability checks, convert the OpenAI tools the agent was given into Dokimos `ToolDefinition`s:

```java
List<ToolDefinition> tools = OpenAiSupport.toToolDefinitions(chatCompletionTools);
```

## Evaluate the trace

Build a test case from the trace and run the agent evaluators. See [Agent Evaluation](../evaluation/agent-evaluation) for the full evaluator set:

```java
Map<String, Object> outputs = trace.toOutputMap();
EvalTestCase testCase = EvalTestCase.builder()
    .input("Find flights to Paris and book a hotel for 5 nights")
    .actualOutput("toolCalls", outputs.get("toolCalls"))
    .actualOutput("output", outputs.get("output"))
    .metadata("tools", tools)
    .build();

EvalResult result = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
```

## Complete example

See [`OpenAIAgentEvaluationExample.java`](https://github.com/dokimos-dev/dokimos/blob/master/dokimos-examples/src/main/java/dev/dokimos/examples/basic/OpenAIAgentEvaluationExample.java) for a full runnable example: a tool-calling loop, trace capture, and every agent evaluator end to end. Set `OPENAI_API_KEY` and run `main`.
