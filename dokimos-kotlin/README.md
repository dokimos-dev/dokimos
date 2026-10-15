# Dokimos Kotlin DSL

Idiomatic Kotlin builders for `dokimos-core`.

```kotlin
import dev.dokimos.kotlin.dsl.*
import dev.dokimos.core.EvalTestCaseParam
import dev.dokimos.core.JudgeLM

val judge: JudgeLM = JudgeLM { """{"score":0.8,"reason":"ok"}""" }

val experiment = experiment {
    name = "Customer Support Check"

    dataset {
        name = "customer-support"
        example {
            input = "How long does shipping take?"
            expected = "Standard shipping takes 5-7 business days."
        }
    }

    task { example ->
        val prompt = "Answer concisely: ${example.input()}"
        mapOf("output" to callModel(prompt)) // call your model
    }

    evaluators {
        exactMatch { threshold = 0.5 }
        llmJudge(judge) {
            name = "Answer Quality"
            criteria = "Is the answer helpful, accurate, and professionally worded?"
            evaluationParams = listOf(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT)
            threshold = 0.7
        }
    }
}
```

Key helpers:
- `experiment { ... }`, `dataset { ... }`, `example { ... }`
- Tasks: `task { example -> mapOf("output" to ...) }`, `typedTask<T> { ... }`, `measuredTask { ... }` (adds `CallMetrics`), `suspendTask { ... }` (coroutines)
- Evaluators: `exactMatch {}`, `regex {}`, `structuralMatch {}`, `llmJudge(judge) {}`, and the agent evaluators (`toolCallValidity {}`, `toolCorrectness {}`, `toolTrajectory {}`, `toolError {}`, `toolEfficiency {}`, `planQuality {}`, `planAdherence {}`, ...)
- Conversations (`dev.dokimos.kotlin.dsl.conversation`): `trajectory {}`, `simulator {}`, `llmUser(judge) {}`, `trajectoryEvaluator(judge) {}`, `criterion(name, description, weight)`, `goldenGenerator {}`
- Agents (`dev.dokimos.kotlin.dsl.agents`): `tools {}`, `toolDefinition(name) {}`, `toolCall(name) {}`, `toolCalls {}`, `agentTrace {}`, `agentTestCase {}`
- Regression gate (`dev.dokimos.kotlin.dsl.gate` / `dev.dokimos.kotlin.core`): `gateConfig {}` builds a `GateConfig`; `ExperimentResult.assertNoRegression(baseline) { ... }` takes the config inline

Agent evaluation without hand-written JSON schemas or output-map keys:

```kotlin
import dev.dokimos.kotlin.dsl.agents.*

val tools = tools {
    tool("search_flights") {
        description = "Search for available flights"
        parameters {
            string("origin", "Origin airport IATA code", required = true)
            string("destination", "Destination airport IATA code", required = true)
        }
    }
}

val testCase = agentTestCase {
    input = "Find flights from NYC to Paris"
    trace {
        toolCall("search_flights", mapOf("origin" to "JFK", "destination" to "CDG"))
        finalResponse = "Found your flights."
    }
    tools(tools)
    expectedToolCall("search_flights", mapOf())
    tasks("Search for flights")
}

val result = toolCallValidity().evaluate(testCase)
```

For advanced cases you can still pass fully constructed `Task`, `Dataset`, or `Evaluator` instances into the DSL blocks.
