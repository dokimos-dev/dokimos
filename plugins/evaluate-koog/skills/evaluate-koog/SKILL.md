---
name: evaluate-koog
description: Sets up evaluation of Koog AI agents using Dokimos. Use this skill when the user wants to evaluate, test, or benchmark a Koog agent, or integrate Koog with Dokimos evaluations. Also use when the user mentions Koog agents, AI agent evaluation, or agent testing with Dokimos.
---

# Evaluate Koog Agent

Set up Dokimos evaluation for a Koog AI agent. The user will describe their agent and evaluation goals via `$ARGUMENTS`.

## Where things live

- **Koog support**: `dokimos-koog/src/main/kotlin/dev/dokimos/koog/KoogSupport.kt`
- **Trace collector**: `dokimos-koog/src/main/kotlin/dev/dokimos/koog/KoogTraceCollector.kt`
- **Koog tests**: `dokimos-koog/src/test/kotlin/dev/dokimos/koog/`
- **Maven dependency**: `dev.dokimos:dokimos-koog`

Before writing code, read `KoogSupport.kt` to understand the available utilities.

## Key functions

`KoogSupport.kt` provides:

- **`asJudge(agentCall: suspend (String) -> String)`** — wraps any suspend function into a `JudgeLM`
- **`asJudge(agent: () -> AIAgent<String, String>)`** — wraps a Koog agent factory into a `JudgeLM`
- **`AIAgent.runBlocking(input, context)`** — extension to run a Koog agent synchronously

## Setting up evaluation

### Using a Koog agent as the system under test

```kotlin
val agent: () -> AIAgent<String, String> = { createMyAgent() }

val task = Task { example ->
    val input = example.inputs()["input"] as String
    val output = agent().runBlocking(input)
    mapOf("output" to output)
}

val result = Experiment.builder()
    .name("Koog Agent Evaluation")
    .dataset(dataset)
    .task(task)
    .evaluator(ExactMatchEvaluator.builder().build())
    .build()
    .run()
```

### Using a Koog agent as a judge

```kotlin
val judge = asJudge { prompt -> myAgent().run(prompt) }
// or
val judge = asJudge { createMyAgent() }

val evaluator = LLMJudgeEvaluator.builder()
    .name("helpfulness")
    .judge(judge)
    .criteria("Is the response helpful and accurate?")
    .evaluationParams(listOf(
        EvalTestCaseParam.INPUT,
        EvalTestCaseParam.ACTUAL_OUTPUT,
        EvalTestCaseParam.EXPECTED_OUTPUT
    ))
    .threshold(0.7)
    .build()
```

### Kotlin DSL (with dokimos-kotlin)

If the user has `dokimos-kotlin` as a dependency, use the DSL:

```kotlin
val result = experiment {
    name = "Koog Agent Eval"
    dataset = Dataset.fromJson(Path.of("datasets/qa.json"))
    task { example ->
        val output = agent().runBlocking(example.input())
        mapOf("output" to output)
    }
    evaluator(ExactMatchEvaluator.builder().build())
}
```

## Dependencies

The user needs `dokimos-koog`:

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-koog</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

Koog itself is a provided-scope dependency — the user must bring their own version. The collector reads the event context reflectively, so one build works across Koog 0.6.4 through 1.0.0.

## Evaluating tool calls, not just final output

If the Koog agent uses tools, capture its tool calls with `KoogTraceCollector` and evaluate them with the agent evaluators. Install the collector on the agent's event handler with `collectAgentTrace`, run the agent, then read the trace.

```kotlin
import dev.dokimos.koog.KoogTraceCollector
import dev.dokimos.koog.collectAgentTrace

val collector = KoogTraceCollector()
val agent = AIAgent(/* ... */) {
    install(EventHandler) { collectAgentTrace(collector) }
}

val response = agent.run(userInput)
val testCase = collector.toAgentTrace(response).toTestCase(userInput, tools)
val validity = toolCallValidity { }.evaluate(testCase)
```

For the full agent evaluator set, use the `evaluate-agent` skill.

## Steps

1. Understand from `$ARGUMENTS` what the Koog agent does and how it's constructed
2. Determine if the agent is the system under test, the judge, or both
3. Create a dataset appropriate for the agent's domain
4. Wire up the evaluation using `KoogSupport` utilities
5. Write tests in Kotlin using MockK for mocking
