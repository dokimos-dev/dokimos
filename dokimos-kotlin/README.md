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
- `task { example -> mapOf("output" to ...) }`
- Evaluators: `exactMatch {}`, `regex {}`, `llmJudge(judge) {}`

For advanced cases you can still pass fully constructed `Task`, `Dataset`, or `Evaluator` instances into the DSL blocks.
